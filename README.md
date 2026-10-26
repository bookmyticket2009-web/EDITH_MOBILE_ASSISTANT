# EDITH — Mobile AI Assistant

**A personal Android AI assistant powered by local AI, optional cloud intelligence, and a foundation for modular agent-based automation.**

EDITH is an Android assistant project built with Java, Android, JNI, and `llama.cpp`. It is designed to support on-device language models while also allowing optional online AI responses through a configured cloud API.

The goal is to create a privacy-conscious, extensible assistant that can eventually combine conversational AI, voice interaction, specialized agents, and useful Android actions in one application.

> **Project status:** Native Android compilation and cloud API integration have been tested during development. Feature availability depends on the current source code and configuration. Planned capabilities are identified separately below.

## Table of contents

- [Highlights](#highlights)
- [Current status](#current-status)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Requirements](#requirements)
- [Project structure](#project-structure)
- [Build the APK](#build-the-apk)
- [Install on Android](#install-on-android)
- [AI modes](#ai-modes)
- [Configure a local model](#configure-a-local-model)
- [Cloud AI and credentials](#cloud-ai-and-credentials)
- [Troubleshooting](#troubleshooting)
- [Security and privacy](#security-and-privacy)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Licensing](#licensing)

---

## Highlights

- **Local AI:** Native inference through `llama.cpp` for compatible GGUF models.
- **Hybrid architecture:** Support for local inference and an optional online AI backend.
- **Android-native foundation:** Java application code with JNI integration for native functionality.
- **AI mode selection:** Choose from the modes supported by the current implementation.
- **Response identification:** Local and cloud responses can be identified by their source.
- **Extensible design:** A foundation for future task routing, specialized agents, and Android tools.
- **Developer-friendly workflow:** Gradle-based Android builds using VS Code or another compatible editor.

## Current status

| Component | Status |
|---|---|
| Android application and Gradle build | Established |
| Java-to-native JNI integration | Implemented |
| Local GGUF inference | Implemented; verify on the target device |
| Optional Ollama cloud integration | Tested during development |
| AI mode selection | Established |
| Modular agent framework | Planned |
| Voice input and speech output | Planned |
| Advanced Android automation | Planned |
| User-controlled conversation memory | Planned |

This table describes the project status documented during development, not a guarantee that every feature works in every build.

## Architecture

EDITH separates the Android interface from the AI inference layer so that local and online backends can be developed independently.

```text
                 EDITH Android App
                         |
                         v
                AI Request Handling
                         |
                  Mode Selection
                    /       \
                   /         \
                  v           v
             Local AI      Cloud AI
                  |           |
                  v           v
              llama.cpp    Ollama API
                  |           |
                  v           v
              GGUF Model   Online Service
```

The longer-term design may introduce an agent layer above the inference backends:

- **Agent Registry:** Defines available agents.
- **Agent Router:** Selects an appropriate agent for a request.
- **Task Planner:** Breaks supported tasks into steps.
- **Tool Registry:** Lists approved actions.
- **Agent Runner:** Coordinates execution.
- **Permission and Confirmation Layer:** Controls access to Android capabilities and consequential actions.

These agent components are planned architecture, not claims that the complete framework is already implemented.

## Technology stack

- **Language:** Java
- **Platform:** Android
- **Native integration:** JNI and C++
- **Local inference:** `llama.cpp`
- **Model format:** GGUF
- **Build system:** Gradle
- **Development environment:** VS Code or another compatible editor
- **Optional cloud backend:** Ollama API

## Requirements

For Windows development, you need:

- Windows 10 or 11
- Git
- A compatible JDK
- Android SDK and Android NDK
- Compatible CMake and Gradle versions
- VS Code or another source editor
- An Android device or emulator

Use the toolchain versions already validated by the project. Changing Gradle, the Android Gradle Plugin, NDK, CMake, or native compiler flags can introduce compatibility issues.

For local AI, the Android device also needs sufficient memory and storage for the selected model. Performance depends on device hardware, model architecture, quantization, context length, and thermal limits.

## Project structure

The repository contains the Android application and its native integration. The principal source locations are:

```text
EDITH_MOBILE_ASSISTANT/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── cpp/
│           ├── java/
│           │   └── com/pocketai/app/
│           ├── jniLibs/
│           │   └── arm64-v8a/
│           └── res/
├── llama.cpp/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .gitignore
```

This is a simplified overview; consult the actual repository for the complete file list.

The Android application ID is currently `com.pocketai.app`. The visible app name and application ID are separate settings.

### Native integration

The application includes native libraries associated with `llama.cpp`, GGML, and the C++ runtime.

The Java declarations, native implementations, ABI, and packaged shared libraries must remain compatible. The repository's actual native build configuration is the source of truth for the required libraries.

## Build the APK

The following example uses Windows PowerShell and an existing compatible Gradle installation. It does not require Android Studio if your command-line Android toolchain is configured correctly.

### 1. Open the project

Clone the repository:

```powershell
git clone https://github.com/bookmyticket2009-web/EDITH_MOBILE_ASSISTANT.git
cd EDITH_MOBILE_ASSISTANT
```

If you already have the repository locally, open its folder instead of cloning it again.

### 2. Configure the project path

The repository root is the Android project root in this repository layout.

```powershell
$project = (Get-Location).Path
```

Set `$gradle` to the path of your existing Gradle executable:

```powershell
$gradle = "C:\PATH\TO\GRADLE\bin\gradle.bat"
```

Replace the example path with your actual installation path.

### 3. Build a debug APK

```powershell
& $gradle -p $project :app:assembleDebug --no-daemon
```

A successful build reports:

```text
BUILD SUCCESSFUL
```

The expected APK location is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Check whether the APK exists:

```powershell
Test-Path "$project\app\build\outputs\apk\debug\app-debug.apk"
```

The command should return `True` after a successful build.

**Build note:** The build requires compatible Android SDK and NDK installations and any dependencies that are not already available locally. A successful build on one machine does not guarantee a successful build on every machine.

## Install on Android

Connect your device through USB debugging or configure wireless ADB.

### 1. Find ADB

Set the path to your local `adb.exe`:

```powershell
$adb = "C:\PATH\TO\ANDROID\SDK\platform-tools\adb.exe"
```

### 2. Check connected devices

```powershell
& $adb devices
```

Use the device identifier shown in the output.

### 3. Install the APK

```powershell
& $adb -s "YOUR_DEVICE_ID" install -r "$project\app\build\outputs\apk\debug\app-debug.apk"
```

Replace `YOUR_DEVICE_ID` with the identifier returned by ADB.

### 4. Launch EDITH

```powershell
& $adb -s "YOUR_DEVICE_ID" shell monkey -p com.pocketai.app 1
```

If the app crashes, inspect Android logs:

```powershell
& $adb logcat -d -t 1500 |
    Select-String -Pattern "PocketAI|EDITH|AndroidRuntime|llama|ggml"
```

Review the error messages before changing the source code or native build configuration.

## AI modes

### Local AI

Local mode uses the native inference backend and a compatible GGUF model.

**Benefits**
- Can work without an internet connection after the model has been installed.
- Keeps prompts on the local inference path instead of sending them to the configured cloud provider.
- Does not require cloud API credentials for local generation.

**Limitations**
- Model speed and quality depend on device hardware and model choice.
- Larger models require more memory and storage.
- Longer contexts increase resource use.
- Sustained inference may increase heat and battery consumption.

Local inference does not mean every feature in the application works offline. Features that depend on network services still need connectivity.

### Online AI

The cloud integration tested during development used:

```text
https://ollama.com/api/chat
```

The model identifier tested was:

```text
gpt-oss:120b
```

Provider access, model availability, usage limits, and pricing may change. Confirm current terms with the provider before relying on a particular model.

Online mode requires internet access and valid credentials.

### Hybrid AI

Hybrid mode is intended to make both local and online inference available through one application.

The exact behavior depends on the implemented request-routing logic. Do not assume that EDITH automatically falls back to local AI after every cloud failure unless that behavior has been implemented and tested.

## Configure a local model

The documented local model path is:

```text
getFilesDir()/edith-model.gguf
```

This represents a file inside the application's private Android storage.

Installing the APK does not automatically install a model unless a supported model-import or download workflow does so.

To set up local inference:

1. Choose a compatible GGUF model.
2. Verify the model's origin and license.
3. Check its file size and memory requirements.
4. Transfer or import the model using a supported method.
5. Place it at the path expected by the application.
6. Load it through EDITH.
7. Confirm that the backend reports readiness.
8. Test with a short prompt before trying longer requests.

Start with a smaller quantized model and benchmark it on the target phone. Model compatibility depends on the model architecture and the bundled `llama.cpp` version.

## Cloud AI and credentials

Keep credentials out of source code and public Git history.

Never commit actual API keys, authorization headers, signing keys, private configuration, or logs containing secrets.

If EDITH uses Android private `SharedPreferences` to store a cloud credential, remember that private application storage is not an absolute defense against device compromise.

For a production release, consider a backend architecture that avoids embedding a reusable privileged API key in a distributed application. A key included in an APK can potentially be extracted.

## Troubleshooting

### Gradle build fails

Check:

- Gradle executable path
- JDK compatibility
- Android SDK configuration
- Android Gradle Plugin compatibility
- Dependency availability
- Native build configuration

Avoid upgrading multiple tools at once. Preserve a known-good configuration before making changes.

### Native compilation or linking fails

Check:

- Target ABI
- NDK and CMake compatibility
- Compiler and linker flags
- Required shared libraries
- JNI method names and signatures
- Stale generated build configuration

Preserve the last known-good setup, make one targeted change at a time, and keep the complete error log.

### Local model does not load

Check:

- The model exists in application-private storage.
- The file is compatible with the native runtime.
- The device has enough memory and storage.
- The native libraries load correctly.
- EDITH reports that the local backend is ready.

### Cloud requests fail

Check:

- Internet connectivity
- Endpoint and model identifier
- Credential validity
- Provider availability and usage limits
- HTTP status codes and error handling

Remove credentials and other sensitive values before sharing logs.

### APK cannot be built in a browser editor

A browser-based source editor can support code editing and Git operations, but it does not automatically provide an Android SDK, NDK, native compiler, or build environment. Use a configured development machine or a suitable cloud build environment.

## Security and privacy

EDITH aims to make AI processing and Android actions understandable to the user.

- Keep cloud credentials out of source control.
- Make it clear when requests use an online provider.
- Avoid sending data to cloud services unnecessarily.
- Request only the Android permissions required for implemented features.
- Require appropriate confirmation for consequential actions.
- Keep personal network details and local machine paths out of public documentation.
- Review third-party licenses and model terms.
- Avoid claiming that the whole application is offline if some features require network access.

## Roadmap

The following items represent future development goals, not guaranteed current features.

### Phase 1 — Stability
- Improve reproducibility of native builds.
- Validate local model loading and generation across devices.
- Improve cloud error handling.
- Document model import and configuration.
- Improve diagnostic logging.

### Phase 2 — Agent framework
- Create an agent registry.
- Implement agent routing.
- Define structured tool requests.
- Add task planning and controlled execution.
- Share model infrastructure between agents.

### Phase 3 — Android tools
Potential tools include:
- Open URLs.
- Launch supported applications.
- Open supported system screens.
- Start timers using Android APIs.
- Share text through Android's sharing interface.

### Phase 4 — Voice and memory
- Explore speech recognition and speech synthesis.
- Add user-controlled conversation context.
- Provide clear memory and privacy controls.
- Improve interruption and cancellation.

### Phase 5 — Optimization and release
- Benchmark supported local models.
- Improve memory and performance.
- Test on multiple Android devices.
- Review security and dependencies.
- Document release signing and distribution.

## Contributing

Contributions and bug reports are welcome.

When reporting a problem, include:

- Android version and device category
- Relevant commit or application version
- AI mode
- Steps to reproduce
- Expected and actual behavior
- Sanitized error logs

Never include passwords, API keys, private keys, pairing codes, or unrelated personal information.

Keep changes focused, review the Git diff, and test affected functionality before publishing.

## Licensing

The project's own license has not yet been explicitly established in the supplied documentation.

The repository includes or is intended to integrate upstream `llama.cpp` code and may contain other third-party components. Review their applicable licenses and attribution requirements before redistribution.

Model licenses are separate from software licenses. Check the selected model's terms before bundling or distributing it.

## Project links

- **GitHub repository:** https://github.com/bookmyticket2009-web/EDITH_MOBILE_ASSISTANT
- **Upstream llama.cpp:** https://github.com/ggml-org/llama.cpp
- **Ollama API documentation:** https://docs.ollama.com/api

---

**EDITH — Build carefully. Protect privacy. Test every change.**
