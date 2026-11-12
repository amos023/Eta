package io.github.mangi.eta.data.provider

import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ModelReasoningCapabilities
import io.github.mangi.eta.data.model.ReasoningEffort

/**
 * 自定义服务商（各类中转站）通常只返回模型 id，不带上下文窗口与思考档位。
 * 这里按模型名推断一份默认值，只在远端没给数据时补齐，不覆盖已有值。
 *
 * 匹配规则：取最长前缀，`glm-5.3` 优先于 `glm-5`，`deepseek-v4` 优先于 `deepseek`。
 * 表里的数值是可修改的默认值，与各家实际能力不一定完全一致，以编辑弹窗里手动调整为准。
 */
internal object ModelCapabilityPresets {

    internal data class Preset(
        val contextWindow: Int? = null,
        val efforts: List<ReasoningEffort> = emptyList(),
        val defaultEffort: ReasoningEffort? = null,
        val canDisable: Boolean = false,
        val mandatory: Boolean = false,
    ) {
        fun toCapabilities(): ModelReasoningCapabilities = ModelReasoningCapabilities(
            supportedEfforts = efforts,
            defaultEffort = defaultEffort,
            defaultEnabled = true,
            mandatory = mandatory,
            canDisable = canDisable,
        )
    }

    private val entries: List<Pair<String, Preset>> = listOf(
        "glm-5.3" to Preset(
            contextWindow = 200_000,
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            defaultEffort = ReasoningEffort.HIGH,
            canDisable = true,
        ),
        "glm-5" to Preset(
            contextWindow = 200_000,
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            defaultEffort = ReasoningEffort.HIGH,
            canDisable = true,
        ),
        "glm-4" to Preset(
            contextWindow = 128_000,
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            canDisable = true,
        ),
        "qwen3.8" to Preset(
            contextWindow = 1_000_000,
            efforts = listOf(
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH,
            ),
            defaultEffort = ReasoningEffort.MEDIUM,
            canDisable = true,
        ),
        "qwen3.7" to Preset(
            contextWindow = 256_000,
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            defaultEffort = ReasoningEffort.MEDIUM,
            canDisable = true,
        ),
        "deepseek-v4" to Preset(
            contextWindow = 128_000,
            efforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
            defaultEffort = ReasoningEffort.HIGH,
            canDisable = true,
        ),
        "deepseek" to Preset(
            contextWindow = 128_000,
            efforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
            defaultEffort = ReasoningEffort.HIGH,
            canDisable = true,
        ),
        "kimi-k3" to Preset(
            contextWindow = 256_000,
            efforts = listOf(ReasoningEffort.MAX),
            defaultEffort = ReasoningEffort.MAX,
            mandatory = true,
        ),
        "kimi-k2" to Preset(
            contextWindow = 256_000,
            canDisable = true,
        ),
        "minimax" to Preset(
            contextWindow = 200_000,
            mandatory = true,
        ),
        "step-" to Preset(
            contextWindow = 256_000,
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            defaultEffort = ReasoningEffort.MEDIUM,
            mandatory = true,
        ),
    )

    fun forModel(modelId: String): Preset? {
        val model = modelId.trim().lowercase()
        if (model.isBlank()) return null
        return entries
            .filter { model.startsWith(it.first) }
            .maxByOrNull { it.first.length }
            ?.second
    }
}

/**
 * 远端没给能力数据时按预设补齐；已有值一律保留。
 */
internal fun Model.withPresetCapabilities(): Model {
    if (contextWindow != null && reasoningCapabilities != null) return this
    val preset = ModelCapabilityPresets.forModel(modelId) ?: return this
    return copy(
        contextWindow = contextWindow ?: preset.contextWindow,
        reasoningCapabilities = reasoningCapabilities ?: preset.toCapabilities(),
        reasoning = reasoning ?: true,
    )
}
