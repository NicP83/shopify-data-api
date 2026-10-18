package com.shopify.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized configuration for AI model management
 *
 * This class serves as the single source of truth for valid AI models across the application.
 * All services (ChatAgentService, SeoAgentService, AgentExecutionService) should reference
 * these constants to ensure consistent model validation.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for model selection guidance
 */
public class ModelConfig {

    /**
     * Default model for all services (stable, recommended)
     */
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    /**
     * Valid Anthropic Claude models
     *
     * IMPORTANT: Only models listed here are valid and will work with Anthropic API.
     * Do NOT add models that don't exist (e.g., "claude-3-5-sonnet-20241022" is INVALID).
     *
     * Model families:
     * - Claude 5.5: Current Sonnet (2026)
     * - Claude 4.6: Previous generation (2025-2026)
     * - Claude 4.5: Previous generation (2025)
     * - Claude 4: Previous generation (2025)
     * - Claude 3.7: Enhanced version (2025)
     * - Claude 3.5: Previous version (2024)
     * - Claude 3: Original version (2024)
     */
    public static final List<String> VALID_ANTHROPIC_MODELS = Arrays.asList(
        // Claude 5.5 (Current - 2026)
        "claude-sonnet-5-5",             // DEFAULT - Current Sonnet

        // Claude 4.6 (2025-2026)
        "claude-sonnet-4-6",             // Previous Sonnet (rollback target)
        "claude-opus-4-6",               // Most capable - Claude Opus 4.6

        // Claude 4.5 (2025)
        "claude-sonnet-4-5-20250929",    // Balanced - Sonnet 4.5
        "claude-opus-4-1-20250805",      // Most capable - Claude Opus 4.1
        "claude-haiku-4-5-20251001",     // Fastest - Latest Haiku

        // Claude 4 (2025)
        "claude-sonnet-4-20250514",      // Balanced - Claude Sonnet 4
        "claude-opus-4-20250514",        // Most capable - Claude Opus 4

        // Claude 3.7 (Enhanced - 2025)
        "claude-3-7-sonnet-20250219",    // Stable, well-tested

        // Claude 3.5 (2024)
        "claude-3-5-haiku-20241022",     // Fast - Note: This is HAIKU, not Sonnet!

        // Claude 3 (Original - 2024)
        "claude-3-opus-20240229",        // Most capable (legacy)
        "claude-3-sonnet-20240229",      // Balanced (legacy)
        "claude-3-haiku-20240307"        // Fast (legacy)
    );

    /**
     * Model display names for UI (user-friendly labels)
     */
    private static final Map<String, String> MODEL_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("claude-sonnet-5-5", "Claude Sonnet 5.5 (Latest)"),
        Map.entry("claude-sonnet-4-6", "Claude Sonnet 4.6"),
        Map.entry("claude-opus-4-6", "Claude Opus 4.6 (Most Capable)"),
        Map.entry("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
        Map.entry("claude-opus-4-1-20250805", "Claude Opus 4.1"),
        Map.entry("claude-haiku-4-5-20251001", "Claude Haiku 4.5"),
        Map.entry("claude-sonnet-4-20250514", "Claude Sonnet 4"),
        Map.entry("claude-opus-4-20250514", "Claude Opus 4"),
        Map.entry("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet"),
        Map.entry("claude-3-5-haiku-20241022", "Claude 3.5 Haiku"),
        Map.entry("claude-3-opus-20240229", "Claude 3 Opus"),
        Map.entry("claude-3-sonnet-20240229", "Claude 3 Sonnet"),
        Map.entry("claude-3-haiku-20240307", "Claude 3 Haiku")
    );

    /**
     * Common invalid models that users might try to use
     * These will be migrated to the default model
     */
    public static final Map<String, String> INVALID_MODEL_MIGRATIONS = Map.of(
        "claude-3-5-sonnet-20241022", "claude-sonnet-5-5",  // Common mistake - doesn't exist!
        "claude-3.5-sonnet", "claude-sonnet-5-5",
        "claude-sonnet", "claude-sonnet-5-5",
        "claude-opus", "claude-opus-4-6",
        "claude-haiku", "claude-haiku-4-5-20251001"
    );

    /**
     * Claude 5.x models reject non-default sampling params (temperature/top_p/top_k → HTTP 400)
     * and think by default (adaptive thinking; tuned via output_config.effort instead).
     */
    public static boolean isClaude5(String model) {
        return model != null && model.trim().matches("claude-[a-z]+-5(-\\d+)?");
    }

    /** Thinking tokens count toward max_tokens on Claude 5.x, so keep headroom for the reply. */
    public static final int CLAUDE5_MIN_MAX_TOKENS = 8192;

    /**
     * Put model-appropriate generation params on an Anthropic request body: temperature/top_p for
     * pre-5.x models; for Claude 5.x, drop sampling params, set effort, and floor max_tokens.
     * Pass {@code null} for any param the caller doesn't set.
     */
    public static void applyGenerationParams(ObjectNode requestBody, String model, int maxTokens,
                                             Double temperature, Double topP, String effort) {
        if (isClaude5(model)) {
            requestBody.put("max_tokens", Math.max(maxTokens, CLAUDE5_MIN_MAX_TOKENS));
            if (effort != null && !effort.isBlank()) {
                requestBody.putObject("output_config").put("effort", effort.trim());
            }
        } else {
            requestBody.put("max_tokens", maxTokens);
            if (temperature != null) requestBody.put("temperature", temperature);
            if (topP != null) requestBody.put("top_p", topP);
        }
    }

    /**
     * Check if a model name is valid
     *
     * @param model The model name to validate
     * @return true if the model is valid and supported
     */
    public static boolean isValidModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return false;
        }
        return VALID_ANTHROPIC_MODELS.contains(model.trim());
    }

    /**
     * Get user-friendly display name for a model
     *
     * @param model The model identifier
     * @return Display name, or the model ID itself if not found
     */
    public static String getModelDisplayName(String model) {
        return MODEL_DISPLAY_NAMES.getOrDefault(model, model);
    }

    /**
     * Get all valid models with their display names
     *
     * @return Map of model ID -> display name
     */
    public static Map<String, String> getAllModelsWithDisplayNames() {
        return VALID_ANTHROPIC_MODELS.stream()
            .collect(Collectors.toMap(
                model -> model,
                ModelConfig::getModelDisplayName
            ));
    }

    /**
     * Migrate an invalid model to a valid one
     *
     * @param invalidModel The invalid model name
     * @return The recommended valid model, or DEFAULT_MODEL if no specific migration exists
     */
    public static String migrateInvalidModel(String invalidModel) {
        if (invalidModel == null || invalidModel.trim().isEmpty()) {
            return DEFAULT_MODEL;
        }

        String trimmed = invalidModel.trim();

        // Check if it's already valid
        if (isValidModel(trimmed)) {
            return trimmed;
        }

        // Check for known migrations
        return INVALID_MODEL_MIGRATIONS.getOrDefault(trimmed, DEFAULT_MODEL);
    }

    /**
     * Validate and normalize a model name
     *
     * @param model The model name to validate
     * @return The model if valid, DEFAULT_MODEL if null/empty, throws exception if invalid
     * @throws IllegalArgumentException if the model is invalid and cannot be migrated
     */
    public static String validateAndNormalize(String model) {
        if (model == null || model.trim().isEmpty()) {
            return DEFAULT_MODEL;
        }

        String trimmed = model.trim();

        if (isValidModel(trimmed)) {
            return trimmed;
        }

        throw new IllegalArgumentException(
            "Invalid model: '" + model + "'. Valid models are: " +
            String.join(", ", VALID_ANTHROPIC_MODELS)
        );
    }
}
