package com.omniai.order;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ListIndexBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Đơn hàng mọi kênh (V14, decisions #34). S2 chỉ có đơn DEMO trên web. Owner: LucVT. */
@Entity
@Table(name = "customer_order")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder {

    /** Đơn web thử nghiệm: không thanh toán, không giao, không trừ tồn kho (SRS UC-06, G-04). */
    public static final String STATUS_DEMO = "DEMO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_code", nullable = false, length = 20)
    private String channelCode;

    /** Mã đơn phía sàn (US-51). Đơn web: null. */
    @Column(name = "external_order_id", length = 64)
    private String externalOrderId;

    /** Khách đã đăng nhập (từ S3). S2: null. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** line_no bắt đầu từ 1 theo thứ tự trong list. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_order_item", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    @ListIndexBase(1)
    private List<OrderItem> items = new ArrayList<>();
}
