package io.github.mangi.eta.ui.components

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.media.GeneratedImageExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏预览里的图片操作。
 *
 * 聊天里的图片引用有两种形态：生图产物是 `file://` 本地路径，用户上传的是 data URL。
 * 保存与分享都必须落到真实文件上，所以先统一 materialize 成私有目录里的文件，
 * 再走相册导出或 FileProvider 分享。
 */
internal suspend fun materializeChatImage(context: Context, source: String): File? =
    withContext(Dispatchers.IO) {
        val raw = source.trim()
        when {
            raw.startsWith("data:", ignoreCase = true) -> {
                val header = raw.substringAfter("data:").substringBefore(',')
                val mime = header.substringBefore(';').ifBlank { "image/png" }
                val base64 = raw.substringAfter("base64,", "")
                if (base64.isBlank()) return@withContext null
                val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                    ?: return@withContext null
                val ext = when {
                    mime.contains("jpeg", true) || mime.contains("jpg", true) -> "jpg"
                    mime.contains("webp", true) -> "webp"
                    else -> "png"
                }
                val dir = File(context.filesDir, "generated_images")
                if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
                val file = File(dir, "viewer-${System.currentTimeMillis()}.$ext")
                runCatching { file.writeBytes(bytes) }.getOrNull() ?: return@withContext null
                file
            }
            raw.startsWith("file://", ignoreCase = true) ->
                runCatching { File(java.net.URI(raw)) }.getOrNull()?.takeIf { it.isFile }
            raw.startsWith("/") -> File(raw).takeIf { it.isFile }
            else -> null
        }
    }

/** 保存预览里的当前这一张；返回要提示的文案。 */
internal suspend fun savePreviewImage(context: Context, source: String): String {
    val file = materializeChatImage(context, source)
        ?: return context.getString(R.string.image_share_no_file)
    return when (val result = GeneratedImageExporter.saveToGallery(context, file.absolutePath)) {
        is GeneratedImageExporter.Result.Saved -> context.getString(R.string.image_saved_to_gallery)
        is GeneratedImageExporter.Result.Failed -> result.reason
    }
}

/** 分享预览里的当前这一张；返回错误文案，null 表示已发起。 */
internal suspend fun sharePreviewImage(context: Context, source: String): String? {
    val file = materializeChatImage(context, source)
        ?: return context.getString(R.string.image_share_no_file)
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return context.getString(R.string.image_share_no_file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(intent, null)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return runCatching { context.startActivity(chooser) }
        .exceptionOrNull()
        ?.let { context.getString(R.string.image_share_no_file) }
}

/** 应用内轻提示，避免调用方重复写 Toast。 */
internal fun toastBriefly(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
