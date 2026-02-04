# OneClickCopy

A simple Android app for quick one-tap text copying with copy tracking.

## Features

- **Edit Mode (OFF)**: Write and edit your text snippets, one per line
- **Copy Mode (ON)**: Each line becomes a tappable item
  - Tap any line to copy it to clipboard
  - Checkbox marks items you've already copied
  - Drag handles to reorder items
  - Reset button clears all checkmarks

## Building

### Option 1: Android Studio
1. Open this folder in Android Studio
2. Let Gradle sync
3. Click Run or Build > Build APK

### Option 2: Command Line
```bash
# Make sure ANDROID_HOME is set
export ANDROID_HOME=/path/to/Android/Sdk

# Build debug APK
./gradlew assembleDebug

# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Reorderable list library for drag-and-drop

## Screenshots

Based on the mockups:
- Dark theme
- Purple accent bar
- Green toggle (ON) / Red toggle (OFF)
- Clean, minimal interface
