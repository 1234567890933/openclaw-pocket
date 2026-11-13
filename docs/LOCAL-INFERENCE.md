# On-device inference

## What runs

`third_party/llama.cpp` at commit `827466b9`, CPU backend only, built into
`libocmcore.so` for `arm64-v8a`. See `third_party/VENDORED.md` for the exact
modifications.

The chain is:

```
GeminiViewModel.runLocal
  └─ LocalEngine.generate        (mutex, one model at a time)
       └─ LocalNative.generate   (JNI)
            └─ llama_decode loop (vendored)
                 └─ TokenSink.emit → Kotlin buffer → Room
```

## Model choice

`DeviceProfile` measures RAM, cores and thermal state. `tools/modelpack/model_select.py`
does the same job offline against the release catalogue. The rule both enforce: a
model may use at most a third of physical RAM. Beyond that Android's low-memory
killer starts reaping the app mid-generation, which users experience as a crash.

A 1–2 GB quant is the realistic target on a phone. A 4B model at Q4 runs acceptably
on a flagship and badly on a budget device.

## Performance notes

* **Threads.** `DeviceProfile.recommendedThreads()` returns the performance core
  count, halved when the device is thermally moderate. More threads than cores is
  always slower; leaving one core for the UI is what stops the composer from
  stuttering during generation.
* **Context.** 4096 is the default. Larger contexts cost RAM linearly and prompt
  processing quadratically, and a phone has neither to spare.
* **KV cache** is rebuilt per turn because the engine holds one sequence. For a
  chat this costs prompt processing on every question, which is the price of not
  keeping a 2 GB model's state pinned across sessions.

## Why not run tools locally

Tool calling is the least reliable part of small quantised models: they hallucinate
argument names and drop fields. A wrong `rm` is not a wrong paragraph. The router
sends tool-bearing turns to the cloud for that reason, and says so in the notice.

## Escape hatch

```
./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a \
  -DOCM_ENABLE_LOCAL_LLM=OFF
```

Builds without the engine; the UI reports "on-device unavailable" and Auto routes
everything to the cloud. Useful on a CI runner where the NDK build of ggml is the
only thing standing between you and a green pipeline.
