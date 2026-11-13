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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 本地流式识别：中文 zipformer2 随用随下，推理全部在本机完成，不联网、不需要凭据。
 * 模型为 16 kHz 单声道，与 [SpeechRecorder] 输出一致。
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
            LocalRecognizerCache.acquire(context, pack, settings)
        } catch (error: Exception) {
            AndroidAgentLogger.warn("Eta local speech init failed: ${error.javaClass.simpleName}")
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "离线模型初始化失败，请删除后重新下载")
        }
        val stream = try {
            recognizer.createStream()
        } catch (error: Exception) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "离线模型初始化失败，请删除后重新下载")
        }
        var text = ""
        var endpointSent = false
        var silenceMs = 0
        try {
            // 模型就绪后再开始录音，避免把初始化耗时算进用户的说话时间。
            onReady()
            for (frame in frames) {
                if (frame.isEmpty()) continue
                val samples = toSamples(frame)
                stream.acceptWaveform(samples, SpeechRecorder.RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val partial = recognizer.getResult(stream).text
                if (partial.isNotBlank() && partial != text) {
                    text = partial
                    onPreview(partial)
                }
                // 断句自己算：缓存复用同一个识别器，不能靠每会话可变的模型端点参数。
                if (endpointSilenceMs > 0 && !endpointSent) {
                    val durationMs = samples.size.toLong() * 1000 / SpeechRecorder.RATE
                    silenceMs = if (isSilent(samples)) silenceMs + durationMs.toInt() else 0
                    if (silenceMs >= endpointSilenceMs && text.isNotBlank()) {
                        endpointSent = true
                        onEndpoint()
                    }
                }
            }
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val final = recognizer.getResult(stream).text
            if (final.isNotBlank()) text = final
            text.trim()
        } finally {
            runCatching { stream.release() }
        }
    }
}

/**
 * 大模型加载一次要数秒，识别会话之间复用同一个 [OnlineRecognizer]：
 * 首次启动付出一次加载代价，之后每次识别都是秒开。
 * 线程数变化或模型被重新下载时按配置重建，避免沿用过期的模型路径。
 */
private object LocalRecognizerCache {
    private val mutex = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var signature: String? = null
    private var lastUsed: Long = 0L

    suspend fun acquire(context: Context, pack: LocalSpeechPack, settings: SpeechSettings): OnlineRecognizer =
        mutex.withLock {
            val threads = settings.localAsr.threads.coerceIn(1, 4)
            // 目录 mtime 参与签名：删了重下会改变它，从而重建。
            val stamp = LocalSpeechModel.directory(context, pack).lastModified()
            val key = "${pack.directory}|$threads|$stamp"
            recognizer?.takeIf { signature == key }?.let {
                lastUsed = System.currentTimeMillis()
                return@withLock it
            }
            runCatching { recognizer?.release() }
            recognizer = null
            signature = null
            val created = createRecognizer(context, pack, threads)
            recognizer = created
            signature = key
            lastUsed = System.currentTimeMillis()
            created
        }

    /** 长时间未使用后释放，避免常驻放大进程内存。 */
    suspend fun releaseIfIdle(idleMillis: Long) = mutex.withLock {
        if (recognizer != null && System.currentTimeMillis() - lastUsed > idleMillis) {
            runCatching { recognizer?.release() }
            recognizer = null
            signature = null
        }
    }

    suspend fun release() = mutex.withLock {
        runCatching { recognizer?.release() }
        recognizer = null
        signature = null
    }
}

private fun createRecognizer(
    context: Context,
    pack: LocalSpeechPack,
    threads: Int,
): OnlineRecognizer {
    fun pathOf(name: String) = LocalSpeechModel.pathOf(context, pack, name)
    val config = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SpeechRecorder.RATE, featureDim = FEATURE_DIM),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = pathOf("encoder.int8.onnx"),
                decoder = pathOf("decoder.onnx"),
                joiner = pathOf("joiner.int8.onnx"),
            ),
            tokens = pathOf("tokens.txt"),
            numThreads = threads,
            modelType = pack.modelType,
        ),
        // 端点规则取固定值；停顿时长由识别循环自己按静音帧累计，避免会话间反复重建模型。
        endpointConfig = EndpointConfig(
            rule1 = EndpointRule(false, SLOW_SILENCE, 0f),
            rule2 = EndpointRule(true, DEFAULT_SILENCE, 0f),
            rule3 = EndpointRule(false, 0f, MIN_UTTERANCE_LENGTH),
        ),
        enableEndpoint = true,
        decodingMethod = "greedy_search",
        maxActivePaths = MAX_ACTIVE_PATHS,
    )
    // 模型在应用私有目录而不是 assets 里，传 null 让原生层按路径加载。
    return OnlineRecognizer(config = config)
}

/** 用均方根判断这一帧是否静音；阈值取 16 位幅度约 -42 dB。 */
private fun isSilent(samples: FloatArray): Boolean {
    if (samples.isEmpty()) return true
    var energy = 0.0
    for (sample in samples) energy += sample.toDouble() * sample
    return kotlin.math.sqrt(energy / samples.size) < SILENCE_RMS
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

private const val FEATURE_DIM = 80
private const val MAX_ACTIVE_PATHS = 4
/** 用户未启用「停顿自动发送」时的断句阈值，只影响预览分段。 */
private const val DEFAULT_SILENCE = 0.8f
private const val SLOW_SILENCE = 2.4f
private const val MIN_UTTERANCE_LENGTH = 20f
private const val SILENCE_RMS = 0.008f
/** 与 [SpeechRecorder] 的 60 秒录音上限同量级，留出模型初始化余量。 */
private const val LOCAL_TIMEOUT_MS = 180_000L