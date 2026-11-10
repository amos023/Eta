package io.github.mangi.eta.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowListPopup
import io.github.mangi.eta.ui.model.ConversationGroupUi
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.basic.TextField
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.dialog.Dialog
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.CouiDialogAction
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor

/**
 * 会话侧边栏尺寸标尺。
 *
 * 外层水平边距（12.dp）+ 行内水平边距（16.dp）= 28.dp，与 Miuix 原生 [SmallTitle] 的默认内边距
 * 及 [InputField] 内部搜索图标起点完全对齐，保证搜索框、分组标题与对话标题处于同一条垂直基准线。
 */
private object ConversationPanelMetrics {
    val PaneHorizontalPadding = 12.dp
    val TopInset = 8.dp
    val AfterActionBar = 8.dp
    val BottomInset = 4.dp
    val ActionIconSize = 20.dp
    val FirstSectionTopPadding = 8.dp
    val SectionTopPadding = 16.dp
    val SectionBottomPadding = 6.dp
    val RowMinHeight = 44.dp
    val RowGap = 2.dp
    val RowCornerRadius = 14.dp
    val RowHorizontalPadding = 16.dp
    /** 分支行相对父会话的额外左缩进。 */
    val BranchIndent = 22.dp
    val RowVerticalPadding = 11.dp
    val ActiveDotSize = 6.dp
    val ActiveDotGap = 10.dp
    val EmptyVerticalPadding = 28.dp
    val DockVerticalPadding = 4.dp
    val DockEntryCornerRadius = 12.dp
    val DockEntryIconSize = 20.dp
}

/**
 * 侧栏与外层推移容器共用的背景色。
 *
 * 深色下聊天区保持 surface（纯黑/近黑），侧栏沿用同一颜色会在展开后与聊天区融为一体；
 * 抬高一档到 surfaceContainer，保证两侧始终可分。浅色维持 surface，避免改变现有观感。
 */
@Composable
internal fun conversationPaneContainerColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0xFF18191C) else Color(0xFFF3F4F6)
    }
    val colors = MiuixTheme.colorScheme
    return if (colors.background.luminance() > 0.5f) colors.surface else colors.surfaceContainer
}

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: androidx.compose.ui.unit.Dp,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSearchChange: (String) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    /** 把会话移入分组；[groupId] 为 null 表示移出到未分组。 */
    onConversationAssignGroup: (ConversationSummaryUi, String?) -> Unit = { _, _ -> },
    /** 新建分组并返回其 id，用于移入流程。 */
    onCreateGroup: (String) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    /** 重命名分组。 */
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // state.conversations 已由 AgentAppState 按标题、预览与消息内容过滤。
    val query = state.searchQuery.trim()
    val groups = remember(state.conversations, state.groups) {
        state.conversations.groupForDrawer(state.groups)
    }
    // 移动分组：先记下要移动的会话，弹层确认后再写入。
    var movingConversation by remember { mutableStateOf<ConversationSummaryUi?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    // 折叠的分组 id；默认全部展开。
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    var renamingGroup by remember { mutableStateOf<ConversationGroupUi?>(null) }
    var deletingGroup by remember { mutableStateOf<ConversationGroupUi?>(null) }

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = conversationPaneContainerColor(),
        contentColor = MiuixTheme.colorScheme.onSurface,
    ) {
        // 搜索与工具条占据独立布局空间，列表只在中间视口内滚动和回弹。
        Column(modifier = Modifier.fillMaxSize()) {
            PaneFixedRegion {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
                ) {
                    Spacer(Modifier.height(ConversationPanelMetrics.TopInset))
                    PaneActionBar(query = state.searchQuery, onSearchChange = onSearchChange)
                    Spacer(Modifier.height(ConversationPanelMetrics.AfterActionBar))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding)
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.RowGap),
                overscrollEffect = null,
            ) {
                if (state.conversations.isEmpty()) {
                    item {
                        EmptyConversations(isSearching = query.isNotBlank())
                    }
                } else {
                    groups.forEachIndexed { groupIndex, group ->
                        val folder = group.section as? ConversationDrawerSection.Folder
                        // 第一个手动分组前插入「分组」总标题，与置顶、时间分区区分开。
                        val previousSection = groups.getOrNull(groupIndex - 1)?.section
                        if (folder != null && previousSection !is ConversationDrawerSection.Folder) {
                            item(key = "group-caption") {
                                Text(
                                    text = stringResource(R.string.conversation_groups_caption),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(
                                        start = ConversationPanelMetrics.RowHorizontalPadding,
                                        top = if (groupIndex == 0) {
                                            ConversationPanelMetrics.FirstSectionTopPadding
                                        } else {
                                            ConversationPanelMetrics.SectionTopPadding
                                        },
                                        end = ConversationPanelMetrics.RowHorizontalPadding,
                                        bottom = 2.dp,
                                    ),
                                )
                            }
                        }
                        val isCollapsed = folder != null && folder.groupId in collapsedGroups
                        item(key = "section-${group.section}") {
                            ConversationSectionHeader(
                                group = group,
                                isFirstSection = groupIndex == 0,
                                collapsed = isCollapsed,
                                onToggleCollapsed = {
                                    val id = folder?.groupId ?: return@ConversationSectionHeader
                                    collapsedGroups = if (id in collapsedGroups) {
                                        collapsedGroups - id
                                    } else {
                                        collapsedGroups + id
                                    }
                                },
                                onRenameGroup = {
                                    folder?.let { f ->
                                        renamingGroup = state.groups.firstOrNull { it.id == f.groupId }
                                    }
                                },
                                onDeleteGroup = {
                                    folder?.let { f ->
                                        deletingGroup = state.groups.firstOrNull { it.id == f.groupId }
                                    }
                                },
                            )
                        }
                        if (isCollapsed) return@forEachIndexed
                        items(
                            items = group.items,
                            key = { it.id },
                        ) { conversation ->
                            ConversationTextRow(
                                conversation = conversation,
                                selected = conversation.id == state.selectedConversationId,
                                onClick = { onConversationSelected(conversation.id) },
                                onTogglePin = { onConversationTogglePin(conversation) },
                                onRename = { onConversationRename(conversation) },
                                onExport = { onConversationExport(conversation) },
                                onDelete = { onConversationDelete(conversation) },
                                onMoveToGroup = { movingConversation = conversation },
                                // 分组内成员整体右移，与分组头形成包含关系。
                                depth = if (folder != null) 1 else 0,
                            )
                        }
                    }
                }
            }
            PaneFixedRegion {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ConversationPanelMetrics.RowHorizontalPadding),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(ConversationPanelMetrics.DockVerticalPadding))
                    PaneDock(
                        onOpenSettings = onOpenSettings,
                        onOpenModelProviders = onOpenModelProviders,
                        onOpenTools = onOpenTools,
                        onOpenSkills = onOpenSkills,
                        onOpenCharacters = onOpenCharacters,
                        onOpenPermissions = onOpenPermissions,
                    )
                    Spacer(Modifier.height(ConversationPanelMetrics.BottomInset))
                }
            }
        }
    }

    // ── 移入分组弹层 ──────────────────────────────────────────────────────
    val target = movingConversation
    if (target != null) {
        ConversationGroupPickerSheet(
            groups = state.groups,
            currentGroupId = target.groupId,
            onPick = { groupId ->
                onConversationAssignGroup(target, groupId)
                movingConversation = null
            },
            onCreateNew = {
                movingConversation = null
                showNewGroupDialog = true
            },
            onDismiss = { movingConversation = null },
        )
    }
    if (showNewGroupDialog) {
        ConversationGroupNameDialog(
            initialName = "",
            onConfirm = { name ->
                showNewGroupDialog = false
                onCreateGroup(name)
            },
            onDismiss = { showNewGroupDialog = false },
        )
    }
    renamingGroup?.let { group ->
        ConversationGroupNameDialog(
            initialName = group.name,
            onConfirm = { name ->
                renamingGroup = null
                onRenameGroup(group.id, name)
            },
            onDismiss = { renamingGroup = null },
        )
    }
    deletingGroup?.let { group ->
        ConversationGroupDeleteDialog(
            name = group.name,
            onConfirm = {
                deletingGroup = null
                onDeleteGroup(group.id)
            },
            onDismiss = { deletingGroup = null },
        )
    }
}

@Composable
private fun PaneFixedRegion(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(conversationPaneContainerColor()),
    ) {
        content()
    }
}

@Composable
private fun PaneActionBar(
    query: String,
    onSearchChange: (String) -> Unit,
) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiSearchBarBox(
                query = query,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.conversation_search_hint),
                backgroundColor = conversationPaneContainerColor(),
            )
        }
        AppearanceUiStyle.MIUIX -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    modifier = Modifier.weight(1f),
                    insideMargin = DpSize.Zero,
                    expanded = false,
                    onExpandedChange = {},
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = onSearchChange,
                            onSearch = onSearchChange,
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.conversation_search_hint),
                            color = MiuixTheme.colorScheme.surfaceContainerHighest,
                        )
                    },
                    content = {},
                )
            }
        }
    }
}

@Composable
private fun ConversationSectionHeader(
    group: ConversationDrawerGroup,
    isFirstSection: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
) {
    val folder = group.section as? ConversationDrawerSection.Folder
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val anchor = rememberCouiMenuAnchor()
    val topPadding = if (isFirstSection) {
        ConversationPanelMetrics.FirstSectionTopPadding
    } else {
        ConversationPanelMetrics.SectionTopPadding
    }
    val label = group.localizedLabel()
    val renameText = stringResource(R.string.action_rename)
    val deleteText = stringResource(R.string.conversation_group_delete)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .couiMenuAnchor(anchor)
            .combinedClickable(
                // 分组标题可点击折叠；时间分区标题只做长按菜单。
                onClick = { if (folder != null) onToggleCollapsed() },
                onLongClick = if (folder != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        anchor.open { showMenu = true }
                    }
                } else {
                    null
                },
            )
            .padding(
                start = ConversationPanelMetrics.RowHorizontalPadding,
                top = topPadding,
                end = ConversationPanelMetrics.RowHorizontalPadding,
                bottom = ConversationPanelMetrics.SectionBottomPadding,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 分组前加文件夹图标：字号与会话标题一致，靠图标 + 成员缩进表达包含关系。
            if (folder != null) {
                Icon(
                    imageVector = GroupFolderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = if (folder != null) {
                    MiuixTheme.textStyles.body1
                } else {
                    MiuixTheme.textStyles.footnote1
                },
                fontWeight = if (folder != null) FontWeight.Medium else FontWeight.Normal,
                color = if (folder != null) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // weight(1f) 吃掉全部剩余宽度，把后面的数量与箭头稳定推到行尾。
                modifier = Modifier.weight(1f),
            )
            if (folder != null) {
                Text(
                    text = group.items.size.toString(),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.65f),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        // 与会话标题同字号后，箭头同步放大以保持视觉重量。
                        .size(20.dp)
                        .rotate(if (collapsed) -90f else 0f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f),
                )
            }
        }
    }

    if (folder != null && showMenu) {
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                val destructive = CouiDialogDefaults.themeColors().destructive
                val items = remember(renameText, deleteText, destructive) {
                    listOf(
                        CouiMenuItem(value = 0, text = renameText, icon = Icons.Outlined.Edit),
                        CouiMenuItem(
                            value = 1,
                            text = deleteText,
                            icon = Icons.Outlined.Delete,
                            iconTint = destructive,
                            dividerBefore = true,
                        ),
                    )
                }
                Menu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    items = items,
                    onItemClick = { item ->
                        showMenu = false
                        if (item.value == 0) onRenameGroup() else onDeleteGroup()
                    },
                    anchor = anchor,
                )
            }
            AppearanceUiStyle.MIUIX -> {
                WindowListPopup(
                    show = showMenu,
                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { showMenu = false },
                ) {
                    val renameItem = remember(renameText) {
                        DropdownItem(
                            text = renameText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                )
                            },
                        )
                    }
                    val deleteItem = remember(deleteText) {
                        DropdownItem(
                            text = deleteText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                    val deleteColors = DropdownDefaults.dropdownColors(
                        contentColor = MiuixTheme.colorScheme.error,
                        selectedContentColor = MiuixTheme.colorScheme.error,
                        selectedIndicatorColor = MiuixTheme.colorScheme.error,
                    )
                    ListPopupColumn {
                        DropdownImpl(
                            item = renameItem,
                            optionSize = 2,
                            isSelected = false,
                            index = 0,
                            onSelectedIndexChange = {
                                showMenu = false
                                onRenameGroup()
                            },
                        )
                        DropdownImpl(
                            item = deleteItem,
                            optionSize = 2,
                            isSelected = false,
                            index = 1,
                            dropdownColors = deleteColors,
                            onSelectedIndexChange = {
                                showMenu = false
                                onDeleteGroup()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTextRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onMoveToGroup: () -> Unit,
    /** 1 表示该行是分支，缩进到父会话下方。 */
    depth: Int = 0,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val anchor = rememberCouiMenuAnchor()
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val couiDark = CouiTheme.isDark
    val couiRowShape = remember { CouiShape(14.dp, 2f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                // 分支行整体右移，用缩进表达父子归属；不再画连接线，避免与时间分区视觉打架。
                .padding(start = if (depth > 0) ConversationPanelMetrics.BranchIndent else 0.dp)
                .couiMenuAnchor(anchor)
                .then(
                    if (isCoui) {
                        Modifier
                            .clip(couiRowShape)
                            .background(
                                if (selected) {
                                    if (couiDark) Color(0xFF26292E) else Color.White
                                } else {
                                    Color.Transparent
                                },
                                couiRowShape,
                            )
                    } else {
                        Modifier.squircleSurface(
                            color = if (selected) {
                                MiuixTheme.colorScheme.tertiaryContainer
                            } else {
                                Color.Transparent
                            },
                            cornerRadius = ConversationPanelMetrics.RowCornerRadius,
                        )
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        anchor.open { showActionMenu = true }
                    },
                )
                .padding(
                    horizontal = ConversationPanelMetrics.RowHorizontalPadding,
                    vertical = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            conversation.characterName?.let { name ->
                CharacterAvatar(
                    name = name,
                    avatarPath = conversation.characterAvatarPath,
                    size = 32.dp,
                    shape = CircleShape,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                val title = conversation.title.ifBlank { conversation.preview }
                Text(
                    text = title,
                    color = if (selected) {
                        MiuixTheme.colorScheme.onTertiaryContainer
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 标题与角色名相同（如未改名的角色会话）时不再重复第二行。
                conversation.characterName?.takeIf { it != title }?.let { name ->
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                conversation.runStatus?.let { status ->
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (conversation.isActiveRun) {
                Box(
                    modifier = Modifier
                        .padding(start = ConversationPanelMetrics.ActiveDotGap)
                        .size(ConversationPanelMetrics.ActiveDotSize)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }

        val pinText = stringResource(
            if (conversation.isPinned) {
                R.string.action_unpin_conversation
            } else {
                R.string.action_pin_conversation
            },
        )
        val renameText = stringResource(R.string.action_rename)
        val exportText = stringResource(R.string.action_export)
        val deleteText = stringResource(R.string.action_delete)
        val moveGroupText = stringResource(R.string.conversation_group_move_to)
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                val destructiveColor = CouiDialogDefaults.themeColors().destructive
                val menuItems = remember(
                    pinText, renameText, exportText, deleteText, moveGroupText, destructiveColor,
                ) {
                    listOf(
                        CouiMenuItem(value = 0, text = pinText, icon = Icons.Outlined.PushPin),
                        CouiMenuItem(value = 1, text = renameText, icon = Icons.Outlined.Edit),
                        CouiMenuItem(value = 2, text = exportText, icon = Icons.Outlined.Download),
                        CouiMenuItem(value = 4, text = moveGroupText, icon = Icons.Outlined.AutoStories),
                        CouiMenuItem(
                            value = 3,
                            text = deleteText,
                            icon = Icons.Outlined.Delete,
                            iconTint = destructiveColor,
                            dividerBefore = true,
                        ),
                    )
                }
                Menu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    items = menuItems,
                    onItemClick = { item ->
                        showActionMenu = false
                        when (item.value) {
                            0 -> onTogglePin()
                            1 -> onRename()
                            2 -> onExport()
                            3 -> onDelete()
                            4 -> onMoveToGroup()
                        }
                    },
                    anchor = anchor,
                )
            }
            AppearanceUiStyle.MIUIX -> {
                WindowListPopup(
                    show = showActionMenu,
                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { showActionMenu = false },
                ) {
                    val pinItem = remember(pinText) {
                        DropdownItem(
                            text = pinText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                )
                            },
                        )
                    }
                    val renameItem = remember(renameText) {
                        DropdownItem(
                            text = renameText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                )
                            },
                        )
                    }
                    val exportItem = remember(exportText) {
                        DropdownItem(
                            text = exportText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                )
                            },
                        )
                    }
                    val deleteItem = remember(deleteText) {
                        DropdownItem(
                            text = deleteText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                    val moveGroupItem = remember(moveGroupText) {
                        DropdownItem(
                            text = moveGroupText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                                )
                            },
                        )
                    }
                    val deleteColors = DropdownDefaults.dropdownColors(
                        contentColor = MiuixTheme.colorScheme.error,
                        selectedContentColor = MiuixTheme.colorScheme.error,
                        selectedIndicatorColor = MiuixTheme.colorScheme.error,
                    )
                    ListPopupColumn {
                        DropdownImpl(
                            item = pinItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 0,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onTogglePin()
                            },
                        )
                        DropdownImpl(
                            item = renameItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 1,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onRename()
                            },
                        )
                        DropdownImpl(
                            item = exportItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 2,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onExport()
                            },
                        )
                        DropdownImpl(
                            item = moveGroupItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 3,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onMoveToGroup()
                            },
                        )
                        DropdownImpl(
                            item = deleteItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 4,
                            dropdownColors = deleteColors,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversations(isSearching: Boolean) {
    Text(
        text = stringResource(
            if (isSearching) R.string.conversation_no_results else R.string.conversation_empty,
        ),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(
            horizontal = ConversationPanelMetrics.RowHorizontalPadding,
            vertical = ConversationPanelMetrics.EmptyVerticalPadding,
        ),
    )
}

@Composable
private fun PaneDock(
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DockEntry(
            icon = Icons.Outlined.Settings,
            label = "设置",
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Outlined.Cloud,
            label = "模型",
            onClick = onOpenModelProviders,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Outlined.Widgets,
            label = "工具",
            onClick = onOpenTools,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Outlined.AutoStories,
            label = "Skills",
            onClick = onOpenSkills,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Outlined.Shield,
            label = "权限",
            onClick = onOpenPermissions,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Outlined.SportsBar,
            label = "角色",
            onClick = onOpenCharacters,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DockEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .squircleSurface(
                color = Color.Transparent,
                cornerRadius = ConversationPanelMetrics.DockEntryCornerRadius,
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(ConversationPanelMetrics.DockEntryIconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
    /** 组内被折叠的分支数；0 表示该组没有分支。 */
    val branchCount: Int = 0,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
    /** 手动分组：成员来自 conversation_groups，标题即组名。 */
    data class Folder(val groupId: String, val name: String) : ConversationDrawerSection
    /** 未归入任何分组的会话，排在最后。 */
    data object Ungrouped : ConversationDrawerSection
}

@Composable
private fun ConversationDrawerGroup.localizedLabel(): String = when (val value = section) {
    ConversationDrawerSection.Pinned -> stringResource(R.string.conversation_section_pinned)
    ConversationDrawerSection.Today -> stringResource(R.string.conversation_section_today)
    is ConversationDrawerSection.Dated -> value.label
    is ConversationDrawerSection.Folder -> value.name
    ConversationDrawerSection.Ungrouped -> stringResource(R.string.conversation_section_ungrouped)
}

/**
 * 侧栏分组：只有手动分组。
 *
 * 顺序为「手动分组 → 未分组」；未分组部分沿用原有时间分区（今天 / 日期）。
 * 分支不再自动聚合到父会话下，父子关系只用于给分支行做缩进。
 */
private fun List<ConversationSummaryUi>.groupForDrawer(
    folders: List<ConversationGroupUi> = emptyList(),
): List<ConversationDrawerGroup> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<ConversationDrawerGroup>()

    val pinned = filter { it.isPinned }
    if (pinned.isNotEmpty()) {
        result += ConversationDrawerGroup(ConversationDrawerSection.Pinned, pinned)
    }
    val rest = filterNot { it.isPinned }

    val groupedByFolder = rest.filter { !it.groupId.isNullOrBlank() }.groupBy { it.groupId!! }
    folders.forEach { folder ->
        val members = groupedByFolder[folder.id] ?: return@forEach
        if (members.isEmpty()) return@forEach
        result += ConversationDrawerGroup(
            section = ConversationDrawerSection.Folder(folder.id, folder.name),
            items = members,
        )
    }

    // 未分组：指向已删除分组的孤儿会话也归到这里。
    val ungrouped = rest.filter { it.groupId.isNullOrBlank() || folders.none { g -> g.id == it.groupId } }
    for (conversation in ungrouped) {
        val section = conversation.drawerSection()
        val last = result.lastOrNull()
        if (last != null && last.section == section) {
            result[result.lastIndex] = last.copy(items = last.items + conversation)
        } else {
            result += ConversationDrawerGroup(section = section, items = listOf(conversation))
        }
    }
    return result
}


private fun ConversationSummaryUi.drawerSection(): ConversationDrawerSection = when {
    isPinned -> ConversationDrawerSection.Pinned
    isActiveRun || isUpdatedToday(updatedAtMillis) -> ConversationDrawerSection.Today
    else -> ConversationDrawerSection.Dated(timeLabel)
}

private fun isUpdatedToday(timestampMillis: Long): Boolean {
    if (timestampMillis <= 0L) return true
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return now.get(java.util.Calendar.ERA) == target.get(java.util.Calendar.ERA) &&
        now.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)
}

/** 选择目标分组：顶部「未分组」用于移出当前分组，底部「新建分组」。 */
@Composable
private fun ConversationGroupPickerSheet(
    groups: List<ConversationGroupUi>,
    currentGroupId: String?,
    onPick: (String?) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noneLabel = stringResource(R.string.conversation_section_ungrouped)
    val newLabel = stringResource(R.string.conversation_group_new)
    val rows = remember(groups, currentGroupId, noneLabel) {
        buildList {
            add(null to noneLabel)
            groups.forEach { add(it.id to it.name) }
            add(GROUP_CREATE_NEW to newLabel)
        }
    }
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val dark = CouiTheme.isDark
            Dialog(
                visible = true,
                onDismissRequest = onDismiss,
                dark = dark,
                title = stringResource(R.string.conversation_group_move_to),
                confirmAction = CouiDialogAction(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
            ) {
                SelectionRows(rows = rows, currentGroupId = currentGroupId, onPick = onPick, onCreateNew = onCreateNew)
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = true,
                title = stringResource(R.string.conversation_group_move_to),
                onDismissRequest = onDismiss,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                SelectionRows(rows = rows, currentGroupId = currentGroupId, onPick = onPick, onCreateNew = onCreateNew)
            }
        }
    }
}

/** 占位 key：列表里「新建分组」一行的标识。 */
private const val GROUP_CREATE_NEW = "__eta_new_group__"

@Composable
private fun SelectionRows(
    rows: List<Pair<String?, String>>,
    currentGroupId: String?,
    onPick: (String?) -> Unit,
    onCreateNew: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        rows.forEach { (id, label) ->
            val isCreate = id == GROUP_CREATE_NEW
            val selected = id == currentGroupId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (isCreate) onCreateNew() else onPick(id)
                    }
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    color = if (isCreate) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 新建分组名输入。 */
@Composable
private fun ConversationGroupNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val title = stringResource(R.string.conversation_group_new_title)
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiInputDialog(
                visible = true,
                title = title,
                message = null,
                value = name,
                onValueChange = { name = it },
                placeholder = title,
                confirmText = stringResource(R.string.action_confirm),
                confirmEnabled = name.isNotBlank(),
                onConfirm = { onConfirm(name.trim()) },
                onDismissRequest = onDismiss,
            )
        }
        AppearanceUiStyle.MIUIX -> {
            WindowDialog(
                show = true,
                title = title,
                onDismissRequest = onDismiss,
            ) {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MiuixDialogActions(
                        confirmText = stringResource(R.string.action_confirm),
                        confirmEnabled = name.isNotBlank(),
                        onCancel = onDismiss,
                        onConfirm = { onConfirm(name.trim()) },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

/** 删除分组确认；只删分组，组内会话退回未分组。 */
@Composable
private fun ConversationGroupDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.conversation_group_delete)
    val summary = stringResource(R.string.conversation_group_delete_summary)
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiConfirmDialog(
                visible = true,
                title = title,
                message = "$name\n$summary",
                confirmText = stringResource(R.string.action_confirm),
                onConfirm = onConfirm,
                onDismissRequest = onDismiss,
            )
        }
        AppearanceUiStyle.MIUIX -> {
            WindowDialog(
                show = true,
                title = title,
                summary = summary,
                onDismissRequest = onDismiss,
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_confirm),
                    confirmEnabled = true,
                    onCancel = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/** 分组用的文件夹图标：1.7 描边线性风格，与项目其他图标一致。 */
private val GroupFolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GroupFolder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = {
                // 文件夹外形：左上起笔，顶部带一个小凸起。
                moveTo(3f, 7.5f)
                quadTo(3f, 5.5f, 5f, 5.5f)
                horizontalLineTo(9.2f)
                lineTo(11.2f, 7.8f)
                horizontalLineTo(19f)
                quadTo(21f, 7.8f, 21f, 9.8f)
                verticalLineTo(16.5f)
                quadTo(21f, 18.5f, 19f, 18.5f)
                horizontalLineTo(5f)
                quadTo(3f, 18.5f, 3f, 16.5f)
                close()
            },
        )
    }.build()
}
