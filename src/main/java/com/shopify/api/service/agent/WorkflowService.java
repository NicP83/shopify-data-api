package com.shopify.api.service.agent;

import com.shopify.api.model.agent.Agent;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.model.agent.WorkflowStep;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.repository.agent.WorkflowRepository;
import com.shopify.api.repository.agent.WorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing workflows
 *
 * Workflows orchestrate multiple agents in sequences with conditional logic,
 * approvals, and scheduled execution.
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final AgentRepository agentRepository;

    /**
     * Create a new workflow
     */
    @Transactional
    public Workflow createWorkflow(Workflow workflow) {
        log.info("Creating new workflow: {}", workflow.getName());

        if (workflowRepository.existsByName(workflow.getName())) {
            throw new IllegalArgumentException("Workflow with name '" + workflow.getName() + "' already exists");
        }

        return workflowRepository.save(workflow);
    }

    /**
     * Get workflow by ID
     */
    @Transactional(readOnly = true)
    public Optional<Workflow> getWorkflowById(Long id) {
        return workflowRepository.findById(id);
    }

    /**
     * Get workflow by name
     */
    @Transactional(readOnly = true)
    public Optional<Workflow> getWorkflowByName(String name) {
        return workflowRepository.findByName(name);
    }

    /**
     * Get all workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAll();
    }

    /**
     * Get all active workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getActiveWorkflows() {
        return workflowRepository.findByIsActiveTrue();
    }

    /**
     * Get workflows by trigger type
     */
    @Transactional(readOnly = true)
    public List<Workflow> getWorkflowsByTriggerType(String triggerType) {
        return workflowRepository.findByTriggerType(triggerType);
    }

    /**
     * Get active workflows by trigger type
     */
    @Transactional(readOnly = true)
    public List<Workflow> getActiveWorkflowsByTriggerType(String triggerType) {
        return workflowRepository.findByTriggerTypeAndIsActiveTrue(triggerType);
    }

    /**
     * Update an existing workflow
     */
    @Transactional
    public Workflow updateWorkflow(Long id, Workflow updatedWorkflow) {
        log.info("Updating workflow with ID: {}", id);

        Workflow existingWorkflow = workflowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));

        // Check if name is being changed and if new name already exists
        if (!existingWorkflow.getName().equals(updatedWorkflow.getName()) &&
            workflowRepository.existsByName(updatedWorkflow.getName())) {
            throw new IllegalArgumentException("Workflow with name '" + updatedWorkflow.getName() + "' already exists");
        }

        // Update fields
        existingWorkflow.setName(updatedWorkflow.getName());
        existingWorkflow.setDescription(updatedWorkflow.getDescription());
        existingWorkflow.setTriggerType(updatedWorkflow.getTriggerType());
        existingWorkflow.setTriggerConfigJson(updatedWorkflow.getTriggerConfigJson());
        existingWorkflow.setExecutionMode(updatedWorkflow.getExecutionMode());
        existingWorkflow.setIsActive(updatedWorkflow.getIsActive());

        // Update Phase 2 fields (UI/UX enhancements)
        existingWorkflow.setInputSchemaJson(updatedWorkflow.getInputSchemaJson());
        existingWorkflow.setInterfaceType(updatedWorkflow.getInterfaceType());
        existingWorkflow.setIsPublic(updatedWorkflow.getIsPublic());

        return workflowRepository.save(existingWorkflow);
    }

    /**
     * Delete a workflow
     */
    @Transactional
    public void deleteWorkflow(Long id) {
        log.info("Deleting workflow with ID: {}", id);

        if (!workflowRepository.existsById(id)) {
            throw new IllegalArgumentException("Workflow not found with ID: " + id);
        }

        workflowRepository.deleteById(id);
    }

    /**
     * Add a step to a workflow
     */
    @Transactional
    public WorkflowStep addWorkflowStep(Long workflowId, Long agentId, WorkflowStep step) {
        log.info("Adding step to workflow {}", workflowId);

        Workflow workflow = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + workflowId));

        if (agentId != null) {
            Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + agentId));
            step.setAgent(agent);
        }

        step.setWorkflow(workflow);
        return workflowStepRepository.save(step);
    }

    /**
     * Get all steps for a workflow
     */
    @Transactional(readOnly = true)
    public List<WorkflowStep> getWorkflowSteps(Long workflowId) {
        return workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);
    }

    /**
     * Update a workflow step
     */
    @Transactional
    public WorkflowStep updateWorkflowStep(Long stepId, Long agentId, WorkflowStep updatedStep) {
        log.info("Updating workflow step with ID: {}", stepId);

        WorkflowStep existingStep = workflowStepRepository.findById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow step not found with ID: " + stepId));

        // Update agent if provided
        if (agentId != null) {
            Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + agentId));
            existingStep.setAgent(agent);
        } else {
            existingStep.setAgent(null);
        }

        // Update fields
        existingStep.setStepOrder(updatedStep.getStepOrder());
        existingStep.setStepType(updatedStep.getStepType());
        existingStep.setName(updatedStep.getName());
        existingStep.setInputMappingJson(updatedStep.getInputMappingJson());
        existingStep.setOutputVariable(updatedStep.getOutputVariable());
        existingStep.setConditionExpression(updatedStep.getConditionExpression());
        existingStep.setDependsOn(updatedStep.getDependsOn());
        existingStep.setApprovalConfigJson(updatedStep.getApprovalConfigJson());
        existingStep.setRetryConfigJson(updatedStep.getRetryConfigJson());
        existingStep.setTimeoutSeconds(updatedStep.getTimeoutSeconds());

        return workflowStepRepository.save(existingStep);
    }

    /**
     * Delete a workflow step
     */
    @Transactional
    public void deleteWorkflowStep(Long stepId) {
        log.info("Deleting workflow step with ID: {}", stepId);

        if (!workflowStepRepository.existsById(stepId)) {
            throw new IllegalArgumentException("Workflow step not found with ID: " + stepId);
        }

        workflowStepRepository.deleteById(stepId);
    }

    /**
     * Activate a workflow
     */
    @Transactional
    public void activateWorkflow(Long id) {
        log.info("Activating workflow with ID: {}", id);

        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));

        workflow.setIsActive(true);
        workflowRepository.save(workflow);
    }

    /**
     * Deactivate a workflow
     */
    @Transactional
    public void deactivateWorkflow(Long id) {
        log.info("Deactivating workflow with ID: {}", id);

        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found with ID: " + id));

        workflow.setIsActive(false);
        workflowRepository.save(workflow);
    }

    /**
     * Reorder workflow steps
     */
    @Transactional
    public void reorderWorkflowSteps(Long workflowId, List<Long> stepIds) {
        log.info("Reordering steps for workflow {}", workflowId);

        List<WorkflowStep> steps = workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);

        if (steps.size() != stepIds.size()) {
            throw new IllegalArgumentException("Step IDs count doesn't match existing steps count");
        }

        for (int i = 0; i < stepIds.size(); i++) {
            Long stepId = stepIds.get(i);
            WorkflowStep step = steps.stream()
                .filter(s -> s.getId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step not found with ID: " + stepId));

            step.setStepOrder(i + 1);
            workflowStepRepository.save(step);
        }
    }
}
