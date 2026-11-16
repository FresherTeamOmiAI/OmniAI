-- Database riêng của simulator Shopee (T-50.3, T-51.1). Owner: ChienPX.
-- Không nằm trong database hub `omniai`. Cột đặt theo field của Open Platform v2.

CREATE TABLE item_shopee (
    item_id     BIGINT PRIMARY KEY,
    shop_id     BIGINT NOT NULL,
    item_sku    VARCHAR(100),
    item_name   VARCHAR(255),
    item_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

-- Body update_price: price_list[].model_id + original_price, gắn với item_id.
CREATE TABLE price_list_shopee (
    item_id        BIGINT NOT NULL REFERENCES item_shopee (item_id),
    model_id       BIGINT NOT NULL,
    original_price BIGINT NOT NULL,
    PRIMARY KEY (item_id, model_id),
    CONSTRAINT ck_price_list_shopee_price CHECK (original_price >= 0)
);

-- Body update_stock: stock_list[].seller_stock[]. location_id rỗng là kho mặc định.
CREATE TABLE seller_stock_shopee (
    item_id     BIGINT NOT NULL REFERENCES item_shopee (item_id),
    model_id    BIGINT NOT NULL,
    location_id VARCHAR(64) NOT NULL DEFAULT '',
    stock       INTEGER NOT NULL,
    PRIMARY KEY (item_id, model_id, location_id),
    CONSTRAINT ck_seller_stock_shopee_stock CHECK (stock >= 0)
);

-- Response get_order_detail: order_list[] trừ item_list.
CREATE TABLE order_shopee (
    order_sn     VARCHAR(32) PRIMARY KEY,
    shop_id      BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    region       VARCHAR(8) NOT NULL DEFAULT 'VN',
    currency     VARCHAR(3) NOT NULL DEFAULT 'VND',
    total_amount BIGINT NOT NULL,
    create_time  BIGINT NOT NULL,
    update_time  BIGINT NOT NULL,
    CONSTRAINT ck_order_shopee_region CHECK (region = 'VN'),
    CONSTRAINT ck_order_shopee_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_order_shopee_total CHECK (total_amount >= 0)
);

-- item_list[] của get_order_detail.
CREATE TABLE order_item_shopee (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_sn                  VARCHAR(32) NOT NULL REFERENCES order_shopee (order_sn),
    item_id                   BIGINT NOT NULL,
    item_name                 VARCHAR(255) NOT NULL,
    item_sku                  VARCHAR(100) NOT NULL,
    model_id                  BIGINT NOT NULL,
    model_quantity_purchased  INTEGER NOT NULL,
    model_original_price      BIGINT NOT NULL,
    model_discounted_price    BIGINT NOT NULL,
    CONSTRAINT ck_order_item_shopee_qty CHECK (model_quantity_purchased > 0),
    CONSTRAINT ck_order_item_shopee_price CHECK (model_original_price >= 0 AND model_discounted_price >= 0)
);

CREATE INDEX idx_order_item_shopee_order_sn ON order_item_shopee (order_sn);

-- Push code 3. Field trong push là ordersn; chi tiết đơn dùng order_sn. Hai cột giữ đúng hai tên đó.
CREATE TABLE push_shopee (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_sn      VARCHAR(32) NOT NULL REFERENCES order_shopee (order_sn),
    shop_id       BIGINT NOT NULL,
    code          INTEGER NOT NULL,
    ordersn       VARCHAR(32) NOT NULL,
    status        VARCHAR(32) NOT NULL,
    update_time   BIGINT NOT NULL,
    callback_url  TEXT NOT NULL,
    raw_body      TEXT NOT NULL,
    "authorization" VARCHAR(128) NOT NULL,
    "timestamp"   BIGINT NOT NULL
);

CREATE INDEX idx_push_shopee_order_sn ON push_shopee (order_sn);
