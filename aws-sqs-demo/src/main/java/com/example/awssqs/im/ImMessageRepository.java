package com.example.awssqs.im;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * IM消息持久化Repository
 */
@Repository
public interface ImMessageRepository extends JpaRepository<ImMessagePersistence, Long> {

    Optional<ImMessagePersistence> findByMessageId(String messageId);

    Optional<ImMessagePersistence> findByClientMessageId(String clientMessageId);

    @Query("SELECT m FROM ImMessagePersistence m WHERE m.conversationId = :conversationId " +
           "ORDER BY m.createdAt DESC LIMIT :limit")
    List<ImMessagePersistence> findByConversationIdOrderByCreatedAtDesc(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit);

    @Query("SELECT m FROM ImMessagePersistence m WHERE m.status = :status " +
           "AND m.createdAt < :timeoutTime AND m.manualIntervention = false")
    List<ImMessagePersistence> findTimeoutMessages(
            @Param("status") ImMessagePersistence.ImMessageStatus status,
            @Param("timeoutTime") LocalDateTime timeoutTime);

    @Query("SELECT m FROM ImMessagePersistence m WHERE m.status IN :statuses " +
           "AND m.createdAt < :timeoutTime AND m.retryCount < m.maxRetryCount " +
           "AND m.manualIntervention = false")
    List<ImMessagePersistence> findRetryableMessages(
            @Param("statuses") List<ImMessagePersistence.ImMessageStatus> statuses,
            @Param("timeoutTime") LocalDateTime timeoutTime);

    @Query("SELECT m FROM ImMessagePersistence m WHERE m.inDlq = true AND m.status = :status")
    List<ImMessagePersistence> findMessagesInDlq(@Param("status") ImMessagePersistence.ImMessageStatus status);

    long countByStatus(ImMessagePersistence.ImMessageStatus status);

    boolean existsByClientMessageId(String clientMessageId);

    @Query("SELECT MAX(m.sequenceNumber) FROM ImMessagePersistence m WHERE m.conversationId = :conversationId")
    Long findMaxSequenceNumber(@Param("conversationId") String conversationId);
}
