package com.shopify.api.repository;

import com.shopify.api.model.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {

    Optional<ConversationSession> findBySessionId(String sessionId);

    List<ConversationSession> findByCustomerEmailOrderByLastActiveAtDesc(String customerEmail);

    List<ConversationSession> findTop5ByCustomerEmailOrderByLastActiveAtDesc(String customerEmail);
}
