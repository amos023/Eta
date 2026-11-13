package io.github.mangi.eta.agent.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把生成的图片导出到系统相册。
 *
 * 走 MediaStore 的 Images 集合，落在 `Pictures/Eta`。Android 10 之后向自己的
 * MediaStore 记录写入不需要任何存储权限，所以在 minSdk 33 上直接插即可。
 */
internal object GeneratedImageExporter {

    private const val ALBUM = "Eta"

    sealed interface Result {
        data class Saved(val uri: Uri) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun saveToGallery(context: Context, path: String): Result = withContext(Dispatchers.IO) {
        val file = resolveFile(path) ?: return@withContext Result.Failed("图片文件不存在")
        if (!file.isFile || file.length() <= 0) return@withContext Result.Failed("图片文件为空")
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext Result.Failed("系统相册拒绝写入")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法打开相册输出流")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.Saved(uri)
        } catch (failure: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            Result.Failed(failure.message ?: "写入相册失败")
        }
    }

    /** 会话记录里存的是 `file://` 绝对路径；也接受裸路径。 */
    private fun resolveFile(path: String): File? {
        val raw = path.removePrefix("file://")
        if (raw.isBlank()) return null
        return runCatching { File(raw) }.getOrNull()
    }
}
