package com.shopify.api.service;

import com.shopify.api.config.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for validating and managing AI model configurations
 *
 * This service provides centralized validation logic for AI models across the application.
 * It ensures that only valid models are used and provides helpful error messages.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for model management guidelines
 */
@Service
public class ModelValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ModelValidationService.class);

    /**
     * Validate that a model is supported
     *
     * @param model The model name to validate
     * @throws IllegalArgumentException if the model is invalid
     */
    public void validateModel(String model) {
        if (!ModelConfig.isValidModel(model)) {
            String errorMessage = String.format(
                "Invalid model '%s'. Valid Anthropic models are: %s",
                model,
                String.join(", ", ModelConfig.VALID_ANTHROPIC_MODELS)
            );
            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Validate and normalize a model name
     * Returns the validated model, or DEFAULT_MODEL if null/empty
     *
     * @param model The model name to validate
     * @return The validated model name
     * @throws IllegalArgumentException if the model is invalid
     */
    public String validateAndNormalizeModel(String model) {
        try {
            String normalized = ModelConfig.validateAndNormalize(model);

            if (normalized.equals(ModelConfig.DEFAULT_MODEL) &&
                (model == null || model.trim().isEmpty())) {
                logger.info("No model specified, using default: {}", ModelConfig.DEFAULT_MODEL);
            }

            return normalized;
        } catch (IllegalArgumentException e) {
            logger.error("Model validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Attempt to migrate an invalid model to a valid one
     * This is useful for database migrations and legacy data
     *
     * @param invalidModel The potentially invalid model name
     * @return A valid model (either the original if valid, or a migrated alternative)
     */
    public String migrateInvalidModel(String invalidModel) {
        if (invalidModel == null || invalidModel.trim().isEmpty()) {
            logger.warn("Empty model name, migrating to default: {}", ModelConfig.DEFAULT_MODEL);
            return ModelConfig.DEFAULT_MODEL;
        }

        String trimmed = invalidModel.trim();

        // Check if already valid
        if (ModelConfig.isValidModel(trimmed)) {
            logger.debug("Model '{}' is valid, no migration needed", trimmed);
            return trimmed;
        }

        // Migrate to valid model
        String migratedModel = ModelConfig.migrateInvalidModel(trimmed);
        logger.warn("Migrating invalid model '{}' to '{}'", invalidModel, migratedModel);
        return migratedModel;
    }

    /**
     * Get all available models
     *
     * @return List of valid model identifiers
     */
    public List<String> getAvailableModels() {
        return ModelConfig.VALID_ANTHROPIC_MODELS;
    }

    /**
     * Get all available models with display names
     *
     * @return Map of model ID -> display name
     */
    public Map<String, String> getAvailableModelsWithDisplayNames() {
        return ModelConfig.getAllModelsWithDisplayNames();
    }

    /**
     * Get the default model
     *
     * @return The default model identifier
     */
    public String getDefaultModel() {
        return ModelConfig.DEFAULT_MODEL;
    }

    /**
     * Get user-friendly display name for a model
     *
     * @param model The model identifier
     * @return Display name
     */
    public String getModelDisplayName(String model) {
        return ModelConfig.getModelDisplayName(model);
    }

    /**
     * Check if a model is valid without throwing an exception
     *
     * @param model The model name to check
     * @return true if valid, false otherwise
     */
    public boolean isValidModel(String model) {
        return ModelConfig.isValidModel(model);
    }

    /**
     * Validate model with fallback behavior
     * If the model is invalid, logs a warning and returns the default model
     * instead of throwing an exception. Useful for non-critical paths.
     *
     * @param model The model to validate
     * @return The model if valid, or DEFAULT_MODEL if invalid
     */
    public String validateOrDefault(String model) {
        if (model == null || model.trim().isEmpty()) {
            logger.info("No model specified, using default: {}", ModelConfig.DEFAULT_MODEL);
            return ModelConfig.DEFAULT_MODEL;
        }

        String trimmed = model.trim();

        if (ModelConfig.isValidModel(trimmed)) {
            return trimmed;
        }

        logger.warn("Invalid model '{}', falling back to default: {}",
            model, ModelConfig.DEFAULT_MODEL);
        return ModelConfig.DEFAULT_MODEL;
    }
}
