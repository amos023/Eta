@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.navigation.NewProviderType
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.coui.CouiTextField
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun ProviderConnectionFields(
    provider: ProviderSetting,
    draft: ProviderConfigDraft,
    onDraftChange: (ProviderConfigDraft) -> Unit,
    loginState: ProviderLoginState,
    enabled: Boolean,
    browserError: String?,
    onOpenBrowser: () -> Unit,
    onManageUsage: () -> Unit,
    onLogin: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
    showTypeSelector: Boolean = false,
) {
    val context = LocalContext.current
    var apiKeyVisible by remember { mutableStateOf(false) }
    // 字段显隐一律看草稿里选中的类型，而不是初始 provider 实例：
    // 新建流程里切换类型后 provider 实例不变，只有草稿会变。
    val type = draft.providerType
    val isAnthropic = type == NewProviderType.Anthropic
    val isGemini = type == NewProviderType.Gemini
    val isImageOnly = type == NewProviderType.ImageOnly
    // 生图、Anthropic、Gemini 都只认 API Key，这些类型下不出现账号登录入口。
    val oauth = ProviderAuthModes.isOAuth(draft.authMode) && !isImageOnly && !isAnthropic && !isGemini
    val accountMode = if (isImageOnly || isAnthropic || isGemini) null else providerAccountAuthMode(provider)
    // Endpoint 模式跟随协议栈：凡是走 OpenAI 兼容协议（含生图提供商）且用 API Key 登录都显示，
    // 只有 Anthropic / Gemini / 账号登录不显示——它们本身没有 chat completions / responses 之分。
    // 生图提供商固定走 {baseUrl}/images/generations，没有 chat completions / responses 之分，
    // 也不该在「上游格式」里换成 Anthropic 或 Gemini——那些协议不提供生图接口。
    val showEndpointMode = !oauth && !isAnthropic && !isGemini && !isImageOnly
    val showWebSearchSwitch =
        isGemini || (draft.endpointMode == OpenAiEndpointMode.RESPONSES && showEndpointMode)
    // 「生图模型」开关只在 OpenAI 兼容协议下出现：Anthropic / Gemini 没有生图接口，
    // 在它们的页面里给这个开关等于承诺做不到的事。
    // 生图提供商统一从「生图设置 → 添加生图提供商」创建，这里不再暴露开关：
    // 从聊天入口进来的提供商做的是对话配置，给一个只会把页面变成矛盾的开关没有意义。
    val showImageOnlySwitch = false
    // 类型下拉用上游的协议命名；生图模型不在这里，单独一个开关。
    val typeLabels = listOf(
        "OpenAI-compatible",
        "Anthropic Messages",
        "Gemini Interactions",
    )
    val typeIndex = when (type) {
        NewProviderType.Anthropic -> 1
        NewProviderType.Gemini -> 2
        else -> 0
    }
    val onTypeSelected: (Int) -> Unit = { index ->
        onDraftChange(
            draft.withProviderType(
                when (index) {
                    1 -> NewProviderType.Anthropic
                    2 -> NewProviderType.Gemini
                    else -> NewProviderType.OpenAiCompatible
                },
            ),
        )
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            // 顺序：认证方式 → 名称 → Base URL → API Key → 上游格式 → Endpoint 模式 →
            // 生图模型 → 联网搜索。「上游格式」与「Endpoint 模式」紧挨着，统一放在 API Key 之后。
            if (accountMode != null) {
                CouiDropdownPreference(
                    title = "认证方式",
                    items = listOf("API Key", "账号登录"),
                    selectedIndex = if (oauth) 1 else 0,
                    enabled = enabled,
                    onSelectedIndexChange = { index ->
                        onDraftChange(draft.withAuthMode(if (index == 0) ProviderAuthModes.API_KEY else accountMode, provider))
                    },
                )
                Divider()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 12.dp),
            ) {
                CouiTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = stringResource(R.string.ui_name_1be7ae),
                    singleLine = true,
                    enabled = enabled,
                )
            }
            if (oauth) {
                Divider()
                ProviderAccountControls(
                    state = loginState,
                    authMode = draft.authMode,
                    enabled = enabled,
                    browserError = browserError,
                    onOpenBrowser = onOpenBrowser,
                    onManageUsage = onManageUsage,
                    onLogin = onLogin,
                    onDisconnect = onDisconnect,
                    onCancel = onCancel,
                )
            } else {
                Divider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 12.dp),
                ) {
                    CouiTextField(
                        value = draft.baseUrl,
                        onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                        label = "Base URL",
                        singleLine = true,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CouiTextField(
                        value = draft.apiKey,
                        onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                        label = "API Key",
                        singleLine = true,
                        enabled = enabled,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(28.dp)
                                    .clickable(role = Role.Button) { apiKeyVisible = !apiKeyVisible },
                                contentAlignment = Alignment.Center,
                            ) {
                                CouiIcon(
                                    imageVector = if (apiKeyVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = context.getString(
                                        if (apiKeyVisible) R.string.page_hide_bb0e7e else R.string.page_show_71b677,
                                    ),
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.summary,
                                )
                            }
                        },
                    )
                    if (isAnthropic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CouiTextField(
                            value = draft.anthropicVersion,
                            onValueChange = { onDraftChange(draft.copy(anthropicVersion = it)) },
                            label = "anthropic-version",
                            singleLine = true,
                            enabled = enabled,
                        )
                    }
                }
            }
            // 「生图模型」开关紧跟在 API Key 输入区下方，先确定是不是生图提供商，再看协议相关选项。
            if (showImageOnlySwitch) {
                Divider()
                CouiSwitchPreference(
                    title = stringResource(R.string.provider_image_only),
                    summary = stringResource(R.string.provider_image_only_summary),
                    checked = isImageOnly,
                    enabled = enabled,
                    onCheckedChange = { imageOnly ->
                        onDraftChange(
                            draft.withProviderType(
                                if (imageOnly) NewProviderType.ImageOnly else NewProviderType.OpenAiCompatible,
                            ),
                        )
                    },
                )
            }
            if ((showTypeSelector && !isImageOnly) || showEndpointMode) {
                Divider()
                if (showTypeSelector && !isImageOnly) {
                    CouiDropdownPreference(
                        title = stringResource(R.string.provider_type_label),
                        items = typeLabels,
                        selectedIndex = typeIndex,
                        enabled = enabled,
                        onSelectedIndexChange = onTypeSelected,
                    )
                }
                if (showEndpointMode) {
                    if (showTypeSelector && !isImageOnly) {
                        Divider()
                    }
                    CouiDropdownPreference(
                        title = stringResource(R.string.ui_endpoint_mode_3c8546),
                        items = listOf("Chat Completions API", "Responses API"),
                        selectedIndex = if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) 1 else 0,
                        enabled = enabled,
                        summary = context.getString(
                            if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) {
                                R.string.page_using_typed_items_with_semantic_streaming_events_f9c906
                            } else {
                                R.string.page_use_standard_chat_completions_ee4b1a
                            },
                        ),
                        onSelectedIndexChange = { index ->
                            onDraftChange(
                                draft.copy(
                                    endpointMode = if (index == 1) OpenAiEndpointMode.RESPONSES else OpenAiEndpointMode.CHAT_COMPLETIONS,
                                ),
                            )
                        },
                    )
                }
            }
            if (showWebSearchSwitch) {
                Divider()
                CouiSwitchPreference(
                    title = if (isGemini) "Google Search" else stringResource(R.string.ui_server_side_web_search_ddb8e0),
                    summary = if (isGemini) {
                        null
                    } else {
                        stringResource(R.string.ui_allows_the_model_to_call_web_searches_provided_by_th_2f752f)
                    },
                    checked = draft.hostedWebSearchEnabled,
                    enabled = enabled,
                    onCheckedChange = { onDraftChange(draft.copy(hostedWebSearchEnabled = it)) },
                )
            }
        }

        AppearanceUiStyle.MIUIX -> {
            // 与 COUI 一致：上游格式 → Endpoint 模式 → 认证方式 依次排列，
            // 名称 / Base URL / API Key 在同一输入区连续显示。
            if (showTypeSelector && !isImageOnly) {
                WindowSpinnerPreference(
                    title = stringResource(R.string.provider_type_label),
                    items = typeLabels.map { DropdownItem(text = it) },
                    selectedIndex = typeIndex,
                    enabled = enabled,
                    onSelectedIndexChange = onTypeSelected,
                )
            }
            if (showEndpointMode) {
                WindowSpinnerPreference(
                    title = stringResource(R.string.ui_endpoint_mode_3c8546),
                    items = listOf(DropdownItem(text = "Chat Completions API"), DropdownItem(text = "Responses API")),
                    selectedIndex = if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) 1 else 0,
                    enabled = enabled,
                    summary = context.getString(
                        if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) R.string.page_using_typed_items_with_semantic_streaming_events_f9c906
                        else R.string.page_use_standard_chat_completions_ee4b1a,
                    ),
                    onSelectedIndexChange = { index ->
                        onDraftChange(draft.copy(endpointMode = if (index == 1) OpenAiEndpointMode.RESPONSES else OpenAiEndpointMode.CHAT_COMPLETIONS))
                    },
                )
            }
            if (accountMode != null) {
                WindowSpinnerPreference(
                    title = "认证方式",
                    items = listOf(DropdownItem(text = "API Key"), DropdownItem(text = "账号登录")),
                    selectedIndex = if (oauth) 1 else 0,
                    enabled = enabled,
                    onSelectedIndexChange = { index ->
                        onDraftChange(draft.withAuthMode(if (index == 0) ProviderAuthModes.API_KEY else accountMode, provider))
                    },
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = stringResource(R.string.ui_name_1be7ae),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (oauth) {
                ProviderAccountControls(
                    state = loginState,
                    authMode = draft.authMode,
                    enabled = enabled,
                    browserError = browserError,
                    onOpenBrowser = onOpenBrowser,
                    onManageUsage = onManageUsage,
                    onLogin = onLogin,
                    onDisconnect = onDisconnect,
                    onCancel = onCancel,
                )
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextField(
                        value = draft.baseUrl,
                        onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                        label = "Base URL",
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = draft.apiKey,
                        onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                        label = "API Key",
                        singleLine = true,
                        enabled = enabled,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = context.getString(if (apiKeyVisible) R.string.page_hide_bb0e7e else R.string.page_show_71b677),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isAnthropic) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = draft.anthropicVersion,
                            onValueChange = { onDraftChange(draft.copy(anthropicVersion = it)) },
                            label = "anthropic-version",
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (showImageOnlySwitch) {
                SwitchPreference(
                    title = stringResource(R.string.provider_image_only),
                    summary = stringResource(R.string.provider_image_only_summary),
                    checked = isImageOnly,
                    enabled = enabled,
                    onCheckedChange = { imageOnly ->
                        onDraftChange(
                            draft.withProviderType(
                                if (imageOnly) NewProviderType.ImageOnly else NewProviderType.OpenAiCompatible,
                            ),
                        )
                    },
                )
            }
            if (showWebSearchSwitch) {
                SwitchPreference(
                    title = if (isGemini) "Google Search" else stringResource(R.string.ui_server_side_web_search_ddb8e0),
                    summary = if (isGemini) null
                    else stringResource(R.string.ui_allows_the_model_to_call_web_searches_provided_by_th_2f752f),
                    checked = draft.hostedWebSearchEnabled,
                    enabled = enabled,
                    onCheckedChange = { onDraftChange(draft.copy(hostedWebSearchEnabled = it)) },
                )
            }
        }
    }
}
