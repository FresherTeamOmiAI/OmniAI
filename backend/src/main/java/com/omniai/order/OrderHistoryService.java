package com.omniai.order;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.common.ChannelType;
import com.omniai.customer.ChannelIdentity;
import com.omniai.customer.ChannelIdentityRepository;
import com.omniai.customer.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lịch sử đơn mọi kênh của 1 khách (T-23.1, US-23, decisions #52). ai-brain (tool T-23.2) đọc qua
 * {@link InternalOrderController}, người mua đọc qua {@link MeOrderController}. Chỉ trả đơn của kênh đã liên kết.
 * Owner: LucVT.
 */
@Service
@Transactional(readOnly = true)
public class OrderHistoryService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    private final CustomerOrderRepository orders;
    private final CustomerRepository customers;
    private final ChannelIdentityRepository identities;

    public OrderHistoryService(CustomerOrderRepository orders, CustomerRepository customers,
                               ChannelIdentityRepository identities) {
        this.orders = orders;
        this.customers = customers;
        this.identities = identities;
    }

    public OrderHistoryView history(long customerId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new Invalid(List.of(new FieldViolation("limit", "phải từ 1 đến " + MAX_LIMIT)));
        }
        if (!customers.existsById(customerId)) {
            throw new CustomerNotFound(customerId);
        }
        // Lấy dư 1 dòng để biết còn đơn cũ hơn không.
        List<Long> ids = orders.findHistoryIds(customerId, limit + 1);
        boolean hasMore = ids.size() > limit;
        List<Long> page = hasMore ? ids.subList(0, limit) : ids;

        Map<Long, CustomerOrder> byId = page.isEmpty() ? Map.of()
                : orders.findWithItems(page).stream().collect(Collectors.toMap(CustomerOrder::getId, Function.identity()));
        List<OrderView> views = page.stream().map(byId::get).map(OrderView::of).toList();

        List<String> linked = identities.findByCustomerId(customerId).stream()
                .map(ChannelIdentity::getChannel)
                .distinct()
                .sorted(Comparator.comparing(ChannelType::name))
                .map(ChannelType::name)
                .toList();
        return new OrderHistoryView(views, linked, hasMore);
    }

    public static class CustomerNotFound extends RuntimeException {
        public CustomerNotFound(long id) {
            super("Không có khách id=" + id);
        }
    }
}
