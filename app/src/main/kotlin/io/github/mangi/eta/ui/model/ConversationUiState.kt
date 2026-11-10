package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationPaneUiState(
    val conversations: List<ConversationSummaryUi>,
    val selectedConversationId: String?,
    val searchQuery: String,
    /** 手动分组；按 sortOrder 升序，用于侧栏分组头。 */
    val groups: List<ConversationGroupUi> = emptyList(),
)

/** 侧栏分组头；成员关系通过 [ConversationSummaryUi.groupId] 关联。 */
@Immutable
data class ConversationGroupUi(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

@Immutable
data class ConversationSummaryUi(
    val id: String,
    val title: String,
    val preview: String,
    val timeLabel: String,
    val updatedAtMillis: Long = 0L,
    val mode: ConversationModeUi,
    val isPinned: Boolean = false,
    val isActiveRun: Boolean = false,
    val runStatus: String? = null,
    val characterName: String? = null,
    val characterAvatarPath: String? = null,
    /** 分支来源会话；主线会话为 null。 */
    val parentConversationId: String? = null,
    /** 手动归入的分组；null 表示未分组。 */
    val groupId: String? = null,
)

@Immutable
enum class ConversationModeUi {
    Chat,
    PhoneAgent,
    Terminal,
    Automation,
}
