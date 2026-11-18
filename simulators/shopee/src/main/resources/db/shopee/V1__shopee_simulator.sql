-- Simulator Shopee nằm trong cùng database Postgres với hub (Supabase chỉ có database postgres).
-- Bảng tiền tố tbl_shopee_ để tách khỏi bảng hub. Cột đặt theo field của Open Platform v2. Owner: ChienPX.

CREATE TABLE tbl_shopee_item (
    item_id     BIGINT PRIMARY KEY,
    shop_id     BIGINT NOT NULL,
    item_sku    VARCHAR(100),
    item_name   VARCHAR(255),
    item_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

-- Body update_price: price_list[].model_id + original_price, gắn với item_id.
CREATE TABLE tbl_shopee_price_list (
    item_id        BIGINT NOT NULL REFERENCES tbl_shopee_item (item_id),
    model_id       BIGINT NOT NULL,
    original_price BIGINT NOT NULL,
    PRIMARY KEY (item_id, model_id),
    CONSTRAINT ck_tbl_shopee_price_list_price CHECK (original_price >= 0)
);

-- Body update_stock: stock_list[].seller_stock[]. location_id rỗng là kho mặc định.
CREATE TABLE tbl_shopee_seller_stock (
    item_id     BIGINT NOT NULL REFERENCES tbl_shopee_item (item_id),
    model_id    BIGINT NOT NULL,
    location_id VARCHAR(64) NOT NULL DEFAULT '',
    stock       INTEGER NOT NULL,
    PRIMARY KEY (item_id, model_id, location_id),
    CONSTRAINT ck_tbl_shopee_seller_stock_stock CHECK (stock >= 0)
);

-- Response get_order_detail: order_list[] trừ item_list.
CREATE TABLE tbl_shopee_order (
    order_sn     VARCHAR(32) PRIMARY KEY,
    shop_id      BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    region       VARCHAR(8) NOT NULL DEFAULT 'VN',
    currency     VARCHAR(3) NOT NULL DEFAULT 'VND',
    total_amount BIGINT NOT NULL,
    create_time  BIGINT NOT NULL,
    update_time  BIGINT NOT NULL,
    CONSTRAINT ck_tbl_shopee_order_region CHECK (region = 'VN'),
    CONSTRAINT ck_tbl_shopee_order_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_tbl_shopee_order_total CHECK (total_amount >= 0)
);

-- item_list[] của get_order_detail.
CREATE TABLE tbl_shopee_order_item (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_sn                  VARCHAR(32) NOT NULL REFERENCES tbl_shopee_order (order_sn),
    item_id                   BIGINT NOT NULL,
    item_name                 VARCHAR(255) NOT NULL,
    item_sku                  VARCHAR(100) NOT NULL,
    model_id                  BIGINT NOT NULL,
    model_quantity_purchased  INTEGER NOT NULL,
    model_original_price      BIGINT NOT NULL,
    model_discounted_price    BIGINT NOT NULL,
    CONSTRAINT ck_tbl_shopee_order_item_qty CHECK (model_quantity_purchased > 0),
    CONSTRAINT ck_tbl_shopee_order_item_price CHECK (model_original_price >= 0 AND model_discounted_price >= 0)
);

CREATE INDEX idx_tbl_shopee_order_item_order_sn ON tbl_shopee_order_item (order_sn);

-- Push code 3. Field trong push là ordersn; chi tiết đơn dùng order_sn. Hai cột giữ đúng hai tên đó.
CREATE TABLE tbl_shopee_push (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_sn      VARCHAR(32) NOT NULL REFERENCES tbl_shopee_order (order_sn),
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

CREATE INDEX idx_tbl_shopee_push_order_sn ON tbl_shopee_push (order_sn);
