package com.omniai.channel.web;

/** Tin từ chat widget: {"messageId": "...", "text": "..."}. */
public record WebChatInbound(String messageId, String text) {
}
