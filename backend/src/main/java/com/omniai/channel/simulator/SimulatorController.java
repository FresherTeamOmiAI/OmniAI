package com.omniai.channel.simulator;

import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import com.omniai.common.ChannelType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/simulator/{channel}/messages  (channel: web | shopee | tiktok)
 * Trả về cả rawText (AI nói gì) và finalText (khách nhận gì) để bench đo policy.
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final MessageRouter router;

    public SimulatorController(MessageRouter router) {
        this.router = router;
    }

    @PostMapping("/{channel}/messages")
    public BotReply send(@PathVariable String channel, @Valid @RequestBody SimulatorRequest request) {
        ChannelType type = ChannelType.valueOf(channel.toUpperCase());
        return router.handle(new SimulatorAdapter(type).toCanonical(request));
    }
}
