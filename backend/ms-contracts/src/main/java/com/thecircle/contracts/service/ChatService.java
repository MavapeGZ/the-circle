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
import java.util.List;
import java.util.UUID;

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
    @Transactional
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
                    c.setCreatedAt(LocalDateTime.now());
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
        return conversationRepository
                .findByOwnerIdOrInitiatorIdOrderByLastMessageAtDesc(callerId, callerId)
                .stream()
                .map(c -> toDto(c, callerId))
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

    private ConversationDto toDto(Conversation c, String callerId) {
        String otherUserId = callerId.equals(c.getOwnerId()) ? c.getInitiatorId() : c.getOwnerId();
        Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
        long unread = messageRepository
                .countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), callerId);
        return new ConversationDto(c.getId(), c.getArticleId(), c.getOwnerId(), c.getInitiatorId(),
                otherUserId, c.getCreatedAt(), c.getLastMessageAt(), last == null ? null : last.getBody(), unread);
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(),
                m.getBody(), m.getCreatedAt(), m.getReadAt());
    }
}
