package com.thecircle.contracts.service;

import com.thecircle.contracts.client.CatalogClient;
import com.thecircle.contracts.dto.ConversationDto;
import com.thecircle.contracts.dto.MessageDto;
import com.thecircle.contracts.model.Conversation;
import com.thecircle.contracts.model.Message;
import com.thecircle.contracts.repository.ConversationRepository;
import com.thecircle.contracts.repository.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chat between an article's owner and an interested user. A conversation always
 * references an article and never requires a contract: users can talk before,
 * during and after a deal until the item is delivered.
 */
@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CatalogClient catalogClient;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       CatalogClient catalogClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.catalogClient = catalogClient;
    }

    /**
     * Returns the caller's conversation about {@code articleId}, creating it if it
     * does not exist. The caller becomes the initiator; the article author is the
     * owner. The author cannot start a conversation about their own article (there
     * is no second party to talk to), and an article that cannot be resolved is a
     * 404.
     */
    // Not @Transactional: the catalog lookup is a synchronous cross-service HTTP
    // call and must not hold a DB connection/transaction across the round-trip.
    // The individual repository operations are transactional on their own, and the
    // unique-constraint race is handled explicitly below.
    public ConversationDto startOrGet(String callerId, String articleId) {
        CatalogClient.ArticleSnapshot article = catalogClient.getArticle(articleId);
        if (article == null || article.authorId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }
        String ownerId = String.valueOf(article.authorId());
        if (ownerId.equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot start a conversation about your own article.");
        }
        Conversation conversation = conversationRepository
                .findByArticleIdAndInitiatorId(articleId, callerId)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setId(UUID.randomUUID().toString());
                    c.setArticleId(articleId);
                    c.setOwnerId(ownerId);
                    c.setInitiatorId(callerId);
                    LocalDateTime now = LocalDateTime.now();
                    c.setCreatedAt(now);
                    // Seed lastMessageAt so an empty conversation sorts by its creation
                    // time instead of as a NULL (which Postgres orders FIRST in DESC).
                    c.setLastMessageAt(now);
                    try {
                        return conversationRepository.save(c);
                    } catch (DataIntegrityViolationException race) {
                        // A concurrent request (e.g. a double click) just created it; the
                        // unique (article_id, initiator_id) constraint rejected this one.
                        return conversationRepository.findByArticleIdAndInitiatorId(articleId, callerId)
                                .orElseThrow(() -> race);
                    }
                });
        return toDto(conversation, callerId);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listForUser(String callerId) {
        List<Conversation> conversations = conversationRepository
                .findByOwnerIdOrInitiatorIdOrderByLastMessageAtDesc(callerId, callerId);
        if (conversations.isEmpty()) return List.of();

        // Batch the last-message preview and unread counts in two queries instead
        // of two per conversation (avoids an N+1 on the inbox).
        List<String> ids = conversations.stream().map(Conversation::getId).toList();
        Map<String, String> lastBodyById = messageRepository.findLatestPerConversation(ids).stream()
                .collect(Collectors.toMap(Message::getConversationId, Message::getBody, (a, b) -> a));
        Map<String, Long> unreadById = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByConversation(ids, callerId)) {
            unreadById.put((String) row[0], ((Number) row[1]).longValue());
        }

        return conversations.stream()
                .map(c -> toDto(c, callerId,
                        lastBodyById.get(c.getId()),
                        unreadById.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /**
     * Returns the thread's messages and marks the ones addressed to the caller as
     * read. Only the two participants may read a conversation.
     */
    @Transactional
    public List<MessageDto> getMessages(String conversationId, String callerId) {
        Conversation conversation = requireParticipant(conversationId, callerId);
        messageRepository.markRead(conversation.getId(), callerId, LocalDateTime.now());
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public MessageDto sendMessage(String conversationId, String callerId, String body) {
        Conversation conversation = requireParticipant(conversationId, callerId);
        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setConversationId(conversation.getId());
        message.setSenderId(callerId);
        message.setBody(body);
        message.setCreatedAt(now);
        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);
        return toDto(saved);
    }

    private Conversation requireParticipant(String conversationId, String callerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.isParticipant(callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this conversation");
        }
        return conversation;
    }

    /**
     * Deletes every conversation the user takes part in, with its messages. Called
     * when an account is removed so no chat history outlives the user.
     */
    @Transactional
    public void deleteAllForUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        List<Conversation> conversations = conversationRepository
                .findByOwnerIdOrInitiatorIdOrderByLastMessageAtDesc(userId, userId);
        if (conversations.isEmpty()) return;
        List<String> ids = conversations.stream().map(Conversation::getId).toList();
        messageRepository.deleteByConversationIdIn(ids);
        conversationRepository.deleteAll(conversations);
    }

    // Single-conversation variant (creation / fetch-one): resolves preview + unread
    // on its own. The inbox list uses the batched overload to avoid an N+1.
    private ConversationDto toDto(Conversation c, String callerId) {
        Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
        long unread = messageRepository
                .countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), callerId);
        return toDto(c, callerId, last == null ? null : last.getBody(), unread);
    }

    private ConversationDto toDto(Conversation c, String callerId, String lastMessage, long unread) {
        String otherUserId = callerId.equals(c.getOwnerId()) ? c.getInitiatorId() : c.getOwnerId();
        return new ConversationDto(c.getId(), c.getArticleId(), c.getOwnerId(), c.getInitiatorId(),
                otherUserId, c.getCreatedAt(), c.getLastMessageAt(), lastMessage, unread);
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(),
                m.getBody(), m.getCreatedAt(), m.getReadAt());
    }
}
