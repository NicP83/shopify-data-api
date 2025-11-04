package com.shopify.api.controller;

import com.shopify.api.config.ShopifyAppConfig;
import com.shopify.api.service.ShopifyOAuthService;
import com.shopify.api.service.ShopifyShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Shopify app installation (OAuth flow)
 */
@Controller
@RequestMapping("/shopify")
public class ShopifyInstallController {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyInstallController.class);

    private final ShopifyAppConfig shopifyAppConfig;
    private final ShopifyOAuthService shopifyOAuthService;
    private final ShopifyShopService shopifyShopService;

    @Autowired
    public ShopifyInstallController(ShopifyAppConfig shopifyAppConfig,
                                     ShopifyOAuthService shopifyOAuthService,
                                     ShopifyShopService shopifyShopService) {
        this.shopifyAppConfig = shopifyAppConfig;
        this.shopifyOAuthService = shopifyOAuthService;
        this.shopifyShopService = shopifyShopService;
    }

    /**
     * Step 1: Install app (redirect to Shopify OAuth)
     * GET /shopify/install?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/install")
    public RedirectView installApp(@RequestParam String shop, HttpSession session) {
        logger.info("Installing app for shop: {}", shop);

        // Validate shop domain
        if (!shopifyOAuthService.isValidShopDomain(shop)) {
            logger.error("Invalid shop domain: {}", shop);
            return new RedirectView("/error?message=Invalid shop domain");
        }

        // Generate nonce for CSRF protection
        String nonce = shopifyOAuthService.generateNonce();
        session.setAttribute("oauth_nonce", nonce);
        session.setAttribute("oauth_shop", shop);

        logger.debug("Generated nonce: {}", nonce);

        // Redirect to Shopify OAuth authorization
        String authUrl = shopifyAppConfig.getAuthorizationUrl(shop, nonce);
        return new RedirectView(authUrl);
    }

    /**
     * Step 2: OAuth callback (Shopify redirects here after approval)
     * GET /shopify/callback?shop=...&code=...&state=...&hmac=...
     */
    @GetMapping("/callback")
    public RedirectView authCallback(@RequestParam String shop,
                                      @RequestParam String code,
                                      @RequestParam String state,
                                      @RequestParam String hmac,
                                      @RequestParam(required = false) String timestamp,
                                      HttpSession session) {
        logger.info("OAuth callback received for shop: {}", shop);

        try {
            // Verify nonce (CSRF protection)
            String storedNonce = (String) session.getAttribute("oauth_nonce");
            String storedShop = (String) session.getAttribute("oauth_shop");

            if (storedNonce == null || !storedNonce.equals(state)) {
                logger.error("Invalid nonce. Expected: {}, Got: {}", storedNonce, state);
                return new RedirectView("/error?message=Invalid state parameter");
            }

            if (!shop.equals(storedShop)) {
                logger.error("Shop mismatch. Expected: {}, Got: {}", storedShop, shop);
                return new RedirectView("/error?message=Shop mismatch");
            }

            // Verify HMAC signature
            Map<String, String> params = new HashMap<>();
            params.put("shop", shop);
            params.put("code", code);
            params.put("state", state);
            if (timestamp != null) {
                params.put("timestamp", timestamp);
            }

            if (!shopifyOAuthService.verifyHmac(params, hmac)) {
                logger.error("HMAC verification failed for shop: {}", shop);
                return new RedirectView("/error?message=HMAC verification failed");
            }

            // Exchange code for access token
            String accessToken = shopifyOAuthService.exchangeCodeForToken(shop, code);

            // Save shop to database
            shopifyShopService.saveShop(shop, accessToken, shopifyAppConfig.getScopes());

            // Clear session
            session.removeAttribute("oauth_nonce");
            session.removeAttribute("oauth_shop");

            logger.info("Successfully installed app for shop: {}", shop);

            // Redirect to admin dashboard
            return new RedirectView("/admin?shop=" + shop + "&installed=true");

        } catch (Exception e) {
            logger.error("OAuth callback error: {}", e.getMessage(), e);
            return new RedirectView("/error?message=Installation failed");
        }
    }
}
