package com.shopify.api.controller;

import com.shopify.api.entity.Workflow;
import com.shopify.api.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic Controller for debugging database state
 *
 * This controller provides endpoints to inspect the database state,
 * check migration status, and verify workflow data.
 *
 * IMPORTANT: This should be removed or secured before production!
 */
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
public class DiagnosticController {

    private final WorkflowRepository workflowRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get comprehensive database diagnostics
     *
     * Returns:
     * - All workflows with Phase 2 fields
     * - Migration status
     * - Database column information
     */
    @GetMapping("/workflows")
    public Map<String, Object> getWorkflowDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        try {
            // Get all workflows
            List<Workflow> allWorkflows = workflowRepository.findAll();
            diagnostics.put("totalWorkflows", allWorkflows.size());
            diagnostics.put("workflows", allWorkflows);

            // Count by status
            long activeCount = allWorkflows.stream().filter(Workflow::getIsActive).count();
            long publicCount = allWorkflows.stream().filter(w -> w.getIsPublic() != null && w.getIsPublic()).count();
            diagnostics.put("activeWorkflows", activeCount);
            diagnostics.put("publicWorkflows", publicCount);

            // Count by interface type
            Map<String, Long> interfaceTypeCounts = new HashMap<>();
            for (Workflow w : allWorkflows) {
                String type = w.getInterfaceType() != null ? w.getInterfaceType() : "NULL";
                interfaceTypeCounts.put(type, interfaceTypeCounts.getOrDefault(type, 0L) + 1);
            }
            diagnostics.put("interfaceTypeCounts", interfaceTypeCounts);

            // Check V004 migration status
            try {
                String query = "SELECT version, description, installed_on FROM flyway_schema_history WHERE version = '004'";
                List<Map<String, Object>> migrationInfo = jdbcTemplate.queryForList(query);
                diagnostics.put("v004MigrationStatus", migrationInfo.isEmpty() ? "NOT_RUN" : "COMPLETED");
                diagnostics.put("v004MigrationDetails", migrationInfo);
            } catch (Exception e) {
                diagnostics.put("v004MigrationStatus", "ERROR");
                diagnostics.put("v004MigrationError", e.getMessage());
            }

            // Check column existence
            try {
                String columnQuery =
                    "SELECT column_name, data_type, is_nullable, column_default " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = 'workflows' " +
                    "AND column_name IN ('input_schema_json', 'interface_type', 'is_public') " +
                    "ORDER BY column_name";
                List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnQuery);
                diagnostics.put("phase2Columns", columns);
                diagnostics.put("phase2ColumnsExist", columns.size() == 3);
            } catch (Exception e) {
                diagnostics.put("phase2ColumnsError", e.getMessage());
            }

            // Sample workflow details
            if (!allWorkflows.isEmpty()) {
                Workflow sample = allWorkflows.get(0);
                Map<String, Object> sampleData = new HashMap<>();
                sampleData.put("id", sample.getId());
                sampleData.put("name", sample.getName());
                sampleData.put("isActive", sample.getIsActive());
                sampleData.put("isPublic", sample.getIsPublic());
                sampleData.put("interfaceType", sample.getInterfaceType());
                sampleData.put("hasInputSchema", sample.getInputSchemaJson() != null);
                sampleData.put("inputSchemaJson", sample.getInputSchemaJson());
                diagnostics.put("sampleWorkflow", sampleData);
            }

            diagnostics.put("status", "success");

        } catch (Exception e) {
            diagnostics.put("status", "error");
            diagnostics.put("error", e.getMessage());
            diagnostics.put("errorType", e.getClass().getName());
        }

        return diagnostics;
    }

    /**
     * Get minimal workflow summary
     */
    @GetMapping("/workflows/summary")
    public Map<String, Object> getWorkflowSummary() {
        Map<String, Object> summary = new HashMap<>();

        try {
            List<Workflow> all = workflowRepository.findAll();
            List<Workflow> active = workflowRepository.findByIsActive(true);

            summary.put("total", all.size());
            summary.put("active", active.size());
            summary.put("publicActive", active.stream().filter(w -> w.getIsPublic() != null && w.getIsPublic()).count());

            // List all workflow IDs, names, and flags
            List<Map<String, Object>> workflowList = all.stream().map(w -> {
                Map<String, Object> wMap = new HashMap<>();
                wMap.put("id", w.getId());
                wMap.put("name", w.getName());
                wMap.put("isActive", w.getIsActive());
                wMap.put("isPublic", w.getIsPublic());
                wMap.put("interfaceType", w.getInterfaceType());
                wMap.put("hasInputSchema", w.getInputSchemaJson() != null);
                return wMap;
            }).toList();

            summary.put("workflows", workflowList);

        } catch (Exception e) {
            summary.put("error", e.getMessage());
        }

        return summary;
    }
}
