package com.example.awssqs.im;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * IM消息实体
 * 用于实时通讯系统的消息载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessage {

    /**
     * 消息唯一ID（客户端生成，用于幂等性）
     */
    private String messageId;

    /**
     * 客户端消息ID（用于去重确认）
     */
    private String clientMessageId;

    /**
     * 会话ID（单聊/群聊）
     */
    private String conversationId;

    /**
     * 发送者ID
     */
    private String senderId;

    /**
     * 接收者ID（单聊）或群组ID（群聊）
     */
    private String receiverId;

    /**
     * 消息类型：TEXT, IMAGE, VIDEO, AUDIO, FILE, SYSTEM
     */
    private MessageType messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息元数据（JSON格式，存储扩展信息）
     */
    private String metadata;

    /**
     * 消息优先级：HIGH（系统消息）, NORMAL（普通消息）, LOW（离线消息同步）
     */
    private MessagePriority priority;

    /**
     * 是否需要已读回执
     */
    @Builder.Default
    private Boolean requireReadReceipt = false;

    /**
     * 消息创建时间戳
     */
    private Long timestamp;

    /**
     * 消息过期时间（秒），0表示永不过期
     */
    @Builder.Default
    private Integer expireInSeconds = 0;

    /**
     * 租户ID（多租户场景）
     */
    private Long tenantId;

    /**
     * 设备ID（用于多端同步）
     */
    private String deviceId;

    /**
     * 消息序号（用于排序和去重）
     */
    private Long sequenceNumber;

    public enum MessageType {
        TEXT,       // 文本消息
        IMAGE,      // 图片消息
        VIDEO,      // 视频消息
        AUDIO,      // 语音消息
        FILE,       // 文件消息
        SYSTEM,     // 系统消息
        TYPING,     // 输入状态
        RECALL,     // 撤回消息
        REPLY,      // 回复消息
        FORWARD     // 转发消息
    }

    public enum MessagePriority {
        HIGH,       // 高优先级：系统通知、撤回、敏感操作
        NORMAL,     // 普通优先级：正常聊天消息
        LOW         // 低优先级：历史消息同步
    }

    /**
     * 获取消息去重键
     */
    @JsonIgnore
    public String getDeduplicationKey() {
        return clientMessageId != null ? clientMessageId : messageId;
    }

    /**
     * 获取FIFO消息组ID（按会话分组，保证同一会话消息有序）
     */
    @JsonIgnore
    public String getMessageGroupId() {
        return "conv-" + conversationId;
    }

    /**
     * 检查消息是否已过期
     */
    @JsonIgnore
    public boolean isExpired() {
        if (expireInSeconds == null || expireInSeconds <= 0) {
            return false;
        }
        long expireTime = timestamp + (expireInSeconds * 1000L);
        return Instant.now().toEpochMilli() > expireTime;
    }
}
