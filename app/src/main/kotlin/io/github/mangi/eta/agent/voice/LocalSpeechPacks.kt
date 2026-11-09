package io.github.mangi.eta.agent.voice

/**
 * 可下载的本地语音模型包。APK 只带推理引擎，模型在用户选中本地识别时按需下载。
 * 目前只维护一个包：中文流式高精度 zipformer2，边说边出字。
 */
internal enum class LocalSpeechPack(
    /** 应用私有目录下的子目录名，不带路径分隔符。 */
    val directory: String,
    /** 流式包边录边出字；非流式包录完再整段识别。 */
    val streaming: Boolean,
    /** 上游仓库中的模型根地址。 */
    val sourceUrl: String,
    val modelType: String,
    val files: List<ModelFile>,
) {
    /** 中文流式 zipformer2：153 MB encoder，准确率明显高于 14M 小模型，仍然实时。 */
    STREAMING(
        directory = "sherpa-zh-stream",
        streaming = true,
        sourceUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30/resolve/main",
        modelType = "zipformer2",
        files = listOf(
            ModelFile(
                remote = "encoder.int8.onnx", local = "encoder.int8.onnx", bytes = 161_141_793L,
                sha256 = "5ac51e27981bb4dab01bb9be4958453ba50c3b61c063ddda0eab23fd3671aa4f",
            ),
            ModelFile(
                remote = "decoder.onnx", local = "decoder.onnx", bytes = 5_165_083L,
                sha256 = "06522ad63cec0fdf6809f4e1db9bb4f7d710c34582e3b35db62ac60eccafac7e",
            ),
            ModelFile(
                remote = "joiner.int8.onnx", local = "joiner.int8.onnx", bytes = 1_033_416L,
                sha256 = "b34584dc6f561089e1d747fedebb3765f2caa72c927ef54d7ca55e5ae40a814b",
            ),
            ModelFile(
                remote = "tokens.txt", local = "tokens.txt", bytes = 20_628L,
                sha256 = "6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652",
            ),
        ),
    ),
    ;

    val totalBytes: Long get() = files.sumOf { it.bytes }
}

/** [remote] 是上游仓库里的文件名，[local] 是落盘后给识别器用的短名。 */
internal data class ModelFile(
    val remote: String,
    val local: String,
    val bytes: Long,
    val sha256: String,
)