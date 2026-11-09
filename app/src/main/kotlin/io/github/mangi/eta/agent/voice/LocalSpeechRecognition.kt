package io.github.mangi.eta.agent.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.SpeechSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 内置离线识别：sherpa-onnx 流式 zipformer 模型由 [LocalSpeechModel] 在首次使用时下载到应用私有目录，
 * 推理全部在本机完成，不建立网络连接、不需要任何凭据。模型为 16 kHz 单声道，与 [SpeechRecorder] 输出一致。
 */
internal suspend fun recognizeSpeechLocal(
    context: Context,
    settings: SpeechSettings,
    frames: ReceiveChannel<ByteArray>,
    onReady: () -> Unit,
    onPreview: (String) -> Unit,
    onEndpoint: () -> Unit,
    endpointSilenceMs: Int,
): String = withTimeout(LOCAL_TIMEOUT_MS) {
    withContext(Dispatchers.Default) {
        val pack = LocalSpeechPack.STREAMING
        if (!LocalSpeechModel.installed(context, pack)) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "请先在语音设置中下载离线模型")
        }
        val recognizer = try {
            createLocalRecognizer(context, pack, settings, endpointSilenceMs)
        } catch (error: Exception) {
            AndroidAgentLogger.warn("Eta local speech init failed: ${error.javaClass.simpleName}")
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "离线模型初始化失败，请删除后重新下载")
        }
        val stream = try {
            recognizer.createStream()
        } catch (error: Exception) {
            runCatching { recognizer.release() }
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "离线模型初始化失败，请删除后重新下载")
        }
        var text = ""
        var endpointSent = false
        try {
            // 模型就绪后再开始录音，避免把初始化耗时算进用户的说话时间。
            onReady()
            for (frame in frames) {
                if (frame.isEmpty()) continue
                stream.acceptWaveform(toSamples(frame), SpeechRecorder.RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val partial = recognizer.getResult(stream).text
                if (partial.isNotBlank() && partial != text) {
                    text = partial
                    onPreview(partial)
                }
                // 与云端流式识别一致：只有要求自动发送时才把断句交回上层结束录音。
                if (endpointSilenceMs > 0 && !endpointSent && recognizer.isEndpoint(stream)) {
                    endpointSent = true
                    onEndpoint()
                }
            }
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val final = recognizer.getResult(stream).text
            if (final.isNotBlank()) text = final
            text.trim()
        } finally {
            runCatching { stream.release() }
            runCatching { recognizer.release() }
        }
    }
}

private fun createLocalRecognizer(
    context: Context,
    pack: LocalSpeechPack,
    settings: SpeechSettings,
    endpointSilenceMs: Int,
): OnlineRecognizer {
    fun pathOf(name: String) = LocalSpeechModel.pathOf(context, pack, name)
    // 断句时长沿用「停顿后自动发送」的配置；未开启时用默认 800 ms，只影响预览分段。
    val trailingSilence = (if (endpointSilenceMs > 0) endpointSilenceMs else DEFAULT_SILENCE_MS) / 1000f
    val config = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SpeechRecorder.RATE, featureDim = FEATURE_DIM),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = pathOf("encoder.int8.onnx"),
                decoder = pathOf("decoder.int8.onnx"),
                joiner = pathOf("joiner.int8.onnx"),
            ),
            tokens = pathOf("tokens.txt"),
            numThreads = settings.localAsr.threads.coerceIn(1, 4),
            modelType = MODEL_TYPE,
        ),
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(false, SLOW_SILENCE, 0f),
            rule2 = EndpointRule(true, trailingSilence, 0f),
            rule3 = EndpointRule(false, 0f, MIN_UTTERANCE_LENGTH),
        ),
        enableEndpoint = true,
        decodingMethod = "greedy_search",
        maxActivePaths = MAX_ACTIVE_PATHS,
    )
    // 模型在应用私有目录而不是 assets 里，传 null 让原生层按路径加载。
    return OnlineRecognizer(config = config)
}

/** PCM16 小端采样转 sherpa-onnx 需要的 [-1, 1] 浮点。 */
private fun toSamples(bytes: ByteArray): FloatArray {
    val count = bytes.size / 2
    val samples = FloatArray(count)
    for (index in 0 until count) {
        val low = bytes[index * 2].toInt() and 0xff
        val high = bytes[index * 2 + 1].toInt()
        samples[index] = ((low or (high shl 8)).toShort()).toFloat() / 32768f
    }
    return samples
}

private const val MODEL_TYPE = "zipformer"
private const val FEATURE_DIM = 80
private const val MAX_ACTIVE_PATHS = 4
private const val DEFAULT_SILENCE_MS = 800
private const val SLOW_SILENCE = 2.4f
private const val MIN_UTTERANCE_LENGTH = 20f
/** 与 [SpeechRecorder] 的 60 秒录音上限同量级，留出模型初始化余量。 */
private const val LOCAL_TIMEOUT_MS = 120_000L
