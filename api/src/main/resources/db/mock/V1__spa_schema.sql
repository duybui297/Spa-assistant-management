-- spa_mock: bản sao CẤU TRÚC DB Spa (MySQL `clinic`), chỉ dùng cho V1, chứa dữ liệu GIẢ.
-- Tên bảng, tên cột giữ đúng như entity của ms-service / ms-users (nhánh staging, mốc 14/10/2026).
-- Chỉ tạo các bảng app AI cần đọc (HANDOFF 2.3). Một số bảng rút bớt cột app AI không dùng — ghi chú ngay tại bảng.

CREATE TABLE branch (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    code       VARCHAR(50),
    name       VARCHAR(255) NOT NULL,
    name_en    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE category (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    name_en        VARCHAR(100),
    code           VARCHAR(100) NOT NULL UNIQUE,
    branch_id      VARCHAR(36),
    priority_order INTEGER DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE service (
    id                        VARCHAR(36)    NOT NULL PRIMARY KEY,
    service_code              VARCHAR(100)   NOT NULL,
    second_service_code       VARCHAR(100),
    image_id                  VARCHAR(36),
    service_name              VARCHAR(500)   NOT NULL,
    service_name_en           VARCHAR(500),
    category_id               VARCHAR(36),
    branch_id                 VARCHAR(36),
    price                     NUMERIC(19, 2) NOT NULL,
    special_price             NUMERIC(19, 2),
    ecommerce_price           NUMERIC(19, 2),
    duration_minutes          INTEGER,
    total_sessions            BIGINT,
    priority_order            INTEGER DEFAULT 0,
    description               TEXT,
    booking_online_active     BOOLEAN DEFAULT TRUE,
    ecommerce_active          BOOLEAN DEFAULT FALSE,
    blocked                   BOOLEAN DEFAULT FALSE,
    video_url                 VARCHAR(1024),
    commercial_tag_categories VARCHAR(1000),
    commission_method         VARCHAR(50),
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP
);

CREATE TABLE commission (
    id                 VARCHAR(36)   NOT NULL PRIMARY KEY,
    role               VARCHAR(100)  NOT NULL,
    commission_type    VARCHAR(50)   NOT NULL,
    percentage_revenue NUMERIC(5, 2),
    cash_bonus_amount  NUMERIC(19, 2),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    service_id         VARCHAR(36),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);

-- staff (ms-users). Bỏ cột avatar.
CREATE TABLE staff (
    version        BIGINT       NOT NULL DEFAULT 0,
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    staff_code     VARCHAR(20),
    full_name      VARCHAR(100) NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    email          VARCHAR(100),
    job_position   VARCHAR(50)  NOT NULL,
    date_of_birth  TIMESTAMP,
    note           VARCHAR(5000),
    booking_online BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    user_id        VARCHAR(36),
    role           VARCHAR(20),
    created_date   TIMESTAMP,
    updated_date   TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP,
    deleted_by     VARCHAR(100)
);

-- customers (ms-users). Bản gốc có ~50 cột (hồ sơ y tế, ngân hàng…); chỉ giữ cột app AI dùng.
CREATE TABLE customers (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    customer_code     VARCHAR(20),
    full_name         VARCHAR(200) NOT NULL,
    phone_number      VARCHAR(20)  NOT NULL,
    date_of_birth     TIMESTAMP,
    email             VARCHAR(100),
    gender            VARCHAR(10),
    address           VARCHAR(500),
    consultant_person VARCHAR(200),
    consultant_id     VARCHAR(36),
    branch_id         VARCHAR(36),
    status            VARCHAR(50),
    join_date         TIMESTAMP,
    last_service_date TIMESTAMP,
    created_date      TIMESTAMP,
    updated_date      TIMESTAMP,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE orders (
    id               VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_code       VARCHAR(20)    NOT NULL UNIQUE,
    customer_id      VARCHAR(36),
    customer_name    VARCHAR(255),
    customer_phone   VARCHAR(20),
    customer_address VARCHAR(500),
    customer_code    VARCHAR(20),
    customer_type    VARCHAR(20),
    order_date       TIMESTAMP,
    sub_total        NUMERIC(19, 2) DEFAULT 0,
    discount_percent NUMERIC(5, 2)  DEFAULT 0,
    vat_percent      NUMERIC(5, 2)  DEFAULT 0,
    vat              NUMERIC(19, 2) DEFAULT 0,
    discount         NUMERIC(19, 2) DEFAULT 0,
    coupon_code      VARCHAR(50),
    coupon_discount  NUMERIC(19, 2) DEFAULT 0,
    total_amount     NUMERIC(19, 2) NOT NULL DEFAULT 0,
    paid_amount      NUMERIC(19, 2) DEFAULT 0,
    debt_amount      NUMERIC(19, 2) DEFAULT 0,
    payment_method   VARCHAR(50),
    status           VARCHAR(50)    NOT NULL,
    performed_by     VARCHAR(100),
    branch_id        VARCHAR(36),
    note             TEXT,
    created_by       VARCHAR(100),
    created_by_code  VARCHAR(20),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    deleted          BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_order_customer_id ON orders (customer_id);
CREATE INDEX idx_order_order_date ON orders (order_date);

CREATE TABLE order_items (
    id                    VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id              VARCHAR(36)    NOT NULL,
    item_type             VARCHAR(20)    NOT NULL,
    item_id               VARCHAR(36),
    item_name             VARCHAR(500)   NOT NULL,
    quantity              INTEGER        NOT NULL DEFAULT 1,
    unit_price            NUMERIC(19, 2) NOT NULL DEFAULT 0,
    vat                   NUMERIC(19, 2) DEFAULT 0,
    item_discount_percent NUMERIC(5, 2)  DEFAULT 0,
    discount              NUMERIC(19, 2) DEFAULT 0,
    total_amount          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    performed_by          VARCHAR(100),
    note                  VARCHAR(5000),
    is_treatment_card     BOOLEAN DEFAULT FALSE,
    tc_total_sessions     INTEGER,
    tc_bonus_sessions     INTEGER DEFAULT 0,
    tc_unlimited          BOOLEAN DEFAULT FALSE,
    tc_expiry_date        DATE,
    tc_card_id            VARCHAR(36),
    created_at            TIMESTAMP
);
CREATE INDEX idx_order_item_order_id ON order_items (order_id);
CREATE INDEX idx_order_item_item_id ON order_items (item_id);

CREATE TABLE order_item_staff (
    id                VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_item_id     VARCHAR(36)    NOT NULL,
    order_id          VARCHAR(36)    NOT NULL,
    staff_id          VARCHAR(36),
    staff_name        VARCHAR(255),
    staff_role        VARCHAR(100),
    commission_amount NUMERIC(19, 2) DEFAULT 0,
    created_at        TIMESTAMP
);
CREATE INDEX idx_ois_order_item_id ON order_item_staff (order_item_id);

CREATE TABLE order_payments (
    id               VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id         VARCHAR(36)    NOT NULL,
    paid_amount      NUMERIC(19, 2) NOT NULL,
    remaining_amount NUMERIC(19, 2),
    payment_method   VARCHAR(50),
    paid_at          TIMESTAMP,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP
);

CREATE TABLE order_refunds (
    id            VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id      VARCHAR(36)    NOT NULL,
    refund_amount NUMERIC(19, 2) NOT NULL,
    refund_date   TIMESTAMP,
    note          TEXT,
    created_by    VARCHAR(100),
    created_at    TIMESTAMP
);

CREATE TABLE debt_payment (
    id             VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id       VARCHAR(36)    NOT NULL,
    customer_id    VARCHAR(36)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    payment_method VARCHAR(50),
    payment_date   TIMESTAMP,
    note           VARCHAR(5000),
    created_by     VARCHAR(100),
    created_at     TIMESTAMP
);

CREATE TABLE service_card (
    id                 VARCHAR(36)    NOT NULL PRIMARY KEY,
    customer_id        VARCHAR(36),
    customer_name      VARCHAR(255),
    customer_phone     VARCHAR(20),
    customer_code      VARCHAR(50),
    customer_email     VARCHAR(255),
    service_id         VARCHAR(36),
    service_name       VARCHAR(500)   NOT NULL,
    order_id           VARCHAR(36),
    order_code         VARCHAR(50),
    total_sessions     INTEGER        NOT NULL,
    bonus_sessions     INTEGER,
    used_sessions      INTEGER        NOT NULL DEFAULT 0,
    remaining_sessions INTEGER        NOT NULL,
    price              NUMERIC(19, 2),
    total_amount       NUMERIC(19, 2),
    discount           NUMERIC(19, 2),
    final_amount       NUMERIC(19, 2),
    purchase_date      TIMESTAMP,
    expiry_date        TIMESTAMP,
    card_code          VARCHAR(20),
    note               VARCHAR(5000),
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by         VARCHAR(100),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);

CREATE TABLE treatment_card_payment (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    card_id       VARCHAR(36) NOT NULL,
    payment_code  VARCHAR(20),
    order_id      VARCHAR(36),
    sessions_used INTEGER,
    note          TEXT,
    created_by    VARCHAR(100),
    created_at    TIMESTAMP
);

CREATE TABLE treatment_card_commission (
    id                VARCHAR(36)    NOT NULL PRIMARY KEY,
    card_id           VARCHAR(36)    NOT NULL,
    staff_id          VARCHAR(36),
    staff_name        VARCHAR(255),
    staff_role        VARCHAR(100),
    payment_id        VARCHAR(36),
    payment_code      VARCHAR(20),
    commission_amount NUMERIC(18, 0),
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

-- product (ms-users)
CREATE TABLE product (
    id           VARCHAR(36)    NOT NULL PRIMARY KEY,
    product_code VARCHAR(20),
    name         VARCHAR(300)   NOT NULL,
    quantity     BIGINT         NOT NULL DEFAULT 0,
    price        NUMERIC(15, 2) NOT NULL,
    supplier     VARCHAR(50)    NOT NULL,
    description  TEXT,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_date TIMESTAMP,
    updated_date TIMESTAMP,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMP,
    deleted_by   VARCHAR(100)
);

CREATE TABLE warehouse_slip (
    id               VARCHAR(36) NOT NULL PRIMARY KEY,
    slip_code        VARCHAR(20) NOT NULL,
    slip_type        VARCHAR(20) NOT NULL,
    staff_id         VARCHAR(36),
    staff_name       VARCHAR(200),
    transaction_date TIMESTAMP,
    note             TEXT,
    status           VARCHAR(20) NOT NULL,
    created_by       VARCHAR(100),
    created_by_name  VARCHAR(200),
    created_by_code  VARCHAR(50),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(100)
);

CREATE TABLE warehouse_slip_item (
    id           VARCHAR(36)    NOT NULL PRIMARY KEY,
    slip_id      VARCHAR(36)    NOT NULL,
    product_id   VARCHAR(36)    NOT NULL,
    product_code VARCHAR(20),
    product_name VARCHAR(300),
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(15, 2),
    note         VARCHAR(5000),
    expiry_date  TIMESTAMP
);

CREATE TABLE warehouse_opening_stock (
    id           VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id   VARCHAR(36) NOT NULL,
    product_code VARCHAR(20),
    quantity     INTEGER     NOT NULL,
    period_date  TIMESTAMP,
    created_by   VARCHAR(100),
    created_at   TIMESTAMP
);

CREATE TABLE appointment (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    appointment_code  VARCHAR(10),
    customer_id       VARCHAR(36),
    customer_name     VARCHAR(255) NOT NULL,
    date              VARCHAR(10)  NOT NULL,
    appointment_time  VARCHAR(5),
    duration          INTEGER,
    phone             VARCHAR(50),
    email             VARCHAR(255),
    address           VARCHAR(500),
    purpose           VARCHAR(50),
    is_repeat         BOOLEAN,
    series_id         VARCHAR(36),
    branch            VARCHAR(100),
    service           VARCHAR(255),
    performed_by      VARCHAR(100),
    note              VARCHAR(5000),
    status            VARCHAR(50),
    end_time          VARCHAR(5),
    assigned_staff_id VARCHAR(36),
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);
