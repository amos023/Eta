package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「消息定位」的打开请求。
 * 菜单在顶栏（AgentAppShell），而列表滚动状态在聊天正文（AgentChatBody）内部，
 * 两者不在同一组合作用域；用一次性事件把请求转给真正持有列表的一方。
 */
internal object AgentMessageNavigation {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openRequests = requests.asSharedFlow()

    private val fileRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fileOpenRequests = fileRequests.asSharedFlow()

    fun requestOpen() {
        requests.tryEmit(Unit)
    }

    /** 请求打开「此聊天中的文件」。 */
    fun requestOpenFiles() {
        fileRequests.tryEmit(Unit)
    }
}

/**
 * 列表高度上限。
 *
 * COUI 弹窗自身封顶 632dp，内容超出后会把整块内容套进 verticalScroll——
 * 那样往上滑虽然露出底部提示，标题也会一起滚走。所以列表必须卡在
 * 「标题 + 跳到最新 + 列表 + 提示」整体装得下的范围内。
 * 632 - 标题块 - 按钮区 - 边距 ≈ 468dp，减去固定的两行约 88dp，列表取 380dp。
 */
private val LIST_MAX_HEIGHT = 380.dp

/** 定位弹层的一条候选：用户提问所在轮次。 */
internal data class MessageNavItem(
    val messageId: String,
    /** 提示词正文，已剥去文件引用等附加内容。 */
    val prompt: String,
)

@Composable
internal fun MessageNavigatorSheet(
    items: List<MessageNavItem>,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onJump: (MessageNavItem) -> Unit,
    onJumpToLatest: () -> Unit,
) {
    if (!visible) return
    val dark = CouiTheme.isDark
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            Dialog(
                visible = true,
                onDismissRequest = onDismissRequest,
                dark = dark,
                title = stringResource(R.string.message_nav_title),
                confirmAction = CouiDialogAction(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissRequest,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
            ) {
                MessageNavContent(items = items, onJump = onJump, onJumpToLatest = onJumpToLatest)
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = true,
                title = stringResource(R.string.message_nav_title),
                onDismissRequest = onDismissRequest,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                MessageNavContent(items = items, onJump = onJump, onJumpToLatest = onJumpToLatest)
            }
        }
    }
}

@Composable
private fun MessageNavContent(
    items: List<MessageNavItem>,
    onJump: (MessageNavItem) -> Unit,
    onJumpToLatest: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
    val uiStyle = LocalUiStyle.current
    val rowInteraction = remember { MutableInteractionSource() }
    // 打开时直接落在最新一条：长会话里最常找的是最近的提问，
    // 从头开始意味着每次都要先滑到底才能找。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (items.size - 1).coerceAtLeast(0),
    )
    // 索引可能晚于弹层首帧才读回（首次为空），此时 initial 位置已经失效，
    // 内容到位后再落一次最新一条。
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }
    // 列表自身封顶、内部滚动，标题、「跳到最新消息」与底部提示保持固定。
    // 不给 Column 设高度、也不给列表用 weight：两者都会把容器撑到上限，
    // 内容不足时下方留下大片空白。
    Column(modifier = Modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.message_nav_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
            return@Column
        }
        // 固定在顶部、不随列表滚动：目标是「回最新」，位置必须可预期。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = rowInteraction, indication = null) { onJumpToLatest() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.message_nav_jump_latest),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "↓",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        // 与列表之间画一条分隔，避免被误当成列表首项。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LIST_MAX_HEIGHT),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.messageId }) { position, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = rowInteraction, indication = null) {
                            onJump(item)
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = (position + 1).toString(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (uiStyle == AppearanceUiStyle.COUI) {
                            colors.summary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.padding(end = 10.dp).padding(top = 3.dp),
                    )
                    Text(
                        text = item.prompt.ifBlank { stringResource(R.string.message_nav_untitled) },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.message_nav_hint),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
    }
}
