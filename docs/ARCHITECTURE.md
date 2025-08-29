# ImmaginarIA Architecture

ImmaginarIA is an Android application that lets children collaborate on an improvised story by exchanging short voice messages. Once the story is complete the app assembles the audio snippets into a multimedia storybook using generative AI services.

## Module Overview

| Module | Responsibility |
|--------|----------------|
| `app` | Android entry module with activities, navigation and UI. |
| `recorder` | Capture and store voice clips from each player. |
| `session` | Maintain the ordered list of story pieces and enforce game rules. |
| `transcriber` | Convert recorded audio into text. The implementation can call a local ASR engine or a remote API. |
| `llm` | Communicate with an LLM service to clean up transcripts, enforce continuity between messages and build the storyboard. |
| `image` | Generate an illustration for each storyboard element using a text-to-image model. |
| `tts` | Produce narrated audio for the final story using a TTS engine. |
| `storyviewer` | Render the illustrated and narrated story for playback. |

## Data Flow

1. **Recording** – Players take turns recording a short message. Each clip is stored locally and added to the session queue.
2. **Transcription** – After a clip is recorded it is sent to the transcriber module to obtain text.
3. **Story Assembly** – When the session ends the list of transcripts is sent to the LLM module. The LLM returns a structured storyboard that connects the clips.
4. **Illustration** – For every storyboard item the image module requests an illustration from a generative model.
5. **Narration** – The final story text is passed to the TTS module to generate audio narration.
6. **Playback** – The storyviewer module presents the sequence of images and plays narration audio, letting users save or replay the story.

## Tech Stack

* Language: Kotlin
* Architecture: MVVM with repository pattern
* Build System: Gradle
* External Services: ASR, Large Language Model, Image generation, Text-to-Speech

## Future Work

* Implement networking layer for authenticated API calls
* Local caching of generated assets
* Offline mode with on-device models
