package io.github.mangi.eta.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiTextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生图设置页里的「添加生图提供商」表单。
 *
 * 只收三个必须项：名称、Base URL、API Key。提交后直接创建 imageOnly 提供商并立刻拉取，
 * 拉取链路会自动丢掉聊天模型，只留生图模型，所以这里不需要再选「只要生图模型」，
 * 也不用跳到完整的提供商页面去填一堆用不上的字段。
 */
@Composable
internal fun ImageProviderSetupContent(
    name: String,
    onNameChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    enabled: Boolean,
    nameLabel: String,
    baseUrlLabel: String,
    apiKeyLabel: String,
    hint: String,
    message: String?,
    onConfirm: (() -> Unit)? = null,
    confirmLabel: String = "",
    confirmEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 弹窗自身没有内边距，这里左右各留 18dp，输入框才不会贴到卡片边缘。
            .padding(horizontal = 18.dp),
    ) {
        CouiTextField(
            value = name,
            onValueChange = onNameChange,
            label = nameLabel,
            singleLine = true,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CouiTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = baseUrlLabel,
            singleLine = true,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CouiTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = apiKeyLabel,
            singleLine = true,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = hint,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        if (onConfirm != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = confirmLabel,
                style = MiuixTheme.textStyles.body2,
                color = if (confirmEnabled) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = confirmEnabled, onClick = onConfirm)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
