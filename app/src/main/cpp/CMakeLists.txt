cmake_minimum_required(VERSION 3.22.1)
project(ocmcore CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Local inference
#
# ON by default because "runs on the phone's own CPU" is a product requirement,
# not an add-on. Turn it OFF if your NDK toolchain chokes on ggml; the app then
# builds with a stub engine and reports "on-device unavailable" at runtime instead
# of failing to link.
# ---------------------------------------------------------------------------
option(OCM_ENABLE_LOCAL_LLM "Build the vendored llama.cpp CPU engine" ON)

set(OCM_LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/llama.cpp)

if (OCM_ENABLE_LOCAL_LLM AND EXISTS ${OCM_LLAMA_DIR}/CMakeLists.txt)
    message(STATUS "OCM: building local engine from ${OCM_LLAMA_DIR}")

    set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_APP        OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_MTMD       OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)
    set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
    set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
    set(GGML_CCACHE            OFF CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
    set(GGML_CPU               ON  CACHE BOOL "" FORCE)
    set(GGML_CPU_ARM_ARCH      ON  CACHE BOOL "" FORCE)

    add_subdirectory(${OCM_LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

    set(OCM_LOCAL_SOURCES local_engine.cpp)
    set(OCM_LOCAL_DEFS OCM_HAS_LLAMA=1)
    set(OCM_LOCAL_LIBS llama ggml ggml-base ggml-cpu)
else()
    message(STATUS "OCM: local engine disabled, building stub")
    set(OCM_LOCAL_SOURCES local_engine.cpp)
    set(OCM_LOCAL_DEFS OCM_HAS_LLAMA=0)
    set(OCM_LOCAL_LIBS "")
endif()

add_library(ocmcore SHARED
    native-lib.cpp
    sha256.cpp
    vault_gate.cpp
    ${OCM_LOCAL_SOURCES}
)

target_compile_definitions(ocmcore PRIVATE ${OCM_LOCAL_DEFS})

target_compile_options(ocmcore PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -fvisibility=hidden
    -ffunction-sections -fdata-sections
)

target_link_options(ocmcore PRIVATE -Wl,--gc-sections)
target_link_libraries(ocmcore ${OCM_LOCAL_LIBS})

find_library(log-lib log)
target_link_libraries(ocmcore ${log-lib})
