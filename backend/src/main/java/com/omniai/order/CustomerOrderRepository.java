package com.omniai.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /**
     * Id đơn của khách ở mọi kênh, mới nhất trước (T-23.1, decisions #52). Đơn WEB khớp {@code customer_id}; đơn sàn
     * chỉ khớp khi danh tính sàn ({@code channel_code}, {@code buyer_external_id}) đang gắn với khách. Đơn sàn có
     * {@code customer_id} mà danh tính chưa (hoặc đã gỡ) liên kết thì không tính.
     */
    @Query(nativeQuery = true, value = """
            SELECT o.id FROM customer_order o
            WHERE (o.channel_code = 'WEB' AND o.customer_id = :customerId)
               OR (o.channel_code <> 'WEB' AND EXISTS (
                      SELECT 1 FROM channel_identity ci
                      WHERE ci.customer_id = :customerId
                        AND ci.channel = o.channel_code
                        AND ci.external_id = o.buyer_external_id))
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT :limit""")
    List<Long> findHistoryIds(@Param("customerId") long customerId, @Param("limit") int limit);

    /** Đơn kèm các dòng sản phẩm trong 1 câu (tránh mỗi đơn 1 câu đọc dòng). Không giữ thứ tự của {@code ids}. */
    @Query("SELECT DISTINCT o FROM CustomerOrder o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<CustomerOrder> findWithItems(@Param("ids") Collection<Long> ids);
}
