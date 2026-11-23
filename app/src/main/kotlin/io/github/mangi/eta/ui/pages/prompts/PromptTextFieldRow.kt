package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import io.github.mangi.eta.ui.components.coui.CouiTextField

/**
 * COUI 提示词输入行。
 *
 * 标签固定在输入框内部左上角，不随内容变化移动位置：
 * 有没有内容都停在同一个地方，正文永远从标签下方开始。
 * 这样既知道这个框是什么，也不会出现标签跳动或压住正文。
 */
@Composable
internal fun PromptTextFieldRow(
    title: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
) {
    val colors = CouiPageDefaults.themeColors()
    val clipboard = LocalClipboardManager.current
    var fullscreen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val minLines = 1
    val maxLines = if (singleLine) 1 else 6
    // 标签占的高度：12sp 行高约 17dp + 顶部 9dp 内边距，正文从 28dp 起。
    val labelSpace = 28.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiPageDefaults.rowPadding)
                .padding(top = 10.dp),
        ) {
            CouiTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                // 标签固定在外面画，输入框自己不需要占位符。
                placeholder = null,
                showClearButton = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = labelSpace),
            )
            // 固定标签：位置与字号都不随输入变化。
            androidx.compose.foundation.text.BasicText(
                text = title,
                style = TextStyle(
                    color = if (enabled) colors.summary else colors.disabled,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 9.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${value.length} 字 · 约 ${estimateTokens(value)} token",
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            if (!singleLine) {
                Text(
                    text = "全屏编辑",
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.accent else colors.disabled,
                    modifier = Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                        ) { fullscreen = true }
                        .padding(horizontal = 6.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = "粘贴",
                kind = CouiTextKind.ItemSummary,
                color = if (enabled) colors.accent else colors.disabled,
                modifier = Modifier
                    .clickable(enabled = enabled) {
                        val text = clipboard.getText()?.text.orEmpty()
                        if (text.isNotEmpty()) onValueChange(value + text)
                    }
                    .padding(horizontal = 6.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "清空",
                kind = CouiTextKind.ItemSummary,
                color = if (enabled && value.isNotEmpty()) colors.accent else colors.disabled,
                modifier = Modifier
                    .clickable(enabled = enabled && value.isNotEmpty()) {
                        onValueChange("")
                    }
                    .padding(horizontal = 6.dp),
            )
        }
    }

    if (fullscreen) {
        PromptFullscreenEditor(
            title = title,
            value = value,
            onValueChange = onValueChange,
            onDismiss = { fullscreen = false },
        )
    }
}
