package com.example.awssqs.im;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * IM消息投递服务
 * 负责将消息投递给接收方（在线/离线）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageDeliveryService {

    private final ImMessageRepository messageRepository;

    // TODO: 注入 WebSocket 推送服务
    // private final WebSocketPushService webSocketPushService;

    // TODO: 注入离线消息存储服务
    // private final OfflineMessageService offlineMessageService;

    // TODO: 注入推送服务（APNs, FCM等）
    // private final PushNotificationService pushNotificationService;

    /**
     * 投递消息给接收方
     */
    @Transactional
    public void deliverToReceiver(ImMessage message) {
        String messageId = message.getMessageId();
        String receiverId = message.getReceiverId();

        log.info("[IM-DELIVERY] Delivering message: messageId={}, receiverId={}", messageId, receiverId);

        try {
            // 1. 更新投递状态
            updateDeliveryStatus(messageId, ImMessagePersistence.DeliveryStatus.PUSHED);

            // 2. 检查接收方是否在线
            boolean isOnline = checkUserOnline(receiverId);

            if (isOnline) {
                // 3a. 在线用户：直接推送
                deliverToOnlineUser(message);
            } else {
                // 3b. 离线用户：存储离线消息
                storeOfflineMessage(message);
            }

            log.info("[IM-DELIVERY] Message delivered: messageId={}, online={}", messageId, isOnline);

        } catch (Exception e) {
            log.error("[IM-DELIVERY] Failed to deliver message: messageId={}, error={}",
                    messageId, e.getMessage(), e);
            updateDeliveryStatus(messageId, ImMessagePersistence.DeliveryStatus.FAILED);
            throw new RuntimeException("Delivery failed", e);
        }
    }

    /**
     * 投递系统消息
     */
    @Transactional
    public void deliverSystemMessage(ImMessage message) {
        log.info("[IM-DELIVERY] Delivering system message: messageId={}", message.getMessageId());

        // 系统消息通常需要广播给多个用户或特定群组
        // TODO: 实现系统消息投递逻辑
    }

    /**
     * 处理撤回消息
     */
    @Transactional
    public void handleRecallMessage(ImMessage message) {
        log.info("[IM-DELIVERY] Handling recall message: messageId={}", message.getMessageId());

        // 查找原始消息
        String originalMessageId = message.getContent(); // 假设content中存储了原始消息ID
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(originalMessageId);

        if (opt.isPresent()) {
            ImMessagePersistence original = opt.get();

            // 检查是否在撤回时间窗口内（如2分钟）
            if (canRecallMessage(original)) {
                // 标记原始消息为已撤回
                original.setStatus(ImMessagePersistence.ImMessageStatus.RECALLED);
                original.setUpdatedAt(LocalDateTime.now());
                messageRepository.save(original);

                // 通知接收方消息已撤回
                notifyRecall(original, message);
            }
        }
    }

    /**
     * 推送输入状态
     */
    public void pushTypingStatus(ImMessage message) {
        // 输入状态是实时的，不需要持久化
        // TODO: 通过WebSocket推送输入状态
        log.debug("[IM-DELIVERY] Pushing typing status: conversationId={}, senderId={}",
                message.getConversationId(), message.getSenderId());
    }

    // ============== 私有方法 ==============

    private boolean checkUserOnline(String userId) {
        // TODO: 检查用户是否在线（通过WebSocket连接状态或Redis）
        // 这里简化处理，返回false让消息存储为离线消息
        return false;
    }

    private void deliverToOnlineUser(ImMessage message) {
        // 通过WebSocket推送消息
        // TODO: webSocketPushService.push(message);

        // 更新投递状态
        updateDeliveryStatus(message.getMessageId(), ImMessagePersistence.DeliveryStatus.DELIVERED);

        log.info("[IM-DELIVERY] Message pushed to online user: messageId={}, receiverId={}",
                message.getMessageId(), message.getReceiverId());
    }

    private void storeOfflineMessage(ImMessage message) {
        // 存储离线消息
        // TODO: offlineMessageService.store(message);

        // 更新投递状态
        updateDeliveryStatus(message.getMessageId(), ImMessagePersistence.DeliveryStatus.STORED_OFFLINE);

        // 可选：发送推送通知
        // TODO: pushNotificationService.sendNotification(message);

        log.info("[IM-DELIVERY] Message stored as offline: messageId={}, receiverId={}",
                message.getMessageId(), message.getReceiverId());
    }

    private void updateDeliveryStatus(String messageId, ImMessagePersistence.DeliveryStatus status) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setDeliveryStatus(status);
            persistence.setUpdatedAt(LocalDateTime.now());

            if (status == ImMessagePersistence.DeliveryStatus.DELIVERED) {
                persistence.setDeliveredAt(LocalDateTime.now());
            }

            messageRepository.save(persistence);
        }
    }

    private boolean canRecallMessage(ImMessagePersistence original) {
        if (original.getCreatedAt() == null) {
            return false;
        }

        // 检查消息创建时间是否在撤回窗口内（2分钟）
        LocalDateTime recallDeadline = original.getCreatedAt().plusMinutes(2);
        return LocalDateTime.now().isBefore(recallDeadline);
    }

    private void notifyRecall(ImMessagePersistence original, ImMessage recallMessage) {
        // TODO: 通过WebSocket通知接收方消息已撤回
        log.info("[IM-DELIVERY] Notifying recall: originalMessageId={}, receiverId={}",
                original.getMessageId(), original.getReceiverId());
    }
}
