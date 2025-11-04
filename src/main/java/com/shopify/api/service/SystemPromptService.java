package com.shopify.api.service;

import com.shopify.api.model.SystemPrompt;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.SystemPromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing AI system prompts
 */
@Service
public class SystemPromptService {

    private static final Logger logger = LoggerFactory.getLogger(SystemPromptService.class);

    private final SystemPromptRepository systemPromptRepository;

    @Autowired
    public SystemPromptService(SystemPromptRepository systemPromptRepository) {
        this.systemPromptRepository = systemPromptRepository;
    }

    /**
     * Get active system prompt for a shop by name
     * Falls back to global prompt if shop-specific doesn't exist
     */
    public Optional<SystemPrompt> getActivePrompt(String promptName, ShopifyShop shop) {
        logger.debug("Fetching active prompt '{}' for shop: {}", promptName, shop.getShopDomain());
        return systemPromptRepository.findActivePromptForShop(promptName, shop);
    }

    /**
     * Get active system prompt by type
     */
    public Optional<SystemPrompt> getActivePromptByType(String promptType, ShopifyShop shop) {
        logger.debug("Fetching active prompt of type '{}' for shop: {}", promptType, shop.getShopDomain());
        return systemPromptRepository.findActivePromptByTypeForShop(promptType, shop);
    }

    /**
     * Get all active prompts for a shop (includes global)
     */
    public List<SystemPrompt> getAllActivePrompts(ShopifyShop shop) {
        return systemPromptRepository.findAllActivePromptsForShop(shop);
    }

    /**
     * Get shop-specific prompts only
     */
    public List<SystemPrompt> getShopSpecificPrompts(ShopifyShop shop) {
        return systemPromptRepository.findShopSpecificPrompts(shop);
    }

    /**
     * Get all global prompts
     */
    public List<SystemPrompt> getGlobalPrompts() {
        return systemPromptRepository.findAllGlobalPrompts();
    }

    /**
     * Create a new system prompt
     */
    @Transactional
    public SystemPrompt createPrompt(SystemPrompt prompt) {
        logger.info("Creating new prompt: {} (type: {})", prompt.getPromptName(), prompt.getPromptType());

        // Set version to latest + 1
        Integer latestVersion = systemPromptRepository.findLatestVersion(
            prompt.getPromptName(),
            prompt.getShop()
        );
        prompt.setVersion(latestVersion + 1);

        return systemPromptRepository.save(prompt);
    }

    /**
     * Update existing prompt (creates new version)
     */
    @Transactional
    public SystemPrompt updatePrompt(Long promptId, String newPromptText, String description) {
        SystemPrompt existingPrompt = systemPromptRepository.findById(promptId)
            .orElseThrow(() -> new RuntimeException("Prompt not found: " + promptId));

        logger.info("Updating prompt: {} (creating new version)", existingPrompt.getPromptName());

        // Deactivate old version
        existingPrompt.setIsActive(false);
        systemPromptRepository.save(existingPrompt);

        // Create new version
        SystemPrompt newVersion = SystemPrompt.builder()
            .shop(existingPrompt.getShop())
            .promptName(existingPrompt.getPromptName())
            .promptType(existingPrompt.getPromptType())
            .promptText(newPromptText)
            .promptDescription(description != null ? description : existingPrompt.getPromptDescription())
            .version(existingPrompt.getVersion() + 1)
            .isActive(true)
            .createdBy(existingPrompt.getCreatedBy())
            .build();

        return systemPromptRepository.save(newVersion);
    }

    /**
     * Deactivate a prompt
     */
    @Transactional
    public void deactivatePrompt(Long promptId) {
        SystemPrompt prompt = systemPromptRepository.findById(promptId)
            .orElseThrow(() -> new RuntimeException("Prompt not found: " + promptId));

        logger.info("Deactivating prompt: {}", prompt.getPromptName());
        prompt.setIsActive(false);
        systemPromptRepository.save(prompt);
    }

    /**
     * Activate a specific prompt version
     */
    @Transactional
    public SystemPrompt activatePromptVersion(Long promptId) {
        SystemPrompt prompt = systemPromptRepository.findById(promptId)
            .orElseThrow(() -> new RuntimeException("Prompt not found: " + promptId));

        logger.info("Activating prompt version: {} v{}", prompt.getPromptName(), prompt.getVersion());

        // Deactivate all other versions of this prompt
        List<SystemPrompt> allVersions = systemPromptRepository.findAllVersions(
            prompt.getPromptName(),
            prompt.getShop()
        );

        for (SystemPrompt version : allVersions) {
            if (!version.getId().equals(promptId)) {
                version.setIsActive(false);
                systemPromptRepository.save(version);
            }
        }

        // Activate this version
        prompt.setIsActive(true);
        return systemPromptRepository.save(prompt);
    }

    /**
     * Track prompt usage
     */
    @Transactional
    public void trackUsage(Long promptId, long responseTimeMs, boolean success) {
        SystemPrompt prompt = systemPromptRepository.findById(promptId)
            .orElseThrow(() -> new RuntimeException("Prompt not found: " + promptId));

        prompt.incrementUsageCount();
        prompt.updateAverageResponseTime(responseTimeMs);
        prompt.updateSuccessRate(success);

        systemPromptRepository.save(prompt);

        logger.debug("Tracked usage for prompt: {} (usage: {}, success rate: {}%)",
            prompt.getPromptName(), prompt.getUsageCount(), prompt.getSuccessRate());
    }

    /**
     * Get all versions of a prompt
     */
    public List<SystemPrompt> getPromptVersions(String promptName, ShopifyShop shop) {
        return systemPromptRepository.findAllVersions(promptName, shop);
    }

    /**
     * Get prompt by ID
     */
    public Optional<SystemPrompt> getPromptById(Long promptId) {
        return systemPromptRepository.findById(promptId);
    }

    /**
     * Delete a prompt (soft delete by deactivating)
     */
    @Transactional
    public void deletePrompt(Long promptId) {
        deactivatePrompt(promptId);
    }

    /**
     * Get top performing prompts
     */
    public List<SystemPrompt> getTopPerformingPrompts() {
        return systemPromptRepository.findTopPerformingPrompts();
    }

    /**
     * Count active prompts for a shop
     */
    public long countActivePrompts(ShopifyShop shop) {
        return systemPromptRepository.countActivePromptsForShop(shop);
    }

    /**
     * Create shop-specific prompt from global template
     */
    @Transactional
    public SystemPrompt createShopPromptFromGlobal(String globalPromptName, ShopifyShop shop, String customizations) {
        // Find global prompt
        SystemPrompt globalPrompt = systemPromptRepository.findActivePromptForShop(globalPromptName, null)
            .orElseThrow(() -> new RuntimeException("Global prompt not found: " + globalPromptName));

        logger.info("Creating shop-specific prompt for {} based on global '{}'",
            shop.getShopDomain(), globalPromptName);

        // Create shop-specific version with customizations
        String customizedText = globalPrompt.getPromptText();
        if (customizations != null && !customizations.isBlank()) {
            customizedText += "\n\nShop-specific customizations:\n" + customizations;
        }

        SystemPrompt shopPrompt = SystemPrompt.builder()
            .shop(shop)
            .promptName(globalPrompt.getPromptName())
            .promptType(globalPrompt.getPromptType())
            .promptText(customizedText)
            .promptDescription("Shop-specific version of: " + globalPrompt.getPromptDescription())
            .version(1)
            .isActive(true)
            .createdBy("shop-customization")
            .build();

        return systemPromptRepository.save(shopPrompt);
    }
}
