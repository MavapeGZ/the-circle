package com.thecircle.contracts.controllers;

import com.thecircle.contracts.dto.ConversationDto;
import com.thecircle.contracts.dto.MessageDto;
import com.thecircle.contracts.dto.SendMessageRequest;
import com.thecircle.contracts.dto.StartConversationRequest;
import com.thecircle.contracts.security.JwtAuthService;
import com.thecircle.contracts.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chat endpoints. ms-contracts is not gateway-authenticated, so every call
 * verifies the bearer token itself (same shared JWT secret as ms-users) and
 * authorizes access against the conversation's two participants.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final JwtAuthService jwtAuthService;

    public ChatController(ChatService chatService, JwtAuthService jwtAuthService) {
        this.chatService = chatService;
        this.jwtAuthService = jwtAuthService;
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> startConversation(
            @Valid @RequestBody StartConversationRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        return ResponseEntity.ok(chatService.startOrGet(callerId, request.articleId()));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> listConversations(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        return ResponseEntity.ok(chatService.listForUser(callerId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable String conversationId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        return ResponseEntity.ok(chatService.getMessages(conversationId, callerId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(conversationId, callerId, request.body()));
    }
}
