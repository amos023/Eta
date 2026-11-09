package io.github.mangi.eta.agent.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.SpeechSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 本地整段识别：先用 [collectSpeechAudio] 收齐整段录音，再一次性送入非流式模型。
 * 与流式包相比不产生中间预览，但中文准确率更高、耗时更低，也不需要任何网络。
 */
internal suspend fun recognizeSpeechLocalOffline(
    context: Context,
    pack: LocalSpeechPack,
    settings: SpeechSettings,
    wav: ByteArray,
    onProgress: (String) -> Unit,
): String = withTimeout(LOCAL_OFFLINE_TIMEOUT_MS) {
    withContext(Dispatchers.Default) {
        if (!LocalSpeechModel.installed(context, pack)) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "请先在语音设置中下载对应的本地模型")
        }
        // pcmToWav 由本工程生成，头固定 44 字节，这里直接跳过去拿 PCM。
        if (wav.size <= WAV_HEADER_BYTES) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有录到语音，请重试")
        val samples = toSamples(wav, WAV_HEADER_BYTES, wav.size - WAV_HEADER_BYTES)
        if (samples.isEmpty()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有录到语音，请重试")
        onProgress("正在识别")
        val recognizer = try {
            createOfflineRecognizer(context, pack, settings)
        } catch (error: Exception) {
            AndroidAgentLogger.warn("Eta local offline speech init failed: ${error.javaClass.simpleName}")
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本地模型初始化失败，请删除后重新下载")
        }
        val stream = try {
            recognizer.createStream()
        } catch (error: Exception) {
            runCatching { recognizer.release() }
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本地模型初始化失败，请删除后重新下载")
        }
        try {
            stream.acceptWaveform(samples, SpeechRecorder.RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            runCatching { stream.release() }
            runCatching { recognizer.release() }
        }
    }
}

private fun createOfflineRecognizer(
    context: Context,
    pack: LocalSpeechPack,
    settings: SpeechSettings,
): OfflineRecognizer {
    val model = LocalSpeechModel.pathOf(context, pack, "model.int8.onnx")
    val tokens = LocalSpeechModel.pathOf(context, pack, "tokens.txt")
    val threads = settings.localAsr.threads.coerceIn(1, 4)
    val modelConfig = when (pack) {
        LocalSpeechPack.PARA_ZH -> OfflineModelConfig(
            paraformer = OfflineParaformerModelConfig(model = model),
            tokens = tokens,
            numThreads = threads,
            modelType = "paraformer",
        )
        LocalSpeechPack.SENSE_VOICE -> OfflineModelConfig(
            senseVoice = OfflineSenseVoiceModelConfig(
                model = model,
                // 空语言让模型自行判断；指定 zh 时中文更快也更稳。
                language = settings.localAsr.senseLanguage.trim(),
                useInverseTextNormalization = true,
            ),
            tokens = tokens,
            numThreads = threads,
            modelType = "sense_voice",
        )
        LocalSpeechPack.STREAMING -> throw SpeechFailure(
            SpeechErrorCode.CONFIGURATION, "流式模型不支持整段识别",
        )
    }
    val config = OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SpeechRecorder.RATE, featureDim = FEATURE_DIM),
        modelConfig = modelConfig,
    )
    // 模型在应用私有目录而不是 assets 里，传 null 让原生层按路径加载。
    return OfflineRecognizer(config = config)
}

/** PCM16 小端采样转 sherpa-onnx 需要的 [-1, 1] 浮点。 */
private fun toSamples(bytes: ByteArray, offset: Int, length: Int): FloatArray {
    val count = length / 2
    val samples = FloatArray(count)
    for (index in 0 until count) {
        val low = bytes[offset + index * 2].toInt() and 0xff
        val high = bytes[offset + index * 2 + 1].toInt()
        samples[index] = ((low or (high shl 8)).toShort()).toFloat() / 32768f
    }
    return samples
}

private const val FEATURE_DIM = 80
private const val WAV_HEADER_BYTES = 44
private const val LOCAL_OFFLINE_TIMEOUT_MS = 120_000L