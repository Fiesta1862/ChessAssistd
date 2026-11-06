# ChessAssist — Build Instructions

## Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- NDK 26+ (install via SDK Manager → SDK Tools → NDK)
- API 26+ device or emulator (arm64-v8a)

## FILL: libstockfish.so
1. Clone https://github.com/official-stockfish/Stockfish
2. Cross-compile for Android arm64-v8a:
   ```
   cd src
   make -j4 ARCH=aarch64-android ANDROID_NDK=/path/to/ndk build
   ```
   Or download a pre-built `.so` from the `Stockfish-Android` release page on GitHub.
3. Rename the output to `libstockfish.so`
4. Place at:
   ```
   app/src/main/jniLibs/arm64-v8a/libstockfish.so
   ```

## FILL: Piece PNG templates
Place 12 PNG files in `app/src/main/assets/pieces/`:
- `wp.png` `wr.png` `wn.png` `wb.png` `wq.png` `wk.png`
- `bp.png` `br.png` `bn.png` `bb.png` `bq.png` `bk.png`

Source: screenshot each piece from Chess.com's default board theme at ~128×128px.
The `BoardReader` scales them to cell size at runtime.

## FILL: JNI bridge
`StockfishEngine.kt` calls:
- `nativeInit(): Long` — spawn Stockfish process or init engine, return handle
- `nativeSendCommand(handle: Long, cmd: String)` — write UCI command
- `nativeReadLine(handle: Long, timeoutMs: Long): String?` — read one line, null on timeout
- `nativeQuit(handle: Long)` — cleanup

Implement these in C/C++ as a thin stdin/stdout pipe to the Stockfish binary,
or use Stockfish's `NNUE` C++ API directly linked into the .so.
Minimal JNI stub example (pipe-based):

```c
// jni_bridge.c
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

typedef struct { FILE* in; FILE* out; } Handle;

JNIEXPORT jlong JNICALL
Java_com_chessassist_StockfishEngine_nativeInit(JNIEnv* env, jobject thiz) {
    Handle* h = malloc(sizeof(Handle));
    // spawn stockfish subprocess — replace path with extracted binary path
    // For embedded .so approach, call sf::init() directly instead
    return (jlong)h;
}
JNIEXPORT void JNICALL
Java_com_chessassist_StockfishEngine_nativeSendCommand(JNIEnv* env, jobject thiz, jlong handle, jstring cmd) {
    // write cmd\n to Stockfish stdin
}
JNIEXPORT jstring JNICALL
Java_com_chessassist_StockfishEngine_nativeReadLine(JNIEnv* env, jobject thiz, jlong handle, jlong timeoutMs) {
    // read one line from Stockfish stdout with timeout
    return NULL;
}
JNIEXPORT void JNICALL
Java_com_chessassist_StockfishEngine_nativeQuit(JNIEnv* env, jobject thiz, jlong handle) {
    // send "quit", close pipes, free handle
}
```

## Build
```bash
cd ChessAssist
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First run flow
1. Launch ChessAssist
2. Grant "Draw over other apps" permission
3. Grant notification permission (Android 13+)
4. Tap "Calibrate Board Region" → drag rect over the chess board → Save
5. Tap "Start Overlay" → grant screen capture
6. Switch to Chess.com — arrow and panel appear over the board
7. Tap "Stop" in notification or in the app to end

## Chess.com Fair Play Policy
Chess.com prohibits computer assistance in rated games, live games, and tournaments.
Use this tool only in: unrated casual games, games vs bots, analysis mode,
or your own test harness. Violation of Chess.com's Fair Play Policy can result
in account closure. This tool is for educational and research purposes
