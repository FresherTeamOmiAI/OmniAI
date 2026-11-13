package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kênh bán (V13, decisions #32). Owner: LucVT. Giá của sản phẩm trên từng kênh nằm ở bảng product_channel.
 * Thêm kênh = thêm 1 dòng bảng này, không đổi cấu trúc product. {@code code} khớp enum ChannelType.
 */
@Entity
@Table(name = "sales_channel")
@Getter
@Setter
@NoArgsConstructor
public class SalesChannel {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** Kênh shop sở hữu (SRS G-15): được so sánh giá giữa các kênh và gửi link. */
    private boolean owned;

    /** false = tạm ngừng bán trên kênh. Sản phẩm không bắt buộc có giá ở kênh không active. */
    private boolean active = true;
}
