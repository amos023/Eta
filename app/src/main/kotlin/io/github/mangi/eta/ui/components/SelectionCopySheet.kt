package io.github.mangi.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 选择复制弹层的入参；[content] 是原始 Markdown，分段后每段可单独拖选与复制。 */
internal data class SelectionCopyTarget(val messageId: String, val content: String)

/**
 * 把正文按空行切成可独立选择的段落。
 * Markdown 标题、列表、代码块在视觉上本就是块级单位，按空行切分与阅读结构一致；
 * 单个超长段落再按句末标点细分，避免一段占满整屏、拖选时找不到边界。
 */
internal fun selectionParagraphs(content: String, limit: Int = SELECTION_SEGMENT_LIMIT): List<String> {
    val blocks = content.replace("\r\n", "\n").split(Regex("\n[ \t]*\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return blocks.flatMap { block ->
        if (block.length <= limit) listOf(block) else splitBySentence(block, limit)
    }
}

private fun splitBySentence(block: String, limit: Int): List<String> {
    val result = mutableListOf<String>()
    var remaining = block
    while (remaining.length > limit) {
        val window = remaining.take(limit)
        // 优先在句末标点处断开，其次换行，最后硬切。
        val boundary = window.indexOfLast { it in "。！？.!?；;\n" }
        val end = if (boundary > limit / 3) boundary + 1 else limit
        result += remaining.substring(0, end).trimEnd()
        remaining = remaining.substring(end).trimStart()
    }
    if (remaining.isNotEmpty()) result += remaining
    return result
}

private const val SELECTION_SEGMENT_LIMIT = 400

@Composable
internal fun SelectionCopySheet(
    target: SelectionCopyTarget?,
    onDismissRequest: () -> Unit,
) {
    // 关闭动画期间仍需要正文，否则弹层内容会瞬间清空。
    val shown = remember(target) { target }
    if (target == null && shown == null) return
    val content = (target ?: shown)?.content.orEmpty()
    val dark = CouiTheme.isDark
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            Dialog(
                visible = target != null,
                onDismissRequest = onDismissRequest,
                dark = dark,
                title = stringResource(R.string.selection_copy_title),
                confirmAction = CouiDialogAction(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissRequest,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
            ) {
                SelectionCopyContent(content = content, onDismissRequest = onDismissRequest)
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = target != null,
                title = stringResource(R.string.selection_copy_title),
                onDismissRequest = onDismissRequest,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                SelectionCopyContent(content = content, onDismissRequest = onDismissRequest)
            }
        }
    }
}

@Composable
private fun SelectionCopyContent(content: String, onDismissRequest: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val paragraphs = remember(content) { selectionParagraphs(content) }
    var copied by remember { mutableStateOf(false) }

    fun copy(text: String) {
        @Suppress("DEPRECATION")
        clipboardManager.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        copied = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { copy(content) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.selection_copy_all),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(paragraphs) { index, paragraph ->
                SelectionParagraphRow(
                    index = index + 1,
                    text = paragraph,
                    onCopy = { copy(paragraph) },
                )
            }
        }
        Text(
            text = stringResource(
                if (copied) R.string.copy_copied else R.string.selection_copy_hint,
            ),
            style = MiuixTheme.textStyles.footnote2,
            color = if (copied) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
    }
}

/** 单段：文本可拖选，右侧复制整段。 */
@Composable
private fun SelectionParagraphRow(index: Int, text: String, onCopy: () -> Unit) {
    val uiStyle = LocalUiStyle.current
    val indexColor = if (uiStyle == AppearanceUiStyle.COUI) {
        CouiPageDefaults.themeColors(CouiTheme.isDark).summary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = index.toString(),
                style = MiuixTheme.textStyles.footnote2,
                color = indexColor,
            )
            SelectionContainer {
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.padding(start = 8.dp)) {
            when (uiStyle) {
                AppearanceUiStyle.COUI -> CouiIcon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.selection_copy_segment),
                    modifier = Modifier.size(18.dp),
                )
                AppearanceUiStyle.MIUIX -> Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.selection_copy_segment),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}