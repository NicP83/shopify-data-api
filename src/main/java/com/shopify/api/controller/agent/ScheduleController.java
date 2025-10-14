package com.shopify.api.controller.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopify.api.model.agent.WorkflowSchedule;
import com.shopify.api.service.agent.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller for workflow scheduling
 *
 * Provides endpoints for creating, managing, and monitoring workflow schedules.
 *
 * See: docs/multi-agent/IMPLEMENTATION_ROADMAP.md Phase 10
 */
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Slf4j
public class ScheduleController {

    private final SchedulerService schedulerService;

    /**
     * Create a new workflow schedule
     *
     * POST /api/schedules
     * Body: {
     *   "workflowId": 1,
     *   "cronExpression": "0 0 * * * *",
     *   "triggerData": { ... }
     * }
     */
    @PostMapping
    public ResponseEntity<WorkflowSchedule> createSchedule(@RequestBody CreateScheduleRequest request) {
        log.info("Creating schedule for workflow {} with cron: {}",
            request.getWorkflowId(), request.getCronExpression());

        WorkflowSchedule schedule = schedulerService.scheduleWorkflow(
            request.getWorkflowId(),
            request.getCronExpression(),
            request.getTriggerData()
        );

        return ResponseEntity.ok(schedule);
    }

    /**
     * Get all schedules for a workflow
     *
     * GET /api/schedules/workflow/{workflowId}
     */
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<List<WorkflowSchedule>> getSchedulesForWorkflow(@PathVariable Long workflowId) {
        log.info("Getting schedules for workflow {}", workflowId);
        List<WorkflowSchedule> schedules = schedulerService.getSchedulesForWorkflow(workflowId);
        return ResponseEntity.ok(schedules);
    }

    /**
     * Get all active schedules
     *
     * GET /api/schedules?active=true
     */
    @GetMapping
    public ResponseEntity<List<WorkflowSchedule>> getSchedules(
        @RequestParam(required = false, defaultValue = "true") Boolean active) {
        log.info("Getting all schedules (active: {})", active);

        List<WorkflowSchedule> schedules = active
            ? schedulerService.getActiveSchedules()
            : schedulerService.getActiveSchedules(); // TODO: Add getAll() if needed

        return ResponseEntity.ok(schedules);
    }

    /**
     * Cancel (disable) a schedule
     *
     * DELETE /api/schedules/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> cancelSchedule(@PathVariable Long id) {
        log.info("Cancelling schedule {}", id);
        schedulerService.cancelSchedule(id);
        return ResponseEntity.ok(Map.of("message", "Schedule cancelled successfully", "scheduleId", id.toString()));
    }

    /**
     * Reactivate a cancelled schedule
     *
     * PUT /api/schedules/{id}/activate
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<WorkflowSchedule> activateSchedule(@PathVariable Long id) {
        log.info("Activating schedule {}", id);
        schedulerService.reactivateSchedule(id);

        // Return updated schedule - need to fetch it
        // For now, return a simple response
        return ResponseEntity.ok().build();
    }

    /**
     * Update schedule cron expression
     *
     * PUT /api/schedules/{id}/cron
     * Body: {
     *   "cronExpression": "0 0 12 * * *"
     * }
     */
    @PutMapping("/{id}/cron")
    public ResponseEntity<Map<String, String>> updateCron(
        @PathVariable Long id,
        @RequestBody UpdateCronRequest request) {
        log.info("Updating cron expression for schedule {} to: {}", id, request.getCronExpression());

        schedulerService.updateScheduleCron(id, request.getCronExpression());

        return ResponseEntity.ok(Map.of(
            "message", "Cron expression updated successfully",
            "scheduleId", id.toString(),
            "cronExpression", request.getCronExpression()
        ));
    }

    /**
     * Update schedule trigger data
     *
     * PUT /api/schedules/{id}/trigger-data
     * Body: { ... trigger data JSON ... }
     */
    @PutMapping("/{id}/trigger-data")
    public ResponseEntity<Map<String, String>> updateTriggerData(
        @PathVariable Long id,
        @RequestBody JsonNode triggerData) {
        log.info("Updating trigger data for schedule {}", id);

        schedulerService.updateScheduleTriggerData(id, triggerData);

        return ResponseEntity.ok(Map.of(
            "message", "Trigger data updated successfully",
            "scheduleId", id.toString()
        ));
    }

    /**
     * Request DTOs
     */
    @lombok.Data
    public static class CreateScheduleRequest {
        private Long workflowId;
        private String cronExpression;
        private JsonNode triggerData;
    }

    @lombok.Data
    public static class UpdateCronRequest {
        private String cronExpression;
    }
}
