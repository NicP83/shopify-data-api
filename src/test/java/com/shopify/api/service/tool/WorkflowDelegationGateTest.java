package com.shopify.api.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.agent.Workflow;
import com.shopify.api.repository.agent.WorkflowRepository;
import com.shopify.api.service.ChatbotConfigService;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Locks in the existing linked-workflow gate (the agent gate now mirrors it). */
class WorkflowDelegationGateTest {

    @Test
    void unlinkedWorkflowIsRefusedAndNeverRuns() {
        WorkflowRepository repo = mock(WorkflowRepository.class);
        WorkflowOrchestratorService orchestrator = mock(WorkflowOrchestratorService.class);
        ChatbotConfigService configService = mock(ChatbotConfigService.class);
        Workflow workflow = new Workflow();
        workflow.setId(42L);
        workflow.setName("Stock Check");
        workflow.setIsActive(true);
        when(repo.findByName("Stock Check")).thenReturn(Optional.of(workflow));
        when(configService.getConfig()).thenReturn(
            ChatbotConfig.builder().linkedWorkflowIds(List.of(7L)).build());

        WorkflowDelegationToolHandler handler =
            new WorkflowDelegationToolHandler(repo, orchestrator, configService);
        JsonNode input = new ObjectMapper().createObjectNode()
            .put("workflow_name", "Stock Check").put("task", "check stock");

        JsonNode result = handler.execute(input).block();

        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("not available from chat");
        verify(orchestrator, never()).executeWorkflow(anyLong(), any());
    }
}
