<h1 align="center">Eta</h1>

<p align="center"><a href="README.md">简体中文</a> | <strong>English</strong></p>

<p align="center"><strong>A third-party build based on upstream Eta</strong></p>

> **Original project: [Mangi-11/Eta](https://github.com/Mangi-11/Eta)**
>
> This repository is not the original. Eta's design, core implementation and all
> base capabilities come from the upstream authors. Only the changes listed below
> are added here. For feature documentation, usage guides and issue reports,
> please refer to the upstream repository.

---

## Based on which version

| Item | Value |
| --- | --- |
| Upstream baseline | **Eta 3.4.3** (tag `v3.4.3`, commit `32e147a`) |
| This build | 3.4.4 (versionCode 2026111401) |
| Package name | `io.github.mangi.eta.stable`, coexists with the original |

After merging upstream 3.4.3, this branch only applies the additions below.
**Nothing from upstream was removed or degraded.**

---

## What this build adds

### 1. AI image generation

Upstream is text-only; this build lets the Agent produce images directly.

- **In-chat generation**: the Agent calls an image tool, results are saved to disk and optionally to the system gallery, and can be reviewed in chat
- **Input bar entry**: type a prompt and tap to generate; supports size, count (1–4) and auto-save
- **Relay compatibility**: when an endpoint rejects `response_format` / `n` / `size`, it retries other endpoints and degrades gracefully, surfacing the original error
- **Dedicated icon**: the image button no longer shares the thinking icon

### 2. Image-generation providers

- **Separate entry**: the new-provider menu gains "Add image model"; providers created there **only pull image models and exclude chat models**
- **Separate group**: the provider list splits image providers into their own section
- **Kept out of chat**: image models never appear in the chat model picker, whichever path fetched them
- **Editor adaptation**: image models skip the context window requirement and hide reasoning settings, while keeping default size, format and endpoint

### 3. Local offline speech recognition

Upstream speech recognition is entirely cloud-based; this build adds a fully offline path.

- **On-device inference**: sherpa-onnx with a Chinese streaming zipformer2 model, transcribing as you speak
- **No network, no API key**: audio never leaves the device
- **Model on demand**: the engine (~24 MB) ships in the APK; the model (~167 MB) downloads when "Local offline" is selected, with progress, cancel, delete and per-file SHA-256 verification
- **Works in both entry points**: chat dictation and the assistant overlay share the same recognition pipeline

### 4. System TTS playback

- **Uses the device's built-in speech engine**: no network, no key, no cost
- Voice and quality follow the system speech settings; available alongside upstream's cloud playback

---

## Issues fixed along the way

Compatibility problems exposed by combining upstream 3.4.3 with local changes.

| Issue | Detail |
| --- | --- |
| New conversations had no model | Top bar showed "Not selected"; now inherits the previous conversation's model |
| Image models leaked into chat | Relays misreport image models' output modality as `text`; now judged by model id first |
| Image models required a context window | Image calls exchange no tokens; that field is no longer validated |
| Speech settings failed to save permanently | Keystore keys can be permanently invalidated while `getKey` still returns them; now probed for usability and rebuilt when dead |
| Crash on database upgrade | Merging upstream misplaced the cache-column migration, so already-upgraded users never got the new columns; added an idempotent v29→v30 migration that works for both fresh and upgraded databases |

---

## Install

The release build uses package name `io.github.mangi.eta.stable`. It has a different
signature and package name from upstream, so both can be installed side by side.

Download the APK from this repository's Releases. Upgrading from an older version
migrates the database automatically; conversations, models and settings are kept.

Requires **Android 13+ on ARM64 devices**.

---

## Relationship to upstream

- Original project: [Mangi-11/Eta](https://github.com/Mangi-11/Eta)
- Feature docs and upstream usage: visit the repository above
- This repository only carries the additions listed here and does not provide standalone documentation

If you only need the original feature set, use upstream directly. This build targets
users who want image generation, offline speech or system TTS.
