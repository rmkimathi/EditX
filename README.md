# EditX — Android Studio project

A Kotlin + XML Android text/code editor starter project.

## Requirements
- Android Studio (Quail 4 or compatible version)
- Android SDK Platform 37 installed
- JDK 17 (use Android Studio's embedded JDK if supported)

## Open and build
1. Extract `EditX.zip`.
2. In Android Studio choose **Open** and select the `EditX` directory.
3. Allow Gradle sync and install Android SDK Platform 37 if prompted.
4. Run the `app` configuration on a device or emulator (API 28+).

## Features included
- New, Open, Save, Save As using Android's Storage Access Framework
- Undo (basic snapshot-based undo), Select All, Copy, Paste
- Find next, replace one, replace all
- Word wrap toggle and line-number gutter toggle
- Language selection / extension-based detection for C/C++, C#, Java, Kotlin, XML, HTML, JavaScript, Markdown, PHP, Perl, Python, Ruby, Smali, Swift, JSON and Shell
- Lightweight regex-based syntax coloring for comments, strings, common keywords and numbers
- Status bar with language, UTF-8, line and column, and modified indicator

## Notes
- This is a functional starter implementation, not a full IDE engine. Syntax coloring is intentionally lightweight and is not a complete parser or TextMate grammar; language-specific edge cases will not all be colored correctly.
- The line-number gutter updates on edits; advanced synchronized scrolling, code folding, autocomplete, multi-cursor editing, and large-file optimizations are not included yet.
- `compileSdk = 37` and `targetSdk = 37` require the corresponding Android SDK/platform and a sufficiently recent Android Gradle Plugin. If your installed Quail 4 build ships a newer AGP, Android Studio may suggest updating the plugin version during sync.
