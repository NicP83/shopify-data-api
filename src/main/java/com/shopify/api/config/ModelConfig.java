package com.shopify.api.config;

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
    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    /**
     * Valid Anthropic Claude models
     *
     * IMPORTANT: Only models listed here are valid and will work with Anthropic API.
     * Do NOT add models that don't exist (e.g., "claude-3-5-sonnet-20241022" is INVALID).
     *
     * Model families:
     * - Claude 4.6: Latest generation (2025-2026)
     * - Claude 4.5: Previous generation (2025)
     * - Claude 4: Previous generation (2025)
     * - Claude 3.7: Enhanced version (2025)
     * - Claude 3.5: Previous version (2024)
     * - Claude 3: Original version (2024)
     */
    public static final List<String> VALID_ANTHROPIC_MODELS = Arrays.asList(
        // Claude 4.6 (Latest - 2025-2026)
        "claude-sonnet-4-6",             // DEFAULT - Latest Sonnet
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
        Map.entry("claude-sonnet-4-6", "Claude Sonnet 4.6 (Latest)"),
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
        "claude-3-5-sonnet-20241022", "claude-sonnet-4-6",  // Common mistake - doesn't exist!
        "claude-3.5-sonnet", "claude-sonnet-4-6",
        "claude-sonnet", "claude-sonnet-4-6",
        "claude-opus", "claude-opus-4-6",
        "claude-haiku", "claude-haiku-4-5-20251001"
    );

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
