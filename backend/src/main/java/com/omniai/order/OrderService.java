package com.omniai.order;

import com.omniai.catalog.Product;
import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.catalog.ProductRepository;
import com.omniai.catalog.StoreFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đặt hàng ảo trên web store (T-09.3, SRS UC-06, decisions #34). Owner: LucVT.
 * Không gọi cổng thanh toán, không giao hàng, KHÔNG trừ tồn kho (G-04). Hết hàng hoặc không đủ hàng thì không cho đặt.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    /** Số lượng tối đa của 1 sản phẩm trong 1 đơn (khớp CHECK của V14). */
    public static final int MAX_QUANTITY = 10;

    private final CustomerOrderRepository orders;
    private final ProductRepository products;

    public OrderService(CustomerOrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Transactional
    public CustomerOrder createDemo(OrderRequest request) {
        // Gộp các dòng cùng sản phẩm, giữ chỉ số dòng đầu để báo lỗi đúng chỗ.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, Integer> firstIndex = new LinkedHashMap<>();
        List<OrderRequest.Line> lines = request.items();
        for (int i = 0; i < lines.size(); i++) {
            OrderRequest.Line line = lines.get(i);
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
            firstIndex.putIfAbsent(line.productId(), i);
        }

        List<FieldViolation> errors = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            String field = "items[" + firstIndex.get(entry.getKey()) + "]";
            int quantity = entry.getValue();
            Optional<Product> found = products.findByIdAndActiveTrue(entry.getKey());
            if (found.isEmpty()) {
                errors.add(new FieldViolation(field + ".productId", "sản phẩm không tồn tại hoặc đã ngừng bán"));
                continue;
            }
            Product product = found.get();
            Long price = product.getPrices().get(StoreFilter.STORE_CHANNEL);
            if (price == null) {
                errors.add(new FieldViolation(field + ".productId", "sản phẩm chưa có giá web"));
            } else if (product.getStock() <= 0) {
                errors.add(new FieldViolation(field + ".quantity", "hết hàng"));
            } else if (quantity > MAX_QUANTITY) {
                errors.add(new FieldViolation(field + ".quantity", "tổng số lượng của 1 sản phẩm tối đa " + MAX_QUANTITY));
            } else if (quantity > product.getStock()) {
                errors.add(new FieldViolation(field + ".quantity", "chỉ còn " + product.getStock() + " sản phẩm"));
            } else {
                long lineTotal = price * quantity;
                items.add(new OrderItem(product.getId(), product.getSku(), product.getName(), price, quantity, lineTotal));
                total += lineTotal;
            }
        }
        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }

        CustomerOrder order = new CustomerOrder();
        order.setChannelCode(StoreFilter.STORE_CHANNEL);
        order.setStatus(CustomerOrder.STATUS_DEMO);
        order.setTotalAmount(total);
        order.setItems(items);
        // Cố ý KHÔNG đụng product.stock: đơn DEMO không trừ tồn kho (G-04).
        return orders.saveAndFlush(order);
    }

    public CustomerOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFound(id));
    }

    public static class OrderNotFound extends RuntimeException {
        public OrderNotFound(Long id) {
            super("Không có đơn id=" + id);
        }
    }
}
