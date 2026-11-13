package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.SpeechSettings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 已安装的系统语音合成引擎；[name] 为空表示跟随系统默认。 */
internal data class SystemTtsEngine(val name: String, val label: String, val isDefault: Boolean)

internal object SystemTts {
    private const val DEFAULT_ENGINE_SETTING = "tts_default_synth"

    /** 引擎列表需要在清单里声明 TTS_SERVICE 查询，否则 targetSdk 37 下查不到。 */
    fun engines(context: Context): List<SystemTtsEngine> {
        val default = Settings.Secure.getString(context.contentResolver, DEFAULT_ENGINE_SETTING)
        val manager = context.packageManager
        val services = runCatching {
            manager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        }.getOrDefault(emptyList())
        return services.asSequence()
            .mapNotNull { it.serviceInfo }
            .filter { it.enabled && it.applicationInfo?.enabled != false }
            .map { info ->
                val label = runCatching {
                    info.loadLabel(manager).toString().ifBlank { info.packageName }
                }.getOrDefault(info.packageName)
                SystemTtsEngine(info.packageName, label, info.packageName == default)
            }
            .distinctBy { it.name }
            .sortedWith(compareByDescending<SystemTtsEngine> { it.isDefault }.thenBy { it.label })
            .toList()
    }

    fun hasEngine(context: Context): Boolean = engines(context).isNotEmpty()
}

/**
 * 用系统 TTS 引擎把文本合成到临时文件，再读出 PCM 交给播放链路。
 * 引擎输出采样率由引擎决定（常见 16 kHz 或 22.05 kHz），这里统一重采样到 24 kHz，
 * 与云端语音共用同一套播放与淡入淡出逻辑。
 */
internal suspend fun synthesizeSystemSpeech(
    context: Context,
    settings: SpeechSettings,
    text: String,
    onAudio: (ByteArray) -> Unit,
) = withTimeout(SYSTEM_TTS_TIMEOUT_MS) {
    val config = settings.systemTts
    val engineName = config.engine.trim().takeIf { it.isNotEmpty() }
    val tts = createEngine(context, engineName)
    val file = File(context.cacheDir, "eta-tts-${UUID.randomUUID()}.wav")
    try {
        val language = localeOf(config.language)
        when (tts.setLanguage(language)) {
            TextToSpeech.LANG_MISSING_DATA ->
                throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "系统语音引擎缺少该语言的数据包")
            TextToSpeech.LANG_NOT_SUPPORTED ->
                throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "系统语音引擎不支持所选语言")
        }
        tts.setSpeechRate(config.rate.coerceIn(MIN_RATE, MAX_RATE))
        tts.setPitch(config.pitch.coerceIn(MIN_RATE, MAX_RATE))
        synthesizeToFile(tts, text, file)
        val pcm = runCatching { readPcmAt24k(file) }.getOrElse { error ->
            AndroidAgentLogger.warn("Eta system tts decode failed: ${error.javaClass.simpleName}")
            throw SpeechFailure(SpeechErrorCode.PROTOCOL, "系统语音引擎输出无法解析")
        }
        if (pcm.isEmpty()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "系统语音引擎没有产生音频")
        onAudio(pcm)
    } finally {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        file.delete()
    }
}

private suspend fun createEngine(context: Context, engineName: String?): TextToSpeech =
    suspendCancellableCoroutine { continuation ->
        var tts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (!continuation.isActive) return@OnInitListener
            if (status == TextToSpeech.SUCCESS) {
                continuation.resume(tts ?: return@OnInitListener)
            } else {
                runCatching { tts?.shutdown() }
                continuation.resumeWithException(
                    SpeechFailure(
                        SpeechErrorCode.CONFIGURATION,
                        if (engineName == null) "系统语音引擎初始化失败，请在系统设置中选择语音引擎"
                        else "所选系统语音引擎无法使用",
                    ),
                )
            }
        }
        tts = runCatching { TextToSpeech(context.applicationContext, listener, engineName) }.getOrElse { error ->
            continuation.resumeWithException(
                SpeechFailure(SpeechErrorCode.CONFIGURATION, "无法启动系统语音引擎"),
            )
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { runCatching { tts?.shutdown() } }
    }

private suspend fun synthesizeToFile(tts: TextToSpeech, text: String, file: File) =
    suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onError(utteranceId, -1)

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != id || !continuation.isActive) return
                continuation.resumeWithException(
                    SpeechFailure(SpeechErrorCode.SERVER, "系统语音合成失败（$errorCode）"),
                )
            }
        })
        val result = tts.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resumeWithException(
                SpeechFailure(SpeechErrorCode.CONFIGURATION, "系统语音引擎无法写入音频"),
            )
        }
        continuation.invokeOnCancellation { runCatching { tts.stop() } }
    }

private fun localeOf(language: String): Locale {
    if (language.isBlank()) return Locale.getDefault()
    val parts = language.trim().replace('_', '-').split('-')
    return when (parts.size) {
        1 -> Locale(parts[0])
        2 -> Locale(parts[0], parts[1])
        else -> Locale(parts[0], parts[1], parts[2])
    }
}

/**
 * 解析引擎写出的 WAV：只支持 16 位 PCM，立体声取平均转单声道，最后重采样到 24 kHz。
 * 引擎可能写出附加块（如 LIST），因此按块遍历而不是假定 44 字节头。
 */
private fun readPcmAt24k(file: File): ByteArray {
    val bytes = file.readBytes()
    if (bytes.size < 44) throw IllegalStateException("wav too short")
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    if (bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "RIFF" ||
        bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) != "WAVE"
    ) throw IllegalStateException("not a wav")

    var offset = 12
    var channels = 1
    var sampleRate = 0
    var bits = 0
    var dataStart = -1
    var dataSize = 0
    while (offset + 8 <= bytes.size) {
        val id = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
        val size = buffer.getInt(offset + 4)
        val body = offset + 8
        if (id == "fmt ") {
            channels = buffer.getShort(body + 2).toInt() and 0xffff
            sampleRate = buffer.getInt(body + 4)
            bits = buffer.getShort(body + 14).toInt() and 0xffff
        } else if (id == "data") {
            dataStart = body
            dataSize = size.coerceAtMost(bytes.size - body)
            break
        }
        offset = body + size + (size and 1)
    }
    if (dataStart < 0 || sampleRate <= 0 || bits != 16 || channels !in 1..2) {
        throw IllegalStateException("unsupported wav")
    }

    val frameBytes = channels * 2
    val frames = dataSize / frameBytes
    if (frames <= 0) return ByteArray(0)
    // 先转单声道浮点，再按比例线性插值到 24 kHz，避免立体声与采样率两处重复处理。
    val mono = FloatArray(frames)
    for (index in 0 until frames) {
        val base = dataStart + index * frameBytes
        var sum = 0f
        for (channel in 0 until channels) {
            sum += buffer.getShort(base + channel * 2).toFloat()
        }
        mono[index] = sum / channels
    }
    return resample(mono, sampleRate)
}

private fun resample(input: FloatArray, sourceRate: Int): ByteArray {
    if (sourceRate == OUTPUT_SAMPLE_RATE) {
        return ByteArray(input.size * 2).also { output ->
            val buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
            input.forEach { buffer.putShort(it.toInt().toShort()) }
        }
    }
    val ratio = sourceRate.toDouble() / OUTPUT_SAMPLE_RATE
    val count = (input.size / ratio).toInt().coerceAtLeast(1)
    val output = ByteArray(count * 2)
    val buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
    for (index in 0 until count) {
        val position = index * ratio
        val left = position.toInt().coerceIn(0, input.size - 1)
        val right = (left + 1).coerceAtMost(input.size - 1)
        val fraction = (position - left).toFloat()
        val sample = input[left] + (input[right] - input[left]) * fraction
        buffer.putShort(sample.toInt().coerceIn(-32768, 32767).toShort())
    }
    return output
}

private const val OUTPUT_SAMPLE_RATE = 24_000
private const val MIN_RATE = 0.5f
private const val MAX_RATE = 2f
private const val SYSTEM_TTS_TIMEOUT_MS = 90_000L
