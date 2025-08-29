# ImmaginarIA

ImmaginarIA is an experimental Android application where players build a collaborative story by exchanging short voice messages. The app transcribes the clips, arranges them into a coherent story with the help of a language model and generates images and narration for an illustrated storybook.

## Repository Structure

- `app/` – Android application module
- `docs/` – Documentation and design notes

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the high level architecture.

## Building

The project is built with Gradle. You will need a Java Development Kit (JDK 17 or later)
and the Android SDK or Android Studio. Use the provided Gradle wrapper scripts to build
the project.

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

## Deployment

Release builds can be automatically distributed to testers through Firebase App Distribution.
The GitHub Actions workflow in `.github/workflows/firebase-app-distribution.yml` builds the
release APK and uploads it whenever a tag starting with `v` is pushed.
Refer to [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for configuration details.
