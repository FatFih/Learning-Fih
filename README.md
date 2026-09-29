# WebViewShell

Android shell that loads your HTML/CSS/JS from `app/src/main/assets/index.html`.

## Dev loop (VS Code)
- Edit files in `app/src/main/assets/`.
- Preview in desktop browser: right-click index.html -> "Open with Live Server".
- Test on phone: Ctrl+Shift+B (runs `gradle installDebug`), or run the task "Open app on phone" afterwards.
- Inspect on phone: open chrome://inspect in desktop Chrome (debug builds only).

## Notes
- Tasks call `gradle` directly (needs Gradle 8.9+). To use the wrapper instead, run
  `gradle wrapper --gradle-version 8.9` once and swap `gradle` for `./gradlew` in .vscode/tasks.json.
- If Gradle can't find the SDK, set ANDROID_HOME or create local.properties with:
  sdk.dir=/path/to/Android/sdk
- Rename the package: change `com.example.webapp` in app/build.gradle.kts, the Kotlin file's
  package line/folder, AndroidManifest.xml (nothing else), and the "Open app" task.
