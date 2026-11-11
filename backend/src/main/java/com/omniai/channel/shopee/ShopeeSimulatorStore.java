package com.omniai.channel.shopee;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Bộ nhớ của simulator Shopee: giá, tồn kho và đơn vừa phát. Owner: ChienPX. */
@Component
public class ShopeeSimulatorStore {

    public record Item(long itemId, Long originalPrice, Integer stock) {
    }

    public record OrderLine(long itemId, String itemSku, String itemName, int quantity, long price) {
    }

    public record Order(String orderSn, String status, long updateTime, OrderLine line) {
    }

    public record Push(String orderSn, String callbackUrl, String rawBody, String authorization) {
    }

    private final ConcurrentHashMap<Long, long[]> items = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Push> pushes = new ConcurrentHashMap<>();

    public void savePrice(long itemId, long originalPrice) {
        items.compute(itemId, (id, current) -> {
            long stock = current == null ? Long.MIN_VALUE : current[1];
            return new long[] {originalPrice, stock};
        });
    }

    public void saveStock(long itemId, int stock) {
        items.compute(itemId, (id, current) -> {
            long price = current == null ? Long.MIN_VALUE : current[0];
            return new long[] {price, stock};
        });
    }

    public Optional<Item> item(long itemId) {
        long[] values = items.get(itemId);
        if (values == null) {
            return Optional.empty();
        }
        Long price = values[0] == Long.MIN_VALUE ? null : values[0];
        Integer stock = values[1] == Long.MIN_VALUE ? null : (int) values[1];
        return Optional.of(new Item(itemId, price, stock));
    }

    public void saveOrder(Order order, Push push) {
        orders.put(order.orderSn(), order);
        pushes.put(order.orderSn(), push);
    }

    public Optional<Order> order(String orderSn) {
        return Optional.ofNullable(orders.get(orderSn));
    }

    public Optional<Push> push(String orderSn) {
        return Optional.ofNullable(pushes.get(orderSn));
    }
}
