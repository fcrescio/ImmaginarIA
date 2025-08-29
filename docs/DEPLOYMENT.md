# Deployment

This project uses GitHub Actions to automatically distribute release builds to Firebase App Distribution.
Pushing a Git tag that starts with `v` triggers the workflow. The action builds the release APK and uploads it to the testers group in Firebase.

## Configuration

1. Generate a Firebase service account with access to App Distribution and download the JSON credentials.
2. In the repository settings add the following secrets:
   - `FIREBASE_SERVICE_ACCOUNT` – contents of the service account JSON file.
   - `FIREBASE_APP_ID` – the Firebase App ID for the Android application.
   - `FIREBASE_TESTERS_GROUPS` – comma-separated list of tester groups or e-mail addresses that should receive builds.

## Manual trigger

The workflow also exposes a manual trigger from the GitHub Actions tab via **Run workflow**. This lets you distribute a build without pushing a new tag.

## Debugging

Verbose output from the Firebase upload step is enabled in the workflow. To see additional
debug information for all steps, create a repository secret named `ACTIONS_STEP_DEBUG`
and set its value to `true`.
