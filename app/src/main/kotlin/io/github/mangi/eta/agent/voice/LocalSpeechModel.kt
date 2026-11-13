package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
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
 * 本地模型包管理器：APK 只带推理引擎，各模型包在用户选中对应服务时按需下载到应用私有目录。
 * 每个文件独立落盘、逐个校验 SHA-256，已完成的分片在重试时直接复用，弱网下不需要从头再来。
 * 各包状态独立保存，互不影响；下载挂在进程级作用域，离开设置页不会中断。
 */
internal object LocalSpeechModel {
    private val states = MutableStateFlow<Map<LocalSpeechPack, LocalSpeechModelState>>(emptyMap())
    val state: StateFlow<Map<LocalSpeechPack, LocalSpeechModelState>> = states.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<LocalSpeechPack, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun stateOf(pack: LocalSpeechPack): LocalSpeechModelState =
        states.value[pack] ?: LocalSpeechModelState()

    fun directory(context: Context, pack: LocalSpeechPack): File =
        File(context.applicationContext.filesDir, pack.directory)

    fun pathOf(context: Context, pack: LocalSpeechPack, name: String): String =
        File(directory(context, pack), name).absolutePath

    /** 只做存在性与大小检查，识别启动时会调用，必须廉价。 */
    fun installed(context: Context, pack: LocalSpeechPack): Boolean = pack.files.all { file ->
        File(directory(context, pack), file.local).let { it.isFile && it.length() == file.bytes }
    }

    /** 把磁盘状态同步进 [state]；下载中的包不覆盖进度。 */
    fun refresh(context: Context, vararg packs: LocalSpeechPack) {
        val updated = states.value.toMutableMap()
        var changed = false
        packs.forEach { pack ->
            if (updated[pack]?.busy == true) return@forEach
            val next = if (installed(context, pack)) {
                LocalSpeechModelState(LocalSpeechModelStatus.READY, progress = 100)
            } else {
                LocalSpeechModelState(LocalSpeechModelStatus.MISSING)
            }
            if (updated[pack] != next) {
                updated[pack] = next
                changed = true
            }
        }
        if (changed) states.value = updated
    }

    /** 在进程级作用域里启动下载；同一包重复点击不会起第二个任务。 */
    fun start(context: Context, pack: LocalSpeechPack, sourceUrl: String) {
        if (jobs[pack]?.isActive == true) return
        val appContext = context.applicationContext
        jobs[pack] = scope.launch { runCatching { download(appContext, pack, sourceUrl) } }
    }

    /** 取消进行中的下载并把状态复位，避免按钮永久停在「正在下载」。 */
    fun cancel(context: Context, pack: LocalSpeechPack) {
        jobs.remove(pack)?.cancel()
        if (!installed(context, pack)) setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.MISSING))
    }

    suspend fun delete(context: Context, pack: LocalSpeechPack) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { directory(context, pack).deleteRecursively() }
            setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.MISSING))
        }
    }

    suspend fun download(context: Context, pack: LocalSpeechPack, sourceUrl: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val base = sourceUrl.trim().trimEnd('/')
            if (base.isEmpty()) {
                setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.FAILED, error = "请先填写模型下载地址"))
                return@withLock
            }
            val target = directory(context, pack)
            runCatching { target.mkdirs() }
            var downloaded = 0L
            try {
                for (file in pack.files) {
                    val destination = File(target, file.local)
                    if (destination.isFile && destination.length() == file.bytes) {
                        downloaded += file.bytes
                        continue
                    }
                    val part = File(target, "${file.local}.part")
                    setState(
                        pack,
                        LocalSpeechModelState(
                            LocalSpeechModelStatus.DOWNLOADING,
                            progress = percent(pack, downloaded),
                            current = file.local,
                        ),
                    )
                    fetch("$base/${file.remote}", part) {
                        setState(
                            pack,
                            LocalSpeechModelState(
                                LocalSpeechModelStatus.DOWNLOADING,
                                progress = percent(pack, downloaded + part.length()),
                                current = file.local,
                            ),
                        )
                    }
                    if (part.length() != file.bytes || !sha256(part).equals(file.sha256, true)) {
                        part.delete()
                        throw SpeechFailure(SpeechErrorCode.PROTOCOL, "模型校验失败，请重试")
                    }
                    if (destination.exists()) destination.delete()
                    if (!part.renameTo(destination)) {
                        throw SpeechFailure(SpeechErrorCode.STORAGE, "无法保存模型，请检查存储空间")
                    }
                    downloaded += file.bytes
                }
                setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.READY, progress = 100))
            } catch (error: Exception) {
                if (error is CancellationException) {
                    // 取消不是失败：回到可重试状态，已完成的文件保留。
                    setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.MISSING))
                    throw error
                }
                AndroidAgentLogger.warn("Eta local model download failed: ${error.javaClass.simpleName}")
                val failure = error.speechFailure()
                setState(pack, LocalSpeechModelState(LocalSpeechModelStatus.FAILED, error = failure.userMessage))
                throw failure
            }
        }
    }

    private fun setState(pack: LocalSpeechPack, value: LocalSpeechModelState) {
        states.value = states.value.toMutableMap().apply { put(pack, value) }
    }

    private fun percent(pack: LocalSpeechPack, downloaded: Long): Int =
        (downloaded * 100 / pack.totalBytes).toInt().coerceIn(0, 100)

    private fun fetch(url: String, destination: File, onProgress: () -> Unit) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) {
                throw SpeechFailure(SpeechErrorCode.NETWORK, "无法下载模型（HTTP ${it.code}）")
            }
            val body = it.body
            val expected = body.contentLength()
            if (expected > 0) {
                val free = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < expected) throw SpeechFailure(SpeechErrorCode.STORAGE, "存储空间不足，无法下载模型")
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