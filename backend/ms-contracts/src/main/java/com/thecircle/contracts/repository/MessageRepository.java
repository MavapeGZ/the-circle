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

    // Last message of a thread, for the inbox preview (avoids loading the whole thread).
    Message findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    // Unread messages addressed to the caller (i.e. not sent by them) in a thread.
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(String conversationId, String senderId);

    // Batched inbox helpers: resolve preview + unread for many threads in one query
    // each, instead of two queries per conversation (avoids an N+1 on the inbox).
    @Query("select m from Message m where m.conversationId in :ids "
            + "and m.createdAt = (select max(m2.createdAt) from Message m2 where m2.conversationId = m.conversationId)")
    List<Message> findLatestPerConversation(@Param("ids") List<String> ids);

    @Query("select m.conversationId, count(m) from Message m where m.conversationId in :ids "
            + "and m.senderId <> :callerId and m.readAt is null group by m.conversationId")
    List<Object[]> countUnreadByConversation(@Param("ids") List<String> ids, @Param("callerId") String callerId);

    @Modifying
    @Query("update Message m set m.readAt = :now "
            + "where m.conversationId = :conversationId and m.senderId <> :readerId and m.readAt is null")
    int markRead(@Param("conversationId") String conversationId,
                 @Param("readerId") String readerId,
                 @Param("now") LocalDateTime now);

    void deleteByConversationIdIn(List<String> conversationIds);
}
