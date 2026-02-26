package com.example.awssqs.repository;

import com.example.awssqs.domain.MessagePersistence;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 消息持久化Repository
 */
@Repository
public interface MessagePersistenceRepository extends JpaRepository<MessagePersistence, Long> {

    /**
     * 根据消息ID查找
     */
    Optional<MessagePersistence> findByMessageId(String messageId);

    /**
     * 根据消息ID和队列查找
     */
    List<MessagePersistence> findByMessageIdAndQueueName(String messageId, String queueName);

    /**
     * 查找指定状态且在指定时间之前创建的消息
     * 用于对账和超时检测
     */
    List<MessagePersistence> findByStatusAndCreatedAtBefore(MessageStatus status, LocalDateTime beforeTime);

    /**
     * 查找指定队列和状态的消息
     */
    List<MessagePersistence> findByQueueNameAndStatus(String queueName, MessageStatus status);

    /**
     * 查找超时的待处理消息
     */
    @Query("SELECT m FROM MessagePersistence m WHERE m.status = :status " +
           "AND m.createdAt < :timeoutTime " +
           "ORDER BY m.createdAt ASC")
    List<MessagePersistence> findTimeoutPendingMessages(
            @Param("status") MessageStatus status,
            @Param("timeoutTime") LocalDateTime timeoutTime);

    /**
     * 统计指定状态的消息数量
     */
    long countByStatus(MessageStatus status);

    /**
     * 删除指定时间之前的状态为PENDING的消息
     * 用于清理长时间未发送的消息
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MessagePersistence m WHERE m.status = 'PENDING' AND m.createdAt < :beforeTime")
    void deleteOldPendingMessages(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 查找需要确认的消息（已发送但未确认）
     */
    @Query("SELECT m FROM MessagePersistence m WHERE m.status = 'SENT' " +
           "AND m.sentAt < :beforeTime")
    List<MessagePersistence> findUnacknowledgedMessages(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 查找指定队列未处理的消息
     */
    @Query("SELECT COUNT(m) FROM MessagePersistence m WHERE m.queueName = :queueName AND m.status IN :statuses")
    long countByQueueNameAndStatuses(@Param("queueName") String queueName,
                                        @Param("statuses") List<MessageStatus> statuses);
}
