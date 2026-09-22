# Chat local LLM
<div align="center">

https://github.com/user-attachments/assets/2bef5e68-dd70-4aef-b2b6-dda702eea36a

</div>

A one-screen Android chat that runs **Gemma 4 E2B** on the phone with **LiteRT-LM**. The person asks, the model answers, nothing leaves the device.

Companion code for the article series *Running a language model on the phone with LiteRT-LM*, Part I. Article: _link coming soon_.

The app is complete without a model: it installs, runs, and answers every question with "I cannot answer without a model on this phone." Put the 2.6 GB file in place and the same screen answers for real.

## What is in here

| Piece | File | Job |
|---|---|---|
| Engine | `model/ModelEngine.kt` | The only class that touches the runtime. Loads once, streams an answer as a cold `Flow` of the text so far, releases. A release cuts an answer in flight instead of waiting for it. |
| Gateway | `model/ModelGateway.kt` | The four-member interface the app sees. `NoModel` answers with an empty flow and is bound by default; `LiteRtGateway` wraps the engine. |
| Holder | `model/GatewayManager.kt` | A `StateFlow` of the current gateway, swapped once when the file is on the phone. |
| Policy | `model/SessionPolicy.kt` | Pure rules, no Android: load when a screen that may ask opens; release after 60 s in the background or under real memory pressure. |
| Driver | `model/SessionDriver.kt` | Feeds the policy from the process lifecycle with one delayed job, and carries out its actions. |
| Chat | `ui/chat/ChatViewModel.kt` | Bubbles and a `busy` flag. The "no model" and "the model failed" paths are two lines of text, not an `if`. Each turn addresses its own bubble, so a burst of sends never loses a question. |
| Wiring | `di/Modules.kt`, `ChatLocalLlmApplication.kt` | Koin. `NoModel` first; the real gateway swapped in at start-up when the file exists. |

Stack: Kotlin 2.2, Jetpack Compose, Koin 4, kotlinx-coroutines 1.11.0, LiteRT-LM 0.16.1. `minSdk` 26, `targetSdk` 36, `arm64-v8a` and `x86_64`.

## Build and install

Open the project in Android Studio and press Run, or with a phone connected:

```bash
./gradlew :app:installDebug
```

Use the **debug** build. The model goes into the app's private storage with `run-as`, and Android only allows `run-as` on a debuggable app.

## Add the model

The model is Gemma 4 E2B, mobile-QAT build, `gemma-4-E2B-it-q4.litertlm` (2.6 GB), from [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) on Hugging Face. Read the licence on the model card before you ship it.

With the app installed, run the script. It lists the connected devices and asks which one, tells you if the model is already there and whether to keep it, replace it or download it again, confirms before pushing, and restarts the app:

```bash
./scripts/install-model.sh                                   # asks for the path, or downloads
./scripts/install-model.sh ~/Downloads/gemma-4-E2B-it-q4.litertlm
```

On Windows, run it through Git Bash:

```powershell
& "C:\Program Files\Git\bin\bash.exe" .\scripts\install-model.sh
```

By hand it is three adb commands:

```bash
adb push gemma-4-E2B-it-q4.litertlm /data/local/tmp/
adb shell run-as com.example.chatlocalllm mkdir -p files/models
adb shell run-as com.example.chatlocalllm cp /data/local/tmp/gemma-4-E2B-it-q4.litertlm files/models/
```

Restart the app. The status line reads "Model ready" once the chat opens.

Two things to know:

- **Uninstalling the app deletes the model.** It lives in `filesDir`. Gradle's `connectedAndroidTest` uninstalls the app when it finishes, so do not use that task here.
- **The first answer takes the process to about 1.7 GB, and the engine keeps it.** Loading adds about 0.4 GB; the first answer adds 1.1 GB more that stays allocated until the engine is released. The app releases it after sixty seconds in the background, back to about 0.6 GB.

## Tests

Two kinds, two questions.

**Unit tests** ask "does my code do the right thing with whatever the runtime gives it?" JVM, milliseconds, no file, no runtime, no Koin. 91 tests.

```bash
./gradlew :app:testDebugUnitTest
```

**Instrumented tests** ask "does the runtime still give what my code expects?" They need a phone with the model file in place and are skipped, not failed, without it. They check that `initialize()` works, that `sendMessageAsync` streams deltas rather than the text so far, that `maxNumTokens` below the bundle's 1024-token prefill chunk fails on the first message, and that `release()` during an answer returns in well under a second. Run them after every LiteRT-LM upgrade.

Install both APKs, then run the class with `am instrument`, not with `connectedAndroidTest`:

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.example.chatlocalllm.ModelEngineInstrumentedTest com.example.chatlocalllm.test/androidx.test.runner.AndroidJUnitRunner
```

**Stress**, about a minute, by hand: twenty answers in a row with flat memory, ten releases mid-answer, five sends 150 ms apart through the ViewModel, and the memory footprint of each state.

```bash
adb shell am instrument -w -e class com.example.chatlocalllm.ModelEngineStressTest com.example.chatlocalllm.test/androidx.test.runner.AndroidJUnitRunner
```

## Numbers

Measured on a Galaxy S24 FE (8 GB, Android 16), CPU backend, fresh process.

| | |
|---|---|
| `load()` with the file in the page cache | about 300 ms |
| `load()` after a reboot | about 10 s |
| "Say hello." streamed | about 1 s |
| Process before load / loaded / after the first answer / after release | 174 / 563 / 1673 / 570 MB |
| `release()` during an answer | 177 to 343 ms, against 4.6 to 7.6 s for the uncut answer |

## Two version pitfalls the build already avoids

| Pitfall | What happens | Fix in place |
|---|---|---|
| kotlinx-coroutines below 1.11.0 | The whole answer streams, then the app dies with a `NoSuchMethodError` on the last chunk. The runtime's POM declares 1.9.0, so Gradle will not upgrade for you ([issue 2812](https://github.com/google-ai-edge/LiteRT-LM/issues/2812)). | 1.11.0 declared explicitly. |
| LiteRT-LM 0.17.x on Kotlin 2.2 | The compiler refuses the Kotlin 2.4 metadata. | Pinned to 0.16.1. |

## Licence

Gemma 4 E2B is distributed under its own licence; read the model card before you ship it.
