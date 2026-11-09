package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal enum class LocalSpeechModelStatus { MISSING, DOWNLOADING, READY, FAILED }

internal data class LocalSpeechModelState(
    val status: LocalSpeechModelStatus = LocalSpeechModelStatus.MISSING,
    /** 0～100，仅下载中有效。 */
    val progress: Int = 0,
    /** 正在下载的文件名。 */
    val current: String = "",
    val error: String? = null,
) {
    val busy: Boolean get() = status == LocalSpeechModelStatus.DOWNLOADING
    val ready: Boolean get() = status == LocalSpeechModelStatus.READY
}

/**
 * 内置离线识别模型按需下载：APK 只带推理引擎，模型在用户首次启用时取回并放入应用私有目录。
 * 每个文件独立落盘、逐个校验 SHA-256，已完成的分片在重试时直接复用，弱网下不需要从头再来。
 */
internal object LocalSpeechModel {
    const val DIRECTORY = "sherpa-zh-14m"
    const val ENCODER_FILE = "encoder.int8.onnx"
    const val DECODER_FILE = "decoder.int8.onnx"
    const val JOINER_FILE = "joiner.int8.onnx"
    const val TOKENS_FILE = "tokens.txt"

    /** [remote] 是上游仓库中的文件名，[local] 是落盘后给识别器用的短名。 */
    internal data class ModelFile(
        val remote: String,
        val local: String,
        val bytes: Long,
        val sha256: String,
    )

    /**
     * 与 sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23 的 int8 导出一致；
     * 摘要即上游 LFS 对象的 SHA-256，升级模型必须同步更新。
     */
    val FILES = listOf(
        ModelFile(
            "encoder-epoch-99-avg-1.int8.onnx", ENCODER_FILE, 21_621_684L,
            "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1",
        ),
        ModelFile(
            "decoder-epoch-99-avg-1.int8.onnx", DECODER_FILE, 1_888_682L,
            "22f123bb8cba9b38974b3df18a3f45e7081f4985ebb2e075d9f21f618c468bbf",
        ),
        ModelFile(
            "joiner-epoch-99-avg-1.int8.onnx", JOINER_FILE, 1_795_562L,
            "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169",
        ),
        ModelFile(
            "tokens.txt", TOKENS_FILE, 48_697L,
            "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac",
        ),
    )
    val TOTAL_BYTES = FILES.sumOf { it.bytes }

    private val mutableState = MutableStateFlow(LocalSpeechModelState())
    val state: StateFlow<LocalSpeechModelState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    /** 下载挂在进程级作用域：设置页退出或重组都不会中断，状态与进度仍然可见。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun directory(context: Context): File = File(context.applicationContext.filesDir, DIRECTORY)

    fun pathOf(context: Context, name: String): String = File(directory(context), name).absolutePath

    /** 只做存在性与大小检查，识别启动时会调用，必须廉价。 */
    fun installed(context: Context): Boolean = FILES.all { file ->
        File(directory(context), file.local).let { it.isFile && it.length() == file.bytes }
    }

    /** 把磁盘状态同步进 [state]；下载中不覆盖进度。 */
    fun refresh(context: Context) {
        if (mutableState.value.busy) return
        mutableState.value = if (installed(context)) {
            LocalSpeechModelState(LocalSpeechModelStatus.READY, progress = 100)
        } else {
            LocalSpeechModelState(LocalSpeechModelStatus.MISSING)
        }
    }

    /** 在进程级作用域里启动下载；重复点击不会起第二个任务。 */
    fun start(context: Context, sourceUrl: String) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch { runCatching { download(appContext, sourceUrl) } }
    }

    /** 取消进行中的下载并把状态复位，避免按钮永久停在「正在下载」。 */
    fun cancel(context: Context) {
        job?.cancel()
        job = null
        if (!installed(context)) mutableState.value = LocalSpeechModelState(LocalSpeechModelStatus.MISSING)
    }

    suspend fun download(context: Context, sourceUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val base = sourceUrl.trim().trimEnd('/')
            if (base.isEmpty()) {
                mutableState.value = LocalSpeechModelState(
                    LocalSpeechModelStatus.FAILED, error = "请先填写模型下载地址",
                )
                return@withLock
            }
            val target = directory(context)
            runCatching { target.mkdirs() }
            var downloaded = 0L
            try {
                for (file in FILES) {
                    val destination = File(target, file.local)
                    if (destination.isFile && destination.length() == file.bytes) {
                        downloaded += file.bytes
                        continue
                    }
                    val part = File(target, "${file.local}.part")
                    mutableState.value = LocalSpeechModelState(
                        LocalSpeechModelStatus.DOWNLOADING,
                        progress = percent(downloaded),
                        current = file.local,
                    )
                    fetch("$base/${file.remote}", part) {
                        mutableState.value = LocalSpeechModelState(
                            LocalSpeechModelStatus.DOWNLOADING,
                            progress = percent(downloaded + part.length()),
                            current = file.local,
                        )
                    }
                    if (part.length() != file.bytes || !sha256(part).equals(file.sha256, true)) {
                        part.delete()
                        throw SpeechFailure(SpeechErrorCode.PROTOCOL, "离线模型校验失败，请重试")
                    }
                    if (destination.exists()) destination.delete()
                    if (!part.renameTo(destination)) {
                        throw SpeechFailure(SpeechErrorCode.STORAGE, "无法保存离线模型，请检查存储空间")
                    }
                }
                mutableState.value = LocalSpeechModelState(LocalSpeechModelStatus.READY, progress = 100)
            } catch (error: Exception) {
                if (error is CancellationException) {
                    // 取消不是失败：清掉半成品并回到可重试状态。
                    mutableState.value = LocalSpeechModelState(LocalSpeechModelStatus.MISSING)
                    throw error
                }
                AndroidAgentLogger.warn("Eta local speech download failed: ${error.javaClass.simpleName}")
                val failure = error.speechFailure()
                mutableState.value = LocalSpeechModelState(
                    LocalSpeechModelStatus.FAILED, error = failure.userMessage,
                )
                throw failure
            }
        }
    }

    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { directory(context).deleteRecursively() }
            mutableState.value = LocalSpeechModelState(LocalSpeechModelStatus.MISSING)
        }
    }

    private fun percent(downloaded: Long): Int =
        (downloaded * 100 / TOTAL_BYTES).toInt().coerceIn(0, 100)

    private fun fetch(url: String, destination: File, onProgress: () -> Unit) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                throw SpeechFailure(SpeechErrorCode.NETWORK, "无法下载离线模型（HTTP ${it.code}）")
            }
            val body = it.body
            val expected = body.contentLength()
            if (expected > 0) {
                val free = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < expected) throw SpeechFailure(SpeechErrorCode.STORAGE, "存储空间不足，无法下载离线模型")
            }
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        onProgress()
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
