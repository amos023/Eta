package io.github.mangi.eta.agent.prompt

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** 自定义提示词的生效方式。 */
internal enum class PromptCustomizationMode {
    /** 作为附加约束追加，内置身份段保持原样。 */
    APPEND,

    /** 作为主身份设定前置，并声明优先级高于内置身份描述。 */
    REPLACE,
}

/**
 * 用户自定义的身份、人格与追加指令。
 *
 * [version] 是「历史条目序号」：只在内容真正变化时 +1；清空历史或恢复默认会归零。
 * [revision] 是内容哈希，用于判断同一编号是否被外部改写。
 *
 * 只承载提示词本身，不涉及任何工具开放范围。
 */
internal data class PromptCustomization(
    val enabled: Boolean = false,
    val mode: PromptCustomizationMode = PromptCustomizationMode.APPEND,
    val baseSystemPrompt: String = "",
    val personaName: String = "",
    val personaStyle: String = "",
    val appendInstructions: String = "",
    val version: Int = 0,
    val updatedAt: Long = 0L,
    val history: List<PromptVersionRecord> = emptyList(),
) {
    val revision: String get() = sha256(fingerprint()).take(REVISION_LENGTH)

    val hasContent: Boolean
        get() = baseSystemPrompt.isNotBlank() || personaName.isNotBlank() ||
            personaStyle.isNotBlank() || appendInstructions.isNotBlank()

    /** 真正参与组装的判定：开关打开且至少有一段内容。 */
    val active: Boolean get() = enabled && hasContent

    /** 内容变化才递增版本；只改开关或只点保存不会产生新版本。 */
    fun edit(
        enabled: Boolean = this.enabled,
        mode: PromptCustomizationMode = this.mode,
        baseSystemPrompt: String = this.baseSystemPrompt,
        personaName: String = this.personaName,
        personaStyle: String = this.personaStyle,
        appendInstructions: String = this.appendInstructions,
        now: Long = System.currentTimeMillis(),
    ): PromptCustomization {
        val next = copy(
            enabled = enabled,
            mode = mode,
            baseSystemPrompt = baseSystemPrompt,
            personaName = personaName,
            personaStyle = personaStyle,
            appendInstructions = appendInstructions,
        )
        if (next.fingerprint() == fingerprint()) return next
        return next.copy(
            version = version + 1,
            updatedAt = now,
            history = (listOf(snapshot()) + history).take(HISTORY_LIMIT),
        )
    }

    /** 回滚到某个历史版本：内容取旧值，版本号继续向前，不复用旧编号。 */
    fun restore(
        record: PromptVersionRecord,
        now: Long = System.currentTimeMillis(),
    ): PromptCustomization = copy(
        enabled = record.enabled,
        mode = record.mode,
        baseSystemPrompt = record.baseSystemPrompt,
        personaName = record.personaName,
        personaStyle = record.personaStyle,
        appendInstructions = record.appendInstructions,
        version = version + 1,
        updatedAt = now,
        history = (listOf(snapshot()) + history).take(HISTORY_LIMIT),
    )

    /** 一键恢复系统默认：编号一并归零、历史清空。 */
    fun resetToDefault(now: Long = System.currentTimeMillis()): PromptCustomization {
        if (!hasContent && !enabled && version == 0 && history.isEmpty()) return this
        return PromptCustomization(updatedAt = now)
    }

    /** 只删历史条目，不动当前版本。 */
    fun removeHistory(version: Int): PromptCustomization =
        copy(history = history.filterNot { it.version == version })

    /** 清空历史同时把编号归零。 */
    fun clearHistory(): PromptCustomization = copy(history = emptyList(), version = 0)

    private fun snapshot(): PromptVersionRecord = PromptVersionRecord(
        version = version,
        revision = revision,
        updatedAt = updatedAt,
        enabled = enabled,
        mode = mode,
        baseSystemPrompt = baseSystemPrompt,
        personaName = personaName,
        personaStyle = personaStyle,
        appendInstructions = appendInstructions,
    )

    private fun fingerprint(): String = buildString {
        append(mode.name)
        append('\u0000')
        append(baseSystemPrompt)
        append('\u0000')
        append(personaName)
        append('\u0000')
        append(personaStyle)
        append('\u0000')
        append(appendInstructions)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("mode", mode.name)
        .put("base", baseSystemPrompt)
        .put("persona_name", personaName)
        .put("persona_style", personaStyle)
        .put("append", appendInstructions)
        .put("version", version)
        .put("updated_at", updatedAt)
        .put("history", JSONArray().also { array -> history.forEach { array.put(it.toJson()) } })

    companion object {
        const val HISTORY_LIMIT = 5
        const val REVISION_LENGTH = 12

        fun decode(raw: String?): PromptCustomization =
            if (raw.isNullOrBlank()) {
                PromptCustomization()
            } else {
                runCatching { fromJson(JSONObject(raw)) }.getOrElse { PromptCustomization() }
            }

        fun fromJson(json: JSONObject): PromptCustomization = PromptCustomization(
            enabled = json.optBoolean("enabled", false),
            mode = runCatching { PromptCustomizationMode.valueOf(json.optString("mode")) }
                .getOrDefault(PromptCustomizationMode.APPEND),
            baseSystemPrompt = json.optString("base"),
            personaName = json.optString("persona_name"),
            personaStyle = json.optString("persona_style"),
            appendInstructions = json.optString("append"),
            version = json.optInt("version", 0),
            updatedAt = json.optLong("updated_at", 0L),
            history = json.optJSONArray("history")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(PromptVersionRecord::fromJson)
                }
            }.orEmpty(),
        )

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}

/** 一次历史快照，供回滚使用。 */
internal data class PromptVersionRecord(
    val version: Int,
    val revision: String,
    val updatedAt: Long,
    val enabled: Boolean,
    val mode: PromptCustomizationMode,
    val baseSystemPrompt: String,
    val personaName: String,
    val personaStyle: String,
    val appendInstructions: String,
) {
    val hasContent: Boolean
        get() = baseSystemPrompt.isNotBlank() || personaName.isNotBlank() ||
            personaStyle.isNotBlank() || appendInstructions.isNotBlank()

    val summary: String
        get() = buildString {
            append(if (mode == PromptCustomizationMode.REPLACE) "替换" else "追加")
            append(" · system ${baseSystemPrompt.length} 字")
            if (personaName.isNotBlank()) append(" · 人格 $personaName")
            if (appendInstructions.isNotBlank()) append(" · 追加 ${appendInstructions.length} 字")
        }

    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("revision", revision)
        .put("updated_at", updatedAt)
        .put("enabled", enabled)
        .put("mode", mode.name)
        .put("base", baseSystemPrompt)
        .put("persona_name", personaName)
        .put("persona_style", personaStyle)
        .put("append", appendInstructions)

    companion object {
        fun fromJson(json: JSONObject): PromptVersionRecord = PromptVersionRecord(
            version = json.optInt("version", 0),
            revision = json.optString("revision"),
            updatedAt = json.optLong("updated_at", 0L),
            enabled = json.optBoolean("enabled", false),
            mode = runCatching { PromptCustomizationMode.valueOf(json.optString("mode")) }
                .getOrDefault(PromptCustomizationMode.APPEND),
            baseSystemPrompt = json.optString("base"),
            personaName = json.optString("persona_name"),
            personaStyle = json.optString("persona_style"),
            appendInstructions = json.optString("append"),
        )
    }
}
