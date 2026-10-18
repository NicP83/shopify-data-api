package com.shopify.api.controller.v1;

import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.model.ApiKey;
import com.shopify.api.service.agent.AgentExecutionService;
import com.shopify.api.service.agent.AgentService;
import com.shopify.api.service.agent.WorkflowOrchestratorService;
import com.shopify.api.service.agent.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Binding checks on /api/v1 run routes. An unknown agent/workflow (404) means the
 * request got past scope and binding checks; 403 means it was refused before any lookup.
 */
class ExternalApiControllerBindingTest {

    private static final Map<String, Object> BODY = Map.of("task", "hello");

    private AgentService agentService;
    private AgentExecutionService agentExecutionService;
    private WorkflowService workflowService;
    private WorkflowOrchestratorService workflowOrchestratorService;
    private ExternalApiController controller;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        agentExecutionService = mock(AgentExecutionService.class);
        workflowService = mock(WorkflowService.class);
        workflowOrchestratorService = mock(WorkflowOrchestratorService.class);
        when(agentService.getAgentById(anyLong())).thenReturn(Optional.empty());
        when(workflowService.getWorkflowById(anyLong())).thenReturn(Optional.empty());
        controller = new ExternalApiController(
            agentService, agentExecutionService, workflowService, workflowOrchestratorService);
    }

    private static MockHttpServletRequest requestWith(String scopes, Long agentId, Long workflowId) {
        ApiKey key = ApiKey.builder().id(1L).name("k").scopes(scopes).active(true).build();
        key.setBoundAgentId(agentId);
        key.setBoundWorkflowId(workflowId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.ATTR_API_KEY, key);
        return request;
    }

    private int runAgent(MockHttpServletRequest request, long id) {
        ResponseEntity<Object> res = controller.runAgent(request, id, BODY).block();
        return res.getStatusCode().value();
    }

    private int runWorkflow(MockHttpServletRequest request, long id) {
        ResponseEntity<Object> res = controller.runWorkflow(request, id, BODY).block();
        return res.getStatusCode().value();
    }

    @Test
    void agentBoundKeyRunsOnlyItsAgent() {
        MockHttpServletRequest hr = requestWith("agents:run,workflows:run", 23L, null);
        assertThat(runAgent(hr, 23L)).isEqualTo(404);
        assertThat(runAgent(hr, 5L)).isEqualTo(403);
        assertThat(runWorkflow(hr, 7L)).isEqualTo(403);
        verify(agentService, never()).getAgentById(5L);
        verify(workflowService, never()).getWorkflowById(anyLong());
        verify(agentExecutionService, never()).executeAgent(anyLong(), any());
    }

    @Test
    void workflowBoundKeyRunsOnlyItsWorkflow() {
        MockHttpServletRequest wf = requestWith("agents:run,workflows:run", null, 7L);
        assertThat(runWorkflow(wf, 7L)).isEqualTo(404);
        assertThat(runWorkflow(wf, 8L)).isEqualTo(403);
        assertThat(runAgent(wf, 23L)).isEqualTo(403);
        verify(workflowOrchestratorService, never()).executeWorkflow(anyLong(), any());
    }

    @Test
    void unboundKeyKeepsCurrentBehaviour() {
        MockHttpServletRequest unbound = requestWith("agents:run,workflows:run", null, null);
        assertThat(runAgent(unbound, 5L)).isEqualTo(404);
        assertThat(runWorkflow(unbound, 8L)).isEqualTo(404);
    }

    @Test
    void scopeIsStillCheckedFirst() {
        MockHttpServletRequest noScope = requestWith("workflows:run", 23L, null);
        assertThat(runAgent(noScope, 23L)).isEqualTo(403);
    }
}
