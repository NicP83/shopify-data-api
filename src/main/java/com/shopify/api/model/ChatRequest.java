package com.shopify.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String message;                 // User's message
    private List<ChatMessage> conversationHistory;  // Previous messages for context
    private String sessionId;               // Session identifier for analytics tracking
    private String userIdentifier;          // Anonymous user identifier (optional, hashed)
}
