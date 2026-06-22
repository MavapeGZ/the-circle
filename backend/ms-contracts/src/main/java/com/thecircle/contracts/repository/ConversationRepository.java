package com.thecircle.contracts.repository;

import com.thecircle.contracts.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByArticleIdAndInitiatorId(String articleId, String initiatorId);

    // Every conversation the user takes part in, most recently active first.
    List<Conversation> findByOwnerIdOrInitiatorIdOrderByLastMessageAtDesc(String ownerId, String initiatorId);
}
