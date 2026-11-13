#include "vault_gate.h"

#include <cctype>

namespace ocm {

GateResult validatePhrase(const std::string& phrase) {
    GateResult result;

    if (phrase.size() != 50) {
        result.reason = "phrase must be exactly 50 characters";
        return result;
    }

    for (char c : phrase) {
        if (!std::isdigit(static_cast<unsigned char>(c))) {
            result.reason = "phrase must contain digits only";
            return result;
        }
    }
    result.wellFormed = true;

    int sum = 0;
    for (int i = 0; i < 45; ++i) {
        sum += phrase[i] - '0';
    }

    const int stated = (phrase[45] - '0') * 10000 +
                       (phrase[46] - '0') * 1000 +
                       (phrase[47] - '0') * 100 +
                       (phrase[48] - '0') * 10 +
                       (phrase[49] - '0');

    result.checksumOk = (stated == sum);
    if (!result.checksumOk) {
        result.reason = "checksum does not match the digit sum";
    }
    return result;
}

}  // namespace ocm
