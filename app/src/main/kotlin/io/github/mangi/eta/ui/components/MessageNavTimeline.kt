package io.github.mangi.eta.ui.components

import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi

/**
 * 「消息定位」的动作时间线构建。
 *
 * 列表原本按「用户原话」逐条列出，而用户最高频的输入恰恰是「继续」「推」
 * 「提交」这类纯指令——它们单独成行时不携带任何定位价值，还会把真正说明
 * 需求的句子挤到看不见。
 *
 * 这里把它们合并进上一个动作，只把「能说清改了什么」的话留成主行。
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
 * 把合并进来的后续指令压成一行：连续的同一个词折成「推 ×2」。
 *
 * 用户连发两次「推」是常态，原样列出来只会重复占位。
 */
internal fun summarizeFollowUps(followUps: List<String>): String {
    if (followUps.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var current = followUps.first()
    var count = 1
    for (index in 1 until followUps.size) {
        val next = followUps[index]
        if (next == current) {
            count++
        } else {
            parts += if (count > 1) "$current ×$count" else current
            current = next
            count = 1
        }
    }
    parts += if (count > 1) "$current ×$count" else current
    return parts.joinToString("、")
}

/** 摘要行最多列几步动作，再多就看不出重点了。 */
private const val ACTION_STEP_LIMIT = 3

/** 摘要里每一步的最大字符数。 */
private const val ACTION_LABEL_LIMIT = 28

/** 从参数预览里捞文件路径，用来把工具调用说成「改了哪个文件」。 */
private val FILE_PATH_PATTERN = Regex(
    """[A-Za-z0-9_./\\-]+\.(?:kt|kts|java|xml|gradle|toml|md|json|yml|yaml|ini)""",
)

/**
 * 把一轮里的一串工具调用压成一句「做了什么」。
 *
 * 优先级：终端命令的首行 > 改动/读取的文件名 > 工具名本身。
 * 相邻重复步骤折叠，最多保留 [ACTION_STEP_LIMIT] 步。
 */
internal fun summarizeToolActions(activities: List<ToolActivityMessageUi>): String {
    val parts = mutableListOf<String>()
    activities.forEach { activity ->
        if (parts.size >= ACTION_STEP_LIMIT) return@forEach
        val label = toolActionLabel(activity)
        if (label.isBlank() || parts.lastOrNull() == label) return@forEach
        parts += label
    }
    return parts.joinToString(" → ")
}

private fun toolActionLabel(activity: ToolActivityMessageUi): String {
    val command = activity.command?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    if (command.isNotEmpty()) {
        return command.take(ACTION_LABEL_LIMIT)
    }
    val file = FILE_PATH_PATTERN.find(activity.argumentsSummary)?.value
    if (file != null) {
        val base = file.substringAfterLast('/').substringAfterLast('\\')
        val name = activity.toolName.lowercase()
        return when {
            name.contains("edit") || name.contains("write") ||
                name.contains("patch") || name.contains("replace") -> "改 $base"
            name.contains("read") || name.contains("view") -> "看 $base"
            else -> base
        }
    }
    return activity.toolName.take(ACTION_LABEL_LIMIT)
}

/**
 * 按用户消息归集工具调用，得到「这个消息 id → 这一轮做了什么」。
 *
 * 只有用户消息和工具活动参与归属，助手正文与思考流不在这里出现。
 */
internal fun buildToolDigests(messages: List<AgentChatMessageUi>): Map<String, String> {
    val digests = mutableMapOf<String, String>()
    var ownerId: String? = null
    val bucket = mutableListOf<ToolActivityMessageUi>()

    fun flush() {
        val id = ownerId ?: return
        val summary = summarizeToolActions(bucket)
        if (summary.isNotBlank()) digests[id] = summary
    }

    messages.forEach { message ->
        when (message) {
            is UserMessageUi -> {
                flush()
                bucket.clear()
                ownerId = message.id
            }
            is ToolActivityMessageUi -> bucket += message
            else -> Unit
        }
    }
    flush()
    return digests
}

/**
 * 按「动作」重建定位列表。
 *
 * 规则：能说清需求的话各占一行；纯指令挂到上一行，成为它的后续。
 * 若开头就是纯指令（没有前置动作），仍保留成独立一行，不做隐藏。
 *
 * [toolDigests] 为每条用户消息附上「这一轮做了什么」，缺失时不显示摘要。
 */
internal fun buildMessageNavItems(
    entries: List<Pair<String, String>>,
    toolDigests: Map<String, String> = emptyMap(),
): List<MessageNavItem> {
    val items = mutableListOf<MessageNavItem>()
    entries.forEach { (id, content) ->
        val prompt = AgentFileReferencePromptCodec.parse(content).request
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return@forEach
        val previous = items.lastOrNull()
        if (previous != null && isDirectivePrompt(trimmed)) {
            items[items.lastIndex] = previous.copy(followUps = previous.followUps + trimmed)
            return@forEach
        }
        items += MessageNavItem(
            messageId = id,
            prompt = prompt,
            title = messageNavTitle(prompt),
            summary = toolDigests[id]?.takeIf { it.isNotBlank() },
        )
    }
    return items
}
