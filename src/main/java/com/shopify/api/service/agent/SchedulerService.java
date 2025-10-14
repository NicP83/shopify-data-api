package com.shopify.api.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.model.agent.WorkflowSchedule;
import com.shopify.api.repository.agent.WorkflowRepository;
import com.shopify.api.repository.agent.WorkflowScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Service for managing workflow schedules and cron-based execution
 *
 * Handles creation, cancellation, and execution of scheduled workflows
 * using Spring's @Scheduled annotation for cron processing.
 *
 * See: docs/multi-agent/IMPLEMENTATION_ROADMAP.md Phase 10
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final WorkflowScheduleRepository scheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowOrchestratorService orchestratorService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new workflow schedule with cron expression
     *
     * @param workflowId     Workflow to schedule
     * @param cronExpression Cron expression (e.g., "0 0 * * * *" for hourly)
     * @param triggerData    Optional trigger data for the workflow
     * @return Created schedule
     */
    @Transactional
    public WorkflowSchedule scheduleWorkflow(Long workflowId, String cronExpression, JsonNode triggerData) {
        log.info("Creating schedule for workflow {} with cron: {}", workflowId, cronExpression);

        // Validate workflow exists
        Workflow workflow = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        // Validate cron expression
        try {
            CronExpression.parse(cronExpression);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
        }

        // Calculate next run time
        LocalDateTime nextRunAt = calculateNextRunTime(cronExpression);

        // Create schedule
        WorkflowSchedule schedule = WorkflowSchedule.builder()
            .workflow(workflow)
            .cronExpression(cronExpression)
            .enabled(true)
            .triggerDataJson(triggerData)
            .nextRunAt(nextRunAt)
            .build();

        WorkflowSchedule saved = scheduleRepository.save(schedule);
        log.info("Created schedule {} - next run at {}", saved.getId(), nextRunAt);

        return saved;
    }

    /**
     * Cancel a workflow schedule
     *
     * @param scheduleId Schedule to cancel
     */
    @Transactional
    public void cancelSchedule(Long scheduleId) {
        log.info("Cancelling schedule {}", scheduleId);

        WorkflowSchedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.setEnabled(false);
        scheduleRepository.save(schedule);

        log.info("Cancelled schedule {}", scheduleId);
    }

    /**
     * Reactivate a cancelled schedule
     *
     * @param scheduleId Schedule to reactivate
     */
    @Transactional
    public void reactivateSchedule(Long scheduleId) {
        log.info("Reactivating schedule {}", scheduleId);

        WorkflowSchedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.setEnabled(true);
        // Recalculate next run time
        schedule.setNextRunAt(calculateNextRunTime(schedule.getCronExpression()));
        scheduleRepository.save(schedule);

        log.info("Reactivated schedule {}", scheduleId);
    }

    /**
     * Get all schedules for a workflow
     *
     * @param workflowId Workflow ID
     * @return List of schedules
     */
    public List<WorkflowSchedule> getSchedulesForWorkflow(Long workflowId) {
        return scheduleRepository.findByWorkflowId(workflowId);
    }

    /**
     * Get all active schedules
     *
     * @return List of active schedules
     */
    public List<WorkflowSchedule> getActiveSchedules() {
        return scheduleRepository.findByEnabled(true);
    }

    /**
     * Process scheduled workflows
     * Runs every minute to check for workflows that need to be executed
     */
    @Scheduled(cron = "0 * * * * *") // Every minute at :00 seconds
    public void processScheduledWorkflows() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Processing scheduled workflows at {}", now);

        // Find all active schedules that are due to run
        List<WorkflowSchedule> dueSchedules = scheduleRepository
            .findByEnabledAndNextRunAtLessThanEqual(true, now);

        if (dueSchedules.isEmpty()) {
            log.debug("No scheduled workflows due to run");
            return;
        }

        log.info("Found {} scheduled workflows to execute", dueSchedules.size());

        for (WorkflowSchedule schedule : dueSchedules) {
            try {
                executeScheduledWorkflow(schedule);
            } catch (Exception e) {
                log.error("Error executing scheduled workflow {}: {}",
                    schedule.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Execute a scheduled workflow and update the schedule
     *
     * @param schedule Schedule to execute
     */
    @Transactional
    public void executeScheduledWorkflow(WorkflowSchedule schedule) {
        log.info("Executing scheduled workflow {} (schedule ID: {})",
            schedule.getWorkflow().getName(), schedule.getId());

        try {
            // Get trigger data (use empty object if null)
            JsonNode triggerData = schedule.getTriggerDataJson();
            if (triggerData == null) {
                triggerData = objectMapper.createObjectNode();
            }

            // Execute workflow asynchronously
            orchestratorService.executeWorkflow(schedule.getWorkflow().getId(), triggerData)
                .subscribe(
                    result -> log.info("Scheduled workflow {} completed successfully",
                        schedule.getWorkflow().getName()),
                    error -> log.error("Scheduled workflow {} failed: {}",
                        schedule.getWorkflow().getName(), error.getMessage())
                );

            // Update schedule
            schedule.setLastRunAt(LocalDateTime.now());
            schedule.setNextRunAt(calculateNextRunTime(schedule.getCronExpression()));
            scheduleRepository.save(schedule);

            log.info("Updated schedule {} - next run at {}", schedule.getId(), schedule.getNextRunAt());

        } catch (Exception e) {
            log.error("Error executing scheduled workflow {}: {}",
                schedule.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Calculate the next run time for a cron expression
     *
     * @param cronExpression Cron expression
     * @return Next run time
     */
    private LocalDateTime calculateNextRunTime(String cronExpression) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error calculating next run time for cron: {}", cronExpression, e);
            // Default to 1 hour from now if calculation fails
            return LocalDateTime.now().plusHours(1);
        }
    }

    /**
     * Update schedule trigger data
     *
     * @param scheduleId  Schedule ID
     * @param triggerData New trigger data
     */
    @Transactional
    public void updateScheduleTriggerData(Long scheduleId, JsonNode triggerData) {
        WorkflowSchedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.setTriggerDataJson(triggerData);
        scheduleRepository.save(schedule);

        log.info("Updated trigger data for schedule {}", scheduleId);
    }

    /**
     * Update schedule cron expression
     *
     * @param scheduleId     Schedule ID
     * @param cronExpression New cron expression
     */
    @Transactional
    public void updateScheduleCron(Long scheduleId, String cronExpression) {
        // Validate cron expression
        try {
            CronExpression.parse(cronExpression);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
        }

        WorkflowSchedule schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        schedule.setCronExpression(cronExpression);
        schedule.setNextRunAt(calculateNextRunTime(cronExpression));
        scheduleRepository.save(schedule);

        log.info("Updated cron expression for schedule {} - next run at {}",
            scheduleId, schedule.getNextRunAt());
    }
}
