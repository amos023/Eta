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

/** 摘要行最多列几步动作，再多就看不出重点了。 */
private const val ACTION_STEP_LIMIT = 3

/** 摘要里每一步的最大字符数。 */
private const val ACTION_LABEL_LIMIT = 28

/** 从参数预览里捞文件路径，用来把工具调用说成「改了哪个文件」。 */
private val FILE_PATH_PATTERN = Regex(
    """[A-Za-z0-9_./\\-]+\.(?:kt|kts|java|xml|gradle|toml|md|json|yml|yaml|ini)""",
)

/**
 * 常见命令 → 中文短标签。
 *
 * 顺序有意义：越具体的规则越靠前，命中多条时按顺序各取一条，
 * 这样 `git push … && curl …dispatch` 能同时说出「推送仓库、触发构建」。
 */
private val COMMAND_LABELS: List<Pair<Regex, String>> = listOf(
    Regex("""actions/workflows/[^\s"]+/dispatches""") to "触发构建",
    Regex("""api\.github\.com/[^\s"]*artifacts""") to "下载产物",
    Regex("""git\s+push""") to "推送仓库",
    Regex("""git\s+commit""") to "提交",
    Regex("""git\s+(revert|reset|checkout)""") to "回退改动",
    Regex("""git\s+(log|show|diff|status|tag|branch|fetch)""") to "查看仓库",
    Regex("""aapt2\s+dump""") to "校验安装包",
    Regex("""gradlew""") to "本地构建",
    Regex("""\bpm\s+install|\bam\s+start""") to "装包启动",
    Regex("""\b(rg|grep)\b""") to "检索代码",
    Regex("""\b(unzip|tar|zip)\b""") to "解包",
    Regex("""\bsetsid\b|\bnohup\b""") to "后台运行",
    Regex("""\b(python3?|node|dash|bash)\b""") to "运行脚本",
    Regex("""^\s*(cp|mv)\b""") to "复制文件",
    Regex("""^\s*rm\b""") to "删除文件",
    Regex("""^\s*(ls|find)\b""") to "查找文件",
    Regex("""^\s*(sed|cat|head|tail|wc)\b""") to "读取文件",
    Regex("""\bcurl\b""") to "网络请求",
    Regex("""\bdf\b|\bfree\b|/proc/meminfo""") to "查看资源",
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
        val labels = COMMAND_LABELS
            .filter { (pattern, _) -> pattern.containsMatchIn(command) }
            .map { (_, label) -> label }
            .distinct()
        if (labels.isNotEmpty()) {
            return labels.take(2).joinToString("、")
        }
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
 * 合并连续纯指令时生成的标题。
 *
 * 同一个词重复出现时折成次数：`推 ×2（第 588、589 轮）`；
 * 不同指令并列时如实列出：`推、提交（第 588、589 轮）`。
 * 轮次就是定位列表里的序号，方便和原列表对上。
 */
internal fun directiveRowTitle(words: List<String>, rounds: List<Int>): String {
    if (words.isEmpty()) return ""
    val distinct = words.distinct()
    val base = if (distinct.size == 1) {
        if (words.size > 1) "${distinct.first()} ×${words.size}" else distinct.first()
    } else {
        distinct.joinToString("、")
    }
    return if (rounds.isEmpty()) base else "$base（第 ${rounds.joinToString("、")} 轮）"
}

/** 合并两段「做了什么」：相同保留一份，不同则串联。 */
private fun mergeActionSummary(first: String?, second: String?): String? = when {
    first.isNullOrBlank() -> second?.takeIf { it.isNotBlank() }
    second.isNullOrBlank() -> first
    first == second || first.contains(second) -> first
    else -> "$first → $second"
}

/**
 * 构建定位列表。
 *
 * 每一行仍然对应你的原话（标题就是原话），只做两件事：
 * 1. 连续纯指令（继续/推/提交）合成一行，标题带次数与轮次；
 * 2. 每行附一行系统动作摘要，说明这一轮实际做了什么。
 *
 * [toolDigests] 为每条用户消息附上摘要，缺失时该行不显示第二行。
 */
internal fun buildMessageNavItems(
    entries: List<Pair<String, String>>,
    toolDigests: Map<String, String> = emptyMap(),
): List<MessageNavItem> {
    val items = mutableListOf<MessageNavItem>()
    var mergedWords: List<String> = emptyList()
    var mergedRounds: List<Int> = emptyList()

    entries.forEachIndexed { index, (id, content) ->
        val prompt = AgentFileReferencePromptCodec.parse(content).request
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return@forEachIndexed
        val round = index + 1
        val summary = toolDigests[id]?.takeIf { it.isNotBlank() }
        val directive = isDirectivePrompt(trimmed)

        // 连续纯指令并成一行；这一行的第一轮作为跳转锚点。
        if (directive && mergedWords.isNotEmpty() && items.isNotEmpty()) {
            mergedWords = mergedWords + trimmed
            mergedRounds = mergedRounds + round
            val previous = items.last()
            items[items.lastIndex] = previous.copy(
                title = directiveRowTitle(mergedWords, mergedRounds),
                summary = mergeActionSummary(previous.summary, summary),
            )
            return@forEachIndexed
        }

        mergedWords = if (directive) listOf(trimmed) else emptyList()
        mergedRounds = if (directive) listOf(round) else emptyList()
        items += MessageNavItem(
            messageId = id,
            prompt = prompt,
            title = if (directive) directiveRowTitle(mergedWords, mergedRounds) else messageNavTitle(prompt),
            summary = summary,
            round = round,
        )
    }
    return items
}
