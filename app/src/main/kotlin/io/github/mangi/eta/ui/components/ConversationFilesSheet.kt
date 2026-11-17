package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentFileReference
import io.github.mangi.eta.agent.model.AgentFileReferenceKind
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.LocalChatImagePreviewer
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「此聊天中的文件」：把当前会话里所有用户消息携带的文件引用汇总成一份清单。
 *
 * 数据来源就是消息正文里的文件引用块（AgentFileReferencePromptCodec），
 * 不额外落库，因此重装、导入备份后依然能还原。
 */
internal data class ConversationFileEntry(
    val reference: AgentFileReference,
    /** 出现在第几条消息里，用于去重排序后保持稳定。 */
    val messageId: String,
    /**
     * 图片消息携带的 data URL（`data:image/...;base64,...`）。
     *
     * 图片附件不进正文的文件引用块，而是存在 UserMessageUi.images 里，
     * 因此判断"是不是图片"必须先看这个字段，不能只看路径扩展名。
     */
    val imageDataUrl: String = "",
) {
    /** 图片条目的稳定 key：data URL 太长，取前缀加长度做指纹。 */
    val stableKey: String
        get() = if (imageDataUrl.isNotBlank()) {
            "img:${imageDataUrl.length}:${imageDataUrl.hashCode()}"
        } else {
            reference.absolutePath.ifBlank { reference.displayName }
        }
}

/** 从消息列表提取文件，按首次出现顺序去重。 */
internal fun collectConversationFiles(messages: List<AgentChatMessageUi>): List<ConversationFileEntry> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<ConversationFileEntry>()
    // 图片编号跨消息连续：不同的消息里各自从 1 开始会重名，这里统一计数。
    var imageOrdinal = 0
    messages.filterIsInstance<UserMessageUi>().forEach { message ->
        // 图片附件：data URL 放在 images 字段里，不走正文的引用块。
        message.images.forEach { dataUrl ->
            if (dataUrl.isBlank()) return@forEach
            val fingerprint = "img:${dataUrl.length}:${dataUrl.hashCode()}"
            if (seen.add(fingerprint)) {
                imageOrdinal += 1
                result += ConversationFileEntry(
                    reference = AgentFileReference(
                        displayName = indexLabel(imageOrdinal, dataUrl),
                        absolutePath = "",
                        kind = AgentFileReferenceKind.File,
                    ),
                    messageId = message.id,
                    imageDataUrl = dataUrl,
                )
            }
        }
        val parsed = AgentFileReferencePromptCodec.parse(message.content)
        parsed.references.forEach { reference ->
            val key = reference.absolutePath.ifBlank { reference.displayName }
            if (seen.add(key)) {
                result += ConversationFileEntry(reference = reference, messageId = message.id)
            }
        }
    }
    return result
}

/** 从 data URL 里取 MIME 后缀做展示名；ordinal 是从 1 开始的全局序号。 */
private fun indexLabel(ordinal: Int, dataUrl: String): String {
    val mime = dataUrl.substringAfter("data:", "").substringBefore(';', "")
    val suffix = when {
        mime.contains("png", true) -> "png"
        mime.contains("gif", true) -> "gif"
        mime.contains("webp", true) -> "webp"
        mime.contains("bmp", true) -> "bmp"
        mime.contains("heic", true) || mime.contains("heif", true) -> "heic"
        mime.isBlank() -> "img"
        else -> "jpg"
    }
    return "图片 $ordinal.$suffix"
}

@Composable
internal fun ConversationFilesSheet(
    files: List<ConversationFileEntry>,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onOpen: (AgentFileReference) -> Unit,
) {
    if (!visible) return
    val dark = CouiTheme.isDark
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            Dialog(
                visible = true,
                onDismissRequest = onDismissRequest,
                dark = dark,
                title = stringResource(R.string.conversation_files_title),
                confirmAction = CouiDialogAction(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissRequest,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
            ) {
                FilesContent(
                    files = files,
                    onOpen = onOpen,
                    onLaunch = onDismissRequest,
                )
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = true,
                title = stringResource(R.string.conversation_files_title),
                onDismissRequest = onDismissRequest,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                FilesContent(
                    files = files,
                    onOpen = onOpen,
                    onLaunch = onDismissRequest,
                )
            }
        }
    }
}

private enum class ConversationFileCategory {
    All,
    Images,
    Files,
}

private fun AgentFileReference.isImageExtension(): Boolean {
    val extension = absolutePath.substringAfterLast('.', "").lowercase()
    return extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
}

/** 图片判定：data URL 附件必定是图片；普通文件回退到扩展名。 */
private fun ConversationFileEntry.isImageEntry(): Boolean =
    imageDataUrl.isNotBlank() || reference.isImageExtension()

@Composable
private fun FilesContent(
    files: List<ConversationFileEntry>,
    onOpen: (AgentFileReference) -> Unit,
    onLaunch: () -> Unit,
) {
    var category by remember { mutableStateOf(ConversationFileCategory.All) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewer = LocalChatImagePreviewer.current
    // 图片走内置全屏查看器（与消息气泡、工具图片同一套）；文件仍交给系统应用。
    // 两种情况都要先关掉弹层，否则全屏查看器会被压在弹层下面，看起来像没反应。
    val onOpenEntry: (ConversationFileEntry) -> Unit = { entry ->
        if (entry.imageDataUrl.isNotBlank()) {
            val dataUrls = files.mapNotNull { it.imageDataUrl.takeIf(String::isNotBlank) }
            val index = dataUrls.indexOf(entry.imageDataUrl).coerceAtLeast(0)
            if (previewer != null && dataUrls.isNotEmpty()) {
                onLaunch()
                previewer.open(
                    images = dataUrls,
                    initialIndex = index,
                    groupKey = CONVERSATION_FILES_IMAGE_GROUP,
                )
            } else {
                onLaunch()
                // 壳层没提供查看器时退化成系统打开，保证功能不丢。
                openConversationImage(context, entry.imageDataUrl)
            }
        } else {
            onOpen(entry.reference)
        }
    }
    val images = remember(files) { files.filter { it.isImageEntry() } }
    val regularFiles = remember(files) { files.filterNot { it.isImageEntry() } }
    val filtered = when (category) {
        ConversationFileCategory.All -> files
        ConversationFileCategory.Images -> images
        ConversationFileCategory.Files -> regularFiles
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CategoryTabs(
            selected = category,
            imageCount = images.size,
            fileCount = regularFiles.size,
            onSelect = { category = it },
        )
        if (filtered.isEmpty()) {
            Text(
                text = when (category) {
                    ConversationFileCategory.All -> stringResource(R.string.conversation_files_empty)
                    ConversationFileCategory.Images -> stringResource(R.string.conversation_images_empty)
                    ConversationFileCategory.Files -> stringResource(R.string.conversation_regular_files_empty)
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            )
            return@Column
        }
        if (category == ConversationFileCategory.Images) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(filtered, key = { it.stableKey }) { entry ->
                    ImageFileTile(entry = entry, onClick = onOpenEntry)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(filtered, key = { it.stableKey }) { entry ->
                    FileRow(entry = entry, onClick = onOpenEntry)
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    selected: ConversationFileCategory,
    imageCount: Int,
    fileCount: Int,
    onSelect: (ConversationFileCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        CategoryTab(stringResource(R.string.conversation_files_tab_all, imageCount + fileCount), selected == ConversationFileCategory.All) {
            onSelect(ConversationFileCategory.All)
        }
        CategoryTab(stringResource(R.string.conversation_files_tab_images, imageCount), selected == ConversationFileCategory.Images) {
            onSelect(ConversationFileCategory.Images)
        }
        CategoryTab(stringResource(R.string.conversation_files_tab_files, fileCount), selected == ConversationFileCategory.Files) {
            onSelect(ConversationFileCategory.Files)
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Text(
        text = label,
        color = color,
        style = MiuixTheme.textStyles.body2,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 缩略图最大边长，避免把原图整张读进内存。 */
private const val THUMBNAIL_MAX_PX = 320

/** 会话文件面板图片预览的分组 key，供查看器做原位展开动画。 */
private const val CONVERSATION_FILES_IMAGE_GROUP = "conversation-files-images"

/**
 * 解码一张图片用于缩略图。
 *
 * 图片来源有两种：聊天里粘贴/发送的图片存成 data URL，普通文件是磁盘路径。
 * 两条都要能解出来，否则图片页永远只有占位图标。
 */
private fun ConversationFileEntry.decodeBitmap(maxPx: Int): android.graphics.Bitmap? {
    val bytes = when {
        imageDataUrl.isNotBlank() -> {
            val base64 = imageDataUrl.substringAfter(',', "")
            if (base64.isBlank()) null else runCatching {
                android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            }.getOrNull()
        }
        reference.absolutePath.isNotBlank() -> runCatching {
            java.io.File(reference.absolutePath).readBytes()
        }.getOrNull()
        else -> null
    } ?: return null
    if (bytes.isEmpty()) return null
    // 两遍解码：先读尺寸算采样率，再按采样率真正解码，控制内存占用。
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) {
        sample *= 2
    }
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}

@Composable
private fun ImageFileTile(entry: ConversationFileEntry, onClick: (ConversationFileEntry) -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, entry.stableKey) {
        value = withContext(Dispatchers.IO) { entry.decodeBitmap(THUMBNAIL_MAX_PX) }
    }
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable { onClick(entry) },
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = entry.reference.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Photo, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
            }
        }
        Text(
            text = entry.reference.displayName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FileRow(entry: ConversationFileEntry, onClick: (ConversationFileEntry) -> Unit) {
    val reference = entry.reference
    val uiStyle = LocalUiStyle.current
    val meta = remember(reference, entry.imageDataUrl) {
        if (entry.imageDataUrl.isNotBlank()) {
            imageMetaLabel(entry.imageDataUrl)
        } else {
            fileMetaLabel(reference)
        }
    }
    when (uiStyle) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
            CouiPreference(
                title = reference.displayName,
                summary = meta,
                showChevron = false,
                onClick = { onClick(entry) },
                icon = {
                    // 本地文件用统一的圆角图标底，与系统「文件」列表观感接近。
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiIcon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
        AppearanceUiStyle.MIUIX -> {
            BasicComponent(
                title = reference.displayName,
                summary = meta,
                onClick = { onClick(entry) },
                startAction = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

/** 文件的一行副标题：优先显示绝对路径，缺失时退化为类型。 */
private fun fileMetaLabel(reference: AgentFileReference): String =
    reference.absolutePath.ifBlank { reference.kind.name }

/** 图片的一行副标题：显示格式与体积，data URL 没有路径可显示。 */
private fun imageMetaLabel(dataUrl: String): String {
    val mime = dataUrl.substringAfter("data:", "").substringBefore(';', "")
    val base64 = dataUrl.substringAfter(',', "")
    val approxBytes = (base64.length * 3L / 4L)
    val size = when {
        approxBytes >= 1024L * 1024L -> "%d.%d MB".format(
            approxBytes / (1024L * 1024L),
            (approxBytes % (1024L * 1024L)) * 10L / (1024L * 1024L),
        )
        approxBytes >= 1024L -> "%d KB".format(approxBytes / 1024L)
        else -> "$approxBytes B"
    }
    return if (mime.isBlank()) size else "$mime · $size"
}

/**
 * 点开图片：把 data URL 落成缓存文件再交给系统查看器。
 * 图片没有磁盘路径，不能走 openConversationFile 那条路。
 */
internal fun openConversationImage(context: android.content.Context, dataUrl: String) {
    val base64 = dataUrl.substringAfter(',', "")
    if (base64.isBlank()) return
    val bytes = runCatching {
        android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    }.getOrNull() ?: return
    if (bytes.isEmpty()) return
    val mime = dataUrl.substringAfter("data:", "").substringBefore(';', "")
        .ifBlank { "image/jpeg" }
    val extension = when {
        mime.contains("png", true) -> "png"
        mime.contains("gif", true) -> "gif"
        mime.contains("webp", true) -> "webp"
        else -> "jpg"
    }
    val cacheDir = java.io.File(context.cacheDir, "conversation-images")
    if (!cacheDir.isDirectory) cacheDir.mkdirs()
    val file = java.io.File(cacheDir, "${dataUrl.hashCode()}.$extension")
    runCatching {
        if (!file.isFile || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
    }.onFailure { return }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
/**
 * 用系统应用打开会话文件：按扩展名推断 MIME，交给外部查看器。
 * 文件不存在或没有可用查看器时静默返回，不弹错误打断阅读。
 */
internal fun openConversationFile(context: android.content.Context, reference: AgentFileReference) {
    val path = reference.absolutePath
    if (path.isBlank()) return
    val file = java.io.File(path)
    if (!file.isFile) {
        // 文件在会话之后被移动或删除：明确告知，不要静默无反应。
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.conversation_file_missing, reference.displayName),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mime = context.contentResolver.getType(uri)
        ?: android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
        ?: "*/*"
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
