package io.github.mangi.eta.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.CustomProviderSetting
import io.github.mangi.eta.data.model.ImageGenerationSettings
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.ImageGenerationRepository
import io.github.mangi.eta.data.repository.ModelRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RemoteModelFetcher
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.navigation.NewProviderType
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.pages.providers.RemoteModelImport
import io.github.mangi.eta.ui.pages.providers.RemoteModelImportSheet
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生图比例到默认尺寸的映射，顺序即下拉顺序。
 *
 * 服务商接口只认 `size` 像素串，所以比例只是一个快捷档位：
 * 选中比例就把尺寸切成该比例的首选像素，落库的始终是尺寸。
 */
private val ASPECT_DEFAULT_SIZES = linkedMapOf(
    "auto" to "auto",
    "1:1" to "1024x1024",
    "3:2" to "1536x1024",
    "2:3" to "1024x1536",
    "4:3" to "1024x768",
    "3:4" to "768x1024",
    "16:9" to "1280x720",
    "9:16" to "720x1280",
)

/** 由尺寸反推比例；1:1 有两档像素，其余一档，未识别的一律当自动。 */
private fun aspectOf(size: String): String = when (size.trim().lowercase()) {
    "1024x1024", "512x512" -> "1:1"
    "1536x1024" -> "3:2"
    "1024x1536" -> "2:3"
    "1024x768" -> "4:3"
    "768x1024" -> "3:4"
    "1280x720" -> "16:9"
    "720x1280" -> "9:16"
    else -> "auto"
}

/**
 * 生图设置。
 *
 * 与输入栏的生图 chip 共用同一份 [ImageGenerationSettings]：这里改完，chip 菜单里的
 * 尺寸与数量立刻跟着变。比例不单独落库，只用来快速把尺寸切到该比例的代表档位。
 */
@Composable
internal fun ImageGenerationSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val settings by ImageGenerationRepository.settingsFlow()
        .collectAsState(initial = ImageGenerationSettings())

    val groups = remember(providers) { AgentModelPickerProjector.projectImageModels(providers) }
    // 这里列出的提供商与「模型提供商」页是同一批对象，点进去就是标准详情页，
    // 区别只是筛选出带生图能力的那些。
    val imageProviders = remember(providers) {
        providers.filter { provider ->
            provider.isEnabled &&
                (provider.imageOnly || provider.models.any { model -> model.supportsImageGeneration })
        }
    }
    val imageModels = remember(groups) {
        groups.flatMap { it.models }.sortedWith(compareBy { it.displayName.lowercase() })
    }

    val autoLabel = stringResource(R.string.image_aspect_auto)
    val noneLabel = stringResource(R.string.image_settings_none)

    val modelItems = remember(imageModels, noneLabel) {
        listOf(noneLabel) + imageModels.map { it.displayName }
    }
    val modelIndex = remember(imageModels, settings.modelId) {
        val index = imageModels.indexOfFirst { it.id == settings.modelId }
        if (index >= 0) index + 1 else 0
    }
    val aspectItems = remember(autoLabel) {
        ASPECT_DEFAULT_SIZES.keys.map { key -> if (key == "auto") autoLabel else key }
    }
    val aspectIndex = remember(settings.size, autoLabel) {
        ASPECT_DEFAULT_SIZES.keys.indexOf(aspectOf(settings.size)).takeIf { it >= 0 } ?: 0
    }
    val sizeItems = remember(autoLabel) {
        ImageGenerationSettings.SIZE_PRESETS.map { size -> if (size == "auto") autoLabel else size }
    }
    val sizeIndex = remember(settings.size) {
        ImageGenerationSettings.SIZE_PRESETS.indexOf(settings.size).takeIf { it >= 0 } ?: 0
    }
    val qualityAuto = stringResource(R.string.image_quality_auto)
    val qualityStandard = stringResource(R.string.image_quality_standard)
    val qualityHd = stringResource(R.string.image_quality_hd)
    val qualityHigh = stringResource(R.string.image_quality_high)
    val qualityItems = remember(qualityAuto, qualityStandard, qualityHd, qualityHigh) {
        ImageGenerationSettings.QUALITY_PRESETS.map { key ->
            when (key) {
                "standard" -> qualityStandard
                "hd" -> qualityHd
                "high" -> qualityHigh
                else -> qualityAuto
            }
        }
    }
    val qualityIndex = remember(settings.quality) {
        ImageGenerationSettings.QUALITY_PRESETS.indexOf(settings.quality).takeIf { it >= 0 } ?: 0
    }

    val onModelChange: (Int) -> Unit = { index ->
        scope.launch {
            ImageGenerationRepository.selectModel(imageModels.getOrNull(index - 1)?.id)
        }
    }
    val onAspectChange: (Int) -> Unit = { index ->
        val key = ASPECT_DEFAULT_SIZES.keys.elementAtOrNull(index)
        val target = key?.let { ASPECT_DEFAULT_SIZES[it] }
        if (target != null) {
            scope.launch { ImageGenerationRepository.selectSize(target) }
        }
    }
    val onSizeChange: (Int) -> Unit = { index ->
        ImageGenerationSettings.SIZE_PRESETS.getOrNull(index)?.let { size ->
            scope.launch { ImageGenerationRepository.selectSize(size) }
        }
    }
    val onQualityChange: (Int) -> Unit = { index ->
        ImageGenerationSettings.QUALITY_PRESETS.getOrNull(index)?.let { quality ->
            scope.launch { ImageGenerationRepository.selectQuality(quality) }
        }
    }

    val context = LocalContext.current
    var showSetup by remember { mutableStateOf(false) }
    var setupName by remember { mutableStateOf("") }
    var setupBaseUrl by remember { mutableStateOf("") }
    var setupApiKey by remember { mutableStateOf("") }
    var setupBusy by remember { mutableStateOf(false) }
    var setupMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<RemoteModelImport?>(null) }
    var providerPendingDelete by remember { mutableStateOf<ProviderSetting?>(null) }

    /** 建好提供商后立刻拉取生图模型并自动选中，不用再回设置里挑一次。 */
    val createAndPull: () -> Unit = {
        if (!setupBusy) {
            val base = setupBaseUrl.trim()
            val key = setupApiKey.trim()
            if (base.isBlank() || key.isBlank()) {
                setupMessage = context.getString(R.string.image_provider_required)
            } else {
                setupBusy = true
                setupMessage = null
                scope.launch {
                    try {
                        val created = ProviderRepository.addProvider(
                            CustomProviderSetting(
                                id = ProviderRepository.newId(),
                                name = setupName.trim().ifBlank {
                                    base.substringAfter("//").substringBefore("/")
                                },
                                baseUrl = base,
                                apiKey = key,
                                endpointMode = OpenAiEndpointMode.CHAT_COMPLETIONS,
                                imageOnly = true,
                            ),
                        )
                        // imageOnly 的拉取链路会自动丢掉聊天模型，只留生图模型；
                        // 拉回来先让用户挑，不要一股脑全写进去。
                        val catalog = RemoteModelFetcher.fetchCatalog(created).getOrElse { throw it }
                        if (catalog.models.isEmpty()) {
                            setupMessage = context.getString(R.string.image_provider_empty)
                        } else {
                            setupName = ""
                            setupBaseUrl = ""
                            setupApiKey = ""
                            showSetup = false
                            pendingImport = RemoteModelImport(
                                catalog = catalog,
                                candidates = catalog.models,
                            )
                        }
                    } catch (throwable: Throwable) {
                        setupMessage = throwable.message
                            ?: context.getString(R.string.image_provider_failed)
                    } finally {
                        setupBusy = false
                    }
                }
            }
        }
    }

    // 没选生图模型但已经有可用模型时，自动兜底选第一个，省掉再去下拉里找。
    LaunchedEffect(settings.modelId, imageModels) {
        val current = settings.modelId
        val valid = current != null && imageModels.any { it.id == current }
        if (!valid) {
            imageModels.firstOrNull()?.let { first ->
                ImageGenerationRepository.selectModel(first.id)
            }
        }
    }

    val pageTitle = stringResource(R.string.image_settings_title)
    val modelGroupTitle = stringResource(R.string.image_settings_group_model)
    val providerGroupTitle = stringResource(R.string.image_settings_group_provider)
    val galleryGroupTitle = stringResource(R.string.image_gallery_entry)
    val galleryEntryLabel = stringResource(R.string.image_gallery_title)
    val galleryEntrySummary = stringResource(R.string.image_gallery_entry_summary)
    val paramsGroupTitle = stringResource(R.string.image_settings_group_params)
    val modelLabel = stringResource(R.string.model_image_generation)
    val aspectLabel = stringResource(R.string.image_aspect_label)
    val sizeLabel = stringResource(R.string.image_size_label)
    val qualityLabel = stringResource(R.string.image_quality_label)
    val qualitySummary = stringResource(R.string.image_quality_summary)
    val noteText = stringResource(R.string.image_settings_note)
    val addProviderLabel = stringResource(R.string.image_provider_add)
    val noProviderLabel = stringResource(R.string.image_provider_none_short)
    val addProviderSummary = stringResource(R.string.image_provider_add_summary)
    val setupHint = stringResource(R.string.image_provider_hint)
    val nameFieldLabel = stringResource(R.string.image_provider_name)
    val baseUrlFieldLabel = stringResource(R.string.image_provider_base_url)
    val apiKeyFieldLabel = stringResource(R.string.image_provider_api_key)
    val confirmLabel = stringResource(R.string.image_provider_confirm)

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = pageTitle,
                onBack = onBack,
                overlay = {
                    val dark = CouiTheme.isDark
                    Dialog(
                        visible = showSetup,
                        onDismissRequest = { if (!setupBusy) showSetup = false },
                        dark = dark,
                        title = addProviderLabel,
                        confirmAction = CouiDialogAction(
                            text = confirmLabel,
                            onClick = { createAndPull() },
                        ),
                        placement = CouiDialogPlacement.Bottom,
                        material = rememberCouiDialogMaterial(dark),
                        backdrop = rememberCouiWindowBackdrop(),
                    ) {
                        ImageProviderSetupContent(
                            name = setupName,
                            onNameChange = { setupName = it },
                            baseUrl = setupBaseUrl,
                            onBaseUrlChange = { setupBaseUrl = it },
                            apiKey = setupApiKey,
                            onApiKeyChange = { setupApiKey = it },
                            enabled = !setupBusy,
                            nameLabel = nameFieldLabel,
                            baseUrlLabel = baseUrlFieldLabel,
                            apiKeyLabel = apiKeyFieldLabel,
                            hint = setupHint,
                            message = setupMessage,
                        )
                    }
                    RemoteModelImportSheet(
                        pending = pendingImport,
                        onDismissRequest = { pendingImport = null },
                        onConfirm = { pending, adopted ->
                            pendingImport = null
                            scope.launch {
                                try {
                                    ModelRepository.syncRemoteCatalog(pending.catalog, adopted)
                                    val fresh = ProviderRepository.providerById(pending.catalog.connection.id)
                                    val first = fresh?.models?.firstOrNull { it.id in adopted }
                                        ?: fresh?.models?.firstOrNull()
                                    first?.let { ImageGenerationRepository.selectModel(it.id) }
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.image_provider_added,
                                            adopted.size,
                                            first?.displayName.orEmpty(),
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } catch (throwable: Throwable) {
                                    Toast.makeText(
                                        context,
                                        throwable.message ?: context.getString(R.string.image_provider_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    )
                },
            ) {
                Group(title = modelGroupTitle) {
                    CouiDropdownPreference(
                        title = modelLabel,
                        items = modelItems,
                        selectedIndex = modelIndex,
                        onSelectedIndexChange = onModelChange,
                    )
                    Divider()
                    CouiPreference(
                        title = addProviderLabel,
                        summary = addProviderSummary,
                        onClick = {
                            setupMessage = null
                            showSetup = true
                        },
                    )
                }
                Group(title = providerGroupTitle) {
                    if (imageProviders.isEmpty()) {
                        CouiPreference(
                            title = noProviderLabel,
                            showChevron = false,
                            enabled = false,
                        )
                    }
                    imageProviders.forEachIndexed { index, provider ->
                        if (index > 0) {
                            Divider()
                        }
                        ImageProviderRow(
                            title = provider.name,
                            summary = provider.baseUrl,
                            onClick = {
                                onNavigate(AppRoute.ModelProviderDetail(provider.id))
                            },
                            onLongClick = { providerPendingDelete = provider },
                        )
                    }
                }
                Group(title = galleryGroupTitle) {
                    CouiPreference(
                        title = galleryEntryLabel,
                        summary = galleryEntrySummary,
                        onClick = { onNavigate(AppRoute.ImageGallery) },
                    )
                }
                Group(title = paramsGroupTitle) {
                    CouiDropdownPreference(
                        title = aspectLabel,
                        items = aspectItems,
                        selectedIndex = aspectIndex,
                        onSelectedIndexChange = onAspectChange,
                    )
                    Divider()
                    CouiDropdownPreference(
                        title = sizeLabel,
                        items = sizeItems,
                        selectedIndex = sizeIndex,
                        onSelectedIndexChange = onSizeChange,
                    )
                    Divider()
                    CouiDropdownPreference(
                        title = qualityLabel,
                        summary = qualitySummary,
                        items = qualityItems,
                        selectedIndex = qualityIndex,
                        onSelectedIndexChange = onQualityChange,
                    )
                }
                Footer(text = noteText)
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = pageTitle,
                onBack = onBack,
                overlay = {
                    OverlayBottomSheet(
                        show = showSetup,
                        title = addProviderLabel,
                        onDismissRequest = { if (!setupBusy) showSetup = false },
                        insideMargin = DpSize(0.dp, 0.dp),
                    ) {
                        ImageProviderSetupContent(
                            name = setupName,
                            onNameChange = { setupName = it },
                            baseUrl = setupBaseUrl,
                            onBaseUrlChange = { setupBaseUrl = it },
                            apiKey = setupApiKey,
                            onApiKeyChange = { setupApiKey = it },
                            enabled = !setupBusy,
                            nameLabel = nameFieldLabel,
                            baseUrlLabel = baseUrlFieldLabel,
                            apiKeyLabel = apiKeyFieldLabel,
                            hint = setupHint,
                            message = setupMessage,
                            onConfirm = { createAndPull() },
                            confirmLabel = confirmLabel,
                            confirmEnabled = !setupBusy,
                        )
                    }
                },
            ) {
                item(key = "image_settings_model_group") {
                    SmallTitle(modelGroupTitle)
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = modelLabel,
                            items = modelItems,
                            selectedIndex = modelIndex,
                            onSelectedIndexChange = onModelChange,
                        )
                        ArrowPreference(
                            title = addProviderLabel,
                            summary = addProviderSummary,
                            onClick = {
                                setupMessage = null
                                showSetup = true
                            },
                        )
                    }
                }
                item(key = "image_settings_provider_group") {
                    SmallTitle(providerGroupTitle)
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        if (imageProviders.isEmpty()) {
                            BasicComponent(
                                title = noProviderLabel,
                                enabled = false,
                            )
                        }
                        imageProviders.forEach { provider ->
                            ImageProviderRow(
                                title = provider.name,
                                summary = provider.baseUrl,
                                onClick = {
                                    onNavigate(AppRoute.ModelProviderDetail(provider.id))
                                },
                                onLongClick = { providerPendingDelete = provider },
                            )
                        }
                    }
                }
                item(key = "image_settings_gallery_group") {
                    SmallTitle(galleryGroupTitle)
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(
                            title = galleryEntryLabel,
                            summary = galleryEntrySummary,
                            onClick = { onNavigate(AppRoute.ImageGallery) },
                        )
                    }
                }
                item(key = "image_settings_params_group") {
                    SmallTitle(paramsGroupTitle)
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = aspectLabel,
                            items = aspectItems,
                            selectedIndex = aspectIndex,
                            onSelectedIndexChange = onAspectChange,
                        )
                        OverlayDropdownPreference(
                            title = sizeLabel,
                            items = sizeItems,
                            selectedIndex = sizeIndex,
                            onSelectedIndexChange = onSizeChange,
                        )
                        OverlayDropdownPreference(
                            title = qualityLabel,
                            items = qualityItems,
                            selectedIndex = qualityIndex,
                            onSelectedIndexChange = onQualityChange,
                        )
                    }
                    Text(
                        text = noteText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }

    CouiConfirmDialog(
        visible = providerPendingDelete != null,
        title = stringResource(R.string.image_provider_delete_title),
        message = providerPendingDelete?.let { provider ->
            stringResource(R.string.image_provider_delete_message, provider.name)
        },
        confirmText = stringResource(R.string.image_provider_delete_action),
        destructive = true,
        onConfirm = {
            val target = providerPendingDelete
            providerPendingDelete = null
            if (target != null) {
                scope.launch { ProviderRepository.deleteProvider(target.id) }
            }
        },
        onDismissRequest = { providerPendingDelete = null },
    )
}

/**
 * 生图提供商列表的一行。
 *
 * CouiPreference 只支持点击，这里要多一个长按删除，所以单独画一行，
 * 内边距与字色沿用 CouiPageDefaults，和上下条目对齐。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageProviderRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
