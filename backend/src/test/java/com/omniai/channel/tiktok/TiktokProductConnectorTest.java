package com.omniai.channel.tiktok;

import com.omniai.channel.tiktok.TiktokModels.ProductBody;
import com.omniai.channel.tiktok.TiktokModels.ProductResult;
import com.omniai.channel.tiktok.TiktokModels.ProductSkuRef;
import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Connector TikTok (T-50.1, T-50.2): dựng body Create/Edit Product đúng schema docv2, giá/tồn, phân loại lỗi. */
class TiktokProductConnectorTest {

    private static final String SHOP = "7494049642642441621";
    private static final String IMAGE_URL = "https://x.supabase.co/storage/v1/object/public/product-images/CERA-SER-01.webp";

    private final TiktokShopClient client = mock(TiktokShopClient.class);
    private final TiktokTokenManager tokens = mock(TiktokTokenManager.class);
    private final TiktokProductProperties props = new TiktokProductProperties(true, "WH1", Map.of(), 50);
    private MockRestServiceServer images;
    private TiktokProductConnector connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        images = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        connector = new TiktokProductConnector(client, tokens, props, builder.build());
        when(tokens.activeShopId()).thenReturn(Optional.of(SHOP));
        when(client.uploadProductImage(eq(SHOP), any(), eq("CERA-SER-01.webp"))).thenReturn("tos-sim-i-omniai/abc");
    }

    private static ProductSnapshot snapshot(String name, boolean active) {
        return new ProductSnapshot(42L, "TIKTOK", "CERA-SER-01", name, "CeraVe", "serum",
                "Serum <mỏng nhẹ>", "Aqua, Niacinamide", 30, List.of("CERA-SER-01.webp"), List.of(IMAGE_URL),
                279_000L, 15, active);
    }

    private void expectImageDownload() {
        images.expect(requestTo(IMAGE_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));
    }

    @Test
    void createSendsDocv2BodyWithImagePriceStockAndIdempotencyKey() {
        expectImageDownload();
        when(client.createProduct(eq(SHOP), any())).thenReturn(
                new ProductResult("1729592969712207008", List.of(new ProductSkuRef("1729592969712207012", "CERA-SER-01"))));

        String externalId = connector.push(snapshot("CeraVe Serum", true), null);

        assertThat(externalId).isEqualTo("1729592969712207008:1729592969712207012");
        ArgumentCaptor<ProductBody> body = ArgumentCaptor.forClass(ProductBody.class);
        verify(client).createProduct(eq(SHOP), body.capture());
        ProductBody b = body.getValue();
        assertThat(b.title()).isEqualTo("CeraVe Serum");
        assertThat(b.categoryId()).isEqualTo("601452"); // serum, bảng mặc định khớp simulator
        assertThat(b.mainImages()).extracting(TiktokModels.ProductImage::uri).containsExactly("tos-sim-i-omniai/abc");
        assertThat(b.skus()).hasSize(1);
        assertThat(b.skus().get(0).sellerSku()).isEqualTo("CERA-SER-01");
        assertThat(b.skus().get(0).price()).isEqualTo(new TiktokModels.ProductPrice("279000", "VND"));
        assertThat(b.skus().get(0).inventory()).containsExactly(new TiktokModels.ProductInventory("WH1", 15));
        assertThat(b.packageWeight()).isEqualTo(new TiktokModels.ProductWeight("80", "GRAM"));
        assertThat(b.idempotencyKey()).isEqualTo("omniai-product-42");
        assertThat(b.description()).contains("Serum &lt;mỏng nhẹ&gt;").contains("Thành phần: Aqua, Niacinamide");
        // Tạo mới đã ACTIVATE sẵn
        verify(client, never()).setProductActive(anyString(), anyString(), anyBoolean());

        assertThat(TiktokModels.JSON.writeValueAsString(b))
                .contains("\"main_images\":[{\"uri\":\"tos-sim-i-omniai/abc\"}]")
                .contains("\"seller_sku\":\"CERA-SER-01\"")
                .contains("\"warehouse_id\":\"WH1\"")
                .contains("\"save_mode\":\"LISTING\"");
    }

    @Test
    void editKeepsSkuIdReusesUploadedImageAndAppliesHiddenState() {
        expectImageDownload();
        when(client.createProduct(eq(SHOP), any())).thenReturn(new ProductResult("P1", List.of(new ProductSkuRef("S1", "CERA-SER-01"))));
        when(client.editProduct(eq(SHOP), eq("P1"), any())).thenReturn(new ProductResult("P1", List.of(new ProductSkuRef("S1", "CERA-SER-01"))));
        connector.push(snapshot("CeraVe Serum", true), null);

        String externalId = connector.push(snapshot("CeraVe Serum mới", false), "P1:S1");

        assertThat(externalId).isEqualTo("P1:S1");
        ArgumentCaptor<ProductBody> body = ArgumentCaptor.forClass(ProductBody.class);
        verify(client).editProduct(eq(SHOP), eq("P1"), body.capture());
        assertThat(body.getValue().skus().get(0).id()).isEqualTo("S1");
        assertThat(body.getValue().idempotencyKey()).isNull();
        verify(client).setProductActive(SHOP, "P1", false);
        verify(client, times(1)).uploadProductImage(eq(SHOP), any(), anyString()); // ảnh đã có uri, không upload lại
    }

    @Test
    void priceAndStockUseUpdateApis() {
        connector.pushPrice(snapshot("x", true), "P1:S1");
        connector.pushStock(snapshot("x", true), "P1:S1");
        verify(client).updatePrice(SHOP, "P1", "S1", 279_000L);
        verify(client).updateInventory(SHOP, "P1", "S1", "WH1", 15);
    }

    @Test
    void classifiesErrors() {
        expectImageDownload();
        when(client.createProduct(eq(SHOP), any()))
                .thenThrow(new TiktokApiException(12052023, "Category does not exist", "R", 200));
        assertThatThrownBy(() -> connector.push(snapshot("x", true), null))
                .isInstanceOfSatisfying(SyncException.class, e -> assertThat(e.retryable()).isFalse());

        org.mockito.Mockito.doThrow(new TiktokApiException(TiktokErrorCode.INTERNAL_ERROR.code(), "internal", "R", 200))
                .when(client).updatePrice(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertThatThrownBy(() -> connector.pushPrice(snapshot("x", true), "P1:S1"))
                .isInstanceOfSatisfying(SyncException.class, e -> assertThat(e.retryable()).isTrue());

        ProductSnapshot noImage = new ProductSnapshot(42L, "TIKTOK", "CERA-SER-01", "x", "b", "serum", null, null, 30,
                List.of(), List.of(), 1000L, 1, true);
        assertThatThrownBy(() -> connector.push(noImage, null))
                .isInstanceOfSatisfying(SyncException.class, e -> assertThat(e.retryable()).isFalse())
                .hasMessageContaining("ảnh");
        ProductSnapshot unknownCategory = new ProductSnapshot(42L, "TIKTOK", "CERA-SER-01", "x", "b", "shampoo", null,
                null, 30, List.of("k"), List.of(IMAGE_URL), 1000L, 1, true);
        assertThatThrownBy(() -> connector.push(unknownCategory, null)).hasMessageContaining("shampoo");
    }

    @Test
    void notReadyWithoutAuthorizedShop() {
        when(tokens.activeShopId()).thenReturn(Optional.empty());
        assertThat(connector.ready()).isFalse();
        assertThatThrownBy(() -> connector.pushStock(snapshot("x", true), "P1:S1"))
                .isInstanceOfSatisfying(SyncException.class, e -> assertThat(e.retryable()).isFalse());
    }
}
