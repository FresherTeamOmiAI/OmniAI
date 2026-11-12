package com.omniai.sim.tiktok.auth;

import com.omniai.sim.tiktok.SimProperties;
import com.omniai.sim.tiktok.common.ApiException;
import com.omniai.sim.tiktok.common.ApiResponse;
import com.omniai.sim.tiktok.common.TiktokErrorCode;
import com.omniai.sim.tiktok.gateway.GatewayFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Authorization: trang ủy quyền của seller, Get/Refresh Access Token (chưa đối chiếu), Get Authorized Shops (docv2 202309).
 * Thật: trang ủy quyền ở services.tiktokshop.com, token ở auth.tiktok-shops.com; simulator gộp vào 1 host.
 */
@RestController
public class AuthController {

    private final SimProperties props;
    private final TokenStore tokens;

    public AuthController(SimProperties props, TokenStore tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    public record TokenData(String accessToken, long accessTokenExpireIn, String refreshToken,
                            long refreshTokenExpireIn, String openId, String sellerName, String sellerBaseRegion,
                            int userType, List<String> grantedScopes) {

        static TokenData of(TokenStore.Grant g, SimProperties.Shop shop) {
            return new TokenData(g.accessToken(), g.accessExpiresAt().getEpochSecond(), g.refreshToken(),
                    g.refreshExpiresAt().getEpochSecond(), "open_" + shop.id(), shop.name(), shop.region(), 0,
                    g.scopes());
        }
    }

    public record AuthorizedShop(String id, String name, String region, String sellerType, String cipher, String code) {
    }

    public record AuthorizedShops(List<AuthorizedShop> shops) {
    }

    /** Trang seller bấm "Đồng ý". auto-approve=true thì chuyển hướng ngay (dùng khi chạy tự động). */
    @GetMapping(value = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorize(@RequestParam("app_key") String appKey,
                                            @RequestParam(value = "state", defaultValue = "") String state) {
        requireAppKey(appKey);
        if (props.autoApprove()) {
            return approve(appKey, state);
        }
        String approveUrl = UriComponentsBuilder.fromPath("/oauth/authorize/approve")
                .queryParam("app_key", appKey).queryParam("state", state).encode().build().toUriString();
        String html = """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Ủy quyền TikTok Shop (giả lập)</title></head>
                <body style="font-family:sans-serif;max-width:480px;margin:48px auto">
                <h2>Ủy quyền cho ứng dụng OmniAI</h2>
                <p>Shop <b>%s</b> (%s) cấp cho ứng dụng <code>%s</code> các quyền:</p><ul>%s</ul>
                <p><a href="%s" style="padding:8px 16px;background:#fe2c55;color:#fff;text-decoration:none;border-radius:4px">Đồng ý</a></p>
                <p style="color:#888">Đây là simulator TikTok Shop của dự án OmniAI, không phải trang thật.</p>
                </body></html>""".formatted(
                HtmlUtils.htmlEscape(props.shop().name()), props.shop().region(), HtmlUtils.htmlEscape(appKey),
                String.join("", props.grantedScopes().stream().map(s -> "<li><code>" + s + "</code></li>").toList()),
                HtmlUtils.htmlEscape(approveUrl));
        return ResponseEntity.ok(html);
    }

    @GetMapping("/oauth/authorize/approve")
    public ResponseEntity<String> approve(@RequestParam("app_key") String appKey,
                                          @RequestParam(value = "state", defaultValue = "") String state) {
        requireAppKey(appKey);
        URI target = UriComponentsBuilder.fromUriString(props.redirectUri())
                .queryParam("code", tokens.issueAuthCode(props.shop().id()))
                .queryParam("state", state)
                .encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()).build();
    }

    @GetMapping("/api/v2/token/get")
    public ApiResponse<TokenData> getToken(@RequestParam("app_key") String appKey,
                                           @RequestParam("app_secret") String appSecret,
                                           @RequestParam("auth_code") String authCode,
                                           @RequestParam("grant_type") String grantType) {
        requireApp(appKey, appSecret);
        requireGrantType(grantType, "authorized_code");
        return ApiResponse.ok(TokenData.of(tokens.exchange(authCode), props.shop()));
    }

    @GetMapping("/api/v2/token/refresh")
    public ApiResponse<TokenData> refreshToken(@RequestParam("app_key") String appKey,
                                               @RequestParam("app_secret") String appSecret,
                                               @RequestParam("refresh_token") String refreshToken,
                                               @RequestParam("grant_type") String grantType) {
        requireApp(appKey, appSecret);
        requireGrantType(grantType, "refresh_token");
        return ApiResponse.ok(TokenData.of(tokens.refresh(refreshToken), props.shop()));
    }

    /** Get Authorized Shops (docv2): lấy cipher để gọi các API dữ liệu shop. Qua gateway nên đã kiểm sign + token. */
    @GetMapping("/authorization/202309/shops")
    public ApiResponse<AuthorizedShops> shops(HttpServletRequest request) {
        GatewayFilter.grant(request);
        SimProperties.Shop s = props.shop();
        return ApiResponse.ok(new AuthorizedShops(List.of(
                new AuthorizedShop(s.id(), s.name(), s.region(), s.sellerType(), s.cipher(), s.code()))));
    }

    private void requireAppKey(String appKey) {
        if (!props.appKey().equals(appKey)) {
            throw new ApiException(TiktokErrorCode.INVALID_SIGN, "Unknown app_key.");
        }
    }

    private void requireApp(String appKey, String appSecret) {
        requireAppKey(appKey);
        if (!props.appSecret().equals(appSecret)) {
            throw new ApiException(TiktokErrorCode.INVALID_SIGN, "Wrong app_secret.");
        }
    }

    private static void requireGrantType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ApiException(TiktokErrorCode.INVALID_PARAMS, "grant_type must be " + expected + ".");
        }
    }
}
