<h1 align="center">Eta</h1>

<p align="center"><a href="README.md">简体中文</a> | <strong>English</strong></p>

<p align="center"><strong>A third-party build based on upstream Eta</strong></p>

> **Original project: [Mangi-11/Eta](https://github.com/Mangi-11/Eta)**
>
> This repository is not the original, nor a GitHub fork — it is an independently
> published repository built from the upstream source. Eta's design, core
> implementation and all base capabilities come from the upstream authors. Only the
> changes listed below are added here. For feature documentation, usage guides and
> issue reports, please refer to the upstream repository.
>
> Both repositories are licensed under **GPL-3.0** and distributed under the same terms.

---

## Based on which version

| Item | Value |
| --- | --- |
| Upstream baseline | **Eta 3.4.3** (tag `v3.4.3`, commit `32e147a`) |
| This build | 3.5.0 (versionCode 2026111403) |
| Package name | `io.github.mangi.eta.stable`, coexists with the original |

71 commits accumulated since upstream 3.4.3. Everything below is layered on top of
upstream code. **Nothing from upstream was removed or degraded.**

---

## 1. AI image generation

Upstream is text-only; this build lets the Agent produce images directly.

- **In-chat generation**: the Agent calls an image tool, results are saved to disk and optionally to the system gallery, and can be reviewed in chat
- **Input bar entry**: type a prompt and tap to generate; supports size, count (1–4) and auto-save
- **Relay compatibility**: when an endpoint rejects `response_format` / `n` / `size`, it retries other endpoints and degrades gracefully, surfacing the original error
- **Dedicated icon**: the image button no longer shares the thinking icon

## 2. Provider configuration

The new and edit screens share one set of connection fields; the upstream format is switched in place instead of picking a different entry from the list.

- **In-page format switch**: OpenAI-compatible / Anthropic Messages / Gemini Interactions. New providers can switch at any time; existing ones keep their own type so a mistaken switch cannot drop model configuration
- **Fields follow the type**: `anthropic-version`, hosted web search and similar fields appear or hide immediately, no longer depending on the initial instance
- **Image-only switch**: a standalone switch right below the API key. Turning it on fetches image models only over the OpenAI-compatible protocol and excludes chat models. Anthropic and Gemini have no image endpoint, so the switch is hidden there
- **Endpoint mode follows the protocol stack**: shown whenever OpenAI-compatible (image providers included) with API-key auth is used, fixing the Endpoint row disappearing after enabling image-only
- **Merged entry point**: four "add provider" entries collapse into one, with the supported formats named in the summary
- **Separate group**: the provider list splits image providers into their own section
- **Kept out of chat**: image models never appear in the chat model picker, whichever path fetched them
- **Editor adaptation**: image models skip the context window requirement and hide reasoning settings, while keeping default size, format and endpoint

## 3. Local offline speech recognition

Upstream speech recognition is entirely cloud-based; this build adds a fully offline path.

- **On-device inference**: sherpa-onnx with a Chinese streaming zipformer2 model, transcribing as you speak
- **No network, no API key**: audio never leaves the device
- **Model on demand**: the engine ships in the APK; the model downloads when "Local offline" is selected, with progress, cancel, delete and per-file SHA-256 verification
- **Works in both entry points**: chat dictation and the assistant overlay share the same recognition pipeline

## 4. System TTS playback

- **Uses the device's built-in speech engine**: no network, no key, no cost
- Voice and quality follow the system speech settings; available alongside upstream's cloud playback

## 5. Message navigation

- **Whole-conversation navigation**: open the navigator from the side pane to browse every turn and jump straight to it
- **Paged loading**: page size raised to 256, so navigation no longer only covers the most recent stretch of a long conversation
- Untitled turns get a recognisable placeholder label

## 6. Conversation file management

- **Per-conversation file list**: every attachment sent in a conversation, browsable and retrievable
- Empty conversations show a clear hint instead of a blank panel

## 7. Segmented copy for long answers

- **Long answers are no longer all-or-nothing**: select and copy individual paragraphs
- Bypasses the system text selection overlay

## 8. Provider list organisation

- **Three sort modes**: a top-bar button sorts by name (Chinese pinyin collation), endpoint type, or configuration state
- **Configured vs unconfigured**: the list is split into two groups with configured providers first
- **Credential-based**: "configured" means it has models **and** usable credentials, so built-ins without a key no longer crowd the group
- **Collapsible provider groups**: the model picker becomes a settings-style grouped card with collapsed groups, dropping redundant tags and filter rows

## 9. Model capability detection

- **Presets by model name**: opening the editor shows suggested context window and reasoning levels instead of empty fields
- **Fixed "unknown context"**: the effective window only read the user override, hiding the value the remote API returned; a fallback is now in place
- **Cross-catalogue detection for relays**: when a custom provider's own catalogue misses, the model ID is looked up in the OpenAI / Anthropic / Gemini catalogues, so windows and reasoning levels resolve correctly
- **Presets cover major families**: gpt-6.1, gpt-6, gpt-5.6, gpt-5, claude, gemini, glm, qwen, deepseek, kimi, minimax, step, longest prefix first
- **Existing records backfilled**: models stored without window or level fields are filled in memory on read without writing back, so user overrides still win and no re-fetch is needed
- **Instant prefill**: typing a model ID in the editor fills the window and levels from the preset immediately
- **Output limit reserved**: the model table gains an output token limit (remote value plus user override)

## 10. Other refinements

| Item | Detail |
| --- | --- |
| First-token placeholder | A placeholder animation covers the wait for the first token |
| User bubble long-press | Vibrates once and no longer opens system text selection |
| "Thinking" label | Trailing ellipsis removed |
| Virtual display IME policy | Fixed the failure that destroyed a virtual display right after creation |
| Virtual display capsule | Fixed width, no more horizontal jumping when expanding or collapsing |
| Virtual display layout | Reworked the preview and action row arrangement |
| First frame | No longer blocked by disk IO at startup |
| Conversation loading | Legacy double-escaped checkpoints no longer break loading |
| Auto-compaction threshold | Triggers at 80% of the window (was 70%), cutting mid-task compactions |
| Stats dialog | Fixed centring and the summary bar border gap |
| Miuix chat list | Fixed one-way scrolling |

## 11. Build and release

- **Stable and debug coexist**: the release build derives a `.stable` package name with its own signature, so both can be installed at once
- **Signing fallback**: environment variables take priority, falling back to `keystore.properties` in the repo root, so release builds work locally
- **CI gate**: lint and unit tests only run on release tags, keeping routine pushes off the runner
- **Release overwrite**: republishing the same version overwrites the asset in place instead of creating a duplicate install

---

## Install

Current version **3.5.0** (versionCode 2026111403). The release build uses package name
`io.github.mangi.eta.stable`. It has a different signature and package name from upstream,
so both can be installed side by side.

Download the APK from this repository's Releases. Upgrading from an older version
migrates the database automatically; conversations, models and settings are kept.

Requires **Android 13+ on ARM64 devices**.

---

## Relationship to upstream

- Original project: [Mangi-11/Eta](https://github.com/Mangi-11/Eta)
- Feature docs and upstream usage: visit the repository above
- Source of this build: [amos023/Eta](https://github.com/amos023/Eta) (this repository, matching the released APK)
- This repository only carries the additions listed here and does not provide standalone documentation

If you only need the original feature set, use upstream directly.

---

## License and modification notice

This build is distributed under GPL-3.0. The full text is in [LICENSE](LICENSE) at the repository root
and is bundled in the APK as `assets/licenses/GPL-3.0.txt`.

Modifications relative to upstream `Mangi-11/Eta` have been made by amos023 since 2026-11-09;
the scope is the sections above. The corresponding source is published in full in this repository
with nothing removed.