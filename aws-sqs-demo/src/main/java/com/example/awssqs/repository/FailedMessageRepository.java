package com.example.awssqs.repository;

import com.example.awssqs.domain.FailedMessage;
import com.example.awssqs.domain.FailedMessage.FailedMessageStatus;
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
 * 失败消息Repository
 */
@Repository
public interface FailedMessageRepository extends JpaRepository<FailedMessage, Long> {

    /**
     * 根据消息ID查找
     */
    Optional<FailedMessage> findByMessageId(String messageId);

    /**
     * 查找指定队列的失败消息
     */
    List<FailedMessage> findByQueueNameOrderByFailedAtDesc(String queueName);

    /**
     * 查找指定状态的消息
     */
    List<FailedMessage> findByStatusOrderByFailedAtDesc(FailedMessageStatus status);

    /**
     * 查找指定租户的失败消息
     */
    List<FailedMessage> findByTenantIdOrderByFailedAtDesc(Long tenantId);

    /**
     * 查找需要重试的消息（状态为PENDING且重试次数小于最大次数）
     */
    @Query("SELECT f FROM FailedMessage f WHERE f.status = 'PENDING' " +
           "AND f.retryCount < :maxRetryCount " +
           "ORDER BY f.createdAt ASC")
    List<FailedMessage> findPendingMessagesForRetry(@Param("maxRetryCount") int maxRetryCount);

    /**
     * 查找超时的PENDING消息
     */
    @Query("SELECT f FROM FailedMessage f WHERE f.status = 'PENDING' " +
           "AND f.createdAt < :timeoutTime " +
           "ORDER BY f.createdAt ASC")
    List<FailedMessage> findTimeoutPendingMessages(@Param("timeoutTime") LocalDateTime timeoutTime);

    /**
     * 统计指定状态的消息数量
     */
    long countByStatus(FailedMessageStatus status);

    /**
     * 查找租户+队列的失败消息数量
     */
    @Query("SELECT COUNT(f) FROM FailedMessage f WHERE f.tenantId = :tenantId " +
           "AND f.queueName = :queueName " +
           "AND f.status = :status")
    long countByTenantIdAndQueueNameAndStatus(@Param("tenantId") Long tenantId,
                                            @Param("queueName") String queueName,
                                            @Param("status") FailedMessageStatus status);

    /**
     * 删除指定时间之前的消息
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM FailedMessage f WHERE f.failedAt < :beforeTime")
    void deleteOldMessages(@Param("beforeTime") LocalDateTime beforeTime);
}
