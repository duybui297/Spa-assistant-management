-- Schema riêng của app AI (spa_ai). Cú pháp SQL chung cho Postgres (V1/Supabase) và MySQL (V2).
-- ${ts} = TIMESTAMP (Postgres) / DATETIME (MySQL). Khóa chính dạng chuỗi UUID sinh trong Java.

CREATE TABLE app_session (
    token_hash   VARCHAR(64)  NOT NULL PRIMARY KEY,
    username     VARCHAR(100) NOT NULL,
    created_at   ${ts}        NOT NULL,
    last_seen_at ${ts}
);

-- Nhóm dịch vụ (D.1): admin gán nhóm cho từng dịch vụ
CREATE TABLE service_group_def (
    code       VARCHAR(50)  NOT NULL PRIMARY KEY,
    label      VARCHAR(200) NOT NULL,
    unit       VARCHAR(30),
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE service_group_map (
    service_id VARCHAR(36)  NOT NULL PRIMARY KEY,
    group_code VARCHAR(50)  NOT NULL,
    updated_by VARCHAR(100),
    updated_at ${ts}
);

-- Bảng luật hoa hồng Tư vấn (D.6). conditions_json: danh sách điều kiện (xem RuleCondition.java)
CREATE TABLE commission_rule (
    id              VARCHAR(50)   NOT NULL PRIMARY KEY,
    label           VARCHAR(500)  NOT NULL,
    pct             DECIMAL(5, 2) NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    priority        INT           NOT NULL DEFAULT 0,
    conditions_json ${text}       NOT NULL,
    updated_by      VARCHAR(100),
    updated_at      ${ts}
);

CREATE TABLE commission_rule_history (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    rule_id     VARCHAR(50)  NOT NULL,
    change_type VARCHAR(20)  NOT NULL,
    before_json ${text},
    after_json  ${text},
    note        VARCHAR(1000),
    changed_by  VARCHAR(100),
    changed_at  ${ts}        NOT NULL
);

-- Mỗi lần bấm nút "Lấy dữ liệu & kiểm tra"
CREATE TABLE commission_run (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    started_at     ${ts}        NOT NULL,
    finished_at    ${ts},
    from_date      DATE         NOT NULL,
    to_date        DATE         NOT NULL,
    total_lines    INT          NOT NULL DEFAULT 0,
    mismatch_lines INT          NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL,
    error_message  VARCHAR(2000),
    triggered_by   VARCHAR(100),
    rules_snapshot ${text}
);

-- Kết quả chấm từng dòng hoa hồng Tư vấn (1 dòng order_item_staff) trong 1 lần chạy
CREATE TABLE commission_check (
    run_id              VARCHAR(36)    NOT NULL,
    order_item_staff_id VARCHAR(36)    NOT NULL,
    order_id            VARCHAR(36)    NOT NULL,
    order_code          VARCHAR(50),
    order_date          ${ts},
    branch_id           VARCHAR(36),
    order_item_id       VARCHAR(36)    NOT NULL,
    item_type           VARCHAR(30),
    service_id          VARCHAR(36),
    service_name        VARCHAR(500),
    customer_id         VARCHAR(36),
    customer_name       VARCHAR(255),
    staff_id            VARCHAR(36),
    staff_name          VARCHAR(255),
    group_code          VARCHAR(50),
    is_package          BOOLEAN        NOT NULL DEFAULT FALSE,
    sessions            INT,
    bonus               INT,
    visit_no            INT            NOT NULL,
    quantity            INT            NOT NULL,
    list_price          DECIMAL(19, 2),
    unit_price          DECIMAL(19, 2),
    base_amount         DECIMAL(19, 2),
    entered_amount      DECIMAL(19, 2),
    entered_pct         DECIMAL(9, 2),
    expected_pct        DECIMAL(9, 2),
    expected_amount     DECIMAL(19, 2),
    diff_amount         DECIMAL(19, 2),
    verdict             VARCHAR(10)    NOT NULL,
    source              VARCHAR(20)    NOT NULL,
    rule_id             VARCHAR(50),
    reason              VARCHAR(2000),
    PRIMARY KEY (run_id, order_item_staff_id)
);

CREATE INDEX idx_check_run_verdict ON commission_check (run_id, verdict);

-- Chủ xác nhận dòng lệch kèm ghi chú (màn ② "Cần duyệt")
CREATE TABLE commission_review (
    order_item_staff_id VARCHAR(36)    NOT NULL PRIMARY KEY,
    status              VARCHAR(20)    NOT NULL,
    note                VARCHAR(2000),
    entered_amount      DECIMAL(19, 2),
    expected_amount     DECIMAL(19, 2),
    reviewed_by         VARCHAR(100),
    reviewed_at         ${ts}          NOT NULL
);

-- Dữ liệu nhập tay (HANDOFF 7.2): chi phí vận hành, nợ nhà cung cấp
CREATE TABLE manual_expense (
    id           VARCHAR(36)    NOT NULL PRIMARY KEY,
    expense_date DATE           NOT NULL,
    category     VARCHAR(100)   NOT NULL,
    description  VARCHAR(1000),
    amount       DECIMAL(19, 2) NOT NULL,
    recurring    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by   VARCHAR(100),
    created_at   ${ts}          NOT NULL
);

CREATE TABLE supplier_debt (
    id            VARCHAR(36)    NOT NULL PRIMARY KEY,
    supplier_name VARCHAR(255)   NOT NULL,
    description   VARCHAR(1000),
    amount        DECIMAL(19, 2) NOT NULL,
    paid_amount   DECIMAL(19, 2) NOT NULL DEFAULT 0,
    invoice_date  DATE,
    due_date      DATE,
    critical      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(100),
    created_at    ${ts}          NOT NULL
);
