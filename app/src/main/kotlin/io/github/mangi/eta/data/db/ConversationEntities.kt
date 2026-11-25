package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.mangi.eta.data.model.ReasoningEffort
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "conversations")
internal data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "thinking_enabled") val thinkingEnabled: Boolean,
    @ColumnInfo(name = "reasoning_effort", defaultValue = "'default'")
    val reasoningEffort: String = ReasoningEffort.DEFAULT.wireValue,
    @ColumnInfo(name = "history_json") val historyJson: String = "[]",
    @ColumnInfo(name = "applied_runtime_run_ids_json") val appliedRuntimeRunIdsJson: String = "[]",
    @ColumnInfo(name = "roleplay_json", defaultValue = "''") val roleplayJson: String = "",
    @ColumnInfo(name = "revisions_json", defaultValue = "''") val revisionsJson: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 会话最近使用的本地 Model.id；null 表示跟随默认模型。 */
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
    /** 由「分支」创建时的来源会话；主线会话为 null。 */
    @ColumnInfo(name = "parent_conversation_id") val parentConversationId: String? = null,
    /** 手动归入的分组；null 表示未分组。 */
    @ColumnInfo(name = "group_id") val groupId: String? = null,
)

/**
 * 会话分组。用于把同一项目下的多个会话（含各自分支）收拢在一起。
 * 顺序由 sort_order 决定，UI 按此升序渲染。
 */
@Entity(
    tableName = "conversation_groups",
    indices = [Index(value = ["sort_order"])],
)
internal data class ConversationGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 项目说明：只在项目页展示，不进入提示词。 */
    @ColumnInfo(name = "description", defaultValue = "''") val description: String = "",
    /** 项目级自定义指令：项目内会话发起请求时注入系统提示。 */
    @ColumnInfo(name = "instructions", defaultValue = "''") val instructions: String = "",
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * 项目资料：项目内所有会话共享的文件。
 * 文件本体复制到应用私有目录，这里只留元数据与路径。
 */
@Entity(
    tableName = "conversation_group_files",
    indices = [Index(value = ["group_id"])],
    foreignKeys = [
        ForeignKey(
            entity = ConversationGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ConversationGroupFileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val name: String,
    /** MIME 类型：文本类直接读内容注入，图片类按附件引用。 */
    @ColumnInfo(name = "mime", defaultValue = "''") val mime: String = "",
    /** 应用私有目录内的绝对路径。 */
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "size_bytes", defaultValue = "0") val sizeBytes: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

internal data class ConversationMetadata(
    val id: String,
    val title: String,
    @ColumnInfo(name = "thinking_enabled") val thinkingEnabled: Boolean,
    @ColumnInfo(name = "reasoning_effort") val reasoningEffort: String,
    @ColumnInfo(name = "applied_runtime_run_ids_json") val appliedRuntimeRunIdsJson: String,
    @ColumnInfo(name = "roleplay_json") val roleplayJson: String = "",
    @ColumnInfo(name = "revisions_json") val revisionsJson: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "parent_conversation_id") val parentConversationId: String? = null,
    @ColumnInfo(name = "group_id") val groupId: String? = null,
)

/** 会话侧栏只读取定长预览，不读取上下文、角色卡与修订正文。 */
internal data class ConversationIndexRow(
    val id: String,
    val title: String,
    @ColumnInfo(name = "reasoning_effort") val reasoningEffort: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "message_id") val messageId: String?,
    @ColumnInfo(name = "message_type") val messageType: String?,
    @ColumnInfo(name = "message_preview") val messagePreview: String?,
    @ColumnInfo(name = "tool_name") val toolName: String?,
    @ColumnInfo(name = "has_roleplay") val hasRoleplay: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "parent_conversation_id") val parentConversationId: String? = null,
    @ColumnInfo(name = "group_id") val groupId: String? = null,
)

internal data class ConversationIdentity(
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
)

internal data class ConversationMessageContent(val id: String, val content: String)

@Serializable
@Entity(
    tableName = "conversation_context_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ConversationContextCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "history_json") val historyJson: String,
    @ColumnInfo(name = "journal_json", defaultValue = "''") val journalJson: String = "",
)

@Serializable
@Entity(tableName = "conversation_state")
internal data class ConversationStateEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "selected_conversation_id") val selectedConversationId: String,
) {
    companion object {
        const val SINGLETON_ID = "main"
    }
}

@Serializable
@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversation_id"),
        Index(value = ["conversation_id", "sort_index"], unique = true),
    ],
)
internal data class ConversationMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    val type: String,
    val content: String,
    @ColumnInfo(name = "images_json") val imagesJson: String = "[]",
    @ColumnInfo(name = "is_edited", defaultValue = "0") val isEdited: Boolean = false,
    @ColumnInfo(name = "render_markdown") val renderMarkdown: Boolean? = null,
    @ColumnInfo(name = "context_tokens") val contextTokens: Int? = null,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int? = null,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int? = null,
    @ColumnInfo(name = "reasoning_tokens") val reasoningTokens: Int? = null,
    @ColumnInfo(name = "cached_tokens") val cachedTokens: Int? = null,
    @ColumnInfo(name = "cache_write_tokens") val cacheWriteTokens: Int? = null,
    @ColumnInfo(name = "compaction_archive_id") val compactionArchiveId: String? = null,
    @ColumnInfo(name = "step_count") val stepCount: Int? = null,
    @ColumnInfo(name = "llm_ms") val llmMs: Long? = null,
    @ColumnInfo(name = "tool_ms") val toolMs: Long? = null,
    @ColumnInfo(name = "ttft_ms") val ttftMs: Long? = null,
    @ColumnInfo(name = "ttft_steps") val ttftSteps: Int? = null,
    @ColumnInfo(name = "decode_ms") val decodeMs: Long? = null,
    @ColumnInfo(name = "decode_tokens") val decodeTokens: Int? = null,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Int? = null,
    @ColumnInfo(name = "tool_name") val toolName: String? = null,
    @ColumnInfo(name = "tool_status") val toolStatus: String? = null,
    @ColumnInfo(name = "arguments_summary") val argumentsSummary: String? = null,
    @ColumnInfo(name = "result_summary") val resultSummary: String? = null,
    @ColumnInfo(name = "image_count") val imageCount: Int = 0,
    @ColumnInfo(name = "tools_json") val toolsJson: String = "[]",
)
