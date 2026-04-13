package com.shopify.api.service;

import com.shopify.api.model.ConversationMessage;
import com.shopify.api.model.ConversationSession;
import com.shopify.api.model.CustomerPreference;
import com.shopify.api.repository.ConversationMessageRepository;
import com.shopify.api.repository.ConversationSessionRepository;
import com.shopify.api.repository.CustomerPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing conversation memory across sessions.
 * Persists chat messages, tracks session state, and loads customer context
 * for personalized interactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationMemoryService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final CustomerPreferenceRepository preferenceRepository;

    /**
     * Get or create a conversation session.
     */
    @Transactional
    public ConversationSession getOrCreateSession(String sessionId, String customerEmail, String customerFirstName) {
        Optional<ConversationSession> existing = sessionRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            ConversationSession session = existing.get();
            session.setLastActiveAt(LocalDateTime.now());
            // Update customer info if provided and not already set
            if (customerEmail != null && session.getCustomerEmail() == null) {
                session.setCustomerEmail(customerEmail);
            }
            if (customerFirstName != null && session.getCustomerFirstName() == null) {
                session.setCustomerFirstName(customerFirstName);
            }
            return sessionRepository.save(session);
        }

        ConversationSession session = ConversationSession.builder()
                .sessionId(sessionId)
                .customerEmail(customerEmail)
                .customerFirstName(customerFirstName)
                .startedAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .messageCount(0)
                .build();
        return sessionRepository.save(session);
    }

    /**
     * Save a message to the conversation history.
     */
    @Transactional
    public ConversationMessage saveMessage(String sessionId, String role, String content, Integer tokensUsed) {
        ConversationMessage message = ConversationMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .tokensUsed(tokensUsed)
                .build();
        ConversationMessage saved = messageRepository.save(message);

        // Update session message count
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setMessageCount(session.getMessageCount() + 1);
            sessionRepository.save(session);
        });

        return saved;
    }

    /**
     * Get recent messages for a session (for loading conversation context).
     */
    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        List<ConversationMessage> messages = messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(messages); // Return in chronological order
        return messages;
    }

    /**
     * Get customer preference profile for personalization.
     */
    public Optional<CustomerPreference> getCustomerPreference(String email) {
        return preferenceRepository.findByCustomerEmail(email);
    }

    /**
     * Get recent session summaries for a returning customer.
     */
    public List<ConversationSession> getRecentSessions(String email) {
        return sessionRepository.findTop5ByCustomerEmailOrderByLastActiveAtDesc(email);
    }

    /**
     * Update or create customer preferences.
     */
    @Transactional
    public CustomerPreference updatePreferences(String email, String firstName, String categories, String skillLevel) {
        CustomerPreference pref = preferenceRepository.findByCustomerEmail(email)
                .orElse(CustomerPreference.builder()
                        .customerEmail(email)
                        .totalSessions(0)
                        .build());

        if (firstName != null) pref.setFirstName(firstName);
        if (categories != null) pref.setPreferredCategories(categories);
        if (skillLevel != null) pref.setSkillLevel(skillLevel);
        pref.setTotalSessions(pref.getTotalSessions() + 1);
        pref.setLastVisit(LocalDateTime.now());

        return preferenceRepository.save(pref);
    }

    /**
     * Build a context string for personalizing the system prompt for a returning customer.
     */
    public String buildCustomerContext(String email) {
        StringBuilder context = new StringBuilder();

        Optional<CustomerPreference> prefOpt = getCustomerPreference(email);
        if (prefOpt.isPresent()) {
            CustomerPreference pref = prefOpt.get();
            context.append("=== RETURNING CUSTOMER CONTEXT ===\n");
            if (pref.getFirstName() != null) {
                context.append("Customer name: ").append(pref.getFirstName()).append("\n");
            }
            context.append("Previous visits: ").append(pref.getTotalSessions()).append("\n");
            if (pref.getPreferredCategories() != null) {
                context.append("Preferred categories: ").append(pref.getPreferredCategories()).append("\n");
            }
            if (pref.getSkillLevel() != null) {
                context.append("Skill level: ").append(pref.getSkillLevel()).append("\n");
            }
            context.append("Use this information to personalize your responses. Greet them by name if known.\n\n");
        }

        // Recent session summaries
        List<ConversationSession> recentSessions = getRecentSessions(email);
        if (!recentSessions.isEmpty()) {
            context.append("Recent conversation summaries:\n");
            for (ConversationSession session : recentSessions) {
                if (session.getSummaryText() != null) {
                    context.append("- ").append(session.getSummaryText()).append("\n");
                }
            }
            context.append("\n");
        }

        return context.toString();
    }
}
