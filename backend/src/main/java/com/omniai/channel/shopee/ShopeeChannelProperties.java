package com.omniai.channel.shopee;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Khóa test của simulator Shopee (simulators/shopee). Không phải khóa sàn thật. Owner: ChienPX. */
@Component
@ConfigurationProperties(prefix = "omniai.channels.shopee")
public class ShopeeChannelProperties {

    private long partnerId = 2000001L;
    private String partnerKey = "test-partner-key";
    private long shopId = 10001L;
    private String accessToken = "test-access-token";
    /** Host Open API Shopee hub gọi ra (giá, tồn, send_message). S2 là simulator ở cổng 8091 ({@code SHOPEE_SIM_URL}). */
    private String apiBaseUrl = "http://localhost:8091";
    /** Tắt thì không có connector giá/tồn SHOPEE trong khung đồng bộ. */
    private boolean syncEnabled = true;
    /** URL hub nhận push. Nằm trong chữ ký push nên phải trùng với simulator. */
    private String pushCallbackUrl = "http://127.0.0.1:8080/api/channels/shopee/push";

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
}
