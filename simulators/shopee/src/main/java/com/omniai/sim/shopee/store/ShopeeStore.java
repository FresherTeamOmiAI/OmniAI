package com.omniai.sim.shopee.store;

import com.omniai.sim.shopee.SimProperties;
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

/**
 * Giá, tồn kho, đơn và chat của simulator Shopee, ghi vào các bảng tbl_shopee_* trong database riêng (không phải DB
 * hub). Lịch sử Flyway giữ bảng tbl_shopee_flyway_history như khi simulator còn nằm trong backend. Owner: ChienPX.
 */
@Component
public class ShopeeStore {

    private static final String ALL_TABLES = """
            tbl_shopee_webchat_push, tbl_shopee_message, tbl_shopee_conversation,
            tbl_shopee_push, tbl_shopee_order_item, tbl_shopee_order,
            tbl_shopee_seller_stock, tbl_shopee_price_list, tbl_shopee_item""";

    public record Item(long itemId, Long originalPrice, Long stock) {
    }

    /** Một item cho trang /ui: giá và tồn của model 0 (sản phẩm không biến thể, kho mặc định). */
    public record ItemView(long itemId, String itemSku, String itemName, String itemStatus, Long originalPrice,
                           Long stock, int models, long createTime, long updateTime) {
    }

    /** Một đơn cho trang /ui, kèm dòng hàng đầu tiên. */
    public record OrderView(String orderSn, String orderStatus, long totalAmount, boolean cod, String messageToSeller,
                            long createTime, long updateTime, OrderLine line) {
    }

    public record Stats(int items, int orders, int conversations, int messages) {
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

    private final SimProperties properties;
    private HikariDataSource pool;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    public ShopeeStore(SimProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void open() {
        SimProperties.Datasource database = properties.datasource();
        ensureDatabase(database);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(database.url());
        dataSource.setUsername(database.username());
        dataSource.setPassword(database.password());
        dataSource.setPoolName("shopee");
        dataSource.setMaximumPoolSize(5);
        Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations("classpath:db/shopee")
                .table("tbl_shopee_flyway_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        this.pool = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void savePrice(long shopId, long itemId, long modelId, long originalPrice) {
        long now = Instant.now().getEpochSecond();
        transactions.executeWithoutResult(status -> {
            upsertItem(shopId, itemId, null, null, now);
            jdbc.update("""
                    INSERT INTO tbl_shopee_price_list (item_id, model_id, original_price)
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
                    INSERT INTO tbl_shopee_seller_stock (item_id, model_id, location_id, stock)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (item_id, model_id, location_id) DO UPDATE SET stock = EXCLUDED.stock
                    """, itemId, modelId, location, stock);
        });
    }

    public Optional<Item> item(long itemId) {
        List<Item> rows = jdbc.query("""
                SELECT i.item_id, p.original_price, s.stock
                FROM tbl_shopee_item i
                LEFT JOIN tbl_shopee_price_list p ON p.item_id = i.item_id AND p.model_id = 0
                LEFT JOIN tbl_shopee_seller_stock s
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
                    INSERT INTO tbl_shopee_order
                        (order_sn, shop_id, order_status, region, currency, total_amount, create_time, update_time,
                         cod, message_to_seller, days_to_ship, ship_by_date)
                    VALUES (?, ?, ?, 'VN', 'VND', ?, ?, ?, ?, ?, ?, ?)
                    """, order.orderSn(), shopId, order.status(), line.price() * line.quantity(),
                    order.updateTime(), order.updateTime(), order.cod(), order.messageToSeller(),
                    order.daysToShip(), order.shipByDate());
            jdbc.update("""
                    INSERT INTO tbl_shopee_order_item
                        (order_sn, item_id, item_name, item_sku, model_id, model_quantity_purchased,
                         model_original_price, model_discounted_price, model_name, model_sku)
                    VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                    """, order.orderSn(), line.itemId(), line.itemName(), line.itemSku(),
                    line.quantity(), line.price(), line.price(), line.modelName(), line.modelSku());
            jdbc.update("""
                    INSERT INTO tbl_shopee_push
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
                FROM tbl_shopee_order WHERE order_sn = ?
                """, (rs, row) -> new Order(rs.getString("order_sn"), rs.getString("order_status"),
                rs.getLong("update_time"), rs.getBoolean("cod"), rs.getString("message_to_seller"),
                rs.getInt("days_to_ship"), rs.getLong("ship_by_date"), null), orderSn);
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        OrderLine line = jdbc.queryForObject("""
                SELECT item_id, item_sku, item_name, model_quantity_purchased, model_original_price, model_name, model_sku
                FROM tbl_shopee_order_item WHERE order_sn = ? ORDER BY id LIMIT 1
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
                    INSERT INTO tbl_shopee_conversation (shop_id, to_id, to_name, unread_count, last_message_timestamp)
                    VALUES (?, ?, ?, 0, ?)
                    ON CONFLICT (shop_id, to_id) DO UPDATE SET
                        to_name = CASE
                            WHEN EXCLUDED.to_name <> '' THEN EXCLUDED.to_name
                            ELSE tbl_shopee_conversation.to_name
                        END
                    RETURNING conversation_id
                    """, Long.class, shopId, buyerId, name, createdTimestamp);
            Long messageId = jdbc.queryForObject("""
                    INSERT INTO tbl_shopee_message
                        (conversation_id, shop_id, from_id, from_user_name, to_id, to_user_name,
                         message_type, content, created_timestamp, status, business_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    RETURNING message_id
                    """, Long.class, conversationId, shopId, fromId, fromUserName, toId, toUserName,
                    messageType, contentJson, createdTimestamp, status);
            jdbc.update("""
                    UPDATE tbl_shopee_conversation SET
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
                INSERT INTO tbl_shopee_webchat_push
                    (message_id, shop_id, code, callback_url, raw_body, "authorization", "timestamp")
                VALUES (?, ?, 10, ?, ?, ?, ?)
                """, Long.parseLong(messageId), shopId, callbackUrl, rawBody, authorization, timestamp);
    }

    public String buyerName(long shopId, long buyerId) {
        List<String> names = jdbc.query(
                "SELECT to_name FROM tbl_shopee_conversation WHERE shop_id = ? AND to_id = ?",
                (rs, row) -> rs.getString("to_name"), shopId, buyerId);
        return names.isEmpty() ? "" : names.getFirst();
    }

    public boolean conversationExists(String conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tbl_shopee_conversation WHERE conversation_id = ?",
                Integer.class, conversationKey(conversationId));
        return count != null && count > 0;
    }

    public List<StoredMessage> messages(String conversationId, int limit, int offset) {
        return jdbc.query("""
                SELECT message_id, conversation_id, from_id, from_user_name, to_id, to_user_name,
                       message_type, content, created_timestamp, status
                FROM tbl_shopee_message
                WHERE conversation_id = ?
                ORDER BY created_timestamp, message_id
                LIMIT ? OFFSET ?
                """, (rs, row) -> messageRow(rs), conversationKey(conversationId), limit, offset);
    }

    public int messageCount(String conversationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tbl_shopee_message WHERE conversation_id = ?",
                Integer.class, conversationKey(conversationId));
        return count == null ? 0 : count;
    }

    public List<StoredConversation> conversations(long shopId, int limit) {
        return jdbc.query("""
                SELECT conversation_id, to_id, to_name, unread_count, latest_message_id, latest_message_type,
                       latest_message_content, latest_message_from_id, last_message_timestamp
                FROM tbl_shopee_conversation
                WHERE shop_id = ?
                ORDER BY last_message_timestamp DESC
                LIMIT ?
                """, (rs, row) -> new StoredConversation(Long.toString(rs.getLong("conversation_id")),
                rs.getLong("to_id"), rs.getString("to_name"), rs.getInt("unread_count"),
                rs.getString("latest_message_id"), rs.getString("latest_message_type"),
                rs.getString("latest_message_content"), rs.getLong("latest_message_from_id"),
                rs.getLong("last_message_timestamp")), shopId, limit);
    }

    /** Item hub đã đẩy giá/tồn hoặc có trong đơn, mới cập nhật trước. */
    public List<ItemView> items() {
        return jdbc.query("""
                SELECT i.item_id, i.item_sku, i.item_name, i.item_status, i.create_time, i.update_time,
                       p.original_price,
                       (SELECT SUM(s.stock) FROM tbl_shopee_seller_stock s
                         WHERE s.item_id = i.item_id AND s.model_id = 0) AS stock,
                       (SELECT COUNT(DISTINCT m.model_id) FROM (
                            SELECT model_id FROM tbl_shopee_price_list WHERE item_id = i.item_id
                            UNION SELECT model_id FROM tbl_shopee_seller_stock WHERE item_id = i.item_id) m) AS models
                FROM tbl_shopee_item i
                LEFT JOIN tbl_shopee_price_list p ON p.item_id = i.item_id AND p.model_id = 0
                ORDER BY i.update_time DESC, i.item_id DESC
                """, (rs, row) -> new ItemView(rs.getLong("item_id"), rs.getString("item_sku"),
                rs.getString("item_name"), rs.getString("item_status"),
                rs.getObject("original_price") == null ? null : rs.getLong("original_price"),
                rs.getObject("stock") == null ? null : rs.getLong("stock"),
                rs.getInt("models"), rs.getLong("create_time"), rs.getLong("update_time")));
    }

    /** Đơn mới nhất trước, tối đa {@code limit}. */
    public List<OrderView> orders(int limit) {
        return jdbc.query("""
                SELECT o.order_sn, o.order_status, o.total_amount, o.cod, o.message_to_seller, o.create_time,
                       o.update_time, l.item_id, l.item_sku, l.item_name, l.model_quantity_purchased,
                       l.model_original_price, l.model_name, l.model_sku
                FROM tbl_shopee_order o
                LEFT JOIN LATERAL (
                    SELECT * FROM tbl_shopee_order_item it WHERE it.order_sn = o.order_sn ORDER BY it.id LIMIT 1
                ) l ON true
                ORDER BY o.create_time DESC, o.order_sn DESC
                LIMIT ?
                """, (rs, row) -> new OrderView(rs.getString("order_sn"), rs.getString("order_status"),
                rs.getLong("total_amount"), rs.getBoolean("cod"), rs.getString("message_to_seller"),
                rs.getLong("create_time"), rs.getLong("update_time"),
                rs.getObject("item_id") == null ? null : new OrderLine(rs.getLong("item_id"), rs.getString("item_sku"),
                        rs.getString("item_name"), rs.getInt("model_quantity_purchased"),
                        rs.getLong("model_original_price"), rs.getString("model_name"), rs.getString("model_sku"))),
                limit);
    }

    public Stats stats() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM tbl_shopee_item) AS items,
                       (SELECT COUNT(*) FROM tbl_shopee_order) AS orders,
                       (SELECT COUNT(*) FROM tbl_shopee_conversation) AS conversations,
                       (SELECT COUNT(*) FROM tbl_shopee_message) AS messages
                """, (rs, row) -> new Stats(rs.getInt("items"), rs.getInt("orders"), rs.getInt("conversations"),
                rs.getInt("messages")));
    }

    /** Xóa toàn bộ dữ liệu simulator (POST /sim/reset). Giữ lịch sử Flyway. */
    public void reset() {
        jdbc.execute("TRUNCATE TABLE " + ALL_TABLES + " RESTART IDENTITY CASCADE");
    }

    /** Cho test đọc thẳng bảng. */
    public JdbcTemplate jdbc() {
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
                INSERT INTO tbl_shopee_item (item_id, shop_id, item_sku, item_name, item_status, create_time, update_time)
                VALUES (?, ?, ?, ?, 'NORMAL', ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET
                    shop_id = EXCLUDED.shop_id,
                    item_sku = COALESCE(EXCLUDED.item_sku, tbl_shopee_item.item_sku),
                    item_name = COALESCE(EXCLUDED.item_name, tbl_shopee_item.item_name),
                    update_time = EXCLUDED.update_time
                """, itemId, shopId, itemSku, itemName, now, now);
    }

    private static void ensureDatabase(SimProperties.Datasource database) {
        String url = database.url();
        String name = databaseName(url);
        if ("postgres".equals(name)) {
            return;
        }
        if (!"shopee".equals(name)) {
            throw new IllegalStateException("Database simulator Shopee phải tên là shopee hoặc postgres");
        }
        String adminUrl = adminUrl(url);
        try (Connection connection = DriverManager.getConnection(adminUrl, database.username(), database.password());
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
