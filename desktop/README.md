# OneClickCopy for Windows

A desktop port of the Android app, built with Compose Multiplatform. Same
dark UI, same Edit/Copy modes, same backup format.

## What is shared with Android

The build copies these sources from `../app` verbatim, so fixes land in both
apps: snippet parsing and copied-state encoding (`domain/`),
`DocumentRepository`, the backup payload, the GitHub gist transport and
`SyncState`. The pure-JVM domain tests from `app/src/test` run here too.

Desktop-specific pieces:

| Android                         | Windows                                        |
|---------------------------------|------------------------------------------------|
| Room database                   | `JsonFileDocumentDao`: one JSON file, atomic writes |
| Android Keystore token storage  | Windows DPAPI (`FileGitHubTokenStore`)         |
| WorkManager sync                | Coroutine debounce + exponential backoff, flushed on exit |
| Long-press                      | Right-click (long-press also works)            |
| System back                     | Esc                                            |

Data lives in `%APPDATA%\OneClickCopy` (`documents.json`). Export/Import uses
the same file format as the phone, so files move between the two. Only one
copy of the app runs at a time; launching it again brings the window forward.

## Keyboard

| Key        | Where     | Action                              |
|------------|-----------|-------------------------------------|
| Ctrl+N     | Documents | New document                        |
| Ctrl+F     | Documents | Search (Esc closes)                 |
| Ctrl+E     | Editor    | Toggle Edit / Copy mode             |
| Enter      | Copy mode | Copy the next unchecked item        |
| Ctrl+Z     | Copy mode | Undo reorder / delete / duplicate   |
| Esc        | Editor    | Back to documents                   |

The pin button keeps the window on top of other apps, handy when pasting
items one by one into another window.

## Build

Run from the repository root (JDK 17):

```sh
./gradlew -p desktop test                 # shared + desktop tests
./gradlew -p desktop run                  # run on this machine
./gradlew -p desktop packageUberJarForCurrentOS -Ptarget=windows
```

`-Ptarget=windows` bundles the Windows Skia binaries, so the jar can be built
on Linux. The output lands in `desktop/build/compose/jars/`.

## Package and install on Windows

On the Windows machine, with a JDK 17 unpacked (for `jpackage`):

```powershell
.\packaging\package-windows.ps1 -Jar OneClickCopy-2.2.0.jar -Jdk C:\path\to\jdk-17
.\packaging\install-windows.ps1
```

The first script builds `dist\OneClickCopy\OneClickCopy.exe` with a trimmed
Java runtime (~120 MB). The second installs it per user, no admin needed, to
`%LOCALAPPDATA%\Programs\OneClickCopy`, adds Start menu and desktop shortcuts,
and registers an uninstaller under Settings > Apps. Re-running it updates in
place; documents are never touched.

On a Windows machine with WiX available, `./gradlew -p desktop packageMsi`
builds an MSI installer instead.
