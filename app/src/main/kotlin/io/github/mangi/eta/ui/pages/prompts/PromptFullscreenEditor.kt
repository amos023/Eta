package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/**
 * 全屏提示词编辑器。
 *
 * 长文本在行内输入框里没法改：一行太窄、键盘挡住半个屏幕、滚动还和页面打架。
 * 这里给一个占满全屏的编辑区，顶部是标题与操作，中间是等宽正文，底部有字数和 token。
 */
@Composable
internal fun PromptFullscreenEditor(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val clipboard = LocalClipboardManager.current
    var state by remember { mutableStateOf(TextFieldValue(value, selection = value.length.let {
        androidx.compose.ui.text.TextRange(it)
    })) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 必须给不透明底色：Dialog 默认背景透明，
                // 不加的话底层页面文字会透上来，和编辑区叠在一起看不清。
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = CouiPageDefaults.rowPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    kind = CouiTextKind.ItemTitle,
                    color = colors.summary,
                    modifier = Modifier
                        .clickableNoRipple { onDismiss() }
                        .padding(end = 16.dp),
                )
                Text(
                    text = title,
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "完成",
                    kind = CouiTextKind.ItemTitle,
                    color = colors.accent,
                    modifier = Modifier
                        .clickableNoRipple {
                            onValueChange(state.text)
                            onDismiss()
                        }
                        .padding(start = 16.dp),
                )
            }

            BasicTextField(
                value = state,
                onValueChange = { state = it },
                textStyle = TextStyle(
                    color = colors.title,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (state.text.isEmpty()) {
                        Text(
                            text = "在这里输入…",
                            kind = CouiTextKind.ItemTitle,
                            color = colors.summary,
                        )
                    }
                    inner()
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.text.length} 字 · 约 ${estimateTokens(state.text)} token",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "粘贴",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.accent,
                    modifier = Modifier
                        .clickableNoRipple {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotEmpty()) {
                                val merged = state.text + text
                                state = TextFieldValue(
                                    merged,
                                    selection = androidx.compose.ui.text.TextRange(merged.length),
                                )
                            }
                        }
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "清空",
                    kind = CouiTextKind.ItemSummary,
                    color = if (state.text.isEmpty()) colors.disabled else colors.accent,
                    modifier = Modifier
                        .clickableNoRipple {
                            if (state.text.isNotEmpty()) {
                                state = TextFieldValue("")
                            }
                        }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** 估算 token：中文按字计，其余按 4 字符 1 token。 */
internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    text.forEach { ch ->
        if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF) cjk++ else other++
    }
    return cjk + other / 4
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
)
