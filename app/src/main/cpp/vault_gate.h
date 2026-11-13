#ifndef OCM_VAULT_GATE_H
#define OCM_VAULT_GATE_H

#include <string>

namespace ocm {

/**
 * Structural validation for the 50-character access phrase.
 *
 * Five nine-digit groups plus a five-digit checksum. The checksum is the sum of
 * the forty-five digits, which lets a human verify their own arithmetic before
 * typing: if the checksum does not match, they mis-solved a step, not the app.
 */
struct GateResult {
    bool wellFormed = false;
    bool checksumOk = false;
    std::string reason;
};

GateResult validatePhrase(const std::string& phrase);

}  // namespace ocm

#endif  // OCM_VAULT_GATE_H
