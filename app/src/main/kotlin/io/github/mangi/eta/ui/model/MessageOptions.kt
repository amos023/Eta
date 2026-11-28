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
            // 默认单选：只有模型显式写 [[options:multi]] 才多选。
            // 按选项个数推断多选是错的——三个互斥方案本该点一下就完事。
            multiSelect = start.groupValues[1].isNotEmpty(),
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
            multiSelect = false,
            fromFallback = true,
        )
    }

    /** 「要不要 / 是否 / …吗」这类强疑问标记。 */
    private val yesNoMarkers = listOf("要不要", "需不需要", "是否需要", "是否", "要不要我", "用不用")

    /** 问句结尾常见的征询标记。 */
    private val yesNoTails = listOf("好吗", "行吗", "可以吗", "么", "吗")

    /** 疑问词开头的问题没法用「要 / 先不用」回答。 */
    private val whWords = listOf("为什么", "怎么", "什么", "哪里", "哪些", "谁", "多少", "如何", "哪个", "哪种")

    /** 二选一句式里要剥掉的前缀。 */
    private val prefixTokens = listOf("你要", "你是否", "你是不是", "你是", "要不要", "需要", "要", "是")

    /**
     * 问句兜底：模型既没输出选项块、也没写编号列表时，从**最后一行问句**里取候选。
     *
     * 只处理两种中文里最常见的可拆问句：
     * - 二选一：「A 还是 B？」
     * - 是/否：「要不要 …」「是否 …」「…吗？」
     * 其余（为什么 / 怎么做 之类）返回 null，不硬塞按钮。
     */
    fun parseQuestionFallback(content: String): MessageOptionsBlock? {
        val line = content.lines().lastOrNull { it.isNotBlank() }?.trim() ?: return null
        if (!line.endsWith("？") && !line.endsWith("?")) return null
        parseEitherOr(line)?.let { return it }
        return parseYesNo(line)
    }

    private fun parseEitherOr(line: String): MessageOptionsBlock? {
        val body = line.trimEnd('？', '?').trim()
        val first = body.indexOf("还是")
        if (first <= 0) return null
        // 出现多次「还是」时容易拆错，交给模型自己去说清楚。
        if (body.indexOf("还是", first + 2) >= 0) return null
        val left = stripPrefix(body.substring(0, first).trim())
        val right = stripPrefix(body.substring(first + 2).trim())
        if (left.isEmpty() || right.isEmpty()) return null
        if (left == right) return null
        val options = listOf(left.take(24), right.take(24))
        return MessageOptionsBlock(
            question = null,
            options = options,
            multiSelect = false,
            fromFallback = true,
        )
    }

    private fun stripPrefix(raw: String): String {
        var value = raw.trim().trim(',', '，', '、')
        var changed = true
        while (changed) {
            changed = false
            prefixTokens.forEach { token ->
                if (value.startsWith(token) && value.length > token.length) {
                    value = value.substring(token.length).trim()
                    changed = true
                }
            }
        }
        return value.trim('，', ',').trim()
    }

    private fun parseYesNo(line: String): MessageOptionsBlock? {
        val body = line.trimEnd('？', '?').trim()
        if (body.isEmpty()) return null
        val marked = yesNoMarkers.any { body.contains(it) } || yesNoTails.any { body.endsWith(it) }
        if (!marked) return null
        // 带疑问词的（为什么/怎么做…）即使以「吗」结尾也不好用是否回答，除非它含「是否」类标记。
        val explicit = yesNoMarkers.any { body.contains(it) }
        if (!explicit && whWords.any { body.contains(it) }) return null
        val options = when {
            body.contains("是不是") || body.contains("是否") -> listOf("是", "不是")
            body.contains("要不要") || body.contains("用不用") ||
                body.contains("需不需要") || body.contains("是否需要") -> listOf("要", "先不用")
            else -> listOf("好", "先不用")
        }
        return MessageOptionsBlock(
            question = null,
            options = options,
            multiSelect = false,
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
