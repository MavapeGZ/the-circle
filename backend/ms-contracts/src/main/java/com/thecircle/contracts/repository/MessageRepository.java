package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    // Unread messages addressed to the caller (i.e. not sent by them) in a thread.
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(String conversationId, String senderId);

    @Modifying
    @Query("update Message m set m.readAt = :now "
            + "where m.conversationId = :conversationId and m.senderId <> :readerId and m.readAt is null")
    int markRead(@Param("conversationId") String conversationId,
                 @Param("readerId") String readerId,
                 @Param("now") LocalDateTime now);

    void deleteByConversationIdIn(List<String> conversationIds);
}
