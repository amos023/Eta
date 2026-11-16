package io.github.mangi.eta.agent.prompt

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 预设包的读写。
 *
 * 导入尽量宽容：除本仓库自己的 `preset.json` 外，还识别
 * - zip 内含任意一个 `.json`（优先 preset.json，其次取第一个 JSON）
 * - 裸 JSON，字段名支持多种常见写法（systemPrompt / system / prompt / content …）
 * - OpenAI / Anthropic / Gemini 的 JSON 配置片段（取其中的 system 字段）
 * - 纯文本 `.md` / `.txt`：整段当作主身份设定
 *
 * 单包上限 256KB；解析失败返回明确错误，不做「尽力而为」。
 */
internal object PromptPresetCodec {
    private const val ENTRY_NAME = "preset.json"
    private const val MAX_BYTES = 256 * 1024

    /** 主身份字段的候选键，按优先级排列。 */
    private val BASE_KEYS = listOf(
        "base_system_prompt", "baseSystemPrompt", "system_prompt", "systemPrompt",
        "system", "prompt", "content", "instruction", "instructions", "text",
    )
    private val NAME_KEYS = listOf("name", "title", "preset_name", "presetName")
    private val DESC_KEYS = listOf("description", "desc", "summary", "note")
    private val APPEND_KEYS = listOf(
        "append_instructions", "appendInstructions", "append", "extra", "additional",
    )
    private val PERSONA_NAME_KEYS = listOf("persona_name", "personaName", "role", "character")
    private val PERSONA_STYLE_KEYS = listOf("persona_style", "personaStyle", "style", "tone")

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Failure(val reason: String) : Result<Nothing>
    }

    fun read(context: Context, uri: Uri): Result<PromptPreset> {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) return Result.Failure("文件太大，超过 256 KB")
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        }.getOrNull() ?: return Result.Failure("读不到文件内容")

        val fileName = queryDisplayName(context, uri)
        return parseBytes(bytes, fileName)
    }

    /** 纯字节解析，便于测试；fileName 只用于纯文本兜底时取名。 */
    fun parseBytes(bytes: ByteArray, fileName: String? = null): Result<PromptPreset> {
        if (bytes.isEmpty()) return Result.Failure("文件是空的")

        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            val entries = readZipJsonEntries(bytes)
            if (entries.isEmpty()) return Result.Failure("zip 里没有可识别的 .json 文件")
            // 优先 preset.json，其次按文件名排序取第一个。
            // 不用 !!：上面虽已判空，但这里再兜一层，避免以后改动引入空指针。
            val preferred = entries.entries.firstOrNull { it.key.equals(ENTRY_NAME, true) }
                ?: entries.entries.minByOrNull { it.key }
                ?: return Result.Failure("zip 里没有可读的 JSON 条目")
            return parseText(preferred.value, fileName)
        }

        val text = bytes.toString(Charsets.UTF_8)
        return parseText(text, fileName)
    }

    /**
     * 文本解析：先按 JSON 试，失败再当纯文本。
     *
     * JSON 支持本仓库格式、常见键名、以及包在数组或对象里的多套配置（取第一套）。
     */
    fun parseText(raw: String, fileName: String? = null): Result<PromptPreset> {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Failure("内容为空")

        // 1) 本仓库标准格式
        runCatching { JSONObject(text) }.getOrNull()?.let { json ->
            PromptPreset.fromJson(json)?.let { return Result.Ok(it) }
            // 2) 其它常见格式：宽松取字段
            flexibleParse(json, fileName)?.let { return Result.Ok(it) }
        }

        // 3) 顶层是数组：取第一项
        runCatching { JSONArray(text) }.getOrNull()?.let { array ->
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                PromptPreset.fromJson(obj)?.let { return Result.Ok(it) }
                flexibleParse(obj, fileName)?.let { return Result.Ok(it) }
            }
            return Result.Failure("JSON 数组里没有可用条目")
        }

        // 4) 纯文本 / Markdown：整段当作主身份
        if (text.length > PromptPreset.MAX_FIELD_CHARS) {
            return Result.Failure("文本太长，超过 ${PromptPreset.MAX_FIELD_CHARS} 字")
        }
        val fallbackName = fileName
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.take(60)
            ?: "导入的提示词"
        return Result.Ok(
            PromptPreset(
                name = fallbackName,
                description = "从文本文件导入",
                mode = PromptCustomizationMode.REPLACE,
                baseSystemPrompt = text,
            ),
        )
    }

    /** 宽松解析：在多组候选键里找第一个非空值。 */
    private fun flexibleParse(json: JSONObject, fileName: String?): PromptPreset? {
        fun pick(keys: List<String>): String =
            keys.firstNotNullOfOrNull { key ->
                json.optString(key).takeIf { it.isNotBlank() && it.length <= PromptPreset.MAX_FIELD_CHARS }
            }.orEmpty()

        // 有些格式把 system 放在 messages 数组里，role=system 的那条才是提示词。
        val fromMessages = runCatching {
            json.optJSONArray("messages")?.let { array ->
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .firstOrNull { it.optString("role").equals("system", true) }
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

        // Gemini 风格：system_instruction.parts[0].text
        val fromGemini = runCatching {
            json.optJSONObject("system_instruction")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val base = fromMessages ?: fromGemini ?: pick(BASE_KEYS)
        if (base.isBlank()) return null

        val name = pick(NAME_KEYS).ifBlank {
            fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "导入的提示词"
        }
        val mode = if (json.optString("mode").equals("APPEND", true)) {
            PromptCustomizationMode.APPEND
        } else {
            PromptCustomizationMode.REPLACE
        }
        return PromptPreset(
            name = name.take(60),
            description = pick(DESC_KEYS).take(280),
            mode = mode,
            baseSystemPrompt = base,
            personaName = pick(PERSONA_NAME_KEYS),
            personaStyle = pick(PERSONA_STYLE_KEYS),
            appendInstructions = pick(APPEND_KEYS),
            author = pick(listOf("author", "by")),
        )
    }

    fun write(context: Context, uri: Uri, preset: PromptPreset): Result<Unit> {
        val payload = encode(preset)
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                ?: return Result.Failure("无法写入目标文件")
            Result.Ok(Unit)
        }.getOrElse { Result.Failure("写入失败：${it.message ?: it.javaClass.simpleName}") }
    }

    fun encode(preset: PromptPreset): ByteArray {
        val json = preset.toJson().toString(2).toByteArray(Charsets.UTF_8)
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_NAME))
            zip.write(json)
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    /** 内置预设从 assets 读，格式与导入包里的 preset.json 完全一致。 */
    fun readAsset(context: Context, assetPath: String): PromptPreset? = runCatching {
        val text = context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
        PromptPreset.fromJson(JSONObject(text))?.copy(builtin = true)
    }.getOrNull()

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    /** 读出 zip 里所有 json 条目；解压侧同样设上限，防压缩炸弹。 */
    private fun readZipJsonEntries(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (!name.endsWith(".json", true)) continue
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = zip.read(chunk)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BYTES) break
                        buffer.write(chunk, 0, read)
                    }
                    if (total in 1..MAX_BYTES) {
                        out[name] = buffer.toByteArray().toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return out
    }
}
