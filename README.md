# ImmaginarIA

ImmaginarIA is an experimental Android application where players build a collaborative story by exchanging short voice messages. The app transcribes the clips, arranges them into a coherent story with the help of a language model and generates images and narration for an illustrated storybook.

## Repository Structure

- `app/` – Android application module
- `docs/` – Documentation and design notes

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the high level architecture.

## Building

The project is built with Gradle. You will need a Java Development Kit (JDK 17 or later)
and the Android SDK or Android Studio. If the Gradle wrapper scripts (`gradlew` or
`gradlew.bat`) are not present, install Gradle separately and replace the wrapper
commands below with `gradle`.

### Linux

From the repository root run:

```bash
./gradlew assembleDebug
```

### macOS

Run the same command as on Linux:

```bash
./gradlew assembleDebug
```

### Windows

Use the batch script:

```bat
gradlew.bat assembleDebug
```

The resulting APK can be found in `app/build/outputs/apk/`.
