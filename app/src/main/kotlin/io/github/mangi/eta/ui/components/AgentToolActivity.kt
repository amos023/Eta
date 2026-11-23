package io.github.mangi.eta.ui.components

import android.content.Context
import android.os.SystemClock
import android.content.Intent
import androidx.compose.foundation.border
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.luminance
import io.github.mangi.eta.R
import android.widget.Toast
import io.github.mangi.eta.agent.browser.BrowserSessionSnapshot
import io.github.mangi.eta.agent.media.GeneratedImageExporter
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.agent.subagent.AgentSubagentToolCatalog
import io.github.mangi.eta.agent.subagent.SubagentActivitySnapshot
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 工作过程之外单独出现的工具条目（例如旧会话或语音浮层），沿用时间线节点外观。
 */
@Composable
internal fun ToolActivityInline(
    message: ToolActivityMessageUi,
    onOpenBrowser: () -> Unit,
    showBrowserShortcut: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        ToolActivityRow(message, onOpenBrowser, showBrowserShortcut, modifier)
        return
    }
    Box(modifier = modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        WorkTimelineNode(
            icon = iconForTool(message.toolName),
            tone = message.status.nodeTone(),
            isLast = true,
            connectTop = false,
        ) {
            ToolActivityRow(message, onOpenBrowser, showBrowserShortcut)
        }
    }
}

/**
 * 工具的折叠形态是两行：做了什么，以及一行结论。命令、完整结果和浏览器预览只在展开后
 * 组合，长会话中成百上千个工具行保持轻量。
 */
@Composable
internal fun ToolActivityRow(
    message: ToolActivityMessageUi,
    onOpenBrowser: () -> Unit,
    showBrowserShortcut: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val running = message.status == ToolActivityStatusUi.Running
    val elapsedSeconds = rememberRunningSeconds(message.id, running)
    if (message.toolName == AgentSubagentToolCatalog.TOOL_NAME) {
        val snapshot = remember(message.command, message.argumentsSummary, message.resultSummary, message.status) {
            SubagentActivitySnapshot.decode(message.command) ?: SubagentActivitySnapshot(
                subagentType = message.argumentsSummary.substringBefore(" · ").ifBlank { "subagent" },
                description = message.argumentsSummary.substringAfter(" · ", message.argumentsSummary),
                status = when (message.status) {
                    ToolActivityStatusUi.Running -> SubagentActivitySnapshot.STATUS_RUNNING
                    ToolActivityStatusUi.Failed -> SubagentActivitySnapshot.STATUS_FAILED
                    else -> SubagentActivitySnapshot.STATUS_COMPLETED
                },
                output = message.resultSummary.orEmpty(),
            )
        }
        SubagentActivityRow(
            message = message,
            snapshot = snapshot,
            elapsedSeconds = elapsedSeconds,
            modifier = modifier,
        )
        return
    }
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    // 只有「当前浏览器」条目订阅实时会话快照，避免每个工具行都跟随快照重组。
    val browser = LocalAgentBrowserSession.current
    val browserSnapshot = if (showBrowserShortcut && browser != null) {
        browser.snapshots.collectAsState().value
    } else {
        null
    }
    val failed = message.status == ToolActivityStatusUi.Failed
    val title = message.argumentsSummary.ifBlank { toolDisplayName(message.toolName) }
    val outcome = remember(message.status, message.resultSummary) { message.outcomeLine() }
    val browserLine = browserSnapshot?.let { snapshot ->
        when {
            snapshot.isLoading -> stringResource(R.string.tool_browser_loading, snapshot.progress)
            snapshot.host.isNotBlank() && snapshot.title.isNotBlank() -> "${snapshot.host} · ${snapshot.title}"
            snapshot.host.isNotBlank() -> snapshot.host
            else -> null
        }
    }
    val subtitle = browserLine ?: outcome
    // 结果只有一行且已作为结论显示时，展开不会带来新信息，不提供展开入口。
    val resultDetail = message.resultSummary?.trim()?.takeIf { it.isNotEmpty() && it != outcome }
    val hasDetail = !message.command.isNullOrBlank() ||
        resultDetail != null ||
        showBrowserShortcut

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WorkRowHeight)
                .clip(RoundedCornerShape(10.dp))
                .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showTitle) {
                    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
                    ShimmerText(
                        text = title,
                        active = running,
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 13.5.sp),
                        color = if (running) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.56f else 0.52f)
                        },
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = if (message.isTerminalTool() && browserLine == null) {
                            MiuixTheme.textStyles.footnote2.copy(fontFamily = EtaMonospace)
                        } else {
                            MiuixTheme.textStyles.footnote2
                        },
                        color = if (failed) {
                            StatusError
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (showTitle) 1.dp else 0.dp),
                    )
                }
            }
            if (elapsedSeconds != null && elapsedSeconds >= RUNNING_TIMER_THRESHOLD_SECONDS) {
                Text(
                    text = stringResource(R.string.tool_elapsed_seconds, elapsedSeconds),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (hasDetail) {
                ExpandChevron(
                    expanded = expanded,
                    contentDescription = stringResource(
                        if (expanded) R.string.tool_details_collapse else R.string.tool_details_expand,
                    ),
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            ToolActivityDetail(
                message = message,
                browserSnapshot = browserSnapshot,
                showBrowserShortcut = showBrowserShortcut,
                onOpenBrowser = onOpenBrowser,
            )
        }
    }
}

/**
 * 工具产出的会话资产图片。生成的图必须能直接在聊天里看见，而不是只留一行「图片 1 张」。
 * 单图按原比例铺满可用宽度，多图横向排布；点击进全屏预览。
 *
 * 由 AgentChatBody 的时间线投影调用，作为独立的聊天流条目出现在工具分组之后、
 * 该轮正文之前，而不是塞进可折叠的工具活动卡内部。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GeneratedArtifactStrip(
    images: List<String>,
    groupKey: String,
    modifier: Modifier = Modifier,
    onEditImage: ((String) -> Unit)? = null,
) {
    if (images.isEmpty()) return
    val previewer = LocalChatImagePreviewer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.image_saved_to_gallery)
    val saveFailedText = stringResource(R.string.image_save_failed)
    val saveLabel = stringResource(R.string.image_action_save)
    val shareLabel = stringResource(R.string.image_action_share)
    val editLabel = stringResource(R.string.image_action_edit)
    val promptLabel = stringResource(R.string.image_action_prompt)
    val copyLabel = stringResource(R.string.action_copy)
    val clipboard = LocalClipboardManager.current
    var saving by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<String?>(null) }
    var prompts by remember(images) { mutableStateOf<List<String>>(emptyList()) }
    // 提示词存在图片旁边的同名 json 里，读盘放到后台，避免组合期做 IO。
    LaunchedEffect(images) {
        prompts = withContext(Dispatchers.IO) { readGeneratedPrompts(images) }
    }
    val single = images.size == 1
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            images.forEachIndexed { index, path ->
                val bitmap = rememberDataUrlBitmap(path) ?: return@forEachIndexed
                val ratio = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                    .coerceIn(0.55f, 1.85f)
                val width = when {
                    single -> 232.dp
                    images.size == 2 -> 156.dp
                    else -> 132.dp
                }
                val height = width / ratio
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .clip(RoundedCornerShape(12.dp))
                        // 单击进全屏预览；长按弹出保存/分享/改图/提示词。
                        .combinedClickable(
                            onClick = {
                                previewer?.open(
                                    images = images,
                                    initialIndex = index,
                                    groupKey = groupKey,
                                )
                            },
                            onLongClick = {
                                actionTarget = path
                                showActions = true
                            },
                        ),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(height),
                    )
                }
            }
        }
        val target = actionTarget
        if (showActions && target != null) {
            ImageActionSheet(
                onDismiss = { showActions = false },
                saveLabel = saveLabel,
                shareLabel = shareLabel,
                editLabel = editLabel,
                promptLabel = promptLabel,
                cancelLabel = stringResource(R.string.action_cancel),
                onSave = {
                    showActions = false
                    if (!saving) {
                        saving = true
                        scope.launch {
                            val result = GeneratedImageExporter.saveToGallery(context, target)
                            saving = false
                            Toast.makeText(
                                context,
                                when (result) {
                                    is GeneratedImageExporter.Result.Saved -> savedText
                                    is GeneratedImageExporter.Result.Failed -> result.reason
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onShare = {
                    showActions = false
                    shareGeneratedImages(context, listOf(target))
                },
                onEdit = onEditImage?.let { callback ->
                    {
                        showActions = false
                        callback(target)
                    }
                },
                onPrompt = if (prompts.isNotEmpty()) {
                    {
                        showActions = false
                        showPrompt = true
                    }
                } else {
                    null
                },
            )
        }
        if (showPrompt && prompts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        0.5.dp,
                        MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                // 合并显示时可能来自多次调用，逐条列出各自的提示词。
                prompts.forEachIndexed { index, text ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = copyLabel,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { clipboard.setText(AnnotatedString(text)) },
                    )
                }
            }
        }
    }
}

/** 长按生成图后弹出的操作面板。 */
@Composable
private fun ImageActionSheet(
    onDismiss: () -> Unit,
    saveLabel: String,
    shareLabel: String,
    editLabel: String,
    promptLabel: String,
    cancelLabel: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    onPrompt: (() -> Unit)?,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(vertical = 6.dp),
        ) {
            ImageActionRow(saveLabel, Icons.Rounded.FileDownload, onSave)
            ImageActionRow(shareLabel, Icons.Rounded.Share, onShare)
            if (onEdit != null) {
                ImageActionRow(editLabel, Icons.Rounded.Edit, onEdit)
            }
            if (onPrompt != null) {
                ImageActionRow(promptLabel, Icons.Rounded.KeyboardArrowDown, onPrompt)
            }
            ImageActionRow(cancelLabel, Icons.Rounded.Close, onDismiss)
        }
    }
}

@Composable
private fun ImageActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** 把生成图所在文件解析出来；记录里可能是 file:// 前缀，也可能是裸绝对路径。 */
private fun resolveGeneratedFile(path: String): File? {
    val raw = path.trim()
    val file = when {
        raw.startsWith("file://", ignoreCase = true) ->
            runCatching { File(java.net.URI(raw)) }.getOrNull()
        raw.startsWith("/") -> File(raw)
        else -> null
    }
    return file?.takeIf { it.isFile }
}

/** 读图片旁的 sidecar json，取每次生图用的提示词；旧图没有该文件时返回空表。 */
private fun readGeneratedPrompts(images: List<String>): List<String> {
    val prompts = LinkedHashSet<String>()
    for (path in images) {
        val file = resolveGeneratedFile(path) ?: continue
        val meta = File(file.parentFile, file.nameWithoutExtension + ".json")
        if (!meta.isFile) continue
        val raw = runCatching { meta.readText() }.getOrNull() ?: continue
        val prompt = runCatching { org.json.JSONObject(raw).optString("prompt") }.getOrNull()
        if (!prompt.isNullOrBlank()) prompts += prompt
    }
    return prompts.toList()
}

/** 分享生成图：走 FileProvider 转 content://，带上临时读权限。 */
private fun shareGeneratedImages(context: Context, images: List<String>) {
    val uris = images.mapNotNull { path ->
        val file = resolveGeneratedFile(path) ?: return@mapNotNull null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
    if (uris.isEmpty()) {
        Toast.makeText(
            context,
            context.getString(R.string.image_share_no_file),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(intent, null)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(chooser) }
}

/**
 * 连续调用同一工具的分组：标题汇总次数，展开后每次调用只占一行，失败的调用仍可单独展开。
 */
@Composable
internal fun ToolGroupRow(
    group: AgentWorkStep.ToolGroup,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val running = group.running
    val latest = group.calls.last()
    val title = toolDisplayName(group.toolName) + " · " +
        pluralStringResource(R.plurals.tool_group_calls, group.calls.size, group.calls.size)
    val subtitle = when {
        running -> latest.argumentsSummary.takeIf(String::isNotBlank)
        group.failedCount > 0 -> pluralStringResource(
            R.plurals.work_summary_failed,
            group.failedCount,
            group.failedCount,
        )
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WorkRowHeight)
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
                ShimmerText(
                    text = title,
                    active = running,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 13.5.sp),
                    color = if (running) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.56f else 0.52f)
                    },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (!running && group.failedCount > 0) {
                            StatusError
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            ExpandChevron(
                expanded = expanded,
                contentDescription = stringResource(
                    if (expanded) R.string.tool_details_collapse else R.string.tool_details_expand,
                ),
                modifier = Modifier.padding(start = 6.dp, end = 4.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                group.calls.forEach { call ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 13.dp, end = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(call.status.dotColor()),
                        )
                        ToolActivityRow(
                            message = call,
                            onOpenBrowser = onOpenBrowser,
                            showBrowserShortcut = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolActivityDetail(
    message: ToolActivityMessageUi,
    browserSnapshot: BrowserSessionSnapshot?,
    showBrowserShortcut: Boolean,
    onOpenBrowser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!message.command.isNullOrBlank()) {
            ToolCommandBlock(command = message.command, context = message.argumentsSummary)
        }
        val result = message.resultSummary?.takeIf(String::isNotBlank)
        if (result != null) {
            SelectionContainer {
                Text(
                    text = result,
                    style = if (message.isTerminalTool()) {
                        MiuixTheme.textStyles.footnote2.copy(fontFamily = EtaMonospace)
                    } else {
                        MiuixTheme.textStyles.footnote2
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = MAX_RESULT_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .squircleSurface(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                            cornerRadius = 10.dp,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        if (showBrowserShortcut) {
            browserSnapshot?.takeIf { it.available }?.let { snapshot ->
                BrowserPagePreview(snapshot = snapshot)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    text = stringResource(R.string.ui_open_current_browser_58358e),
                    onClick = onOpenBrowser,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    minHeight = 36.dp,
                    textStyle = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}

/**
 * 运行计时只在工具执行中组合，每秒仅使这一小段文字重组。起点按条目身份保存，
 * 滚出视口再回来时继续计时；它是界面侧的近似值，不写入归档。
 */
@Composable
private fun rememberRunningSeconds(id: String, running: Boolean): Int? {
    if (!running) return null
    val startedAt = rememberSaveable(id) { SystemClock.elapsedRealtime() }
    var seconds by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, startedAt) {
        while (true) {
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1_000).toInt()
            delay(1_000)
        }
    }
    return seconds
}

private fun ToolActivityMessageUi.isTerminalTool(): Boolean =
    toolName in setOf("terminal", "terminal_job", "terminal_daemon", "run_command")

@Composable
private fun ToolActivityStatusUi.dotColor() = when (this) {
    ToolActivityStatusUi.Running -> StatusRunning
    ToolActivityStatusUi.Success -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
    ToolActivityStatusUi.Failed -> StatusError
    ToolActivityStatusUi.Unknown -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
}

private const val RUNNING_TIMER_THRESHOLD_SECONDS = 3
private const val MAX_RESULT_LINES = 12

/**
 * 浏览器工具的实时页面预览：迷你地址条 + 当前视口截图。
 *
 * 截图只在页面加载中或内容稳定后的低频节拍刷新；组合销毁即停止，
 * 不做后台轮询。截图不可用时退化为图标占位。
 */
@Composable
private fun BrowserPagePreview(
    snapshot: BrowserSessionSnapshot,
    modifier: Modifier = Modifier,
) {
    val browser = LocalAgentBrowserSession.current ?: return
    var preview by remember(snapshot.url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(browser, snapshot.url, snapshot.isLoading) {
        while (true) {
            val image = withContext(Dispatchers.IO) {
                browser.capturePreview()?.let { decodeDataUrlBitmap(it.dataUrl) }
            }
            if (image != null) preview = image
            delay(if (snapshot.isLoading) 1_200L else 4_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 10.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (snapshot.isLoading) StatusRunning else StatusSuccess),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = snapshot.host.ifBlank { snapshot.displayUrl },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val image = preview
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.tool_browser_preview),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.outline,
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (snapshot.title.isNotBlank()) {
                Text(
                    text = snapshot.title,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (snapshot.displayUrl.isNotBlank()) {
                Text(
                    text = snapshot.displayUrl,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolCommandBlock(
    command: String,
    context: String,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(command) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_400)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .squircleSurface(
                color = MiuixTheme.colorScheme.surface,
                cornerRadius = 10.dp,
            )
            .squircleBorder(
                width = 0.5.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                cornerRadius = 10.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.ifBlank { stringResource(R.string.shell_command) },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    @Suppress("DEPRECATION")
                    clipboardManager.setText(AnnotatedString(command))
                    copied = true
                },
                minWidth = 32.dp,
                minHeight = 32.dp,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check
                        else Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(
                        if (copied) R.string.copy_copied else R.string.copy_command,
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = if (copied) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f)
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)),
        )
        SelectionContainer {
            Text(
                text = command,
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = EtaMonospace),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
