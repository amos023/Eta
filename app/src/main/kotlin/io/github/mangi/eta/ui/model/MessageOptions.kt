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
    /** 由编号列表兜底识别而来，正文里那段列表需要同时去掉。 */
    val fromFallback: Boolean = false,
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
            // 显式标注 multi 就是多选；选项超过三个时也按多选处理。
            multiSelect = start.groupValues[1].isNotEmpty() || options.size > 3,
        )
    }

    private val numberedLine = Regex("""^\d{1,2}[.、)]\s*(.+)$""")

    /**
     * 兜底：模型没按格式输出、但回复末尾是「问句 + 编号列表」时，
     * 把列表项当作候选渲染成按钮。条件刻意收紧，避免把普通罗列误判成选项：
     * 列表必须是全文最后一段、条目 2~6 个、上一行以问号结尾。
     */
    fun parseFallback(content: String): MessageOptionsBlock? {
        val lines = content.lines()
        var i = lines.lastIndex
        while (i >= 0 && lines[i].isBlank()) i--
        val collected = mutableListOf<String>()
        while (i >= 0) {
            val match = numberedLine.matchEntire(lines[i].trim()) ?: break
            collected.add(0, match.groupValues[1].trim())
            i--
        }
        if (collected.size !in 2..6) return null
        var j = i
        while (j >= 0 && lines[j].isBlank()) j--
        if (j < 0) return null
        val promptLine = lines[j].trim()
        if (!promptLine.endsWith("？") && !promptLine.endsWith("?")) return null
        return MessageOptionsBlock(
            question = null,
            options = collected,
            multiSelect = collected.size > 3,
            fromFallback = true,
        )
    }

    /** 兜底命中时把末尾的编号列表从正文里去掉，只留问句。 */
    fun stripFallback(content: String): String {
        val lines = content.lines().toMutableList()
        var i = lines.lastIndex
        while (i >= 0 && lines[i].isBlank()) i--
        var removed = 0
        while (i >= 0 && numberedLine.matches(lines[i].trim())) {
            lines.removeAt(i)
            i--
            removed++
        }
        if (removed == 0) return content
        return lines.joinToString("\n").trim().replace(Regex("\n{3,}"), "\n\n")
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
