# OneClickCopy

An Android app for quick one-tap text copying, with per-item copy tracking.

Write a list of snippets — one per line — then flip into **Copy mode**, where every
line becomes a tappable row. Tap to copy to the clipboard; each row checks itself
off so you always know what you've already used.

## Features

- **Edit mode** — a plain text editor; one snippet per line
- **Copy mode** — tap any row to copy, with per-row copy tracking and progress
- **Reorder** — drag any row to rearrange the list
- **Documents** — save and browse multiple snippet lists
- **Search** — filter documents by title or content
- **Autosave** — edits persist automatically, and flush immediately on exit
- **Undo delete** — deleting a document offers an undo action
- **Google Drive backup** — optional cloud sync stored in Drive's private app folder
- **Material 3** — OLED dark gray theme; copy-mode reorder with undo

## Architecture

Single-module app following a conventional layered structure.

```
com.oneclickcopy
├── domain/     Pure Kotlin: snippet parsing, copied-state encoding (no Android deps)
├── data/       Room entities, DAO, and DocumentRepository (single source of truth)
├── backup/     Google Drive transport + versioned backup payload
└── ui/
    ├── home/     HomeScreen + HomeViewModel
    ├── editor/   EditorScreen + EditorViewModel
    ├── theme/    Material 3 color, typography, semantic mode colors
    └── util/     Clipboard and time-formatting helpers
```

**Principles**

- UI is stateless and driven by immutable `UiState` objects exposed as `StateFlow`.
- All persistence goes through `DocumentRepository`; composables never touch Room.
- Business logic lives in ViewModels, so state survives configuration changes.
- Parsing and serialization are pure functions, unit tested without an emulator.
- Dependencies are wired manually through `AppContainer` — no DI framework needed
  for a graph this small.

### Snippet identity

Copied state is keyed by `(text, occurrence index)` rather than raw text. A document
containing the same line more than once therefore tracks each occurrence
independently, and reordering re-canonicalizes occurrence indices so checkmarks stay
attached to the correct row.

## Building

Requires JDK 17 and the Android SDK (API 34).

```bash
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # minified release APK (R8 + resource shrinking)
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android Lint
```

Outputs land in `app/build/outputs/apk/`.

## Testing

43 unit tests run on the JVM (no emulator required) via Robolectric and an in-memory
Room database:

| Suite | Covers |
| --- | --- |
| `SnippetParserTest` | line parsing, blank handling, duplicates, reindexing, large inputs |
| `CopiedStateCodecTest` | encode/decode round trips, unicode, malformed and legacy payloads |
| `DocumentRepositoryTest` | CRUD, search, empty-document cleanup, idempotent backup merge |
| `EditorViewModelTest` | load, mode switching, copy tracking, reorder, autosave flush |

## Data & privacy

- Documents are stored locally in a Room database.
- Drive backup is opt-in and requires Google sign-in.
- The backup file lives in Drive's `appDataFolder`, a private area only this app can
  read — it does not appear among your normal Drive files.
- Restores merge by document UUID: newer versions win and repeated restores never
  duplicate documents.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Room · Navigation Compose ·
kotlinx.serialization · Coroutines/Flow · Google Drive API · Robolectric + Truth
