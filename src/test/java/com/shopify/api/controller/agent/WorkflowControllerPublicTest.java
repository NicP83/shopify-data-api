package com.shopify.api.controller.agent;

import com.shopify.api.model.agent.Workflow;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import com.shopify.api.service.agent.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowControllerPublicTest {

    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final WorkflowController controller =
        new WorkflowController(workflowService, mock(WorkflowOrchestratorService.class));

    @Test
    void publicWorkflowReturnsOnlyFormFields() {
        Workflow wf = Workflow.builder().id(7L).name("Quote").description("d").isPublic(true).build();
        when(workflowService.getWorkflowById(7L)).thenReturn(Optional.of(wf));

        ResponseEntity<Object> res = controller.getPublicWorkflow(7L);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body).containsOnlyKeys("id", "name", "description", "inputSchemaJson", "isPublic");
        assertThat(body.get("name")).isEqualTo("Quote");
    }

    @Test
    void privateWorkflowIsNotFound() {
        Workflow wf = Workflow.builder().id(8L).name("Internal").isPublic(false).build();
        when(workflowService.getWorkflowById(8L)).thenReturn(Optional.of(wf));

        assertThat(controller.getPublicWorkflow(8L).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void missingWorkflowIsNotFound() {
        when(workflowService.getWorkflowById(9L)).thenReturn(Optional.empty());

        assertThat(controller.getPublicWorkflow(9L).getStatusCode().value()).isEqualTo(404);
    }
}
