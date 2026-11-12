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
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
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
)

/** 从消息列表提取文件，按首次出现顺序去重。 */
internal fun collectConversationFiles(messages: List<AgentChatMessageUi>): List<ConversationFileEntry> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<ConversationFileEntry>()
    messages.filterIsInstance<UserMessageUi>().forEach { message ->
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
                FilesContent(files = files, onOpen = onOpen)
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = true,
                title = stringResource(R.string.conversation_files_title),
                onDismissRequest = onDismissRequest,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                FilesContent(files = files, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun FilesContent(
    files: List<ConversationFileEntry>,
    onOpen: (AgentFileReference) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (files.isEmpty()) {
            Text(
                text = stringResource(R.string.conversation_files_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(files, key = { it.reference.absolutePath.ifBlank { it.reference.displayName } }) { entry ->
                FileRow(entry = entry, onClick = { onOpen(entry.reference) })
            }
        }
    }
}

@Composable
private fun FileRow(entry: ConversationFileEntry, onClick: () -> Unit) {
    val reference = entry.reference
    val uiStyle = LocalUiStyle.current
    val meta = remember(reference) { fileMetaLabel(reference) }
    when (uiStyle) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
            CouiPreference(
                title = reference.displayName,
                summary = meta,
                showChevron = false,
                onClick = onClick,
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
                onClick = onClick,
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
/**
 * 用系统应用打开会话文件：按扩展名推断 MIME，交给外部查看器。
 * 文件不存在或没有可用查看器时静默返回，不弹错误打断阅读。
 */
internal fun openConversationFile(context: android.content.Context, reference: AgentFileReference) {
    val path = reference.absolutePath
    if (path.isBlank()) return
    val file = java.io.File(path)
    if (!file.isFile) return
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
