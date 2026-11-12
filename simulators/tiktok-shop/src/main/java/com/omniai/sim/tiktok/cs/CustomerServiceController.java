package com.omniai.sim.tiktok.cs;

import com.omniai.sim.tiktok.common.ApiResponse;
import com.omniai.sim.tiktok.cs.CsModels.ConversationsPage;
import com.omniai.sim.tiktok.cs.CsModels.MessagesPage;
import com.omniai.sim.tiktok.gateway.GatewayFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Customer Service (docv2): path và version đúng theo từng trang, Send Message là bản 202606.
 * Giới hạn page_size: conversations 1–20, messages 1–10.
 */
@RestController
public class CustomerServiceController {

    static final int CONVERSATIONS_MAX_PAGE = 20;
    static final int MESSAGES_MAX_PAGE = 10;
    private static final String UNSUPPORTED_TIPS = "Please check this message in Seller Center.\n";

    private final CustomerService cs;

    public CustomerServiceController(CustomerService cs) {
        this.cs = cs;
    }

    @GetMapping("/customer_service/202309/conversations")
    public ApiResponse<ConversationsPage> conversations(@RequestParam(value = "page_size", required = false) Integer pageSize,
                                                        @RequestParam(value = "page_token", required = false) String pageToken,
                                                        HttpServletRequest request) {
        PageToken.requirePageSize(pageSize, CONVERSATIONS_MAX_PAGE);
        var page = PageToken.slice(cs.conversations(shop(request)), pageToken, pageSize);
        return ApiResponse.ok(new ConversationsPage(page.nextPageToken(), page.items()));
    }

    @PostMapping("/customer_service/202309/conversations")
    public ApiResponse<CsModels.CreateConversationResponse> create(@RequestBody(required = false) CsModels.CreateConversationRequest body,
                                                                   HttpServletRequest request) {
        String id = cs.createConversation(shop(request), body == null ? null : body.buyerUserId());
        return ApiResponse.ok(new CsModels.CreateConversationResponse(id));
    }

    @GetMapping("/customer_service/202309/conversations/{conversationId}/messages")
    public ApiResponse<MessagesPage> messages(@PathVariable String conversationId,
                                              @RequestParam(value = "page_size", required = false) Integer pageSize,
                                              @RequestParam(value = "page_token", required = false) String pageToken,
                                              @RequestParam(value = "sort_order", required = false) String sortOrder,
                                              @RequestParam(value = "sort_field", required = false) String sortField,
                                              HttpServletRequest request) {
        PageToken.requirePageSize(pageSize, MESSAGES_MAX_PAGE);
        var all = cs.messages(shop(request), conversationId, sortField, sortOrder);
        var page = PageToken.slice(all, pageToken, pageSize);
        return ApiResponse.ok(new MessagesPage(page.nextPageToken(), UNSUPPORTED_TIPS, page.items()));
    }

    @PostMapping("/customer_service/202606/conversations/{conversationId}/messages")
    public ApiResponse<CsModels.SendMessageResponse> send(@PathVariable String conversationId,
                                                          @RequestBody(required = false) CsModels.SendMessageRequest body,
                                                          HttpServletRequest request) {
        var message = cs.send(shop(request), conversationId, body);
        return ApiResponse.ok(new CsModels.SendMessageResponse(message.id()));
    }

    @PostMapping("/customer_service/202309/conversations/{conversationId}/messages/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable String conversationId, HttpServletRequest request) {
        cs.markRead(shop(request), conversationId);
        return ApiResponse.okEmpty();
    }

    private static String shop(HttpServletRequest request) {
        return GatewayFilter.grant(request).shopId();
    }
}
