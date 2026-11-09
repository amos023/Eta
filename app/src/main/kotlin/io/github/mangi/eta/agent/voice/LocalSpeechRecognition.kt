package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.res.AssetManager
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
 * 内置离线识别：sherpa-onnx 流式 zipformer 模型随包内置，全部推理在本机完成，
 * 不建立网络连接、不需要任何凭据。模型为 16 kHz 单声道，与 [SpeechRecorder] 输出一致。
 */
internal object LocalSpeechModel {
    const val DIRECTORY = "sherpa-zh-14m"
    private val FILES = listOf(
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "joiner.int8.onnx",
        "tokens.txt",
    )

    /** 模型随 APK 发布，缺失只可能是构建异常；读取一次即可判定，不做静默降级。 */
    fun installed(assets: AssetManager): Boolean = FILES.all { name ->
        runCatching { assets.open("$DIRECTORY/$name").close() }.isSuccess
    }
}

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
        val assets = context.applicationContext.assets
        if (!LocalSpeechModel.installed(assets)) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "内置语音模型缺失，请重新安装 Eta")
        }
        val recognizer = try {
            createLocalRecognizer(assets, settings, endpointSilenceMs)
        } catch (error: Exception) {
            AndroidAgentLogger.warn("Eta local speech init failed: ${error.javaClass.simpleName}")
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "内置语音模型初始化失败")
        }
        val stream = try {
            recognizer.createStream()
        } catch (error: Exception) {
            runCatching { recognizer.release() }
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "内置语音模型初始化失败")
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
    assets: AssetManager,
    settings: SpeechSettings,
    endpointSilenceMs: Int,
): OnlineRecognizer {
    val directory = LocalSpeechModel.DIRECTORY
    // 断句时长沿用「停顿后自动发送」的配置；未开启时用默认 800 ms，只影响预览分段。
    val trailingSilence = (if (endpointSilenceMs > 0) endpointSilenceMs else DEFAULT_SILENCE_MS) / 1000f
    val config = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SpeechRecorder.RATE, featureDim = FEATURE_DIM),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "$directory/encoder.int8.onnx",
                decoder = "$directory/decoder.int8.onnx",
                joiner = "$directory/joiner.int8.onnx",
            ),
            tokens = "$directory/tokens.txt",
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
    return OnlineRecognizer(assets, config)
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
