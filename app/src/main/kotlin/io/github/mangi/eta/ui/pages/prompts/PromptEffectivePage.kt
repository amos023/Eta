package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.prompt.PromptCustomization
import io.github.mangi.eta.agent.prompt.PromptCustomizationMode
import io.github.mangi.eta.agent.prompt.PromptCustomizationStore
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage

/**
 * 生效内容页：直接给出注入到 system 提示里的最终形态。
 *
 * 这是「怎么知道生效没」的答案：看到标签、版本号与原文，
 * 就说明这段内容确实进了请求。
 */
@Composable
internal fun PromptEffectivePage(onBack: () -> Unit) {
    val customization = remember { PromptCustomizationStore.load() }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val colors = CouiPageDefaults.themeColors()
    val segments = remember(customization) { buildSegments(customization) }

    CouiScaffoldPage(
        title = stringResource(R.string.prompt_effective_title),
        onBack = onBack,
    ) {
        Group(title = stringResource(R.string.prompt_effective_state)) {
            CouiPreference(
                title = stringResource(R.string.prompt_effective_active),
                value = if (customization.active) {
                    stringResource(R.string.prompt_effective_yes)
                } else {
                    stringResource(R.string.prompt_effective_no)
                },
                summary = stringResource(
                    R.string.prompt_effective_meta,
                    customization.version,
                    customization.revision,
                ),
            )
            CouiIconDivider()
            CouiPreference(
                title = stringResource(R.string.prompt_effective_mode),
                value = stringResource(
                    if (customization.mode == PromptCustomizationMode.REPLACE) {
                        R.string.prompt_mode_replace
                    } else {
                        R.string.prompt_mode_append
                    },
                ),
            )
        }

        Group(title = stringResource(R.string.prompt_effective_custom)) {
            if (segments.isEmpty()) {
                CouiPreference(
                    title = stringResource(R.string.prompt_effective_empty),
                    summary = null,
                )
            } else {
                segments.forEachIndexed { index, (key, label, content) ->
                    if (index > 0) CouiIconDivider()
                    val open = expandedKey == key
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CouiPreference(
                            title = "${index + 1}. $label",
                            summary = "${content.length} 字",
                            value = if (open) "收起" else "展开",
                            onClick = { expandedKey = if (open) null else key },
                        )
                        if (open) {
                            Text(
                                text = content,
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = CouiPageDefaults.rowPadding,
                                        end = CouiPageDefaults.rowPadding,
                                        bottom = 12.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildSegments(
    customization: PromptCustomization,
): List<Triple<String, String, String>> = buildList {
    val identity = customization.baseSystemPrompt.trim()
    if (identity.isNotEmpty()) {
        add(
            Triple(
                "custom_identity",
                "custom_identity（主身份）",
                "<custom_identity version=\"${customization.version}\" revision=\"${customization.revision}\">\n" +
                    identity +
                    "\n</custom_identity>",
            ),
        )
    }
    val name = customization.personaName.trim()
    val style = customization.personaStyle.trim()
    if (name.isNotEmpty() || style.isNotEmpty()) {
        add(
            Triple(
                "custom_persona",
                "custom_persona（人格）",
                buildString {
                    appendLine("<custom_persona version=\"${customization.version}\">")
                    if (name.isNotEmpty()) appendLine("名称：$name")
                    if (style.isNotEmpty()) appendLine(style)
                    append("</custom_persona>")
                },
            ),
        )
    }
    val instruction = customization.appendInstructions.trim()
    if (instruction.isNotEmpty()) {
        add(
            Triple(
                "custom_instructions",
                "custom_instructions（追加指令）",
                "<custom_instructions version=\"${customization.version}\" revision=\"${customization.revision}\">\n" +
                    instruction +
                    "\n</custom_instructions>",
            ),
        )
    }
}
