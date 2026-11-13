#include "gguf_reader.h"

#include <cstdio>
#include <cstring>
#include <algorithm>

namespace pocketclaw::gguf {
namespace {

constexpr uint32_t kMagic = 0x46554747;  // "GGUF" little endian
constexpr uint64_t kMaxString = 1ull << 20;
constexpr uint64_t kMaxArray = 1ull << 20;
constexpr int kMaxDepth = 4;

uint32_t rd32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

uint64_t rd64(const uint8_t* p) {
    return static_cast<uint64_t>(rd32(p)) | (static_cast<uint64_t>(rd32(p + 4)) << 32);
}

size_t typeSize(Type t) {
    switch (t) {
        case Type::UINT8: case Type::INT8: case Type::BOOL: return 1;
        case Type::UINT16: case Type::INT16: return 2;
        case Type::UINT32: case Type::INT32: case Type::FLOAT32: return 4;
        case Type::UINT64: case Type::INT64: case Type::FLOAT64: return 8;
        default: return 0;
    }
}

size_t bitsPerElement(GgmlType t) {
    switch (t) {
        case GgmlType::F32: return 32;
        case GgmlType::F16: return 16;
        case GgmlType::Q4_0: case GgmlType::Q4_1: return 5;
        case GgmlType::Q5_0: case GgmlType::Q5_1: return 6;
        case GgmlType::Q8_0: case GgmlType::Q8_1: return 9;
        case GgmlType::Q2_K: return 3;
        case GgmlType::Q3_K: return 4;
        case GgmlType::Q4_K: return 5;
        case GgmlType::Q5_K: return 6;
        case GgmlType::Q6_K: return 7;
    }
    return 0;
}

}  // namespace

bool Reader::open(const std::string& path) {
    close();
    file_ = std::fopen(path.c_str(), "rb");
    if (!file_) return false;

    std::fseek(file_, 0, SEEK_END);
    fileSize_ = static_cast<uint64_t>(std::ftell(file_));
    std::fseek(file_, 0, SEEK_SET);
    pos_ = 0;

    uint8_t hdr[24];
    if (fileSize_ < sizeof(hdr) || !readRaw(hdr, sizeof(hdr))) return false;
    if (rd32(hdr) != kMagic) return false;

    version_ = rd32(hdr + 4);
    if (version_ < 2 || version_ > 3) return false;

    tensorCount_ = rd64(hdr + 8);
    kvCount_ = rd64(hdr + 16);

    // Sanity: a real model has thousands of tensors, not billions. This catches a
    // truncated download before it becomes a multi-gigabyte allocation.
    if (tensorCount_ > 100000 || kvCount_ > 100000) return false;

    for (uint64_t i = 0; i < kvCount_; ++i) {
        std::string key;
        if (!readString(key)) return false;
        Value v;
        if (!readValue(v, 0)) return false;
        kv_.emplace(std::move(key), std::move(v));
    }

    tensors_.reserve(static_cast<size_t>(tensorCount_));
    for (uint64_t i = 0; i < tensorCount_; ++i) {
        TensorInfo info;
        if (!readString(info.name)) return false;
        uint32_t nDims = 0;
        uint8_t buf[4];
        if (!readRaw(buf, 4)) return false;
        nDims = rd32(buf);
        if (nDims > 8) return false;
        info.shape.resize(nDims);
        for (uint32_t d = 0; d < nDims; ++d) {
            if (!readRaw(buf, 4)) return false;
            info.shape[d] = rd32(buf);
        }
        uint8_t tbuf[4];
        if (!readRaw(tbuf, 4)) return false;
        info.type = static_cast<GgmlType>(rd32(tbuf));
        uint8_t obuf[8];
        if (!readRaw(obuf, 8)) return false;
        info.offset = rd64(obuf);

        const uint64_t elements = info.elementCount();
        const size_t bits = bitsPerElement(info.type);
        info.byteSize = (elements * bits + 7) / 8;
        tensors_.push_back(std::move(info));
    }

    // Tensor data starts at the next alignment boundary after the header block.
    const uint64_t align = alignment_;
    dataOffset_ = ((pos_ + align - 1) / align) * align;
    if (dataOffset_ > fileSize_) return false;
    return true;
}

void Reader::close() {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
    kv_.clear();
    tensors_.clear();
    pos_ = 0;
}

bool Reader::readRaw(void* dst, size_t n) {
    if (pos_ + n > fileSize_) return false;
    if (std::fread(dst, 1, n, file_) != n) return false;
    pos_ += n;
    return true;
}

bool Reader::skipBytes(uint64_t n) {
    if (pos_ + n > fileSize_) return false;
    pos_ += n;
    if (std::fseek(file_, static_cast<long>(pos_), SEEK_SET) != 0) return false;
    return true;
}

bool Reader::readString(std::string& out) {
    uint8_t len[8];
    if (!readRaw(len, 8)) return false;
    const uint64_t n = rd64(len);
    if (n > kMaxString || pos_ + n > fileSize_) return false;
    out.resize(static_cast<size_t>(n));
    if (n == 0) return true;
    return readRaw(out.data(), static_cast<size_t>(n));
}

bool Reader::readValue(Value& out, int depth) {
    if (depth > kMaxDepth) return false;
    uint8_t tbuf[4];
    if (!readRaw(tbuf, 4)) return false;
    out.type = static_cast<Type>(rd32(tbuf));

    switch (out.type) {
        case Type::UINT8: case Type::INT8: case Type::BOOL: {
            uint8_t v;
            if (!readRaw(&v, 1)) return false;
            out.u64 = v;
            out.b = v != 0;
            return true;
        }
        case Type::UINT16: case Type::INT16: {
            uint8_t v[2];
            if (!readRaw(v, 2)) return false;
            out.u64 = static_cast<uint16_t>(v[0] | (v[1] << 8));
            return true;
        }
        case Type::UINT32: case Type::INT32: {
            uint8_t v[4];
            if (!readRaw(v, 4)) return false;
            out.u64 = rd32(v);
            return true;
        }
        case Type::FLOAT32: {
            uint8_t v[4];
            if (!readRaw(v, 4)) return false;
            float f;
            std::memcpy(&f, v, 4);
            out.f64 = f;
            return true;
        }
        case Type::UINT64: case Type::INT64: {
            uint8_t v[8];
            if (!readRaw(v, 8)) return false;
            out.u64 = rd64(v);
            return true;
        }
        case Type::FLOAT64: {
            uint8_t v[8];
            if (!readRaw(v, 8)) return false;
            double d;
            std::memcpy(&d, v, 8);
            out.f64 = d;
            return true;
        }
        case Type::STRING:
            return readString(out.str);
        case Type::ARRAY: {
            uint8_t tb[4], lb[8];
            if (!readRaw(tb, 4)) return false;
            if (!readRaw(lb, 8)) return false;
            const uint64_t n = rd64(lb);
            if (n > kMaxArray) return false;
            out.array.resize(static_cast<size_t>(n));
            for (uint64_t i = 0; i < n; ++i) {
                if (!readValue(out.array[i], depth + 1)) return false;
            }
            return true;
        }
    }
    return false;
}

bool Reader::readTensor(const TensorInfo& info, void* out, size_t outLen) const {
    if (!file_ || outLen < info.byteSize) return false;
    if (dataOffset_ + info.offset + info.byteSize > fileSize_) return false;
    if (std::fseek(file_, static_cast<long>(dataOffset_ + info.offset), SEEK_SET) != 0) return false;
    return std::fread(out, 1, static_cast<size_t>(info.byteSize), file_) == info.byteSize;
}

std::string Reader::architecture() const {
    auto it = kv_.find("general.architecture");
    return it == kv_.end() ? std::string() : it->second.asString();
}

uint32_t Reader::contextLength() const {
    const std::string key = architecture() + ".context_length";
    auto it = kv_.find(key);
    return it == kv_.end() ? 0u : it->second.asU32();
}

uint32_t Reader::embeddingLength() const {
    const std::string key = architecture() + ".embedding_length";
    auto it = kv_.find(key);
    return it == kv_.end() ? 0u : it->second.asU32();
}

}  // namespace pocketclaw::gguf
