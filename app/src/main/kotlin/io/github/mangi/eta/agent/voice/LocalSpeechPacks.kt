package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.AsrProvider

/**
 * 可下载的本地语音模型包。每个包独立目录、独立校验，互不影响；
 * APK 只带推理引擎，模型在用户选中对应服务时按需下载。
 */
internal enum class LocalSpeechPack(
    /** 应用私有目录下的子目录名，不带路径分隔符。 */
    val directory: String,
    /** 流式包边录边出字；非流式包录完再整段识别。 */
    val streaming: Boolean,
    /** 上游仓库中的模型根地址，可在设置里整体替换。 */
    val sourceUrl: String,
    val files: List<ModelFile>,
) {
    /** 中文流式 zipformer：体积最小，边说边出字。 */
    STREAMING(
        directory = "sherpa-zh-14m",
        streaming = true,
        sourceUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main",
        files = listOf(
            ModelFile(
                remote = "encoder-epoch-99-avg-1.int8.onnx", local = "encoder.int8.onnx", bytes = 21_621_684L,
                sha256 = "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1",
            ),
            ModelFile(
                remote = "decoder-epoch-99-avg-1.int8.onnx", local = "decoder.int8.onnx", bytes = 1_888_682L,
                sha256 = "22f123bb8cba9b38974b3df18a3f45e7081f4985ebb2e075d9f21f618c468bbf",
            ),
            ModelFile(
                remote = "joiner-epoch-99-avg-1.int8.onnx", local = "joiner.int8.onnx", bytes = 1_795_562L,
                sha256 = "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169",
            ),
            ModelFile(
                remote = "tokens.txt", local = "tokens.txt", bytes = 48_697L,
                sha256 = "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac",
            ),
        ),
    ),

    /** 中文 Paraformer：整段识别，比流式包更准，速度更快。 */
    PARA_ZH(
        directory = "sherpa-para-zh",
        streaming = false,
        sourceUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09/resolve/main",
        files = listOf(
            ModelFile(
                remote = "model.int8.onnx", local = "model.int8.onnx", bytes = 81_828_675L,
                sha256 = "3ef6c19369b912f7caf3cef8e545c5ccd1a33d9d7ec792a46668dc41c4b229ec",
            ),
            ModelFile(
                remote = "tokens.txt", local = "tokens.txt", bytes = 75_352L,
                sha256 = "4b2d964e18b9cf139b473003b6698fb2ed9a2a5ec55b93daa677b28f578897aa",
            ),
        ),
    ),

    /** SenseVoice：整段识别，支持普通话/粤语/英日韩，并带标点与情感、事件标签。 */
    SENSE_VOICE(
        directory = "sherpa-sense-voice",
        streaming = false,
        sourceUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main",
        files = listOf(
            ModelFile(
                remote = "model.int8.onnx", local = "model.int8.onnx", bytes = 239_233_841L,
                sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            ),
            ModelFile(
                remote = "tokens.txt", local = "tokens.txt", bytes = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            ),
        ),
    ),
    ;

    val totalBytes: Long get() = files.sumOf { it.bytes }

    companion object {
        fun of(provider: AsrProvider): LocalSpeechPack? = when (provider) {
            AsrProvider.LOCAL -> STREAMING
            AsrProvider.LOCAL_PARA -> PARA_ZH
            AsrProvider.LOCAL_SENSE -> SENSE_VOICE
            else -> null
        }
    }
}

/** [remote] 是上游仓库里的文件名，[local] 是落盘后给识别器用的短名。 */
internal data class ModelFile(
    val remote: String,
    val local: String,
    val bytes: Long,
    val sha256: String,
)