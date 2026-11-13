package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.typeLabel
import io.github.mangi.eta.data.model.isImageOnlyProvider
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.navigation.NewProviderType
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModelProviderListScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    var searchQuery by remember { mutableStateOf("") }
    var providerToDelete by remember { mutableStateOf<ProviderSetting?>(null) }
    var sorting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
    }

    val selectProvider: (String) -> Unit = { providerId ->
        scope.launch {
            RuntimeConfigRepository.setSelectedProviderId(providerId)
            RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
        }
    }

    val filteredProviders = remember(providers, searchQuery) {
        val query = searchQuery.trim()
        providers.filter { provider ->
            query.isBlank() ||
                provider.name.contains(query, ignoreCase = true) ||
                provider.baseUrl.contains(query, ignoreCase = true) ||
                provider.typeLabel.contains(query, ignoreCase = true)
        }
    }
    // 零模型的提供商通常只是占位或还没配好，和有模型的混在一张卡里很难扫读；
    // 拆成两个分组，卡片之间自然出现间距。分组内保持原有 sortOrder 顺序。
    // 判据不能只看「有没有模型」：内置提供商自带预置模型，没填 API Key 也会被算成
    // 已配置，把真正配置好的挤下去。要求同时具备可用凭据才算已配置。
    val configuredProviders = remember(filteredProviders) {
        filteredProviders.filter { it.models.isNotEmpty() && it.hasUsableCredentials() }
    }
    val unconfiguredProviders = remember(filteredProviders) {
        filteredProviders.filter { it.models.isEmpty() || !it.hasUsableCredentials() }
    }
    // 已配置的服务商里，把生图专用的单独拎出来：生图和对话混在一张卡里很难扫读。
    // 判据是「启用的模型全是生图模型」，中转站同时给文字和图像时仍留在对话分组。
    val configuredImageProviders = remember(configuredProviders) {
        configuredProviders.filter { it.isImageOnlyProvider }
    }
    val configuredChatProviders = remember(configuredProviders) {
        configuredProviders.filter { !it.isImageOnlyProvider }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            CouiScaffoldPage(
                title = stringResource(R.string.ui_model_provider_e8c7f5),
                onBack = onBack,
                actions = {
                    CouiTopAppBarIconButton(
                        icon = Icons.Rounded.Sort,
                        contentDescription = stringResource(R.string.provider_sort_title),
                        enabled = providers.isNotEmpty(),
                        onClick = { sorting = true },
                    )
                },
            ) {
                CouiSearchBarBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.ui_search_provider_74e049),
                )

                ProviderSection(title = stringResource(R.string.ui_add_new_provider_74df54)) {
                    CouiPreference(
                        title = "从 models.dev 添加",
                        summary = "浏览 models.dev 社区目录并选择模型",
                        icon = {
                            CouiSettingsIcon(
                                icon = Icons.Rounded.TravelExplore,
                                tint = colors.accent,
                            )
                        },
                        onClick = { onNavigate(AppRoute.CommunityCatalog) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = stringResource(R.string.ui_added_openai_compatible_6bd471),
                        summary = stringResource(R.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02),
                        icon = { ProviderBrandIcon(ProviderSourceTypes.OPENAI) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = stringResource(R.string.ui_new_anthropic_db6098),
                        summary = stringResource(R.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80),
                        icon = { ProviderBrandIcon(ProviderSourceTypes.ANTHROPIC) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Anthropic)) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = "新增 Gemini",
                        summary = "使用 API Key 连接原生 Interactions API",
                        icon = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = "新增生图模型",
                        summary = "只拉取生图模型，聊天模型自动排除",
                        icon = {
                            CouiSettingsIcon(
                                icon = Icons.Rounded.Image,
                                tint = colors.accent,
                            )
                        },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.ImageOnly)) },
                    )
                }

                if (filteredProviders.isEmpty()) {
                    ProviderSection(
                        title = pluralStringResource(
                            R.plurals.provider_configured_count,
                            filteredProviders.size,
                            filteredProviders.size,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiText(
                                text = if (searchQuery.isBlank()) {
                                    stringResource(R.string.provider_empty)
                                } else {
                                    stringResource(R.string.provider_no_matches)
                                },
                                kind = CouiTextKind.PreferenceSummary,
                                color = colors.summary,
                            )
                        }
                    }
                } else {
                    if (configuredChatProviders.isNotEmpty()) {
                        ProviderSection(
                            title = pluralStringResource(
                                R.plurals.provider_configured_count,
                                configuredChatProviders.size,
                                configuredChatProviders.size,
                            ),
                        ) {
                            ProviderGroupRows(
                                providers = configuredChatProviders,
                                selectedProviderId = selectedProviderId,
                                onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                onDelete = { providerToDelete = it },
                                onSelect = selectProvider,
                            )
                        }
                    }
                    if (configuredImageProviders.isNotEmpty()) {
                        ProviderSection(
                            title = stringResource(
                                R.string.provider_configured_image_count,
                                configuredImageProviders.size,
                            ),
                        ) {
                            ProviderGroupRows(
                                providers = configuredImageProviders,
                                selectedProviderId = selectedProviderId,
                                onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                onDelete = { providerToDelete = it },
                                onSelect = selectProvider,
                            )
                        }
                    }
                    if (unconfiguredProviders.isNotEmpty()) {
                        ProviderSection(
                            title = stringResource(
                                R.string.provider_unconfigured_count,
                                unconfiguredProviders.size,
                            ),
                        ) {
                            ProviderGroupRows(
                                providers = unconfiguredProviders,
                                selectedProviderId = selectedProviderId,
                                onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                onDelete = { providerToDelete = it },
                                onSelect = selectProvider,
                            )
                        }
                    }
                }

                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.ui_model_provider_e8c7f5),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { sorting = true }, enabled = providers.isNotEmpty()) {
                        Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.provider_sort_title))
                    }
                },
            ) {
                item(key = "search") {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        label = stringResource(R.string.ui_search_provider_74e049),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                item(key = "create_section") {
                    ProviderSection(title = stringResource(R.string.ui_add_new_provider_74df54)) {
                        ArrowPreference(
                            title = "从 models.dev 添加",
                            summary = "浏览 models.dev 社区目录并选择模型",
                            startAction = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = { onNavigate(AppRoute.CommunityCatalog) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_added_openai_compatible_6bd471),
                            summary = stringResource(R.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02),
                            startAction = {
                                ProviderBrandIcon(ProviderSourceTypes.OPENAI)
                            },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_new_anthropic_db6098),
                            summary = stringResource(R.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80),
                            startAction = {
                                ProviderBrandIcon(ProviderSourceTypes.ANTHROPIC)
                            },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Anthropic)) },
                        )
                        ArrowPreference(
                            title = "新增 Gemini",
                            summary = "使用 API Key 连接原生 Interactions API",
                            startAction = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
                        )
                        ArrowPreference(
                            title = "新增生图模型",
                            summary = "只拉取生图模型，聊天模型自动排除",
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.ImageOnly)) },
                        )
                    }
                }

                item(key = "list_section") {
                    if (filteredProviders.isEmpty()) {
                        ProviderSection(
                            title = pluralStringResource(
                                R.plurals.provider_configured_count,
                                filteredProviders.size,
                                filteredProviders.size,
                            ),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        stringResource(R.string.provider_empty)
                                    } else {
                                        stringResource(R.string.provider_no_matches)
                                    },
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        if (configuredChatProviders.isNotEmpty()) {
                            ProviderSection(
                                title = pluralStringResource(
                                    R.plurals.provider_configured_count,
                                    configuredChatProviders.size,
                                    configuredChatProviders.size,
                                ),
                            ) {
                                ProviderGroupRows(
                                    providers = configuredChatProviders,
                                    selectedProviderId = selectedProviderId,
                                    onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                    onDelete = { providerToDelete = it },
                                    onSelect = selectProvider,
                                )
                            }
                        }
                        if (configuredImageProviders.isNotEmpty()) {
                            ProviderSection(
                                title = stringResource(
                                    R.string.provider_configured_image_count,
                                    configuredImageProviders.size,
                                ),
                            ) {
                                ProviderGroupRows(
                                    providers = configuredImageProviders,
                                    selectedProviderId = selectedProviderId,
                                    onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                    onDelete = { providerToDelete = it },
                                    onSelect = selectProvider,
                                )
                            }
                        }
                        if (unconfiguredProviders.isNotEmpty()) {
                            ProviderSection(
                                title = stringResource(
                                    R.string.provider_unconfigured_count,
                                    unconfiguredProviders.size,
                                ),
                            ) {
                                ProviderGroupRows(
                                    providers = unconfiguredProviders,
                                    selectedProviderId = selectedProviderId,
                                    onOpen = { onNavigate(AppRoute.ModelProviderDetail(it)) },
                                    onDelete = { providerToDelete = it },
                                    onSelect = selectProvider,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (sorting) {
        val onApply: (ProviderSortRule) -> Unit = { rule ->
            sorting = false
            // 重排针对全部提供商；带着搜索结果排会把未命中项挤到末尾，先清空搜索更符合预期。
            searchQuery = ""
            scope.launch {
                ProviderRepository.reorderProviders(providers.sortedByRule(rule).map { it.id })
                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
            }
        }
        ProviderSortDialog(onDismissRequest = { sorting = false }, onSelect = onApply)
    }

    if (providerToDelete != null) {
        val target = providerToDelete
        val onConfirmDelete: () -> Unit = {
            scope.launch {
                target?.let { provider ->
                    ProviderRepository.deleteProvider(provider.id)
                    RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                }
                providerToDelete = null
            }
        }
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiConfirmDialog(
                    visible = true,
                    title = stringResource(R.string.ui_remove_provider_9f848f),
                    message = stringResource(R.string.provider_delete_summary, target?.name.orEmpty()),
                    confirmText = stringResource(R.string.ui_delete_3755f5),
                    destructive = true,
                    onConfirm = onConfirmDelete,
                    onDismissRequest = { providerToDelete = null },
                )
            }
            AppearanceUiStyle.MIUIX -> {
                OverlayDialog(
                    show = true,
                    title = stringResource(R.string.ui_remove_provider_9f848f),
                    summary = stringResource(R.string.provider_delete_summary, target?.name.orEmpty()),
                    onDismissRequest = { providerToDelete = null },
                ) {
                    MiuixDialogActions(
                        confirmText = stringResource(R.string.ui_delete_3755f5),
                        destructive = true,
                        onCancel = { providerToDelete = null },
                        onConfirm = onConfirmDelete,
                    )
                }
            }
        }
    }
}

/**
 * 一个分组内的提供商行。分隔线只在 COUI 下显式画：
 * MIUIX 的 Card 会自己分隔条目，重复画会出现双线。
 */
@Composable
private fun ProviderGroupRows(
    providers: List<ProviderSetting>,
    selectedProviderId: String?,
    onOpen: (String) -> Unit,
    onDelete: (ProviderSetting) -> Unit,
    onSelect: (String) -> Unit,
) {
    val showDivider = LocalUiStyle.current == AppearanceUiStyle.COUI
    providers.forEachIndexed { index, provider ->
        ProviderListItem(
            provider = provider,
            isSelected = provider.id == selectedProviderId,
            onOpen = { onOpen(provider.id) },
            onDelete = if (!provider.isBuiltIn) {
                { onDelete(provider) }
            } else {
                null
            },
            onSelect = { onSelect(provider.id) },
        )
        if (showDivider && index < providers.lastIndex) {
            CouiIconDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderListItem(
    provider: ProviderSetting,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    val opacity = if (provider.isEnabled) 1f else 0.6f
    val subtitle = listOfNotNull(
        provider.typeLabel,
        pluralStringResource(R.plurals.provider_models_count, provider.models.size, provider.models.size),
        stringResource(R.string.ui_built_in_09ceea).takeIf { provider.isBuiltIn },
        stringResource(R.string.ui_disabled_0fe5a9).takeIf { !provider.isEnabled },
    ).joinToString(" · ")

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            Item(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                    .graphicsLayer { alpha = opacity },
                icon = { ProviderIcon(provider) },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CouiText(
                            text = provider.name,
                            kind = CouiTextKind.PreferenceTitle,
                            color = if (isSelected) colors.accent else colors.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSelected) {
                            TagChip(text = stringResource(R.string.ui_current_25e74d), tone = TagChipTone.Emphasized)
                        }
                    }
                },
                summary = {
                    if (provider.baseUrl.isNotBlank()) {
                        CouiText(
                            text = provider.baseUrl,
                            kind = CouiTextKind.PreferenceSummary,
                            color = colors.summary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CouiText(
                        text = subtitle,
                        kind = CouiTextKind.Description,
                        color = colors.summary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                },
                trailing = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(32.dp)
                            .clickable(
                                enabled = provider.isEnabled,
                                role = Role.RadioButton,
                                onClick = onSelect,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RadioButton(
                            modifier = Modifier.size(24.dp),
                            selected = isSelected,
                            enabled = provider.isEnabled,
                        )
                    }
                },
            )
        }
        AppearanceUiStyle.MIUIX -> {
            BasicComponent(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                    .graphicsLayer { alpha = opacity },
                startAction = { ProviderIcon(provider) },
                endActions = {
                    Checkbox(
                        state = ToggleableState(isSelected),
                        onClick = if (provider.isEnabled) onSelect else null,
                        enabled = provider.isEnabled,
                    )
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = provider.name,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSelected) {
                        TagChip(text = stringResource(R.string.ui_current_25e74d), tone = TagChipTone.Emphasized)
                    }
                }
                Text(
                    text = provider.baseUrl,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 排序方式选择：点选后立即按该规则整理顺序并写入数据库，弹层自动关闭。 */
@Composable
private fun ProviderSortDialog(
    onDismissRequest: () -> Unit,
    onSelect: (ProviderSortRule) -> Unit,
) {
    val dark = CouiTheme.isDark
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            Dialog(
                visible = true,
                onDismissRequest = onDismissRequest,
                dark = dark,
                title = stringResource(R.string.provider_sort_title),
                confirmAction = CouiDialogAction(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissRequest,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
            ) {
                ProviderSortOptions(onSelect = onSelect)
            }
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = true,
                title = stringResource(R.string.provider_sort_title),
                onDismissRequest = onDismissRequest,
            ) {
                ProviderSortOptions(onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun ProviderSortOptions(onSelect: (ProviderSortRule) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ProviderSortRow(
            title = stringResource(R.string.provider_sort_by_name),
            summary = stringResource(R.string.provider_sort_by_name_summary),
            onOpen = { onSelect(ProviderSortRule.NAME) },
        )
        ProviderSortRow(
            title = stringResource(R.string.provider_sort_by_type),
            summary = stringResource(R.string.provider_sort_by_type_summary),
            onOpen = { onSelect(ProviderSortRule.TYPE) },
        )
        ProviderSortRow(
            title = stringResource(R.string.provider_sort_by_status),
            summary = stringResource(R.string.provider_sort_by_status_summary),
            onOpen = { onSelect(ProviderSortRule.STATUS) },
        )
    }
}

@Composable
private fun ProviderSortRow(title: String, summary: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    val colors = CouiPageDefaults.themeColors()
                    CouiText(text = title, kind = CouiTextKind.PreferenceTitle, color = colors.title)
                    CouiText(
                        text = summary,
                        kind = CouiTextKind.PreferenceSummary,
                        color = colors.summary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    Text(text = title, style = MiuixTheme.textStyles.body1)
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/**
 * 是否具备可用的调用凭据。
 *
 * - 填了 API Key：可用。
 * - OAuth 登录（ChatGPT / Grok）：凭据不在 apiKey 字段，按 authMode 判断。
 *
 * 本地代理与内置占位项一律按同一标准判断：没有 Key 就是没配好，
 * 免得「本地地址代理」这类 0 模型的空壳占据已配置分组。
 */
internal fun ProviderSetting.hasUsableCredentials(): Boolean =
    apiKey.isNotBlank() || ProviderAuthModes.isOAuth(authMode)
