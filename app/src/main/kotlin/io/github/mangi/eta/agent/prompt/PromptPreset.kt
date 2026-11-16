package io.github.mangi.eta.agent.prompt

import org.json.JSONObject

/**
 * 一份可导入导出的提示词预设。
 *
 * 只带提示词内容，不带工具开放范围，也不带运行期状态。
 */
internal data class PromptPreset(
    val name: String,
    val description: String = "",
    val mode: PromptCustomizationMode = PromptCustomizationMode.APPEND,
    val baseSystemPrompt: String = "",
    val personaName: String = "",
    val personaStyle: String = "",
    val appendInstructions: String = "",
    val author: String = "",
    val createdAt: Long = 0L,
    val builtin: Boolean = false,
) {
    val hasContent: Boolean
        get() = baseSystemPrompt.isNotBlank() || personaName.isNotBlank() ||
            personaStyle.isNotBlank() || appendInstructions.isNotBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("name", name)
        .put("description", description)
        .put("mode", mode.name)
        .put("base_system_prompt", baseSystemPrompt)
        .put("persona_name", personaName)
        .put("persona_style", personaStyle)
        .put("append_instructions", appendInstructions)
        .put("author", author)
        .put("created_at", createdAt)

    companion object {
        const val SCHEMA = 1
        const val MAX_FIELD_CHARS = 20_000
        const val MAX_BUNDLE_BYTES = 256 * 1024

        /** 解析失败返回 null，由调用方给出明确错误，不静默套用半份配置。 */
        fun fromJson(json: JSONObject): PromptPreset? {
            val schema = json.optInt("schema", SCHEMA)
            if (schema > SCHEMA) return null
            val name = json.optString("name").trim()
            if (name.isEmpty()) return null
            fun field(key: String): String? =
                json.optString(key).takeIf { it.length <= MAX_FIELD_CHARS }
            val base = field("base_system_prompt") ?: return null
            val personaName = field("persona_name") ?: return null
            val personaStyle = field("persona_style") ?: return null
            val append = field("append_instructions") ?: return null
            return PromptPreset(
                name = name.take(60),
                description = json.optString("description").take(280),
                mode = runCatching { PromptCustomizationMode.valueOf(json.optString("mode")) }
                    .getOrDefault(PromptCustomizationMode.APPEND),
                baseSystemPrompt = base,
                personaName = personaName,
                personaStyle = personaStyle,
                appendInstructions = append,
                author = json.optString("author").take(60),
                createdAt = json.optLong("created_at", 0L),
            )
        }
    }
}
