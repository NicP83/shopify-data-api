package com.shopify.api.service.agent;

import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing the tool registry
 *
 * Tools are dynamically registered and can be assigned to agents.
 * Each tool has a handler class that implements the actual functionality.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolRegistryService {

    private final ToolRepository toolRepository;

    /**
     * Register a new tool
     */
    @Transactional
    public Tool registerTool(Tool tool) {
        log.info("Registering new tool: {}", tool.getName());

        if (toolRepository.existsByName(tool.getName())) {
            throw new IllegalArgumentException("Tool with name '" + tool.getName() + "' already exists");
        }

        return toolRepository.save(tool);
    }

    /**
     * Get tool by ID
     */
    @Transactional(readOnly = true)
    public Optional<Tool> getToolById(Long id) {
        return toolRepository.findById(id);
    }

    /**
     * Get tool by name
     */
    @Transactional(readOnly = true)
    public Optional<Tool> getToolByName(String name) {
        return toolRepository.findByName(name);
    }

    /**
     * Get all tools
     */
    @Transactional(readOnly = true)
    public List<Tool> getAllTools() {
        return toolRepository.findAll();
    }

    /**
     * Get all active tools
     */
    @Transactional(readOnly = true)
    public List<Tool> getActiveTools() {
        return toolRepository.findByIsActiveTrue();
    }

    /**
     * Get tools by type
     */
    @Transactional(readOnly = true)
    public List<Tool> getToolsByType(String type) {
        return toolRepository.findByType(type);
    }

    /**
     * Get active tools by type
     */
    @Transactional(readOnly = true)
    public List<Tool> getActiveToolsByType(String type) {
        return toolRepository.findByTypeAndIsActiveTrue(type);
    }

    /**
     * Update an existing tool
     */
    @Transactional
    public Tool updateTool(Long id, Tool updatedTool) {
        log.info("Updating tool with ID: {}", id);

        Tool existingTool = toolRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + id));

        // Check if name is being changed and if new name already exists
        if (!existingTool.getName().equals(updatedTool.getName()) &&
            toolRepository.existsByName(updatedTool.getName())) {
            throw new IllegalArgumentException("Tool with name '" + updatedTool.getName() + "' already exists");
        }

        // Update fields
        existingTool.setName(updatedTool.getName());
        existingTool.setType(updatedTool.getType());
        existingTool.setDescription(updatedTool.getDescription());
        existingTool.setInputSchemaJson(updatedTool.getInputSchemaJson());
        existingTool.setHandlerClass(updatedTool.getHandlerClass());
        existingTool.setIsActive(updatedTool.getIsActive());

        return toolRepository.save(existingTool);
    }

    /**
     * Delete a tool
     */
    @Transactional
    public void deleteTool(Long id) {
        log.info("Deleting tool with ID: {}", id);

        if (!toolRepository.existsById(id)) {
            throw new IllegalArgumentException("Tool not found with ID: " + id);
        }

        toolRepository.deleteById(id);
    }

    /**
     * Activate a tool
     */
    @Transactional
    public void activateTool(Long id) {
        log.info("Activating tool with ID: {}", id);

        Tool tool = toolRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + id));

        tool.setIsActive(true);
        toolRepository.save(tool);
    }

    /**
     * Deactivate a tool
     */
    @Transactional
    public void deactivateTool(Long id) {
        log.info("Deactivating tool with ID: {}", id);

        Tool tool = toolRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found with ID: " + id));

        tool.setIsActive(false);
        toolRepository.save(tool);
    }

    /**
     * Validate if a tool handler class exists
     *
     * @param handlerClass Fully qualified class name
     * @return true if class exists and can be loaded
     */
    public boolean validateToolHandler(String handlerClass) {
        try {
            Class.forName(handlerClass);
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("Tool handler class not found: {}", handlerClass);
            return false;
        }
    }
}
