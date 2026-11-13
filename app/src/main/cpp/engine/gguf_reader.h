// GGUF container reader.
//
// The file format is a little-endian header, a metadata key/value block, a tensor
// info table, then the tensor data, all 32-byte aligned after the metadata. Reading
// it correctly is the difference between "the model loads" and "the app crashes on a
// device we cannot debug", so every read here is bounds-checked and every length is
// validated before it is used as an allocation size.
#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <unordered_map>

namespace pocketclaw::gguf {

enum class Type : uint32_t {
    UINT8 = 0, INT8 = 1, UINT16 = 2, INT16 = 3, UINT32 = 4, INT32 = 5,
    FLOAT32 = 6, BOOL = 7, STRING = 8, ARRAY = 9, UINT64 = 10, INT64 = 11, FLOAT64 = 12
};

enum class GgmlType : uint32_t {
    F32 = 0, F16 = 1, Q4_0 = 2, Q4_1 = 3, Q5_0 = 6, Q5_1 = 7,
    Q8_0 = 8, Q8_1 = 9, Q2_K = 10, Q3_K = 11, Q4_K = 12, Q5_K = 13, Q6_K = 14
};

struct Value {
    Type type = Type::UINT32;
    uint64_t u64 = 0;
    double f64 = 0;
    bool b = false;
    std::string str;
    std::vector<Value> array;

    uint32_t asU32() const { return static_cast<uint32_t>(u64); }
    uint64_t asU64() const { return u64; }
    float asF32() const { return static_cast<float>(f64); }
    const std::string& asString() const { return str; }
};

struct TensorInfo {
    std::string name;
    std::vector<uint64_t> shape;
    GgmlType type = GgmlType::F32;
    uint64_t offset = 0;
    uint64_t byteSize = 0;

    uint64_t elementCount() const {
        uint64_t n = 1;
        for (auto d : shape) n *= d;
        return n;
    }
};

class Reader {
public:
    /// Parses the header and metadata. Returns false on any malformed field rather
    /// than throwing, because this runs during startup where an exception would take
    /// the whole process down.
    bool open(const std::string& path);
    void close();

    bool isOpen() const { return file_ != nullptr; }
    uint32_t version() const { return version_; }
    uint64_t tensorCount() const { return tensorCount_; }
    uint64_t alignment() const { return alignment_; }
    uint64_t dataOffset() const { return dataOffset_; }

    const std::unordered_map<std::string, Value>& metadata() const { return kv_; }
    const std::vector<TensorInfo>& tensors() const { return tensors_; }

    /// Reads tensor bytes into a caller-owned buffer of exactly byteSize().
    bool readTensor(const TensorInfo& info, void* out, size_t outLen) const;

    std::string architecture() const;
    uint32_t contextLength() const;
    uint32_t embeddingLength() const;

private:
    bool readString(std::string& out);
    bool readValue(Value& out, int depth);
    bool skipBytes(uint64_t n);
    bool readRaw(void* dst, size_t n);

    std::FILE* file_ = nullptr;
    uint64_t fileSize_ = 0;
    uint64_t pos_ = 0;
    uint32_t version_ = 0;
    uint64_t tensorCount_ = 0;
    uint64_t kvCount_ = 0;
    uint64_t alignment_ = 32;
    uint64_t dataOffset_ = 0;
    std::unordered_map<std::string, Value> kv_;
    std::vector<TensorInfo> tensors_;
};

}  // namespace pocketclaw::gguf
