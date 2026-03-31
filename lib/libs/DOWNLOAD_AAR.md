# sherpa-onnx AAR

Download sherpa-onnx-1.12.34.aar from:
https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.34/sherpa-onnx-1.12.34.aar

Place in this directory (lib/libs/).
This file is not committed to git due to its size (45 MB).

## ABI Coverage

sherpa-onnx-android ships native `.so` files for:
- `arm64-v8a` ✅ (Motorola Edge 60 Fusion, most modern devices)
- `armeabi-v7a` ✅

**Not included:** `x86`, `x86_64` — sherpa-onnx will not work on x86 emulators.
Use an ARM emulator (e.g., Pixel 7 API 35 with ARM image) for testing.

## Integrity

After downloading, verify the file is not corrupt:
- Expected size: ~45 MB
- Run `./gradlew :lib:dependencies` — should show the AAR in the fileTree dependency
