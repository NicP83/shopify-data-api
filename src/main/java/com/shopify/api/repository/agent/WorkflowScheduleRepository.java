package com.shopify.api.repository.agent;

import com.shopify.api.model.agent.WorkflowSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for WorkflowSchedule entity
 *
 * Manages cron-based workflow scheduling configuration.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Repository
public interface WorkflowScheduleRepository extends JpaRepository<WorkflowSchedule, Long> {

    /**
     * Find all schedules for a workflow
     */
    List<WorkflowSchedule> findByWorkflowId(Long workflowId);

    /**
     * Find all schedules by enabled status
     */
    List<WorkflowSchedule> findByEnabled(Boolean enabled);

    /**
     * Find schedules due to run (next_run_at <= now)
     */
    @Query("SELECT ws FROM WorkflowSchedule ws WHERE ws.enabled = true AND ws.nextRunAt <= :now")
    List<WorkflowSchedule> findSchedulesDueToRun(LocalDateTime now);

    /**
     * Find enabled schedules due to run - method signature compatible with Spring Data JPA
     */
    List<WorkflowSchedule> findByEnabledAndNextRunAtLessThanEqual(Boolean enabled, LocalDateTime nextRunAt);

    /**
     * Find schedules by workflow and enabled status
     */
    Optional<WorkflowSchedule> findByWorkflowIdAndEnabledTrue(Long workflowId);

    /**
     * Count enabled schedules
     */
    long countByEnabledTrue();

    /**
     * Delete schedule for a workflow
     */
    void deleteByWorkflowId(Long workflowId);
}
