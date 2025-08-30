# AGENTS Instructions

These notes help contributors work with this repository.

## General

- Prefer `rg` for searching and avoid `grep -R` or `ls -R`.
- Run commands from the repository root unless noted.
- Keep the working tree clean and do not commit environment-specific files such as `app/google-services.json`.

## Development workflow

- Use the Gradle wrapper (`./gradlew`) for all build tasks.
- Before committing code, run:
  - `./gradlew lint` – static analysis
  - `./gradlew test` – unit tests (requires a local `app/google-services.json` to satisfy the Firebase plugin)
- If tests or lint fail because of missing local configuration, mention this in the pull request notes.

## Code style

- The project uses Kotlin. Follow the official Kotlin style guide.
- Use 4 spaces for indentation and write KDoc for public functions.
- Group imports in the order: standard library, third-party, project.

## Documentation

- Documentation lives in `docs/` and uses Markdown with lines wrapped at 80 columns.
