package io.github.mangi.eta.ui.components

import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi

/**
 * 「消息定位」列表构建。
 *
 * 列表按用户原话逐条列出，但用户最高频的输入是「继续」「推」「提交」这类
 * 纯指令——单独成行不携带任何定位价值，还会把真正说明需求的句子挤到看不见。
 * 这里只做一件事：把**相邻的**纯指令合并成一行，其余原样保留。
 */

/** 纯指令词表：整句就是这些词时，没有任何定位价值。 */
private val DIRECTIVE_WORDS = setOf(
    "继续", "推", "提交", "推送", "好", "行", "可以", "对", "是", "嗯",
    "改", "做", "继续吧", "推吧", "改吧", "做吧", "好的", "行吧", "可以了",
    "推上去", "一起改", "一起做", "继续改", "继续做", "都行", "随便",
    "ok", "okay", "yes", "go",
)

/** 单字组合也当纯指令：如「好行」「可以改」。 */
private const val DIRECTIVE_CHARS = "好行可以嗯对推改做继续提交"

/** 超过这个长度的句子一定携带信息，不再当纯指令处理。 */
private const val DIRECTIVE_MAX_LENGTH = 6

/** 主行标题的最大字数，超出截断。 */
private const val TITLE_MAX_LENGTH = 26

/** 判断一条输入是不是「只有指令、没有信息」。 */
internal fun isDirectivePrompt(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty() || compact.length > DIRECTIVE_MAX_LENGTH) return false
    if (compact.lowercase() in DIRECTIVE_WORDS) return true
    return compact.all { char -> char in DIRECTIVE_CHARS }
}

/**
 * 从原话里取一行能当标题的内容：取第一个非空行，遇到句末标点就截断。
 */
internal fun messageNavTitle(prompt: String): String {
    val firstLine = prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return ""
    val end = firstLine.indexOfFirst { it == '。' || it == '？' || it == '！' || it == '?' || it == '!' }
    val head = if (end > 0) firstLine.substring(0, end) else firstLine
    return if (head.length <= TITLE_MAX_LENGTH) head else head.take(TITLE_MAX_LENGTH) + "…"
}

/**
 * 合并后的指令行标题。
 *
 * 同一个词重复出现时折成次数：`推 ×2（第 588、589 轮）`；
 * 不同指令并列时如实列出：`推、提交（第 588、589 轮）`。
 * 单轮时不写轮次。
 */
internal fun directiveRowTitle(words: List<String>, rounds: List<Int>): String {
    if (words.isEmpty()) return ""
    val distinct = words.distinct()
    val base = if (distinct.size == 1) {
        if (words.size > 1) "${distinct.first()} ×${words.size}" else distinct.first()
    } else {
        distinct.joinToString("、")
    }
    return when {
        rounds.size <= 1 -> base
        else -> "$base（第 ${rounds.joinToString("、")} 轮）"
    }
}

/** 一轮的补充信息：我当时最后那句回复的首句，以及这一轮有没有出错。 */
internal data class TurnDigest(
    val summary: String? = null,
    val failed: Boolean = false,
)

/** 摘要最大字数。 */
private const val SUMMARY_MAX_LENGTH = 34

/** 去掉 Markdown 记号，只留能读的文字。 */
private fun stripMarkdown(text: String): String = text
    .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
    .replace(Regex("""\*\*|__|`""" ), "")
    .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "")
    .trim()

/**
 * 取一条回复的首句当摘要。
 *
 * 用我自己给你的回复，而不是工具调用日志：回复天然是人话，
 * 也天然写了结果；工具日志会被脱敏、cd、heredoc 打碎。
 */
internal fun assistantFirstSentence(content: String): String {
    val text = stripMarkdown(content)
    if (text.isEmpty()) return ""
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return ""
    val end = firstLine.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '!' || it == '?' }
    val head = if (end > 0) firstLine.substring(0, end) else firstLine
    return if (head.length <= SUMMARY_MAX_LENGTH) head else head.take(SUMMARY_MAX_LENGTH) + "…"
}

/**
 * 按用户消息归集：这一轮我最后说了什么、这一轮有没有工具失败。
 *
 * 只依赖消息本身，不需要时间戳，也不改数据库。
 */
internal fun buildTurnDigests(messages: List<AgentChatMessageUi>): Map<String, TurnDigest> {
    val digests = mutableMapOf<String, TurnDigest>()
    var ownerId: String? = null
    var lastReply: String? = null
    var failed = false

    fun flush() {
        val id = ownerId ?: return
        val summary = lastReply?.let { assistantFirstSentence(it) }?.takeIf { it.isNotBlank() }
        if (summary != null || failed) {
            digests[id] = TurnDigest(summary = summary, failed = failed)
        }
    }

    messages.forEach { message ->
        when (message) {
            is UserMessageUi -> {
                flush()
                ownerId = message.id
                lastReply = null
                failed = false
            }
            is AgentMessageUi -> if (message.content.isNotBlank()) lastReply = message.content
            is ToolActivityMessageUi ->
                if (message.status == ToolActivityStatusUi.Failed) failed = true
            else -> Unit
        }
    }
    flush()
    return digests
}

/**
 * 构建定位列表：相邻纯指令合成一行，其余原话各占一行。
 *
 * 跳转锚点取该行的第一轮消息。
 */
internal fun buildMessageNavItems(
    entries: List<Pair<String, String>>,
    digests: Map<String, TurnDigest> = emptyMap(),
): List<MessageNavItem> {
    val items = mutableListOf<MessageNavItem>()
    var mergedWords: List<String> = emptyList()
    var mergedRounds: List<Int> = emptyList()

    entries.forEachIndexed { index, (id, content) ->
        val prompt = AgentFileReferencePromptCodec.parse(content).request
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return@forEachIndexed
        val round = index + 1
        val directive = isDirectivePrompt(trimmed)
        val digest = digests[id]

        if (directive && mergedWords.isNotEmpty() && items.isNotEmpty()) {
            mergedWords = mergedWords + trimmed
            mergedRounds = mergedRounds + round
            val previous = items.last()
            items[items.lastIndex] = previous.copy(
                title = directiveRowTitle(mergedWords, mergedRounds),
                summary = digest?.summary ?: previous.summary,
                failed = previous.failed || (digest?.failed == true),
            )
            return@forEachIndexed
        }

        mergedWords = if (directive) listOf(trimmed) else emptyList()
        mergedRounds = if (directive) listOf(round) else emptyList()
        items += MessageNavItem(
            messageId = id,
            prompt = prompt,
            title = if (directive) directiveRowTitle(mergedWords, mergedRounds) else messageNavTitle(prompt),
            round = round,
            summary = digest?.summary,
            failed = digest?.failed == true,
        )
    }
    return items
}
