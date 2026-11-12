package com.omniai.conversation;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Một tin trong hội thoại (bảng conversation_message, V11). IN = tin khách gửi, OUT = câu bot gửi ra kênh.
 * Câu OUT giữ cả rawText (AI nói gì) lẫn text = finalText (khách nhận gì) và các luật đã kích hoạt. Owner: TuTM.
 */
@Entity
@Table(name = "conversation_message")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {

    public enum Direction { IN, OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Direction direction;

    /** IN: messageId của tin khách; OUT: messageId của tin mà câu bot trả lời. */
    @Column(name = "message_id", nullable = false)
    private String messageId;

    /** IN: tin khách; OUT: finalText sau policy. */
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    /** Chỉ OUT: câu gốc của AI Brain trước policy. */
    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(nullable = false)
    private boolean blocked;

    @Column(nullable = false)
    private boolean handoff;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "message_violation", joinColumns = @JoinColumn(name = "message_id"))
    @OrderColumn(name = "position")
    private List<StoredViolation> violations = new ArrayList<>();
}
