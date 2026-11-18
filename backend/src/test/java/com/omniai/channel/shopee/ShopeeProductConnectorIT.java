package com.omniai.channel.shopee;

import com.omniai.TestcontainersConfig;
import com.omniai.brain.BrainClient;
import com.omniai.sync.ProductSnapshot;
import com.omniai.sync.SyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Connector giá/tồn Shopee (T-50.2) gọi HTTP thật, có ký, vào simulator Shopee trong backend (cổng ngẫu nhiên).
 * Số trên simulator phải khớp giá kênh SHOPEE và tồn của hub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "omniai.sync.enabled=false")
@Import(TestcontainersConfig.class)
class ShopeeProductConnectorIT {

    @LocalServerPort
    int port;
    @Autowired
    ShopeeChannelProperties props;
    @Autowired
    ShopeeSimulatorStore store;
    @Autowired
    JsonMapper json;
    @MockitoBean
    BrainClient brainClient;

    private ShopeeProductConnector connector;

    @BeforeEach
    void setUp() {
        store.jdbc().execute("TRUNCATE TABLE tbl_shopee_seller_stock, tbl_shopee_price_list, tbl_shopee_item "
                + "RESTART IDENTITY CASCADE");
        ShopeeChannelProperties local = copy(props);
        local.setApiBaseUrl("http://localhost:" + port);
        connector = new ShopeeProductConnector(local, json);
    }

    private static ShopeeChannelProperties copy(ShopeeChannelProperties p) {
        ShopeeChannelProperties c = new ShopeeChannelProperties();
        c.setPartnerId(p.getPartnerId());
        c.setPartnerKey(p.getPartnerKey());
        c.setShopId(p.getShopId());
        c.setAccessToken(p.getAccessToken());
        return c;
    }

    private static ProductSnapshot snapshot(long price, int stock) {
        return new ProductSnapshot(42L, "SHOPEE", "CERA-SER-01", "Serum", "CeraVe", "serum", "Mô tả", "Aqua", 30,
                List.of(), List.of(), price, stock, true);
    }

    @Test
    void priceAndStockOnSimulatorMatchHub() {
        connector.pushPrice(snapshot(289_000, 15), "880011");
        connector.pushStock(snapshot(289_000, 15), "880011");

        ShopeeSimulatorStore.Item item = store.item(880011L).orElseThrow();
        assertThat(item.originalPrice()).isEqualTo(289_000L);
        assertThat(item.stock()).isEqualTo(15L);

        connector.pushStock(snapshot(289_000, 0), "880011");
        assertThat(store.item(880011L).orElseThrow().stock()).isZero();
    }

    @Test
    void wrongKeyIsPermanentErrorAndBadItemIdIsRejected() {
        ShopeeChannelProperties wrong = copy(props);
        wrong.setApiBaseUrl("http://localhost:" + port);
        wrong.setPartnerKey("sai-khoa");
        ShopeeProductConnector bad = new ShopeeProductConnector(wrong, json);

        assertThatThrownBy(() -> bad.pushPrice(snapshot(289_000, 1), "880011"))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("error_sign")
                .satisfies(e -> assertThat(((SyncException) e).retryable()).isFalse());
        assertThatThrownBy(() -> connector.pushStock(snapshot(1, 1), "abc"))
                .isInstanceOf(SyncException.class);
        assertThat(store.item(880011L)).isEmpty();
    }
}
