# Story export format

Completed stories can be exported as a `.zip` archive from the Finished Stories
list. The archive contains the following top-level entries:

- `metadata.json` – structured description of the story and exported assets.
- `text/` – folder with plain-text versions of the story in different
  languages.
- `images/` – folders containing the generated artwork.
- `audio/` – optional folder with recorded audio segments when available.

## metadata.json

The `metadata.json` file is encoded as UTF-8 and exposes the content that the
application needs to reconstruct the storyboard. Its schema is:

- `id`: Numeric identifier of the story.
- `title`: The story title.
- `timestamp`: Unix epoch milliseconds when the story was created.
- `language`: Optional ISO language tag that represents the original language
  of the story.
- `processed`: Indicates whether the story finished processing when exported.
- `story_original`: Original-language text used to build the storyboard.
- `story_english`: English rewrite of the story when available.
- `content`: Raw JSON response from the stitching model, if present.
- `segments`: Array describing the recorded input. Each item contains:
  - `source_path`: Local path of the original recording on the device.
  - `file`: Relative path inside the archive (for example
    `"audio/segment_0.wav"`) when the audio file is exported.
- `characters`: Array of character descriptors. Each item contains:
  - `name`: Character name in the working language.
  - `description`: Character description in the working language.
  - `name_english`: Optional English name.
  - `description_english`: Optional English description.
  - `image`: Relative path to the exported character image when present.
- `environments`: Array of environment descriptors mirroring the structure of
  `characters`.
- `scenes`: Array of storyboard scenes. Each item contains:
  - `caption_original`: Scene description in the original language.
  - `caption_english`: English version of the caption.
  - `environment`: Display name of the referenced environment asset.
  - `characters`: Array of character display names appearing in the scene.
  - `image`: Relative path to the exported scene image when present.

All relative image and audio paths match the file names stored in the archive.

## Text files

The `text` directory contains human-readable story content:

- `story_original.txt`: Original language story.
- `story_english.txt`: English rewrite when it differs from the original.

Files are encoded as UTF-8 and can be opened by any text editor.

## Image and audio assets

Every exported image is written under `images/<category>/` where `<category>`
can be `characters`, `environments`, or `scenes`. File names include an index
and a sanitized version of the asset name (for example,
`images/scenes/1-forest-entrance.png`).

If audio segments were captured during story creation, they are exported in the
`audio/` directory using their original file extensions when available. When no
extension is present, `.wav` is used by default.

## File location and sharing

Archives are stored in the application files directory under `exports/` before
being shared through Android's attachment intents. Users can attach the archive
from email clients or other apps that support sending files.
