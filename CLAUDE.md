# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

### Quick build (arm64 only, single ABI)

```bash
./build-arm64.sh          # debug
./build-arm64.sh release  # release (needs SIGN_KEY_* env vars)
```

### Full build (all ABIs)

```bash
# Set JAVA_HOME to Java 17+
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Debug (single ABI for speed)
BUILD_ABI=arm64-v8a ./gradlew :app:assembleDebug

# Release
./gradlew :app:assembleRelease

# Build all plugins too
./gradlew :assembleReleasePlugins
```

### Nix environment

If system deps (extra-cmake-modules, gettext) aren't installed, use the Nix flake:

```bash
nix develop .#noAS --command ./gradlew :app:assembleDebug
```

### Install to device

```bash
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Run tests

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Instrumented tests (requires connected device/emulator)
./gradlew :app:connectedAndroidTest
```

## System dependencies

- Java 17+
- Android SDK Platform & Build-Tools (see `build-logic/convention/src/main/kotlin/Versions.kt` for versions)
- Android NDK + CMake 3.31.6
- `extra-cmake-modules` and `gettext` (from system package manager)

## Architecture

This is an Android input method (IME) that ports the fcitx5 input method framework to Android. The app runs as a single APK (`org.fcitx.fcitx5.android`) with optional plugin APKs for additional input engines.

### Module split

| Layer | Modules | Description |
|---|---|---|
| **App** | `:app` | Main IME APK — UI, input service, themes, voice input |
| **Native libs** | `:lib:fcitx5`, `:lib:libime`, `:lib:fcitx5-lua`, `:lib:fcitx5-chinese-addons` | C++ fcitx5 framework and input engines, built via CMake and linked into the app via JNI |
| **Shared Kotlin** | `:lib:common`, `:lib:plugin-base` | AIDL IPC interfaces, shared Kotlin code between app and plugins |
| **Plugins** | `:plugin:anthy`, `:plugin:chewing`, `:plugin:hangul`, `:plugin:jyutping`, `:plugin:rime`, `:plugin:sayura`, `:plugin:thai`, `:plugin:unikey`, `:plugin:clipboard-filter` | Optional standalone APKs for language-specific input engines |
| **Build logic** | `build-logic` (composite build) | Custom Gradle plugins for convention, native builds, metadata generation |
| **Codegen** | `:codegen` | KSP symbol processors that generate key mapping and scancode mapping code |

### Key architectural points

- **C++ core via JNI**: The fcitx5 engine runs in-process as native C++ code. The Kotlin layer communicates with it through JNI wrappers (`core/Fcitx.kt`, `core/FcitxAPI.kt`). The `daemon/` package manages the fcitx process lifecycle.

- **Plugin system**: Language engines (Anthy, Chewing, RIME, etc.) ship as separate APKs. Their native `.so` files are prebuilt and installed into the main APK's assets at build time via the `fcitx-component` Gradle plugin. At runtime, plugins are discovered and loaded through the main app.

- **Input method service**: `FcitxInputMethodService` (in `input/`) is the core Android `InputMethodService`. It owns the `InputView` (keyboard UI), candidate views, and coordinates between Android input events and the fcitx5 engine.

- **Voice input** (newly integrated): Previously a separate APK communicating via AIDL IPC, now integrated directly into `:app` as the `voice/` package. Uses Retrofit/OkHttp to call a transcription server. Audio is recorded at 16kHz WAV via `AndroidAudioRecorder`.

- **Gradle convention plugins**: The `build-logic/` composite build defines reusable Gradle plugins. Important ones: `AndroidAppConventionPlugin` (common Android config), `NativeAppConventionPlugin` (split ABIs, prebuilt .so packaging), `FcitxComponentPlugin` (installs CMake-built assets), `BuildMetadataPlugin` (generates version/commit metadata JSON).

- **KSP code generation**: `codegen/` contains two KSP processors — `GenKeyMapping.kt` and `GenScancodeMapping.kt` — that generate keyboard mapping code at compile time.

- **Git submodules**: 22 submodules, mostly fcitx5 C++ repositories and RIME dictionaries. After a fresh clone, run `git submodule update --init --recursive`.

### Build variants

The `BUILD_ABI` environment variable controls which native ABIs are built. Set it to a single ABI (e.g., `arm64-v8a`) for faster development builds. Without it, the build produces a multi-ABI APK. Version code is ABI-specific (`baseVersionCode` + ABI offset in `Versions.kt`).

### Signing

Release builds need signing credentials set as environment/project properties (`SIGN_KEY_STORE`, `SIGN_KEY_ALIAS`, `SIGN_KEY_STORE_PASS`, `SIGN_KEY_PASS`). See `build-logic/convention/src/main/kotlin/ProjectExtensions.kt`.

### Prebuilt native libraries

The fcitx5 C++ libraries are precompiled and stored as tarballs in the `prebuilt/` directory of each `:lib:*` module. The `PrebuiltPackageConfigurationTask` in the convention plugin resolves them. When updating native code, the prebuilt tarballs must be regenerated.

### IDE setup tip

To avoid slow indexing, mark `lib/fcitx5/src/main/cpp/prebuilt` as "Excluded" in Android Studio's Project view.
