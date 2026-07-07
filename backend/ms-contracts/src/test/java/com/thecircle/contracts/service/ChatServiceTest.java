package com.thecircle.contracts.service;

import com.thecircle.contracts.client.CatalogClient;
import com.thecircle.contracts.dto.ConversationDto;
import com.thecircle.contracts.model.Conversation;
import com.thecircle.contracts.model.Message;
import com.thecircle.contracts.repository.ConversationRepository;
import com.thecircle.contracts.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock CatalogClient catalogClient;
    @Mock com.thecircle.contracts.i18n.Messages messages;
    @InjectMocks ChatService chatService;

    private CatalogClient.ArticleSnapshot article(long authorId) {
        return new CatalogClient.ArticleSnapshot("a1", authorId, "SYMBOLIC_SALE", 10.0, null);
    }

    @Test
    void startOrGet_ownerOfArticle_rejected() {
        when(catalogClient.getArticle("a1")).thenReturn(article(7L));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.startOrGet("7", "a1"));
        assertEquals(400, ex.getStatusCode().value());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void startOrGet_unknownArticle_404() {
        when(catalogClient.getArticle("a1")).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.startOrGet("3", "a1"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void startOrGet_createsWhenAbsent() {
        when(catalogClient.getArticle("a1")).thenReturn(article(7L));
        when(conversationRepository.findByArticleIdAndInitiatorId("a1", "3")).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationDto dto = chatService.startOrGet("3", "a1");

        assertEquals("7", dto.ownerId());
        assertEquals("3", dto.initiatorId());
        assertEquals("7", dto.otherUserId());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void getMessages_nonParticipant_forbidden() {
        Conversation c = conv("c1", "7", "3");
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(c));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.getMessages("c1", "99"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getMessages_participant_marksReadAndReturns() {
        Conversation c = conv("c1", "7", "3");
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(c));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(java.util.List.of());

        chatService.getMessages("c1", "3");

        verify(messageRepository).markRead(eq("c1"), eq("3"), any(LocalDateTime.class));
    }

    @Test
    void sendMessage_nonParticipant_forbidden() {
        Conversation c = conv("c1", "7", "3");
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(c));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage("c1", "99", "hi"));
        assertEquals(403, ex.getStatusCode().value());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_participant_persistsAndBumpsConversation() {
        Conversation c = conv("c1", "7", "3");
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(c));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        chatService.sendMessage("c1", "7", "hello");

        verify(messageRepository).save(any(Message.class));
        verify(conversationRepository).save(c);
        assertNotNull(c.getLastMessageAt());
    }

    private Conversation conv(String id, String ownerId, String initiatorId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setArticleId("a1");
        c.setOwnerId(ownerId);
        c.setInitiatorId(initiatorId);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }
}
