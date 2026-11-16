package com.omniai.channel.shopee;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Giá, tồn kho và đơn của simulator Shopee, ghi vào database `shopee`. Owner: ChienPX. */
@Component
public class ShopeeSimulatorStore {

    public record Item(long itemId, Long originalPrice, Long stock) {
    }

    public record OrderLine(long itemId, String itemSku, String itemName, int quantity, long price, String modelName, String modelSku) {
    }

    public record Order(String orderSn, String status, long updateTime, boolean cod, String messageToSeller,
                        int daysToShip, long shipByDate, OrderLine line) {
    }

    public record Push(String orderSn, String callbackUrl, String rawBody, String authorization) {
    }

    /** Tin chat. messageId và conversationId là chuỗi số, cùng kiểu mẫu webchat_push. */
    public record StoredMessage(String messageId, String conversationId, long fromId, String fromUserName, long toId,
                                String toUserName, String messageType, String contentJson, long createdTimestamp,
                                String status) {
    }

    public record StoredConversation(String conversationId, long toId, String toName, int unreadCount,
                                     String latestMessageId, String latestMessageType, String latestMessageContent,
                                     long latestMessageFromId, long lastMessageTimestamp) {
    }

    private final ShopeeChannelProperties properties;
    private HikariDataSource pool;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    public ShopeeSimulatorStore(ShopeeChannelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void open() {
        ShopeeChannelProperties.Datasource database = properties.getDatasource();
        ensureDatabase(database);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(database.getUrl());
        dataSource.setUsername(database.getUsername());
        dataSource.setPassword(database.getPassword());
        dataSource.setPoolName("shopee");
        dataSource.setMaximumPoolSize(4);
        this.pool = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/shopee")
                .load()
                .migrate();
    }

    public void savePrice(long shopId, long itemId, long modelId, long originalPrice) {
        long now = Instant.now().getEpochSecond();
        transactions.executeWithoutResult(status -> {
            upsertItem(shopId, itemId, null, null, now);
            jdbc.update("""
                    INSERT INTO price_list_shopee (item_id, model_id, original_price)
                    VALUES (?, ?, ?)
                    ON CONFLICT (item_id, model_id) DO UPDATE SET original_price = EXCLUDED.original_price
                    """, itemId, modelId, originalPrice);
        });
    }

    public void saveStock(long shopId, long itemId, long modelId, String locationId, long stock) {
        long now = Instant.now().getEpochSecond();
        String location = locationId == null || locationId.isEmpty() ? "-" : locationId;
        transactions.executeWithoutResult(status -> {
            upsertItem(shopId, itemId, null, null, now);
            jdbc.update("""
                    INSERT INTO seller_stock_shopee (item_id, model_id, location_id, stock)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (item_id, model_id, location_id) DO UPDATE SET stock = EXCLUDED.stock
                    """, itemId, modelId, location, stock);
        });
    }

    public Optional<Item> item(long itemId) {
        List<Item> rows = jdbc.query("""
                SELECT i.item_id, p.original_price, s.stock
                FROM item_shopee i
                LEFT JOIN price_list_shopee p ON p.item_id = i.item_id AND p.model_id = 0
                LEFT JOIN seller_stock_shopee s
                    ON s.item_id = i.item_id AND s.model_id = 0 AND s.location_id = '-'
                WHERE i.item_id = ?
                """, (rs, row) -> new Item(rs.getLong("item_id"),
                rs.getObject("original_price") == null ? null : rs.getLong("original_price"),
                rs.getObject("stock") == null ? null : rs.getLong("stock")), itemId);
        return rows.stream().findFirst();
    }

    public void saveOrder(long shopId, Order order, Push push) {
        OrderLine line = order.line();
        transactions.executeWithoutResult(status -> {
            upsertItem(shopId, line.itemId(), line.itemSku(), line.itemName(), order.updateTime());
            jdbc.update("""
                    INSERT INTO order_shopee
                        (order_sn, shop_id, order_status, region, currency, total_amount, create_time, update_time,
                         cod, message_to_seller, days_to_ship, ship_by_date)
                    VALUES (?, ?, ?, 'VN', 'VND', ?, ?, ?, ?, ?, ?, ?)
                    """, order.orderSn(), shopId, order.status(), line.price() * line.quantity(),
                    order.updateTime(), order.updateTime(), order.cod(), order.messageToSeller(),
                    order.daysToShip(), order.shipByDate());
            jdbc.update("""
                    INSERT INTO order_item_shopee
                        (order_sn, item_id, item_name, item_sku, model_id, model_quantity_purchased,
                         model_original_price, model_discounted_price, model_name, model_sku)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                    """, order.orderSn(), line.itemId(), line.itemName(), line.itemSku(),
                    line.quantity(), line.price(), line.price(), line.modelName(), line.modelSku());
            jdbc.update("""
                    INSERT INTO push_shopee
                        (order_sn, shop_id, code, ordersn, status, completed_scenario, update_time,
                         callback_url, raw_body, "authorization", "timestamp")
                    VALUES (?, ?, 3, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, order.orderSn(), shopId, order.orderSn(), order.status(),
                    "COMPLETED".equals(order.status()) ? "NORMAL" : "", order.updateTime(),
                    push.callbackUrl(), push.rawBody(), push.authorization(), order.updateTime());
        });
    }

    public Optional<Order> order(String orderSn) {
        List<Order> orders = jdbc.query("""
                SELECT order_sn, order_status, update_time, cod, message_to_seller, days_to_ship, ship_by_date
                FROM order_shopee WHERE order_sn = ?
                """, (rs, row) -> new Order(rs.getString("order_sn"), rs.getString("order_status"),
                rs.getLong("update_time"), rs.getBoolean("cod"), rs.getString("message_to_seller"),
                rs.getInt("days_to_ship"), rs.getLong("ship_by_date"), null), orderSn);
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        OrderLine line = jdbc.queryForObject("""
                SELECT item_id, item_sku, item_name, model_quantity_purchased, model_original_price, model_name, model_sku
                FROM order_item_shopee WHERE order_sn = ? ORDER BY id LIMIT 1
                """, (rs, row) -> new OrderLine(rs.getLong("item_id"), rs.getString("item_sku"),
                rs.getString("item_name"), rs.getInt("model_quantity_purchased"),
                rs.getLong("model_original_price"), rs.getString("model_name"), rs.getString("model_sku")), orderSn);
        Order stored = orders.getFirst();
        return Optional.of(new Order(stored.orderSn(), stored.status(), stored.updateTime(), stored.cod(),
                stored.messageToSeller(), stored.daysToShip(), stored.shipByDate(), line));
    }

    /**
     * @param fromBuyer true khi khách gửi vào: tăng unread. Shop gửi ra thì unread về 0.
     *                  buyerName rỗng không ghi đè tên đã có.
     */
    public StoredMessage appendMessage(long shopId, long buyerId, String buyerName, boolean fromBuyer,
                                       long fromId, String fromUserName, long toId, String toUserName,
                                       String messageType, String contentJson, String status, long createdTimestamp) {
        String name = buyerName == null ? "" : buyerName;
        return transactions.execute(tx -> {
            Long conversationId = jdbc.queryForObject("""
                    INSERT INTO conversation_shopee (shop_id, to_id, to_name, unread_count, last_message_timestamp)
                    VALUES (?, ?, ?, 0, ?)
                    ON CONFLICT (shop_id, to_id) DO UPDATE SET
                        to_name = CASE
                            WHEN EXCLUDED.to_name <> '' THEN EXCLUDED.to_name
                            ELSE conversation_shopee.to_name
                        END
                    RETURNING conversation_id
                    """, Long.class, shopId, buyerId, name, createdTimestamp);
            Long messageId = jdbc.queryForObject("""
                    INSERT INTO message_shopee
                        (conversation_id, shop_id, from_id, from_user_name, to_id, to_user_name,
                         message_type, content, created_timestamp, status, business_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    RETURNING message_id
                    """, Long.class, conversationId, shopId, fromId, fromUserName, toId, toUserName,
                    messageType, contentJson, createdTimestamp, status);
            jdbc.update("""
                    UPDATE conversation_shopee SET
                        unread_count = CASE WHEN ? THEN unread_count + 1 ELSE 0 END,
                        latest_message_id = ?,
                        latest_message_type = ?,
                        latest_message_content = ?,
                        latest_message_from_id = ?,
                        last_message_timestamp = ?
                    WHERE conversation_id = ?
                    """, fromBuyer, Long.toString(messageId), messageType, contentJson, fromId,
                    createdTimestamp, conversationId);
            return new StoredMessage(Long.toString(messageId), Long.toString(conversationId), fromId, fromUserName,
                    toId, toUserName, messageType, contentJson, createdTimestamp, status);
        });
    }

    public void saveWebchatPush(String messageId, long shopId, String callbackUrl, String rawBody, String authorization,
                                long timestamp) {
        jdbc.update("""
                INSERT INTO webchat_push_shopee
                    (message_id, shop_id, code, callback_url, raw_body, "authorization", "timestamp")
                VALUES (?, ?, 10, ?, ?, ?, ?)
                """, Long.parseLong(messageId), shopId, callbackUrl, rawBody, authorization, timestamp);
    }

    public String buyerName(long shopId, long buyerId) {
        List<String> names = jdbc.query(
                "SELECT to_name FROM conversation_shopee WHERE shop_id = ? AND to_id = ?",
                (rs, row) -> rs.getString("to_name"), shopId, buyerId);
        return names.isEmpty() ? "" : names.getFirst();
    }

    public boolean conversationExists(String conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_shopee WHERE conversation_id = ?",
                Integer.class, conversationKey(conversationId));
        return count != null && count > 0;
    }

    public List<StoredMessage> messages(String conversationId, int limit, int offset) {
        return jdbc.query("""
                SELECT message_id, conversation_id, from_id, from_user_name, to_id, to_user_name,
                       message_type, content, created_timestamp, status
                FROM message_shopee
                WHERE conversation_id = ?
                ORDER BY created_timestamp, message_id
                LIMIT ? OFFSET ?
                """, (rs, row) -> messageRow(rs), conversationKey(conversationId), limit, offset);
    }

    public int messageCount(String conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_shopee WHERE conversation_id = ?",
                Integer.class, conversationKey(conversationId));
        return count == null ? 0 : count;
    }

    public List<StoredConversation> conversations(long shopId, int limit) {
        return jdbc.query("""
                SELECT conversation_id, to_id, to_name, unread_count, latest_message_id, latest_message_type,
                       latest_message_content, latest_message_from_id, last_message_timestamp
                FROM conversation_shopee
                WHERE shop_id = ?
                ORDER BY last_message_timestamp DESC
                LIMIT ?
                """, (rs, row) -> new StoredConversation(Long.toString(rs.getLong("conversation_id")),
                rs.getLong("to_id"), rs.getString("to_name"), rs.getInt("unread_count"),
                rs.getString("latest_message_id"), rs.getString("latest_message_type"),
                rs.getString("latest_message_content"), rs.getLong("latest_message_from_id"),
                rs.getLong("last_message_timestamp")), shopId, limit);
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    private static StoredMessage messageRow(ResultSet rs) throws SQLException {
        return new StoredMessage(Long.toString(rs.getLong("message_id")),
                Long.toString(rs.getLong("conversation_id")), rs.getLong("from_id"), rs.getString("from_user_name"),
                rs.getLong("to_id"), rs.getString("to_user_name"), rs.getString("message_type"),
                rs.getString("content"), rs.getLong("created_timestamp"), rs.getString("status"));
    }

    private static long conversationKey(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Wrong parameters, detail: conversation_id.");
        }
    }

    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private void upsertItem(long shopId, long itemId, String itemSku, String itemName, long now) {
        jdbc.update("""
                INSERT INTO item_shopee (item_id, shop_id, item_sku, item_name, item_status, create_time, update_time)
                VALUES (?, ?, ?, ?, 'NORMAL', ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET
                    shop_id = EXCLUDED.shop_id,
                    item_sku = COALESCE(EXCLUDED.item_sku, item_shopee.item_sku),
                    item_name = COALESCE(EXCLUDED.item_name, item_shopee.item_name),
                    update_time = EXCLUDED.update_time
                """, itemId, shopId, itemSku, itemName, now, now);
    }

    private static void ensureDatabase(ShopeeChannelProperties.Datasource database) {
        String url = database.getUrl();
        if (!"shopee".equals(databaseName(url))) {
            throw new IllegalStateException("Database simulator Shopee phải tên là shopee");
        }
        String adminUrl = adminUrl(url);
        try (Connection connection = DriverManager.getConnection(adminUrl, database.getUsername(), database.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            try (ResultSet found = statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'shopee'")) {
                if (found.next()) {
                    return;
                }
            }
            statement.execute("CREATE DATABASE shopee");
        } catch (SQLException error) {
            throw new IllegalStateException("Không tạo được database shopee tại " + adminUrl, error);
        }
    }

    static String databaseName(String url) {
        int scheme = url.indexOf("://");
        int slash = scheme < 0 ? -1 : url.indexOf('/', scheme + 3);
        if (slash < 0 || slash == url.length() - 1) {
            throw new IllegalArgumentException("SHOPEE_DB_URL thiếu tên database");
        }
        int query = url.indexOf('?', slash);
        return query < 0 ? url.substring(slash + 1) : url.substring(slash + 1, query);
    }

    static String adminUrl(String url) {
        int scheme = url.indexOf("://");
        int slash = url.indexOf('/', scheme + 3);
        int query = url.indexOf('?', slash);
        String suffix = query < 0 ? "" : url.substring(query);
        return url.substring(0, slash + 1) + "postgres" + suffix;
    }
}
