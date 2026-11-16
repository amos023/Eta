package io.github.mangi.eta.agent.prompt

import android.content.Context
import io.github.mangi.eta.config.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置预设来自 assets。
 *
 * 只承载提示词内容，不带版本号与历史；
 * 应用一份预设 = 在当前配置上生成一个新版本，不触碰任何工具权限。
 */
internal object PromptPresetRepository {
    private const val BUILTIN_DIR = "prompt_presets"
    private const val KEY_SAVED = "agent_prompt_presets_json"
    private const val MAX_SAVED = 20

    /** 内置清单固定：顺序就是界面顺序，按强度从高到低。 */
    private val BUILTIN_FILES = listOf(
        "moren.json",
        "pozhijei.json",
        "pozhunhe.json",
        "pozhuancheg.json",
        "pozhiqyue.json",
        "pozhixiezuo.json",
    )

    fun builtins(context: Context): List<PromptPreset> =
        BUILTIN_FILES.mapNotNull { file ->
            readAsset(context, "$BUILTIN_DIR/$file")
        }

    fun saved(): List<PromptPreset> {
        val preferences = Prefs.localAgentPreferences() ?: return emptyList()
        val raw = runCatching { preferences.getString(KEY_SAVED, null) }.getOrNull()
            ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(PromptPreset::fromJson)
        }
    }

    /** 导入的预设排在最前，同名覆盖。 */
    fun addImported(preset: PromptPreset): List<PromptPreset> {
        val next = (listOf(preset.copy(builtin = false)) + saved().filterNot { it.name == preset.name })
            .take(MAX_SAVED)
        persist(next)
        return next
    }

    fun imported(): List<PromptPreset> = saved()

    fun upsert(preset: PromptPreset): List<PromptPreset> {
        val existing = saved().filterNot { it.name == preset.name }
        val next = (listOf(preset.copy(builtin = false)) + existing).take(MAX_SAVED)
        persist(next)
        return next
    }

    fun remove(name: String): List<PromptPreset> {
        val next = saved().filterNot { it.name == name }
        persist(next)
        return next
    }

    /** 把预设写进当前配置：内容变化才升版本。 */
    fun applyPreset(
        current: PromptCustomization,
        preset: PromptPreset,
        now: Long = System.currentTimeMillis(),
    ): PromptCustomization = current.edit(
        enabled = preset.hasContent,
        mode = preset.mode,
        baseSystemPrompt = preset.baseSystemPrompt,
        personaName = preset.personaName,
        personaStyle = preset.personaStyle,
        appendInstructions = preset.appendInstructions,
        now = now,
    )

    private fun readAsset(context: Context, assetPath: String): PromptPreset? = runCatching {
        val text = context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
        PromptPreset.fromJson(JSONObject(text))?.copy(builtin = true)
    }.getOrNull()

    private fun persist(presets: List<PromptPreset>) {
        val preferences = Prefs.localAgentPreferences() ?: return
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        runCatching { preferences.edit().putString(KEY_SAVED, array.toString()).commit() }
    }
}
