package com.omniai.identity;

import com.omniai.common.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Khách gõ mã OMNI trong chat sàn (T-22.3 gọi sau khi {@link LinkCodes#PATTERN} khớp câu chat).
 * Nằm dưới /api/internal nên cần header {@code X-Internal-Token} (decisions #48).
 */
@RestController
public class InternalLinkController {

    public record ClaimRequest(
            @NotNull(message = "Thiếu kênh") ChannelType channel,
            @NotBlank(message = "Thiếu id người mua trên kênh") @Size(max = 255) String channelUserId,
            @NotBlank(message = "Thiếu mã") @Size(max = 40) String code) {
    }

    private final LinkCodeService links;

    public InternalLinkController(LinkCodeService links) {
        this.links = links;
    }

    @PostMapping("/api/internal/link-codes/claim")
    public LinkCodeService.Claimed claim(@Valid @RequestBody ClaimRequest request) {
        return links.claim(request.channel(), request.channelUserId().trim(), request.code());
    }
}
