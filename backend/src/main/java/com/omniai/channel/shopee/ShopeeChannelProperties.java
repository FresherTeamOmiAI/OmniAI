package com.omniai.channel.shopee;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Khóa test của simulator Shopee. Không phải khóa sàn thật. Owner: ChienPX. */
@Component
@ConfigurationProperties(prefix = "omniai.channels.shopee")
public class ShopeeChannelProperties {

    private long partnerId = 2000001L;
    private String partnerKey = "test-partner-key";
    private long shopId = 10001L;
    private String accessToken = "test-access-token";
    /** Host Open API Shopee hub gọi ra (giá, tồn; US-50). Simulator nằm ngay trong backend nên mặc định là hub. */
    private String apiBaseUrl = "http://127.0.0.1:8080";
    /** Tắt thì không có connector giá/tồn SHOPEE trong khung đồng bộ. */
    private boolean syncEnabled = true;
    /** URL hub sẽ nhận push đơn. Dùng để ký, chưa gọi mạng nếu để trống. */
    private String pushCallbackUrl = "http://127.0.0.1:8080/api/channels/shopee/push";
    /** Postgres của simulator. Supabase dùng database postgres; local/test vẫn dùng database shopee. */
    private Datasource datasource = new Datasource();

    public long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(long partnerId) {
        this.partnerId = partnerId;
    }

    public String getPartnerKey() {
        return partnerKey;
    }

    public void setPartnerKey(String partnerKey) {
        this.partnerKey = partnerKey;
    }

    public long getShopId() {
        return shopId;
    }

    public void setShopId(long shopId) {
        this.shopId = shopId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getPushCallbackUrl() {
        return pushCallbackUrl;
    }

    public void setPushCallbackUrl(String pushCallbackUrl) {
        this.pushCallbackUrl = pushCallbackUrl;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    /** JDBC của database simulator. Không dùng datasource của hub. */
    public static class Datasource {
        private String url = "jdbc:postgresql://localhost:5432/shopee";
        private String username = "omniai";
        private String password = "omniai";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
