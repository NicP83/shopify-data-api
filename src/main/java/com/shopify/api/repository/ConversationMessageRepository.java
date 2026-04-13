package com.shopify.api.repository;

import com.shopify.api.model.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ConversationMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);
}
