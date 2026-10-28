package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.service.voucher.VoucherChatContext;
import com.shopify.api.service.voucher.VoucherService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferDiscountChatToolHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesLooseNumbersWithoutTrustingThem() throws Exception {
        VoucherService vouchers = mock(VoucherService.class);
        when(vouchers.offer(any(), any())).thenReturn(new VoucherService.OfferResult("refused",
            Map.of("reason_code", "BAD_AMOUNT", "message", "The voucher amount must be at least $5")));
        OfferDiscountChatToolHandler handler = new OfferDiscountChatToolHandler(vouchers);

        String out = handler.execute(mapper.readTree(
                "{\"cart_items\":[{\"variant_id\":\"12\",\"quantity\":\"2\"},{\"variant_id\":null,\"quantity\":1}],"
                    + "\"amount\":\"-$20\",\"reason\":\"r\",\"customer_email\":\"a@b.co\"}"))
            .contextWrite(ctx -> ctx.put(VoucherChatContext.CONTEXT_KEY, new VoucherChatContext("s", null, null)))
            .block();

        ArgumentCaptor<VoucherService.OfferInput> input = ArgumentCaptor.forClass(VoucherService.OfferInput.class);
        verify(vouchers).offer(input.capture(), any());
        assertThat(input.getValue().requestedAmount()).isEqualByComparingTo(new BigDecimal("-20"));
        assertThat(input.getValue().lines()).hasSize(2);
        assertThat(input.getValue().lines().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(input.getValue().lines().get(1).variantId()).isNull();
        assertThat(mapper.readTree(out).get("status").asText()).isEqualTo("refused");
    }

    @Test
    void serviceErrorsBecomeAPoliteRefusal() throws Exception {
        VoucherService vouchers = mock(VoucherService.class);
        when(vouchers.offer(any(), any())).thenThrow(new IllegalStateException("db down"));
        String out = new OfferDiscountChatToolHandler(vouchers).execute(mapper.readTree("{}")).block();
        assertThat(mapper.readTree(out).get("status").asText()).isEqualTo("refused");
        assertThat(out).doesNotContain("db down");
    }

    @Test
    void enabledFollowsTheServiceAndFailsClosed() {
        VoucherService vouchers = mock(VoucherService.class);
        OfferDiscountChatToolHandler handler = new OfferDiscountChatToolHandler(vouchers);
        when(vouchers.toolAvailable()).thenReturn(true);
        assertThat(handler.isEnabled(null)).isTrue();
        when(vouchers.toolAvailable()).thenThrow(new RuntimeException("boom"));
        assertThat(handler.isEnabled(null)).isFalse();
    }
}
