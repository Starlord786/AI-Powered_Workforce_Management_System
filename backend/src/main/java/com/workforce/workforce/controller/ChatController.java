package com.workforce.workforce.controller;

import com.workforce.workforce.ai.ChatService;
import com.workforce.workforce.ai.OllamaClient;
import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HR chatbot. Any logged-in user; each user only gets their own data. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(String question, List<ChatService.Msg> history) {}

    private final ChatService chat;
    private final UserRepository users;

    public ChatController(ChatService chat, UserRepository users) {
        this.chat = chat;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody ChatRequest req, @AuthenticationPrincipal Jwt jwt) {
        if (req.question() == null || req.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please type a question"));
        }
        AppUser user = users.findByEmail(jwt.getSubject()).orElse(null);
        if (user == null || user.getEmployee() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Your account is not linked to an employee record"));
        }
        try {
            ChatService.Answer a = chat.answer(user, req.question(), req.history());
            return ResponseEntity.ok(Map.of("answer", a.answer(), "seconds", a.seconds()));
        } catch (OllamaClient.OllamaException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }
}
