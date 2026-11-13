#!/usr/bin/env bash
#
# Re-vendors llama.cpp at a pinned commit.
#
# The tree is committed on purpose: an NDK build that reaches the network is a build
# that fails on a plane, and a floating branch means two developers can compile
# different engines from the same commit of this repo.
#
set -euo pipefail
cd "$(dirname "$0")/.."

PIN="${1:-827466b9efa0574806bad7732bcd78bfb6d9311d}"
DEST="third_party/llama.cpp"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "fetching llama.cpp @ $PIN"
git clone --quiet https://github.com/ggml-org/llama.cpp.git "$TMP/llama.cpp"
git -C "$TMP/llama.cpp" checkout --quiet "$PIN"

rm -rf "$DEST"
mkdir -p "$DEST"
cp -r "$TMP/llama.cpp"/{CMakeLists.txt,LICENSE,cmake,licenses,include,src,ggml} "$DEST"/

# Drop backends this app cannot use, and the docs that would double the tree size.
find "$DEST" -name "*.md" -delete
for backend in ggml-cuda ggml-vulkan ggml-sycl ggml-metal ggml-opencl ggml-hexagon \
               ggml-et ggml-webgpu ggml-openvino ggml-cann ggml-zdnn ggml-virtgpu ggml-rpc; do
  rm -rf "$DEST/ggml/src/$backend"
done

# Re-apply the vendor/ guard. See third_party/VENDORED.md.
python3 - "$DEST/CMakeLists.txt" <<'PY'
import sys, pathlib
p = pathlib.Path(sys.argv[1])
t = p.read_text()
old = "add_subdirectory(vendor)"
new = """# [OCM PATCH] see third_party/VENDORED.md
if (LLAMA_BUILD_TOOLS OR LLAMA_BUILD_EXAMPLES OR LLAMA_BUILD_APP)
    add_subdirectory(vendor)
endif()"""
if old in t:
    p.write_text(t.replace(old, new, 1))
    print("patched vendor guard")
PY

echo "done: $DEST ($(du -sh "$DEST" | cut -f1))"
