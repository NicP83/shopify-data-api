package com.shopify.api.controller.agent;

import com.shopify.api.dto.agent.CreateToolRequest;
import com.shopify.api.dto.agent.ToolResponse;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.service.agent.ToolRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Tool management
 *
 * Provides endpoints for tool registry operations.
 * Tools are dynamically registered and assigned to agents.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ToolController {

    private final ToolRegistryService toolRegistryService;

    /**
     * Register a new tool
     * POST /api/tools
     */
    @PostMapping
    public ResponseEntity<ToolResponse> registerTool(@Valid @RequestBody CreateToolRequest request) {
        log.info("Registering new tool: {}", request.getName());

        Tool tool = Tool.builder()
            .name(request.getName())
            .type(request.getType())
            .description(request.getDescription())
            .inputSchemaJson(request.getInputSchemaJson())
            .handlerClass(request.getHandlerClass())
            .isActive(request.getIsActive())
            .build();

        Tool registeredTool = toolRegistryService.registerTool(tool);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ToolResponse.fromEntity(registeredTool));
    }

    /**
     * Get all tools
     * GET /api/tools
     */
    @GetMapping
    public ResponseEntity<List<ToolResponse>> getAllTools(
        @RequestParam(required = false) Boolean activeOnly,
        @RequestParam(required = false) String type
    ) {
        log.info("Fetching tools - activeOnly: {}, type: {}", activeOnly, type);

        List<Tool> tools;

        if (type != null) {
            tools = Boolean.TRUE.equals(activeOnly)
                ? toolRegistryService.getActiveToolsByType(type)
                : toolRegistryService.getToolsByType(type);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            tools = toolRegistryService.getActiveTools();
        } else {
            tools = toolRegistryService.getAllTools();
        }

        List<ToolResponse> response = tools.stream()
            .map(ToolResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get tool by ID
     * GET /api/tools/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ToolResponse> getToolById(@PathVariable Long id) {
        log.info("Fetching tool with ID: {}", id);

        return toolRegistryService.getToolById(id)
            .map(ToolResponse::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get tool by name
     * GET /api/tools/by-name/{name}
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<ToolResponse> getToolByName(@PathVariable String name) {
        log.info("Fetching tool with name: {}", name);

        return toolRegistryService.getToolByName(name)
            .map(ToolResponse::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an existing tool
     * PUT /api/tools/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ToolResponse> updateTool(
        @PathVariable Long id,
        @Valid @RequestBody CreateToolRequest request
    ) {
        log.info("Updating tool with ID: {}", id);

        Tool updatedTool = Tool.builder()
            .name(request.getName())
            .type(request.getType())
            .description(request.getDescription())
            .inputSchemaJson(request.getInputSchemaJson())
            .handlerClass(request.getHandlerClass())
            .isActive(request.getIsActive())
            .build();

        Tool tool = toolRegistryService.updateTool(id, updatedTool);
        return ResponseEntity.ok(ToolResponse.fromEntity(tool));
    }

    /**
     * Delete a tool
     * DELETE /api/tools/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        log.info("Deleting tool with ID: {}", id);
        toolRegistryService.deleteTool(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Activate a tool
     * POST /api/tools/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateTool(@PathVariable Long id) {
        log.info("Activating tool with ID: {}", id);
        toolRegistryService.activateTool(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Deactivate a tool
     * POST /api/tools/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateTool(@PathVariable Long id) {
        log.info("Deactivating tool with ID: {}", id);
        toolRegistryService.deactivateTool(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Validate tool handler class
     * GET /api/tools/validate-handler?handlerClass=...
     */
    @GetMapping("/validate-handler")
    public ResponseEntity<Boolean> validateHandler(@RequestParam String handlerClass) {
        log.info("Validating handler class: {}", handlerClass);
        boolean isValid = toolRegistryService.validateToolHandler(handlerClass);
        return ResponseEntity.ok(isValid);
    }

    /**
     * Exception handler for IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
