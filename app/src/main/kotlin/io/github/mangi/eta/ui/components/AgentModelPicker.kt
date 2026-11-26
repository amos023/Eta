package io.github.mangi.eta.ui.components

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.ModelRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RemoteModelFetcher
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.pages.providers.RemoteModelImport
import io.github.mangi.eta.ui.pages.providers.RemoteModelImportSheet
import java.util.Locale
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelProviderGroupUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.filterGroups
import io.github.mangi.eta.ui.model.modelCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 聊天首页顶栏的复合标题与模型切换入口：
 * - 新对话：居中呈现轻盈的模型胶囊 `[ ✦ 模型名 ▾ ]`
 * - 对话进行中：上行会话标题，下行紧凑模型副标题，整块可点切换模型
 * - 角色对话：左侧头像直达角色详情，右侧双行展示角色名与当前模型
 */
@Composable
internal fun AgentTopBarChatHeader(
    conversationTitle: String?,
    character: RoleplayChatUi?,
    state: AgentModelPickerUiState,
    isStreaming: Boolean,
    onModelSelected: (String) -> Unit,
    onOpenCharacter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var sheetSession by remember { mutableIntStateOf(0) }
    val selected = state.selectedModel
    val enabled = !isStreaming && !state.isChanging && state.providerGroups.isNotEmpty()
    LaunchedEffect(enabled) {
        if (!enabled) {
            showPopup = false
            showSheet = false
        }
    }

    val currentModel = selected?.displayName ?: stringResource(R.string.model_not_selected)
    val switchModelDescription = stringResource(R.string.model_switch_current, currentModel)
    val anchor = rememberCouiMenuAnchor()
    val uiStyle = LocalUiStyle.current
    val isCoui = uiStyle == AppearanceUiStyle.COUI
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val ink = MiuixTheme.colorScheme.onSurface.copy(alpha = 1f)
    val subtitleColor = ink.copy(alpha = if (isDark) 0.56f else 0.52f)
    val chevronColor = ink.copy(alpha = if (isDark) 0.46f else 0.42f)
    val interactionSource = remember { MutableInteractionSource() }

    val triggerModelPicker: () -> Unit = {
        if (state.modelCount <= ModelPopupLimit) {
            anchor.open { showPopup = true }
        } else {
            sheetSession += 1
            showSheet = true
        }
    }

    Box(modifier = modifier) {
        when {
            character != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenCharacter)
                            .padding(2.dp),
                    ) {
                        CharacterAvatar(
                            name = character.characterName,
                            avatarPath = character.avatarPath,
                            size = 30.dp,
                            shape = CircleShape,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(
                        modifier = Modifier
                            .couiMenuAnchor(anchor)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isCoui) {
                                    Modifier.couiButtonFeedback(isDark, interactionSource, enabled = enabled)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = enabled,
                                role = Role.Button,
                                onClick = triggerModelPicker,
                            )
                            .semantics { contentDescription = switchModelDescription }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = character.characterName,
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ModelSubtitleRow(
                            selected = selected,
                            modelName = currentModel,
                            textColor = subtitleColor,
                            chevronColor = chevronColor,
                            showChevron = enabled,
                        )
                    }
                }
            }

            !conversationTitle.isNullOrBlank() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .couiMenuAnchor(anchor)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isCoui) {
                                Modifier.couiButtonFeedback(isDark, interactionSource, enabled = enabled)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = triggerModelPicker,
                        )
                        .semantics { contentDescription = switchModelDescription }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = conversationTitle,
                        style = MiuixTheme.textStyles.body1.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ModelSubtitleRow(
                        selected = selected,
                        modelName = currentModel,
                        textColor = subtitleColor,
                        chevronColor = chevronColor,
                        showChevron = enabled,
                    )
                }
            }

            else -> {
                val pillFill = ink.copy(alpha = if (isDark) 0.08f else 0.042f)
                val pillBorder = ink.copy(alpha = if (isDark) 0.12f else 0.07f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .couiMenuAnchor(anchor)
                        .heightIn(min = 34.dp)
                        .clip(CircleShape)
                        .background(pillFill, CircleShape)
                        .border(0.5.dp, pillBorder, CircleShape)
                        .then(
                            if (isCoui) {
                                Modifier.couiButtonFeedback(isDark, interactionSource, enabled = enabled)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = triggerModelPicker,
                        )
                        .semantics { contentDescription = switchModelDescription }
                        .padding(start = 11.dp, end = if (enabled) 8.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    ModelBrandMark(
                        modelId = selected?.modelId,
                        sourceType = selected?.providerSourceType,
                        size = 15.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentModel,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    if (enabled) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = chevronColor,
                        )
                    }
                }
            }
        }

        when (uiStyle) {
            AppearanceUiStyle.COUI -> {
                val menuItems = remember(state.providerGroups, state.selectedModel?.id) {
                    val multipleGroups = state.providerGroups.size > 1
                    state.providerGroups.flatMapIndexed { groupIndex, group ->
                        group.models.mapIndexed { modelIndex, model ->
                            CouiMenuItem(
                                value = model.id,
                                text = if (multipleGroups) "${group.providerName} · ${model.displayName}" else model.displayName,
                                selected = model.id == state.selectedModel?.id,
                                dividerBefore = multipleGroups && groupIndex > 0 && modelIndex == 0,
                            )
                        }
                    }
                }
                Menu(
                    expanded = showPopup,
                    onDismissRequest = { showPopup = false },
                    items = menuItems,
                    onItemClick = { item ->
                        showPopup = false
                        onModelSelected(item.value)
                    },
                    anchor = anchor,
                )

                val dark = CouiTheme.isDark
                Dialog(
                    visible = showSheet,
                    onDismissRequest = { showSheet = false },
                    dark = dark,
                    title = stringResource(R.string.model_picker_title),
                    confirmAction = CouiDialogAction(
                        text = stringResource(R.string.action_close),
                        onClick = { showSheet = false },
                    ),
                    placement = CouiDialogPlacement.Bottom,
                    material = rememberCouiDialogMaterial(dark),
                    backdrop = rememberCouiWindowBackdrop(),
                ) {
                    key(sheetSession) {
                        ModelSheetContent(
                            state = state,
                            onModelSelected = { modelId ->
                                showSheet = false
                                onModelSelected(modelId)
                            },
                        )
                    }
                }
            }

            AppearanceUiStyle.MIUIX -> {
                WindowListPopup(
                    show = showPopup,
                    alignment = PopupPositionProvider.Align.Start,
                    onDismissRequest = { showPopup = false },
                    maxHeight = 360.dp,
                    minWidth = ModelPopupWidth,
                ) {
                    ModelQuickList(
                        state = state,
                        onModelSelected = { modelId ->
                            showPopup = false
                            onModelSelected(modelId)
                        },
                    )
                }

                WindowBottomSheet(
                    show = showSheet,
                    title = stringResource(R.string.model_picker_title),
                    onDismissRequest = { showSheet = false },
                    insideMargin = DpSize(0.dp, 0.dp),
                ) {
                    key(sheetSession) {
                        ModelSheetContent(
                            state = state,
                            onModelSelected = { modelId ->
                                showSheet = false
                                onModelSelected(modelId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSubtitleRow(
    selected: AgentModelOptionUi?,
    modelName: String,
    textColor: Color,
    chevronColor: Color,
    showChevron: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 1.dp),
    ) {
        ModelBrandMark(
            modelId = selected?.modelId,
            sourceType = selected?.providerSourceType,
            size = 11.dp,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = modelName,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = chevronColor,
            )
        }
    }
}

@Composable
private fun ModelQuickList(
    state: AgentModelPickerUiState,
    onModelSelected: (String) -> Unit,
) {
    val select = rememberConfirmedSelection(onModelSelected)
    val selectedId = select.pendingId ?: state.selectedModel?.id
    val showLabels = state.providerGroups.size > 1
    Column(
        modifier = Modifier
            .width(ModelPopupWidth)
            .verticalScroll(rememberScrollState())
            .padding(top = if (showLabels) 0.dp else 6.dp, bottom = 6.dp),
    ) {
        state.providerGroups.forEach { group ->
            if (showLabels) ModelProviderLabel(group.providerName)
            group.models.forEach { model ->
                ModelPickerRow(
                    model = model,
                    selected = model.id == selectedId,
                    onClick = { select(model.id) },
                )
            }
        }
    }
}


@Composable
private fun ModelSheetContent(
    state: AgentModelPickerUiState,
    onModelSelected: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 不再做服务商分类筛选：整份列表一次给全，靠分组标题折叠来收敛。
    // 折叠状态按 providerId 记，搜索换关键词时保留，方便边找边收。
    val savedCollapsedProviders by remember { SettingsDataStore.modelPickerCollapsedProvidersFlow() }
        .collectAsState(initial = null)
    val collapsedProviders = savedCollapsedProviders.orEmpty()
    val scope = rememberCoroutineScope()
    val groups = remember(state.providerGroups, query) { state.filterGroups(query) }
    val selectedIndex = groups.indexOfFirst { group -> group.models.any { it.id == state.selectedModel?.id } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    // 弹层刚组合时列表还没测量，initialFirstVisibleItemIndex 会落空，打开后仍停在顶部。
    // 等 groups 就绪后再主动定位一次到当前选中项所在的组。
    LaunchedEffect(groups, state.selectedModel?.id) {
        if (groups.isEmpty()) return@LaunchedEffect
        val index = groups.indexOfFirst { group -> group.models.any { it.id == state.selectedModel?.id } }
        if (index > 0) listState.scrollToItem(index)
    }
    LaunchedEffect(listState) {
        snapshotFlow { query }.drop(1).collect { listState.scrollToItem(0) }
    }
    val select = rememberConfirmedSelection(onModelSelected)
    val selectedId = select.pendingId ?: state.selectedModel?.id
    val density = LocalDensity.current
    val sheetHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.68f
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val context = LocalContext.current
    // 中转站改分组后模型会变，允许在面板里直接重拉，不必再进设置找供应商。
    var refreshingProviders by remember { mutableStateOf(setOf<String>()) }
    // 拉取结果先落到这里等用户勾选，只把选中的模型并入当前服务商。
    var pendingImport by remember { mutableStateOf<RemoteModelImport?>(null) }
    val onRefreshProvider: (AgentModelProviderGroupUi) -> Unit = { group ->
        if (group.providerId !in refreshingProviders) {
            refreshingProviders = refreshingProviders + group.providerId
            scope.launch {
                try {
                    val provider = ProviderRepository.providerById(group.providerId)
                    if (provider == null) {
                        Toast.makeText(context, "服务商已不存在，请重新打开面板", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val catalog = RemoteModelFetcher.fetchCatalog(provider).getOrElse { throwable ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.provider_error,
                                throwable.message ?: throwable.javaClass.simpleName,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@launch
                    }
                    // 先把拉到的目录交给用户勾选，只并入选中的新模型，不再整包拉进来。
                    val candidates = ModelRepository.newRemoteModels(group.providerId, catalog.models)
                    if (candidates.isNotEmpty()) {
                        pendingImport = RemoteModelImport(catalog, candidates)
                        return@launch
                    }
                    val sync = ModelRepository.syncRemoteCatalog(catalog, emptySet())
                    if (sync.applied) {
                        RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                    }
                    Toast.makeText(
                        context,
                        if (sync.applied) {
                            "远端没有新模型，已对齐本地已下线的条目"
                        } else {
                            context.getString(R.string.provider_models_up_to_date)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.provider_error,
                            throwable.message ?: context.getString(R.string.provider_sync_failed),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    refreshingProviders = refreshingProviders - group.providerId
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .then(if (isCoui) Modifier else Modifier.navigationBarsPadding()),
    ) {
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiSearchBarBox(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.ui_search_model_df5586),
                    backgroundColor = Color.Transparent,
                )
            }
            AppearanceUiStyle.MIUIX -> {
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.ui_search_model_df5586),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.page_no_matching_model_found_ae7e96),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                    )
                }
            }
            items(groups, key = { "provider:${it.providerId}" }) { group ->
                // 每个服务商一张卡，卡内首行标注归属与数量，右侧箭头可收起这一组。
                ModelProviderCard(
                    group = group,
                    selectedId = selectedId,
                    collapsed = group.providerId in collapsedProviders,
                    onToggleCollapsed = {
                        val updated = if (group.providerId in collapsedProviders) {
                            collapsedProviders - group.providerId
                        } else {
                            collapsedProviders + group.providerId
                        }
                        scope.launch { SettingsDataStore.setModelPickerCollapsedProviders(updated) }
                    },
                    onSelect = { select(it) },
                    refreshing = group.providerId in refreshingProviders,
                    onRefresh = { onRefreshProvider(group) },
                )
            }
        }
    }

    // 与设置页同一套导入弹窗：勾选后才写入，取消则什么都不改。
    RemoteModelImportSheet(
        pending = pendingImport,
        onDismissRequest = { pendingImport = null },
        onConfirm = { pending, adopted ->
            pendingImport = null
            scope.launch {
                try {
                    val sync = ModelRepository.syncRemoteCatalog(pending.catalog, adopted)
                    if (sync.applied) {
                        RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                    }
                    Toast.makeText(
                        context,
                        when {
                            !sync.applied -> context.getString(
                                R.string.page_the_remote_end_did_not_return_a_usable_conversation__781487,
                            )
                            adopted.isEmpty() -> context.getString(R.string.provider_models_up_to_date)
                            else -> context.resources.getQuantityString(
                                R.plurals.provider_models_added,
                                sync.addedCount,
                                sync.addedCount,
                            )
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.provider_error,
                            throwable.message ?: context.getString(R.string.provider_sync_failed),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )
}

/**
 * 点选后先把对勾移到新模型上，停留片刻再收起弹层，让人看清选中了谁。
 * 弹层收起会让作用域随组合一起离开，未完成的确认随之取消，不会回写已关闭的界面。
 */
private class ConfirmedSelection(
    private val onConfirm: (String) -> Unit,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    var pendingId by mutableStateOf<String?>(null)
        private set

    operator fun invoke(modelId: String) {
        if (pendingId != null) return
        pendingId = modelId
        launch {
            delay(SelectionConfirmMs)
            onConfirm(modelId)
        }
    }
}

@Composable
private fun rememberConfirmedSelection(onConfirm: (String) -> Unit): ConfirmedSelection {
    val scope = rememberCoroutineScope()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    return remember { ConfirmedSelection({ currentOnConfirm(it) }) { block -> scope.launch { block() } } }
}

/**
 * 单个服务商的模型分组卡：卡内首行是「服务商名 (数量) + 折叠箭头」，
 * 收起后只剩这一行；展开时每行是「品牌图标 + 名称 + 能力标签 + 选中勾」。
 */
@Composable
private fun ModelProviderCard(
    group: AgentModelProviderGroupUi,
    selectedId: String?,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onSelect: (String) -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> Column(modifier = Modifier.padding(bottom = 14.dp)) {
            ModelGroupCaption(
                name = group.providerName,
                count = group.models.size,
                collapsed = collapsed,
                onToggle = onToggleCollapsed,
                refreshing = refreshing,
                onRefresh = onRefresh,
            )
            if (!collapsed) {
                // 与侧栏分组一致：展开后缩进排列，不再套卡片和分隔线。
                group.models.forEach { model ->
                    Box(modifier = Modifier.padding(start = 14.dp)) {
                        ModelPickerRow(
                            model = model,
                            selected = model.id == selectedId,
                            onClick = { onSelect(model.id) },
                            horizontalPadding = 4.dp,
                        )
                    }
                }
            }
        }
        AppearanceUiStyle.MIUIX -> Column(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 14.dp),
        ) {
            ModelGroupCaption(
                name = group.providerName,
                count = group.models.size,
                collapsed = collapsed,
                onToggle = onToggleCollapsed,
                refreshing = refreshing,
                onRefresh = onRefresh,
            )
            if (!collapsed) {
                Card {
                    group.models.forEachIndexed { index, model ->
                        ModelPickerRow(
                            model = model,
                            selected = model.id == selectedId,
                            onClick = { onSelect(model.id) },
                        )
                        if (index < group.models.lastIndex) CouiIconDivider()
                    }
                }
            }
        }
    }
}

/** 小弹窗（模型少时）里的服务商小标题。 */
@Composable
private fun ModelProviderLabel(name: String, horizontalPadding: Dp = 20.dp) {
    Text(
        text = name,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

/** 卡内首行：服务商名 + 模型数量，最右侧折叠箭头，整行可点切换展开状态。 */
@Composable
private fun ModelGroupCaption(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val title = name.ifBlank { "未命名服务商" }
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
    val color = if (isCoui) colors.summary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    // 与服务商卡片内图标的左缘对齐，标题不会比图标更靠外。
    val horizontal = if (isCoui) 24.dp else 16.dp
    // 收起时箭头转成朝右（-90°），展开时朝下，转动过程有过渡。
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "provider_chevron",
    )
    // 拉取远端目录时让按钮自转，给出明确的进行中反馈。
    val spin by rememberInfiniteTransition(label = "provider_refresh").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "provider_refresh_angle",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = horizontal)
            .padding(top = 2.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MiuixTheme.textStyles.footnote1,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 拉取当前服务商的远端模型目录：中转站改分组后不必再进设置页。
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !refreshing,
                    role = Role.Button,
                    onClick = onRefresh,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "拉取模型",
                tint = if (refreshing) color.copy(alpha = 0.38f) else color,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (refreshing) spin else 0f),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation),
        )
    }
}

@Composable
private fun ModelPickerRow(
    model: AgentModelOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 8.dp,
) {
    // 选中态只体现在右侧的对勾上，不铺底色：参考图里选中行与普通行背景一致。
    val contentColor = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelBrandMark(
            modelId = model.modelId,
            sourceType = model.providerSourceType,
            size = 24.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = model.displayName,
            style = MiuixTheme.textStyles.body2,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 右侧常驻上下文窗口，方便在长列表里直接对比容量。
        val contextLabel = formatContextWindowLabel(model.contextWindow)
        if (contextLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = contextLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ui_current_model_a0af8f),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun ModelBrandMark(
    modelId: String?,
    sourceType: String?,
    size: Dp,
) {
    val logo = modelOrProviderBrandLogoRes(modelId, sourceType)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

private val ModelPopupWidth = 248.dp

/** 不超过这个数量时弹窗一眼就能看完，不需要搜索。 */
private const val ModelPopupLimit = 8

private const val SelectionConfirmMs = 140L

/** 上下文窗口的紧凑写法：128K / 200K / 1M，未知或非正数返回 null 不占位。 */
internal fun formatContextWindowLabel(tokens: Int?): String? {
    val value = tokens?.takeIf { it > 0 } ?: return null
    return when {
        value >= 1_000_000 -> {
            val millions = value / 1_000_000.0
            if (millions % 1.0 == 0.0) "${millions.toInt()}M" else "%.1fM".format(Locale.US, millions)
        }
        value >= 1000 -> {
            val thousands = value / 1000.0
            if (thousands % 1.0 == 0.0) "${thousands.toInt()}K" else "%.1fK".format(Locale.US, thousands)
        }
        else -> value.toString()
    }
}
