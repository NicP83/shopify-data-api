package com.shopify.api.service.voucher;

/**
 * Who is chatting, as seen by the storefront chat controller (never by the model).
 * The controller puts it in the Reactor context under {@link #CONTEXT_KEY}; the
 * offer_discount tool refuses when it is absent, so only the storefront chat
 * endpoints can issue vouchers.
 *
 * @param sessionId  the widget's chat session id
 * @param customerId the widget's userIdentifier (logged-in customer), may be null
 * @param clientIp   best-effort client IP (weak signal: proxies, shared networks)
 */
public record VoucherChatContext(String sessionId, String customerId, String clientIp) {

    public static final String CONTEXT_KEY = "chat.voucherContext";
}
