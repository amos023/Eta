package io.github.mangi.eta.ui.model

/**
 * 回复里内嵌的选项块。模型需要用户拍板时按固定格式输出：
 *
 * ```
 * [[options]]
 * 问题：要改哪一项？
 * - 标题样式
 * - 卡片间距
 * [[/options]]
 * ```
 * 多选写作 `[[options:multi]]`。
 */
data class MessageOptionsBlock(
    val question: String?,
    val options: List<String>,
    val multiSelect: Boolean,
)

object MessageOptionsParser {
    private val startRegex = Regex("""\[\[options(:multi)?\]\]""", RegexOption.IGNORE_CASE)
    private const val END_TAG = "[[/options]]"

    /** 解析正文中的第一个选项块；没有或格式不完整时返回 null。 */
    fun parse(content: String): MessageOptionsBlock? {
        val start = startRegex.find(content) ?: return null
        val endIndex = content.indexOf(END_TAG, start.range.last + 1)
        if (endIndex < 0) return null
        val body = content.substring(start.range.last + 1, endIndex)
        var question: String? = null
        val options = mutableListOf<String>()
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("问题：") || line.startsWith("问题:") ->
                    question = line.substringAfter('：', line.substringAfter(':', "")).trim()
                        .takeIf { it.isNotEmpty() }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val value = line.drop(2).trim()
                    if (value.isNotEmpty()) options += value
                }
            }
        }
        if (options.isEmpty()) return null
        return MessageOptionsBlock(
            question = question,
            options = options,
            multiSelect = start.groupValues[1].isNotEmpty(),
        )
    }

    /** 去掉选项块，正文只保留叙述部分，避免同一段内容出现两次。 */
    fun stripBlock(content: String): String {
        val start = startRegex.find(content) ?: return content
        val endIndex = content.indexOf(END_TAG, start.range.last + 1)
        if (endIndex < 0) return content
        return (content.removeRange(start.range.first, endIndex + END_TAG.length))
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
