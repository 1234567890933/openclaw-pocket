# Vendored third-party code

## llama.cpp

* **Upstream:** https://github.com/ggml-org/llama.cpp
* **Commit:** `827466b9efa0574806bad7732bcd78bfb6d9311d`
* **Licence:** MIT (see `llama.cpp/LICENSE` and `llama.cpp/licenses/`)
* **Re-vendor:** `scripts/pin-third-party.sh <commit>`

### What was kept

`CMakeLists.txt`, `cmake/`, `licenses/`, `LICENSE`, `include/`, `src/`, and `ggml/`.
That is the library plus the CPU backend.

### What was removed

* `ggml/src/ggml-cuda`, `ggml-vulkan`, `ggml-sycl`, `ggml-metal`, `ggml-opencl`,
  `ggml-hexagon`, `ggml-et`, `ggml-webgpu`, `ggml-openvino`, `ggml-cann`,
  `ggml-zdnn`, `ggml-virtgpu`, `ggml-rpc` — GPU and accelerator backends. This app
  builds for a phone CPU only.
* `vendor/` — serves the tools, server and app targets, none of which are built.
* Markdown documentation files — they would double the tree size and are a click
  away upstream.

### Local modification

One line in `CMakeLists.txt`. Upstream calls `add_subdirectory(vendor)`
unconditionally; since `vendor/` is not present here, that call is wrapped in a
guard that skips it unless `LLAMA_BUILD_TOOLS`, `LLAMA_BUILD_EXAMPLES` or
`LLAMA_BUILD_APP` is on. The change is marked with `[OCM PATCH]` in the file.

### Why vendored and not a submodule

A build that reaches the network is a build that fails on a plane, and a floating
branch means two developers compile different engines from the same commit of this
repository. The cost is repository size; the benefit is reproducibility.

### Maintenance

When bumping the commit, run `scripts/pin-third-party.sh`, rebuild, and check
`docs/LOCAL-INFERENCE.md` for any API drift in `local_engine.cpp` — that file is the
only place where llama.cpp types appear.
