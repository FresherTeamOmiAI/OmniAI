package com.omniai.sim.tiktok.product;

import com.omniai.sim.tiktok.SimTestSupport;
import com.omniai.sim.tiktok.common.TiktokSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Nhóm Products (T-50.1, T-50.4): Upload Image, Create/Edit Product, Update Price/Inventory, Activate/Deactivate. */
class ProductTest extends SimTestSupport {

    private static final String WAREHOUSE = "7494049642642500001";
    private String token;

    @BeforeEach
    void login() throws Exception {
        token = accessToken();
    }

    // ---------- helper ----------

    /** Upload Product Image: multipart, query ký không gồm body, không có shop_cipher (docv2). */
    private HttpResponse<String> uploadRaw(byte[] data, String signSecret) throws Exception {
        String path = "/product/202309/images/upload";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("app_key", APP_KEY);
        query.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        query.put("sign", TiktokSigner.signRequest(path, query, null, signSecret));
        String boundary = "----omniai" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\"a.webp\"\r\n"
                + "Content-Type: image/webp\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"use_case\"\r\n\r\nMAIN_IMAGE\r\n--"
                + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri(path + "?" + encode(query)))
                .header("content-type", "multipart/form-data; boundary=" + boundary)
                .header("x-tts-access-token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String uploadImage() throws Exception {
        JsonNode r = json.readTree(uploadRaw(new byte[]{1, 2, 3}, APP_SECRET).body());
        assertThat(r.get("code").asInt()).isZero();
        return r.at("/data/uri").asString();
    }

    private String productBody(String imageUri, String title, String price, int quantity, String categoryId,
                               String idempotencyKey) {
        return """
                {"title":"%s","description":"<p>Serum mỏng nhẹ</p>","category_id":"%s",
                 "main_images":[{"uri":"%s"}],
                 "skus":[{"seller_sku":"CERA-SER-01","price":{"amount":"%s","currency":"VND"},
                          "inventory":[{"warehouse_id":"%s","quantity":%d}]}],
                 "package_weight":{"value":"130","unit":"GRAM"},"save_mode":"LISTING"%s}
                """.formatted(title, categoryId, imageUri, price, WAREHOUSE, quantity,
                idempotencyKey == null ? "" : ",\"idempotency_key\":\"" + idempotencyKey + "\"");
    }

    private JsonNode create(String body) throws Exception {
        return signed("POST", "/product/202309/products", Map.of(), body, token);
    }

    private JsonNode simProduct(String productId) throws Exception {
        for (JsonNode p : getJson("/sim/products")) {
            if (p.get("id").asString().equals(productId)) {
                return p;
            }
        }
        throw new AssertionError("Simulator không có sản phẩm " + productId);
    }

    // ---------- test ----------

    @Test
    void createsProductWithUploadedImageAndReturnsIds() throws Exception {
        String image = uploadImage();
        JsonNode r = create(productBody(image, "CeraVe Serum", "289000", 15, "601450", null));

        assertThat(r.get("code").asInt()).isZero();
        String productId = r.at("/data/product_id").asString();
        assertThat(productId).matches("\\d{19}");
        assertThat(r.at("/data/skus/0/id").asString()).matches("\\d{19}");
        assertThat(r.at("/data/skus/0/seller_sku").asString()).isEqualTo("CERA-SER-01");

        JsonNode stored = simProduct(productId);
        assertThat(stored.get("title").asString()).isEqualTo("CeraVe Serum");
        assertThat(stored.get("status").asString()).isEqualTo("ACTIVATE");
        assertThat(stored.at("/skus/0/price_amount").asString()).isEqualTo("289000");
        assertThat(stored.at("/skus/0/quantity").asInt()).isEqualTo(15);
    }

    @Test
    void sameIdempotencyKeyDoesNotCreateTwice() throws Exception {
        String image = uploadImage();
        String body = productBody(image, "CeraVe Serum", "289000", 15, "601450", "hub-42-TIKTOK");
        String first = create(body).at("/data/product_id").asString();
        String second = create(body).at("/data/product_id").asString();
        assertThat(second).isEqualTo(first);
        assertThat(getJson("/sim/products").size()).isEqualTo(1);
    }

    @Test
    void rejectsWrongSignatureAndMissingScope() throws Exception {
        HttpResponse<String> badSign = uploadRaw(new byte[]{1}, "wrong-secret");
        assertThat(json.readTree(badSign.body()).get("code").asInt()).isEqualTo(106001);

        // Sai chữ ký ở Create Product: ký với body khác body gửi đi
        Map<String, String> query = new LinkedHashMap<>();
        query.put("app_key", APP_KEY);
        query.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        query.put("shop_cipher", CIPHER);
        query.put("sign", TiktokSigner.signRequest("/product/202309/products", query, "{}", APP_SECRET));
        HttpResponse<String> tampered = http.send(HttpRequest.newBuilder(uri("/product/202309/products?" + encode(query)))
                .header("content-type", "application/json").header("x-tts-access-token", token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"x\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(json.readTree(tampered.body()).get("code").asInt()).isEqualTo(106001);

        post("/sim/faults", "{\"revoked_scopes\":[\"seller.product.write\"]}");
        JsonNode noScope = create(productBody(uploadImage(), "x", "1000", 1, "601450", null));
        assertThat(noScope.get("code").asInt()).isEqualTo(105005);
    }

    @Test
    void validatesWithDocv2ErrorCodes() throws Exception {
        String image = uploadImage();
        assertThat(create(productBody(image, "A", "289000", 1, "999999", null)).get("code").asInt()).isEqualTo(12052023);
        assertThat(create(productBody("tos-unknown/x", "A", "289000", 1, "601450", null)).get("code").asInt())
                .isEqualTo(12052300);
        assertThat(create(productBody(image, "A", "-5", 1, "601450", null)).get("code").asInt()).isEqualTo(12052073);
        assertThat(create(productBody(image, "A", "289000", 100000, "601450", null)).get("code").asInt())
                .isEqualTo(12052055);
        assertThat(create(productBody(image, "A", "289000", 1, "601450", null).replace(WAREHOUSE, "1"))
                .get("code").asInt()).isEqualTo(12052097);
        assertThat(create(productBody(image, "A", "289000", 1, "601450", null).replace("\"main_images\":[{\"uri\":\"" + image + "\"}],", ""))
                .get("code").asInt()).isEqualTo(12052028);
        assertThat(create(productBody(image, "A", "289000", 1, "601450", null).replace("\"130\"", "\"0\""))
                .get("code").asInt()).isEqualTo(12019011);
        assertThat(getJson("/sim/products").size()).isZero();
    }

    @Test
    void editPriceInventoryAndStatusMatchHub() throws Exception {
        String image = uploadImage();
        JsonNode created = create(productBody(image, "CeraVe Serum", "289000", 15, "601450", null));
        String productId = created.at("/data/product_id").asString();
        String skuId = created.at("/data/skus/0/id").asString();

        JsonNode edited = signed("PUT", "/product/202509/products/" + productId, Map.of(),
                productBody(image, "CeraVe Serum mới", "289000", 15, "601450", null), token);
        assertThat(edited.get("code").asInt()).isZero();
        assertThat(edited.at("/data/skus/0/id").asString()).isEqualTo(skuId); // giữ SKU id theo seller_sku

        JsonNode price = signed("POST", "/product/202309/products/" + productId + "/prices/update", Map.of(),
                "{\"skus\":[{\"id\":\"" + skuId + "\",\"price\":{\"amount\":\"259000\",\"currency\":\"VND\"}}]}", token);
        assertThat(price.get("code").asInt()).isZero();
        JsonNode stock = signed("POST", "/product/202309/products/" + productId + "/inventory/update", Map.of(),
                "{\"skus\":[{\"id\":\"" + skuId + "\",\"inventory\":[{\"warehouse_id\":\"" + WAREHOUSE + "\",\"quantity\":7}]}]}", token);
        assertThat(stock.get("code").asInt()).isZero();
        JsonNode off = signed("POST", "/product/202309/products/deactivate", Map.of(),
                "{\"product_ids\":[\"" + productId + "\"]}", token);
        assertThat(off.get("code").asInt()).isZero();

        JsonNode stored = simProduct(productId);
        assertThat(stored.get("title").asString()).isEqualTo("CeraVe Serum mới");
        assertThat(stored.at("/skus/0/price_amount").asString()).isEqualTo("259000");
        assertThat(stored.at("/skus/0/quantity").asInt()).isEqualTo(7);
        assertThat(stored.get("status").asString()).isEqualTo("SELLER_DEACTIVATED");

        JsonNode got = get("/product/202309/products/" + productId, Map.of(), token);
        assertThat(got.at("/data/skus/0/price_amount").asString()).isEqualTo("259000");
    }

    @Test
    void updatesOnUnknownProductOrSkuFail() throws Exception {
        assertThat(signed("PUT", "/product/202509/products/123", Map.of(),
                productBody(uploadImage(), "A", "1000", 1, "601450", null), token).get("code").asInt()).isEqualTo(12052260);
        assertThat(signed("POST", "/product/202309/products/123/prices/update", Map.of(),
                "{\"skus\":[{\"id\":\"1\",\"price\":{\"amount\":\"1000\",\"currency\":\"VND\"}}]}", token)
                .get("code").asInt()).isEqualTo(12052032);
        String productId = create(productBody(uploadImage(), "A", "1000", 1, "601450", null)).at("/data/product_id").asString();
        assertThat(signed("POST", "/product/202309/products/" + productId + "/inventory/update", Map.of(),
                "{\"skus\":[{\"id\":\"999\",\"inventory\":[{\"warehouse_id\":\"" + WAREHOUSE + "\",\"quantity\":1}]}]}", token)
                .get("code").asInt()).isEqualTo(12052556);
        assertThat(signed("POST", "/product/202309/products/" + productId + "/prices/update", Map.of(),
                "{\"skus\":[{\"id\":\"999\",\"price\":{\"amount\":\"1000\",\"currency\":\"USD\"}}]}", token)
                .get("code").asInt()).isEqualTo(12052556);
    }
}
