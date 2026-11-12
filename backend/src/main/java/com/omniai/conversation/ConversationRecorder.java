package com.omniai.conversation;

import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;

/**
 * Nơi MessageRouter ghi lại tin vào/ra. Tách interface để test router không cần Spring/DB.
 * Bản thật: {@link ConversationService}.
 */
public interface ConversationRecorder {

    /** Không lưu gì (test). */
    ConversationRecorder NOOP = new ConversationRecorder() {
        @Override
        public void inbound(CanonicalMessage message) {
        }

        @Override
        public void reply(CanonicalMessage message, BotReply reply) {
        }
    };

    /** Lưu tin khách gửi. Gọi trước khi hỏi AI Brain để Brain lỗi thì tin vẫn còn. */
    void inbound(CanonicalMessage message);

    /** Lưu câu bot gửi ra (rawText, finalText, luật vi phạm, blocked, handoff). */
    void reply(CanonicalMessage message, BotReply reply);
}
