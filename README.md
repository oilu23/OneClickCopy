# OneClickCopy

An Android app for quick one-tap text copying, with per-item copy tracking.
A Windows desktop version lives in [`desktop/`](desktop/README.md).

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
- **GitHub backup** — optional automatic backup to a secret gist on your account
- **Material 3** — OLED dark gray theme; copy-mode reorder with undo

## Architecture

Single-module app following a conventional layered structure.

```
com.oneclickcopy
├── domain/     Pure Kotlin: snippet parsing, copied-state encoding (no Android deps)
├── data/       JSON document store, DocumentRepository (single source of truth),
│               one-time import from the pre-2.3 Room database
├── backup/     GitHub gist transport + versioned backup payload
├── sync/       Debounced automatic backup via WorkManager
└── ui/
    ├── AppNavigator  Two-screen navigation state (list, or one open document)
    ├── icons/    The 25 Material icons the app uses (shared with desktop)
    ├── home/     HomeScreen + HomeViewModel
    ├── editor/   EditorScreen + EditorViewModel
    ├── theme/    Material 3 color, typography, semantic mode colors
    └── util/     Clipboard and time-formatting helpers
```

**Principles**

- UI is stateless and driven by immutable `UiState` objects exposed as `StateFlow`.
- All persistence goes through `DocumentRepository`; composables never touch storage.
- Business logic lives in ViewModels, so state survives configuration changes.
- Parsing and serialization are pure functions, unit tested without an emulator.
- Dependencies are wired manually through `AppContainer` — no DI framework needed
  for a graph this small.
- Few dependencies: no annotation processing, no navigation library, no icon
  library. Storage, snippet logic, backup and icons are shared with the Windows app.

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

159 unit tests run on the JVM (no emulator required) via Robolectric, with the
document store in a temporary folder. Highlights:

| Suite | Covers |
| --- | --- |
| `SnippetParserTest` | line parsing, blank handling, duplicates, reindexing, large inputs |
| `CopiedStateCodecTest` | encode/decode round trips, unicode, malformed and legacy payloads |
| `DocumentRepositoryTest` | CRUD, search, empty-document cleanup, idempotent backup merge |
| `JsonFileDocumentDaoTest` | persistence across restarts, id allocation, corrupt-file handling |
| `LegacyDatabaseImportTest` | import from all three Room schemas, write-ahead-log data, run-once, backup copy |
| `EditorViewModelTest` | load, mode switching, copy tracking, reorder, autosave flush on exit |
| `AppNavigatorTest` | open/back, double-tap and repeated-back guards, state restoration |

## Data & privacy

- Documents are stored locally in one JSON file (`documents.json`), written
  atomically. Installs from before 2.3 import their Room database once on first
  launch; the old database and a byte-for-byte copy of it are kept.
- GitHub backup is opt-in. It stores one file in a *secret* gist on your
  account: unlisted, but readable by anyone who has its URL. The token is
  limited to the `gist` scope and encrypted with the Android Keystore.
- Restores merge by document UUID: newer versions win and repeated restores never
  duplicate documents.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · kotlinx.serialization · Coroutines/Flow ·
WorkManager · OkHttp · GitHub REST API · Robolectric + Truth
