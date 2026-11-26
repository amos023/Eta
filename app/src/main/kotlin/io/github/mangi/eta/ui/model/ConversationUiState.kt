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

/** 项目资料：项目内共享的文件条目。 */
@Immutable
data class ConversationGroupFileUi(
    val id: String,
    val groupId: String,
    val name: String,
    val mime: String = "",
    val path: String = "",
    val sizeBytes: Long = 0L,
    val createdAt: Long = 0L,
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
