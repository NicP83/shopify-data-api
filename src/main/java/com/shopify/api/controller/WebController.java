package com.shopify.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to handle React Router client-side routing.
 *
 * Forwards all non-API routes to index.html so React Router can handle them.
 * This enables direct navigation to routes like /workflow/execute/:id
 */
@Controller
public class WebController {

    /**
     * Forward all non-API, non-static-resource requests to index.html
     * This allows React Router to handle client-side routing
     */
    @GetMapping(value = {
        "/",
        "/products",
        "/chat",
        "/fulfillment",
        "/agents",
        "/agents/**",
        "/workflows",
        "/workflows/**",
        "/workflow-gallery",
        "/workflow/execute/**",
        "/workflow/chat/**",
        "/executions",
        "/executions/**",
        "/approvals",
        "/settings",
        "/analytics",
        "/market-intel"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
