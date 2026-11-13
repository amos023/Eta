package io.github.mangi.eta.ui.app

import android.os.SystemClock
import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import io.github.mangi.eta.agent.runtime.SearchSuggestionsBudget
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.UserMessageUi

internal class AgentRunMessageProjector(
    private val nowElapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val thinkingStartedAt = mutableMapOf<String, Long>()

    /**
     * App 与系统助手浮层共用的事件到消息投影。无可见变化的事件原样返回同一列表，
     * 调用方据此跳过保存与时间戳更新；补充输入的插入位置与持久化由调用方决定，这里不处理。
     */
    fun applyEvent(
        runId: String,
        event: AgentEvent,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> = when (event) {
        is AgentEvent.GuiControlChanged -> messages
        is AgentEvent.AssistantBlockStart -> startAssistantBlock(runId, event, messages)
        is AgentEvent.AssistantBlockDelta -> when (event.kind) {
            AgentEvent.AssistantBlockKind.TEXT ->
                appendTextDelta(runId, event.round, event.index, event.delta, messages)
            AgentEvent.AssistantBlockKind.THINKING ->
                appendReasoningDelta(runId, event.round, event.index, event.delta, messages)
            AgentEvent.AssistantBlockKind.TOOL_CALL -> messages
        }
        is AgentEvent.AssistantBlockEnd -> when (event.kind) {
            AgentEvent.AssistantBlockKind.TEXT ->
                finalizeTextBlock(runId, event.round, event.index, event.replacementContent, messages)
            AgentEvent.AssistantBlockKind.THINKING ->
                finalizeThinkingBlock(runId, event.round, event.index, event.replacementContent, messages)
            AgentEvent.AssistantBlockKind.TOOL_CALL -> messages
        }
        is AgentEvent.SearchSuggestions -> attachSearchSuggestions(runId, event, messages)
        is AgentEvent.UsageReceived -> attachUsage(runId, event.round, event.usage.toUi(), messages)
        is AgentEvent.ToolStarted ->
            startTool(runId, event, finalizeTextRound(runId, event.round, finalizeThinkingRound(runId, event.round, messages)))
        is AgentEvent.ToolFinished -> finishTool(runId, event, messages)
        is AgentEvent.SubagentProgress -> updateSubagentProgress(runId, event, messages)
        is AgentEvent.HostedToolStarted ->
            startHostedTool(runId, event, finalizeTextRound(runId, event.round, finalizeThinkingRound(runId, event.round, messages)))
        is AgentEvent.HostedToolFinished -> finishHostedTool(runId, event, messages)
        is AgentEvent.ContextCompaction -> {
            val id = "assistant-$runId-compaction-${event.operationId}"
            messages.filterNot { it.id == id } + SystemNoticeMessageUi(
                id = id,
                code = SystemNoticeCode.ContextCompaction,
                detail = event.displayMessage,
                contextTokens = event.tokensAfter,
                running = event.phase == AgentEvent.ContextCompaction.PHASE_STARTED,
                usage = event.usage?.takeUnless { it.isEmpty }?.toUi()?.copy(
                    requestKind = AgentTokenUsage.REQUEST_KIND_COMPACTION,
                ),
                archiveId = event.archiveId,
                tokensBefore = event.tokensBefore,
            )
        }
        is AgentEvent.ModelRetryScheduled -> scheduleModelRetry(runId, event, messages)
        is AgentEvent.RunFailed -> failRunningTools(event.reason, finalizeText(runId, finalizeThinking(runId, messages)))
        // 空白 reasoning 同样要进来：它负责丢弃本轮占位思考，不能直接原样返回。
        is AgentEvent.AssistantReceived ->
            ensureCompletedThinking(runId, event.round, event.reasoningContent, messages)
        is AgentEvent.RunFinished -> finalizeText(runId, finalizeThinking(runId, messages))
        // 每轮请求发出前就占一条思考占位，覆盖等待响应与首个分片之间的空窗。
        is AgentEvent.RoundStarted -> ensurePendingThinking(runId, event.round, messages)
        is AgentEvent.UserSupplementReceived,
        is AgentEvent.RunStarted,
        is AgentEvent.ProviderRequestStarted,
        is AgentEvent.ProviderResponseStarted,
        is AgentEvent.ToolImagesAttached,
        -> messages
    }

    /**
     * 提交瞬间先占一条思考占位，覆盖从发消息到首个思考/文本分片之间的空窗。
     * 没有这一条时，网络请求、等待响应头、模型开始生成这三段界面完全没有反馈。
     *
     * 首个分片到达时由 appendReasoningDelta 把这条改造成真实 index id 继续续写，
     * 因此不会出现「占位一条、真实内容又一条」的上下两个思考。
     */
    fun ensurePendingThinking(
        runId: String,
        round: Int,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        if (messages.any { it is ThinkingMessageUi && isThinkingMessageForRound(it.id, runId, round) }) {
            return messages
        }
        val thinkingId = thinkingPlaceholderId(runId, round)
        return messages + ThinkingMessageUi(
            id = thinkingId,
            content = "",
            isStreaming = true,
            elapsedSeconds = elapsedSeconds(thinkingId),
            collapsed = false,
        )
    }

    private fun attachSearchSuggestions(
        runId: String,
        event: AgentEvent.SearchSuggestions,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val targetId = assistantMessageId(runId, event.round, event.index)
        val target = messages.filterIsInstance<AgentMessageUi>().firstOrNull { it.id == targetId } ?: return messages
        if (event.html in target.searchSuggestionsHtml) return messages
        val budget = SearchSuggestionsBudget()
        messages.filterIsInstance<AgentMessageUi>()
            .filter { isAssistantMessageForRound(it.id, runId, event.round) }
            .forEachIndexed { ordinal, message ->
                message.searchSuggestionsHtml.forEach { budget.accept(event.round, ordinal, it) }
            }
        if (!budget.accept(event.round, messages.size, event.html)) return messages
        return messages.map { message ->
            if (message === target) target.copy(searchSuggestionsHtml = target.searchSuggestionsHtml + event.html) else message
        }
    }

    /**
     * 只补充 token 用量，不触碰 isStreaming：用量事件紧跟文本块结束，
     * 改回流式会让渲染在流式/静态视图间反复切换并整段重渲染。
     */
    private fun attachUsage(
        runId: String,
        round: Int,
        usage: TokenUsageUi,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        if (usage.isEmpty) return messages
        val targetIndex = messages.indexOfLast { message ->
            message is AgentMessageUi && isAssistantMessageForRound(message.id, runId, round)
        }
        if (targetIndex < 0) return messages
        val turnStartIndex = messages.indexOfLast { message ->
            message is UserMessageUi ||
                (message is SystemNoticeMessageUi && message.code == SystemNoticeCode.ModelRetry && message.id.startsWith("assistant-$runId-retry-"))
        }
        return messages.mapIndexed { index, message ->
            when {
                index == targetIndex && message is AgentMessageUi -> message.copy(usage = usage)
                index > turnStartIndex && index < targetIndex &&
                    message is AgentMessageUi &&
                    isAssistantMessageForRun(message.id, runId) &&
                    message.usage != null -> message.copy(usage = null)
                else -> message
            }
        }
    }

    /** 回放从该 run 的空轨迹重建；仅重排有回放事件的补充输入，旧 handoff 独有的输入必须保留。 */
    fun resetForReplay(
        runId: String,
        messages: List<AgentChatMessageUi>,
        replaySupplementIndexes: Set<Int> = emptySet(),
    ): List<AgentChatMessageUi> {
        if (runId.isBlank()) return messages
        clearRun(runId)
        val replaySupplementIds = replaySupplementIndexes.mapTo(mutableSetOf()) { index ->
            AgentPendingResultRecovery.supplementMessageId(runId, index)
        }
        return messages.filterNot { message ->
            when (message) {
                is AgentMessageUi -> isAssistantMessageForRun(message.id, runId)
                is ThinkingMessageUi -> message.id.startsWith("$runId-thinking-")
                is ToolActivityMessageUi -> message.id.startsWith("$runId-tool-")
                is SystemNoticeMessageUi ->
                    isAssistantMessageForRun(message.id, runId) || message.id == "interrupted-$runId"
                is UserMessageUi -> message.id in replaySupplementIds
                else -> false
            }
        }
    }

    fun finishContextCompaction(
        runId: String,
        messages: List<AgentChatMessageUi>,
        detail: String,
    ): List<AgentChatMessageUi> = messages.map { message ->
        if (message is SystemNoticeMessageUi && message.code == SystemNoticeCode.ContextCompaction &&
            message.id.startsWith("assistant-$runId-compaction-") && message.running) {
            message.copy(detail = detail, running = false)
        } else message
    }

    fun scheduleModelRetry(
        runId: String,
        event: AgentEvent.ModelRetryScheduled,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val finalized = finalizeTextRound(
            runId, event.round, finalizeThinkingRound(runId, event.round, messages),
        )
        val notice = SystemNoticeMessageUi(
            id = "assistant-$runId-retry-${event.round}",
            code = SystemNoticeCode.ModelRetry,
            detail = event.displayMessage,
        )
        return finalized.filterNot { it.id == notice.id } + notice
    }

    fun startAssistantBlock(
        runId: String,
        event: AgentEvent.AssistantBlockStart,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> = transitionVisibleBlock(
        runId = runId,
        round = event.round,
        kind = event.kind,
        index = event.index,
        messages = messages,
    )

    fun appendTextDelta(
        runId: String,
        round: Int,
        index: Int,
        delta: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        if (delta.isEmpty()) return messages

        val transitioned = transitionVisibleBlock(
            runId = runId,
            round = round,
            kind = AgentEvent.AssistantBlockKind.TEXT,
            index = index,
            messages = messages,
        )
        val assistantId = assistantMessageId(runId, round, index)
        var updated = false
        val next = transitioned.map { message ->
            if (message is AgentMessageUi && message.id == assistantId) {
                updated = true
                message.copy(
                    content = message.content + delta,
                    isStreaming = true,
                    renderMarkdown = false,
                )
            } else {
                message
            }
        }
        if (updated) return next

        return next + AgentMessageUi(
            id = assistantId,
            content = delta,
            isStreaming = true,
            renderMarkdown = false,
        )
    }

    fun appendReasoningDelta(
        runId: String,
        round: Int,
        index: Int,
        delta: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        // 纯空白分片不建条目：部分中转站会先吐一个空格，落库后就成了展开无内容的空思考。
        if (delta.isBlank()) return messages

        val transitioned = transitionVisibleBlock(
            runId = runId,
            round = round,
            kind = AgentEvent.AssistantBlockKind.THINKING,
            index = index,
            messages = messages,
        )
        val thinkingId = thinkingMessageId(runId, round, index)
        val elapsedSeconds = elapsedSeconds(thinkingId)
        // 同一轮可以有多个思考块（靠 index 区分），所以只复用两条中的一条：
        // 优先精确命中 index id，其次消化本轮的占位条目——占位被改造成真实 id 后
        // 继续留在原位，不会与真实分片各占一条形成上下两个思考。
        val placeholderId = thinkingPlaceholderId(runId, round)
        val exact = transitioned.firstOrNull { it is ThinkingMessageUi && it.id == thinkingId }
            as? ThinkingMessageUi
        val placeholder = if (exact == null) {
            transitioned.firstOrNull { it is ThinkingMessageUi && it.id == placeholderId }
                as? ThinkingMessageUi
        } else {
            null
        }
        val target = exact ?: placeholder
        if (target != null) {
            return transitioned.map { message ->
                if (message === target) {
                    target.copy(
                        id = thinkingId,
                        content = target.content + delta,
                        isStreaming = true,
                        elapsedSeconds = elapsedSeconds,
                        collapsed = false,
                    )
                } else {
                    message
                }
            }
        }

        return transitioned + ThinkingMessageUi(
            id = thinkingId,
            content = delta,
            isStreaming = true,
            elapsedSeconds = elapsedSeconds,
            collapsed = false,
        )
    }

    fun ensureCompletedThinking(
        runId: String,
        round: Int,
        content: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        // 内容为空白时不建条目：模型没吐 reasoning（普通对话、纯工具轮）时，
        // 过去会落一条「思考已完成」，展开后里面什么都没有。占位思考也要一并丢弃。
        if (content.isBlank()) {
            // 没有占位思考时原样返回同一列表，避免调用方误判为有变化而刷新与写库。
            if (messages.none { it is ThinkingMessageUi && it.isPendingPlaceholder() }) {
                return messages
            }
            return finalizeThinkingRound(runId, round, messages.dropPendingThinking(runId, round))
        }
        // 只有占位思考（还没收到任何分片）时，直接把权威内容写进这条，避免多出一条空块。
        val pending = messages.firstOrNull {
            it is ThinkingMessageUi && it.isPendingPlaceholder() &&
                isThinkingMessageForRound(it.id, runId, round)
        }
        if (pending != null) {
            val filled = messages.map { message ->
                if (message === pending) {
                    (message as ThinkingMessageUi).copy(
                        content = content,
                        isStreaming = false,
                        elapsedSeconds = elapsedSeconds(message.id),
                        collapsed = true,
                    )
                } else {
                    message
                }
            }
            return finalizeThinkingRound(runId, round, filled)
        }
        if (messages.any {
                it is ThinkingMessageUi && isThinkingMessageForRound(it.id, runId, round)
            }
        ) {
            return finalizeThinkingRound(runId, round, messages)
        }

        val thinkingId = thinkingFallbackMessageId(runId, round)
        return messages.insertBeforeFirstAssistant(
            runId = runId,
            round = round,
            message = ThinkingMessageUi(
                id = thinkingId,
                content = content,
                isStreaming = false,
                elapsedSeconds = elapsedSeconds(thinkingId),
                collapsed = true,
            ),
        )
    }

    fun finalizeThinking(runId: String, messages: List<AgentChatMessageUi>): List<AgentChatMessageUi> =
        messages
            .filterNot { it is ThinkingMessageUi && it.isPendingPlaceholder() }
            .map { message ->
                if (message is ThinkingMessageUi && message.id.startsWith("$runId-thinking-")) {
                    message.finished()
                } else {
                    message
                }
            }

    /** 终态不依赖各块结束事件全部到齐；缺少工具结果时只能标为未知，不能推断执行成功。 */
    fun finalizeRun(runId: String, messages: List<AgentChatMessageUi>): List<AgentChatMessageUi> =
        finalizeText(runId, finalizeThinking(runId, messages)).map { message ->
            if (
                message is ToolActivityMessageUi &&
                message.id.startsWith("$runId-tool-") &&
                message.status == ToolActivityStatusUi.Running
            ) {
                message.copy(status = ToolActivityStatusUi.Unknown)
            } else {
                message
            }
        }

    fun finalizeThinkingRound(
        runId: String,
        round: Int,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        return messages.map { message ->
            if (message is ThinkingMessageUi && isThinkingMessageForRound(message.id, runId, round)) {
                message.finished()
            } else {
                message
            }
        }
    }

    fun finalizeThinkingBlock(
        runId: String,
        round: Int,
        index: Int,
        replacementContent: String?,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        return messages.map { message ->
            // 占位思考沿用了本轮第一条，块结束时按轮次收尾而不只认 index id。
            if (message is ThinkingMessageUi && isThinkingMessageForRound(message.id, runId, round)) {
                message.finished(replacementContent)
            } else {
                message
            }
        }
    }

    fun finalizeText(
        runId: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> =
        messages.map { message ->
            if (message is AgentMessageUi && isAssistantMessageForRun(message.id, runId)) {
                message.copy(
                    content = message.content.trimEnd(),
                    isStreaming = false,
                    renderMarkdown = true,
                )
            } else {
                message
            }
        }

    fun finalizeTextRound(
        runId: String,
        round: Int,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        // 定稿时裁掉尾部空白：模型输出常以换行收尾，Markdown 渲染会把每个尾部
        // 换行节点变成一段固定间距，在正文与后续工具卡片之间形成莫名的空行。
        return messages.map { message ->
            if (message is AgentMessageUi && isAssistantMessageForRound(message.id, runId, round)) {
                message.copy(
                    content = message.content.trimEnd(),
                    isStreaming = false,
                    renderMarkdown = true,
                )
            } else {
                message
            }
        }
    }

    fun finalizeTextBlock(
        runId: String,
        round: Int,
        index: Int,
        replacementContent: String?,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val assistantId = assistantMessageId(runId, round, index)
        return messages.map { message ->
            if (message is AgentMessageUi && message.id == assistantId) {
                message.copy(
                    content = replacementContent?.trimEnd() ?: message.content.trimEnd(),
                    isStreaming = false,
                    renderMarkdown = true,
                )
            } else {
                message
            }
        }
    }

    fun startTool(
        runId: String,
        event: AgentEvent.ToolStarted,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        // 工具执行发生在对应 assistant 工具块完整返回之后；此时直接追加即可保留
        // 工具前说明、工具活动与下一轮结果的真实时间顺序。
        val message = ToolActivityMessageUi(
            id = toolActivityMessageId(runId, event.round, event.toolCallId),
            toolName = event.name,
            status = ToolActivityStatusUi.Running,
            argumentsSummary = event.argsPreview,
            command = event.command,
        )
        if (messages.any { it.id == message.id }) return messages
        return messages + message
    }

    fun finishTool(
        runId: String,
        event: AgentEvent.ToolFinished,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val targetId = toolActivityMessageId(runId, event.round, event.toolCallId)
        // success 字段优先；旧版本 Runtime/归档事件缺省时回退到摘要文本判断
        val status = when (event.success) {
            true -> ToolActivityStatusUi.Success
            false -> ToolActivityStatusUi.Failed
            null -> if (event.resultSummary.contains("ok=false", ignoreCase = true)) {
                ToolActivityStatusUi.Failed
            } else {
                ToolActivityStatusUi.Success
            }
        }
        val targetIndex = messages.indexOfLast { it is ToolActivityMessageUi && it.id == targetId }
        if (targetIndex < 0) return messages

        return messages.mapIndexed { index, message ->
            if (index == targetIndex && message is ToolActivityMessageUi) {
                message.copy(
                    status = status,
                    command = event.command ?: message.command,
                    resultSummary = event.resultSummary,
                    imageCount = event.imageCount,
                    images = event.artifacts,
                )
            } else {
                message
            }
        }
    }

    fun updateSubagentProgress(
        runId: String,
        event: AgentEvent.SubagentProgress,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val targetId = toolActivityMessageId(runId, event.round, event.toolCallId)
        val targetIndex = messages.indexOfLast { it is ToolActivityMessageUi && it.id == targetId }
        if (targetIndex < 0) return messages
        return messages.mapIndexed { index, message ->
            if (index == targetIndex && message is ToolActivityMessageUi) {
                message.copy(command = event.snapshotJson)
            } else {
                message
            }
        }
    }

    fun startHostedTool(
        runId: String,
        event: AgentEvent.HostedToolStarted,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val message = ToolActivityMessageUi(
            id = toolActivityMessageId(runId, event.round, event.toolCallId),
            toolName = event.name,
            status = ToolActivityStatusUi.Running,
            argumentsSummary = "",
        )
        return if (messages.any { it.id == message.id }) messages else messages + message
    }

    fun finishHostedTool(
        runId: String,
        event: AgentEvent.HostedToolFinished,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val targetId = toolActivityMessageId(runId, event.round, event.toolCallId)
        return messages.map { message ->
            if (message is ToolActivityMessageUi && message.id == targetId) {
                message.copy(
                    status = if (event.success) ToolActivityStatusUi.Success else ToolActivityStatusUi.Failed,
                    resultSummary = null,
                )
            } else {
                message
            }
        }
    }

    fun failRunningTools(
        reason: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> =
        messages.map { message ->
            if (message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running) {
                message.copy(
                    status = ToolActivityStatusUi.Failed,
                    resultSummary = reason.take(MAX_TOOL_RESULT_PREVIEW_CHARS),
                )
            } else {
                message
            }
        }

    fun interruptRunningTools(
        reason: String,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> =
        messages.map { message ->
            if (message is ToolActivityMessageUi && message.status == ToolActivityStatusUi.Running) {
                message.copy(
                    status = ToolActivityStatusUi.Unknown,
                    resultSummary = reason.take(MAX_TOOL_RESULT_PREVIEW_CHARS),
                )
            } else {
                message
            }
        }

    fun clearRun(runId: String) {
        thinkingStartedAt.keys.removeAll { it.startsWith("$runId-thinking-") }
    }

    private fun ThinkingMessageUi.finished(authoritativeContent: String? = null): ThinkingMessageUi =
        copy(
            content = authoritativeContent ?: content,
            isStreaming = false,
            elapsedSeconds = thinkingStartedAt[id]?.let { startedAt ->
                ((nowElapsedRealtime() - startedAt) / 1000).toInt().coerceAtLeast(0)
            } ?: elapsedSeconds,
            collapsed = true,
        )

    /** 占位思考：已创建但还没收到任何分片，界面上只显示动画。 */
    private fun ThinkingMessageUi.isPendingPlaceholder(): Boolean =
        content.isBlank() && isStreaming

    private fun List<AgentChatMessageUi>.dropPendingThinking(
        runId: String,
        round: Int,
    ): List<AgentChatMessageUi> = filterNot {
        it is ThinkingMessageUi && it.isPendingPlaceholder() && isThinkingMessageForRound(it.id, runId, round)
    }

    /**
     * 部分中转站会把上一轮的 reasoning 原样回吐，导致连续两轮思考内容完全相同。
     * 只把「与本 run 上一条已完成思考重复」的条目标记，不改内容也不删条目。
     */
    private fun elapsedSeconds(thinkingId: String): Int {
        val startedAt = thinkingStartedAt.getOrPut(thinkingId, nowElapsedRealtime)
        return ((nowElapsedRealtime() - startedAt) / 1000).toInt().coerceAtLeast(0)
    }

    private fun transitionVisibleBlock(
        runId: String,
        round: Int,
        kind: AgentEvent.AssistantBlockKind,
        index: Int,
        messages: List<AgentChatMessageUi>,
    ): List<AgentChatMessageUi> {
        val activeAssistantId = if (kind == AgentEvent.AssistantBlockKind.TEXT) {
            assistantMessageId(runId, round, index)
        } else {
            null
        }
        val activeThinkingId = if (kind == AgentEvent.AssistantBlockKind.THINKING) {
            thinkingMessageId(runId, round, index)
        } else {
            null
        }
        return messages.map { message ->
            when {
                message is AgentMessageUi &&
                    message.isStreaming &&
                    isAssistantMessageForRound(message.id, runId, round) &&
                    message.id != activeAssistantId ->
                    message.copy(
                        content = message.content.trimEnd(),
                        isStreaming = false,
                        renderMarkdown = true,
                    )

                message is ThinkingMessageUi &&
                    message.isStreaming &&
                    isThinkingMessageForRound(message.id, runId, round) &&
                    message.id != activeThinkingId ->
                    message.finished()

                else -> message
            }
        }
    }

    private fun List<AgentChatMessageUi>.insertBeforeFirstAssistant(
        runId: String,
        round: Int,
        message: AgentChatMessageUi,
    ): List<AgentChatMessageUi> {
        val assistantIndex = indexOfFirst {
            it is AgentMessageUi && isAssistantMessageForRound(it.id, runId, round)
        }
        return if (assistantIndex >= 0) {
            toMutableList().apply { add(assistantIndex, message) }
        } else {
            this + message
        }
    }

    private fun assistantMessageId(runId: String, round: Int, index: Int): String =
        "${assistantMessagePrefix(runId)}$round-$index"

    private fun thinkingMessageId(runId: String, round: Int, index: Int): String =
        "$runId-thinking-$round-$index"

    /** 每轮唯一：等待首 token 的占位思考。收到首个分片后会被改造成真实 id。 */
    private fun thinkingPlaceholderId(runId: String, round: Int): String =
        "$runId-thinking-$round-pending"

    private fun thinkingFallbackMessageId(runId: String, round: Int): String =
        "$runId-thinking-$round-fallback"

    private fun isAssistantMessageForRound(messageId: String, runId: String, round: Int): Boolean {
        val legacyId = "${assistantMessagePrefix(runId)}$round"
        return messageId == legacyId || messageId.startsWith("$legacyId-")
    }

    private fun isThinkingMessageForRound(messageId: String, runId: String, round: Int): Boolean {
        val prefix = "$runId-thinking-$round"
        return messageId == prefix || messageId.startsWith("$prefix-")
    }

    private fun toolActivityMessageId(runId: String, round: Int, toolCallId: String): String =
        "$runId-tool-$round-${toolCallId.ifBlank { "unknown" }}"

    companion object {
        /**
         * 手动压缩 run 的结果与压缩事件共用同一条时间线标记：事件标记已携带压缩前后的
         * token 信息，成功时原样保留；仍在进行中、失败或事件缺失时才改写或补写。
         */
        fun mergeCompactionResultNotice(
            runId: String,
            messages: List<AgentChatMessageUi>,
            ok: Boolean,
            detail: String,
        ): List<AgentChatMessageUi> {
            val index = messages.indexOfLast {
                it is SystemNoticeMessageUi && it.code == SystemNoticeCode.ContextCompaction &&
                    it.id.startsWith("assistant-$runId-compaction-")
            }
            if (index < 0) {
                return messages + SystemNoticeMessageUi(
                    id = "assistant-$runId-compaction-result",
                    code = SystemNoticeCode.ContextCompaction,
                    detail = detail,
                )
            }
            val notice = messages[index] as SystemNoticeMessageUi
            if (ok && !notice.running) return messages
            return messages.mapIndexed { i, message ->
                if (i == index && message is SystemNoticeMessageUi) {
                    message.copy(detail = detail, running = false)
                } else {
                    message
                }
            }
        }

        /** 终态只能补全最后一次重试之后的回答，不能覆盖已标记失败的半截输出。 */
        fun resultTargetIndex(
            runId: String,
            messages: List<AgentChatMessageUi>,
            includeNotices: Boolean = false,
        ): Int {
            val retryIndex = lastRetryIndex(runId, messages)
            return messages.indices.lastOrNull { index ->
                val message = messages[index]
                index > retryIndex &&
                    (message is AgentMessageUi || includeNotices && message is SystemNoticeMessageUi) &&
                    isAssistantMessageForRun(message.id, runId)
            } ?: -1
        }

        fun resultFallbackId(runId: String, messages: List<AgentChatMessageUi>): String {
            val retry = messages.getOrNull(lastRetryIndex(runId, messages))
            if (retry == null) return "assistant-$runId-1"
            val round = retry.id.substringAfterLast('-').toIntOrNull()?.plus(1) ?: 1
            return "assistant-$runId-$round-result"
        }

        /**
         * 用终态补全最后一条回答：notice 为 null 时写入正文，否则替换为系统提示。
         * 同一轮已有多个文本块时，流式内容已完整，终态正文只在当前块为空时补写。
         */
        fun applyResult(
            runId: String,
            messages: List<AgentChatMessageUi>,
            content: String,
            notice: SystemNoticeCode?,
            detail: String? = null,
        ): List<AgentChatMessageUi> {
            val targetIndex = resultTargetIndex(runId, messages)
            if (targetIndex < 0) {
                val id = resultFallbackId(runId, messages)
                return messages + if (notice == null) {
                    AgentMessageUi(id = id, content = content, isStreaming = false, renderMarkdown = true)
                } else {
                    SystemNoticeMessageUi(id, notice, detail)
                }
            }
            val target = messages[targetIndex] as AgentMessageUi
            val replacement = if (notice != null) SystemNoticeMessageUi(target.id, notice, detail) else {
                val round = target.id.assistantRound(runId)
                val sameRoundBlocks = round?.let {
                    messages.count { message -> message is AgentMessageUi && message.id.assistantRound(runId) == it }
                } ?: 0
                target.copy(
                    content = if (sameRoundBlocks <= 1) content else target.content.ifBlank { content },
                    isStreaming = false,
                    renderMarkdown = true,
                )
            }
            return messages.mapIndexed { index, message -> if (index == targetIndex) replacement else message }
        }

        private fun String.assistantRound(runId: String): Int? =
            removePrefix(assistantMessagePrefix(runId))
                .takeIf { it != this }
                ?.substringBefore('-')
                ?.toIntOrNull()

        private fun lastRetryIndex(runId: String, messages: List<AgentChatMessageUi>): Int =
            messages.indexOfLast {
                it is SystemNoticeMessageUi && it.code == SystemNoticeCode.ModelRetry &&
                    it.id.startsWith("assistant-$runId-retry-")
            }

        private fun assistantMessagePrefix(runId: String): String =
            "assistant-$runId-"

        private fun isAssistantMessageForRun(messageId: String, runId: String): Boolean =
            messageId == "assistant-$runId" ||
                messageId.startsWith(assistantMessagePrefix(runId))
    }

}

private const val MAX_TOOL_RESULT_PREVIEW_CHARS = 48

internal fun AgentTokenUsage.toUi(): TokenUsageUi =
    TokenUsageUi(
        contextTokens = contextTokens,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedTokens = cachedTokens,
        cacheWriteTokens = cacheWriteTokens,
        requestKind = requestKind,
        stepCount = stepCount,
        llmMs = llmMs,
        toolMs = toolMs,
        ttftMs = ttftMs,
        ttftSteps = ttftSteps,
        decodeMs = decodeMs,
        decodeTokens = decodeTokens,
    )
