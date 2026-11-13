#ifndef OCM_SHA256_H
#define OCM_SHA256_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace ocm {

/**
 * Self-contained SHA-256.
 *
 * Android's JCA is fine, but the gate needs a digest that runs identically in
 * native code and in tests, without a provider being registered. Keeping our own
 * also means the comparison never leaves C++, so a hooked `MessageDigest` cannot
 * influence the result.
 */
class Sha256 {
public:
    Sha256();
    void update(const uint8_t* data, size_t len);
    void update(const std::string& text);
    void final(uint8_t out[32]);
    std::string hex();

private:
    void transform(const uint8_t block[64]);

    uint32_t state_[8];
    uint64_t bitLen_;
    uint8_t buffer_[64];
    size_t bufferLen_;
};

std::string toHex(const uint8_t* data, size_t len);

std::string sha256Hex(const std::string& input);

std::string sha256HexSalted(const std::string& salt, const std::string& input);

std::string hmacSha256Hex(const std::string& key, const std::string& message);

/** Constant-time comparison; length mismatch returns false without early exit. */
bool constantTimeEquals(const uint8_t* a, size_t aLen, const uint8_t* b, size_t bLen);

}  // namespace ocm

#endif  // OCM_SHA256_H
