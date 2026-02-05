# OneClickCopy

A simple Android app for quick one-tap text copying with copy tracking.

## Features

- **Edit Mode (OFF)**: Write and edit your text snippets, one per line
- **Copy Mode (ON)**: Each line becomes a tappable item
  - Tap any line to copy it to clipboard
  - Checkbox marks items you've already copied
  - Drag handles to reorder items
  - Reset button clears all checkmarks
- **Document Browser**: Save and manage multiple documents
- **Auto-save**: Documents save automatically as you type
- **Google Drive Backup**: Sync your documents to the cloud

## Google Drive Backup

Sign in with your Google account to enable cloud backup.

### How to Use

1. Tap the **☰ menu** → **Sign in with Google**
2. Authorize the app to access Google Drive
3. Use **Backup to Drive** to manually save all documents

### Auto-Backup

Automatic backup triggers when you return to the home screen (after editing a document or launching the app):

- **Cooldown**: 1 minute between backups (prevents excessive API calls)
- **Silent success**: No notification when backup succeeds
- **Toast on failure**: You'll see an error message if something goes wrong
- **Requires sign-in**: Only runs when you're signed in

### Auto-Restore (First Login Only)

When you sign in to Google for the first time on a device:

- The app checks for an existing backup on your Drive
- If found, documents are **merged** with any local documents
- This only happens **once** per sign-in (not on every app launch)
- Silent unless there's an error

### Data Storage

- Backup file: `oneclickcopy_backup.json` in your Google Drive
- Contains all documents with titles, content, and timestamps
- Each backup overwrites the previous one

## Known Limitations

- Very long single lines (20,000+ characters) may cause lag. This is an edge case — typical copy snippets are much shorter.

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
