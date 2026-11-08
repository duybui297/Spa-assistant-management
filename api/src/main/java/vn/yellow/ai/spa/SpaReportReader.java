package vn.yellow.ai.spa;

import static vn.yellow.ai.spa.SpaReader.EXCLUDED_ORDER_STATUSES;
import static vn.yellow.ai.spa.SpaReader.ldt;
import static vn.yellow.ai.spa.SpaReader.ts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Truy vấn đọc cho các màn assistant (cùng nguyên tắc với {@link SpaReader}: chỉ SELECT, SQL chung,
 * không kèm schema, phép tính ngày làm ở Java). Gom số liệu ở Java để câu SQL đơn giản, chạy được trên cả MySQL.
 */
@Component
public class SpaReportReader {

    private final NamedParameterJdbcTemplate jdbc;

    public SpaReportReader(@Qualifier("spaNamedJdbc") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate toExcl) {
        return new MapSqlParameterSource("from", ts(from)).addValue("to", ts(toExcl))
                .addValue("excluded", EXCLUDED_ORDER_STATUSES);
    }

    // ------------------------------------------------------------------ đơn & dòng đơn

    public record OrderRow(String id, String code, LocalDateTime date, String customerId, String customerName,
                           String customerPhone, String status, BigDecimal subTotal, BigDecimal discount,
                           BigDecimal couponDiscount, BigDecimal total, BigDecimal paid, BigDecimal debt, String branchId) {
    }

    private static final String ORDER_COLS = """
            o.id, o.order_code, o.order_date, o.customer_id, o.customer_name, o.customer_phone, o.status, o.sub_total,
            o.discount, o.coupon_discount, o.total_amount, o.paid_amount, o.debt_amount, o.branch_id""";

    private static OrderRow order(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderRow(rs.getString("id"), rs.getString("order_code"), ldt(rs, "order_date"),
                rs.getString("customer_id"), rs.getString("customer_name"), rs.getString("customer_phone"),
                rs.getString("status"), rs.getBigDecimal("sub_total"), rs.getBigDecimal("discount"),
                rs.getBigDecimal("coupon_discount"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("debt_amount"), rs.getString("branch_id"));
    }

    /** Đơn hợp lệ (không xóa, không hủy / hoàn) theo ngày đơn. */
    public List<OrderRow> orders(LocalDate from, LocalDate toExcl) {
        return jdbc.query("SELECT " + ORDER_COLS + """
                 FROM orders o
                WHERE o.deleted = false AND o.status NOT IN (:excluded)
                  AND o.order_date >= :from AND o.order_date < :to
                ORDER BY o.order_date""", range(from, toExcl), (rs, n) -> order(rs));
    }

    /** Đơn còn nợ (orders.debt_amount là số nợ hiện tại; DebtService của Spa cập nhật khi khách trả). */
    public List<OrderRow> openDebts() {
        return jdbc.query("SELECT " + ORDER_COLS + """
                 FROM orders o
                WHERE o.deleted = false AND o.status NOT IN (:excluded) AND o.debt_amount > 0
                ORDER BY o.order_date""", new MapSqlParameterSource("excluded", EXCLUDED_ORDER_STATUSES),
                (rs, n) -> order(rs));
    }

    public record ItemRow(String id, String orderId, String orderCode, LocalDateTime orderDate, String customerId,
                          String customerName, String itemType, String itemId, String itemName, int quantity,
                          BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal discount, BigDecimal total,
                          BigDecimal listPrice, String categoryName) {
    }

    public List<ItemRow> items(LocalDate from, LocalDate toExcl) {
        return jdbc.query("""
                SELECT i.id, i.order_id, o.order_code, o.order_date, o.customer_id, o.customer_name, i.item_type,
                       i.item_id, i.item_name, i.quantity, i.unit_price, i.item_discount_percent, i.discount,
                       i.total_amount, sv.price AS list_price, c.name AS category_name
                FROM order_items i
                JOIN orders o ON o.id = i.order_id
                LEFT JOIN service sv ON sv.id = i.item_id
                LEFT JOIN category c ON c.id = sv.category_id
                WHERE o.deleted = false AND o.status NOT IN (:excluded)
                  AND o.order_date >= :from AND o.order_date < :to
                ORDER BY o.order_date""", range(from, toExcl),
                (rs, n) -> new ItemRow(rs.getString("id"), rs.getString("order_id"), rs.getString("order_code"),
                        ldt(rs, "order_date"), rs.getString("customer_id"), rs.getString("customer_name"),
                        rs.getString("item_type"), rs.getString("item_id"), rs.getString("item_name"),
                        rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("item_discount_percent"),
                        rs.getBigDecimal("discount"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("list_price"),
                        rs.getString("category_name")));
    }

    // ------------------------------------------------------------------ tiền vào / ra

    public record Money(LocalDateTime at, BigDecimal amount, String orderId, String method) {
    }

    /** Tiền thu thực tế (gồm cả trả nợ, vì DebtService của Spa cũng ghi vào order_payments). */
    public List<Money> payments(LocalDate from, LocalDate toExcl) {
        return jdbc.query("""
                SELECT p.paid_at, p.paid_amount, p.order_id, p.payment_method
                FROM order_payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.deleted = false AND p.paid_at >= :from AND p.paid_at < :to""", range(from, toExcl),
                (rs, n) -> new Money(ldt(rs, "paid_at"), rs.getBigDecimal("paid_amount"), rs.getString("order_id"),
                        rs.getString("payment_method")));
    }

    public record RefundRow(LocalDateTime at, BigDecimal amount, String orderId, String orderCode, String customerName,
                            String note) {
    }

    public List<RefundRow> refunds(LocalDate from, LocalDate toExcl) {
        return jdbc.query("""
                SELECT r.refund_date, r.refund_amount, r.order_id, o.order_code, o.customer_name, r.note
                FROM order_refunds r
                JOIN orders o ON o.id = r.order_id
                WHERE r.refund_date >= :from AND r.refund_date < :to
                ORDER BY r.refund_date DESC""", range(from, toExcl),
                (rs, n) -> new RefundRow(ldt(rs, "refund_date"), rs.getBigDecimal("refund_amount"),
                        rs.getString("order_id"), rs.getString("order_code"), rs.getString("customer_name"),
                        rs.getString("note")));
    }

    public record StaffCommission(LocalDateTime at, String staffId, String staffName, String staffRole,
                                  BigDecimal amount) {
    }

    /** Hoa hồng đã ghi trên đơn (order_item_staff) + khi check-in thẻ (treatment_card_commission). */
    public List<StaffCommission> commissions(LocalDate from, LocalDate toExcl) {
        List<StaffCommission> a = jdbc.query("""
                SELECT o.order_date, s.staff_id, s.staff_name, s.staff_role, s.commission_amount
                FROM order_item_staff s
                JOIN orders o ON o.id = s.order_id
                WHERE o.deleted = false AND o.status NOT IN (:excluded)
                  AND o.order_date >= :from AND o.order_date < :to""", range(from, toExcl),
                (rs, n) -> new StaffCommission(ldt(rs, "order_date"), rs.getString("staff_id"), rs.getString("staff_name"),
                        rs.getString("staff_role"), rs.getBigDecimal("commission_amount")));
        List<StaffCommission> b = jdbc.query("""
                SELECT created_at, staff_id, staff_name, staff_role, commission_amount
                FROM treatment_card_commission
                WHERE created_at >= :from AND created_at < :to""", range(from, toExcl),
                (rs, n) -> new StaffCommission(ldt(rs, "created_at"), rs.getString("staff_id"), rs.getString("staff_name"),
                        rs.getString("staff_role"), rs.getBigDecimal("commission_amount")));
        java.util.ArrayList<StaffCommission> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    // ------------------------------------------------------------------ thẻ liệu trình, lịch hẹn, kho

    public record CardRow(String id, String code, String customerId, String customerName, String customerPhone,
                          String serviceName, int totalSessions, int bonusSessions, int usedSessions,
                          int remainingSessions, BigDecimal finalAmount, LocalDateTime purchaseDate,
                          LocalDateTime expiryDate) {
    }

    public List<CardRow> activeCards() {
        return jdbc.query("""
                SELECT id, card_code, customer_id, customer_name, customer_phone, service_name, total_sessions,
                       bonus_sessions, used_sessions, remaining_sessions, final_amount, purchase_date, expiry_date
                FROM service_card
                WHERE active = true AND remaining_sessions > 0
                ORDER BY expiry_date""", new MapSqlParameterSource(),
                (rs, n) -> new CardRow(rs.getString("id"), rs.getString("card_code"), rs.getString("customer_id"),
                        rs.getString("customer_name"), rs.getString("customer_phone"), rs.getString("service_name"),
                        rs.getInt("total_sessions"), rs.getInt("bonus_sessions"), rs.getInt("used_sessions"),
                        rs.getInt("remaining_sessions"), rs.getBigDecimal("final_amount"), ldt(rs, "purchase_date"),
                        ldt(rs, "expiry_date")));
    }

    public record AppointmentRow(String id, String date, String time, String customerId, String customerName,
                                 String phone, String service, String performedBy, String status) {
    }

    /** appointment.date lưu chuỗi yyyy-MM-dd nên so sánh chuỗi là đúng thứ tự ngày. */
    public List<AppointmentRow> appointments(LocalDate from, LocalDate toIncl) {
        return jdbc.query("""
                SELECT id, date, appointment_time, customer_id, customer_name, phone, service, performed_by, status
                FROM appointment
                WHERE date >= :from AND date <= :to
                ORDER BY date, appointment_time""",
                new MapSqlParameterSource("from", from.toString()).addValue("to", toIncl.toString()),
                (rs, n) -> new AppointmentRow(rs.getString("id"), rs.getString("date"), rs.getString("appointment_time"),
                        rs.getString("customer_id"), rs.getString("customer_name"), rs.getString("phone"),
                        rs.getString("service"), rs.getString("performed_by"), rs.getString("status")));
    }

    public record StockLot(String productId, String productCode, String productName, int quantity,
                           LocalDateTime expiryDate, BigDecimal unitPrice, LocalDateTime importedAt) {
    }

    /** Các lô nhập có hạn dùng (phiếu IMPORT chưa xóa). */
    public List<StockLot> importLots() {
        return jdbc.query("""
                SELECT it.product_id, it.product_code, it.product_name, it.quantity, it.expiry_date, it.unit_price,
                       sl.transaction_date
                FROM warehouse_slip_item it
                JOIN warehouse_slip sl ON sl.id = it.slip_id
                WHERE sl.deleted = false AND sl.slip_type = 'IMPORT' AND it.expiry_date IS NOT NULL
                ORDER BY it.expiry_date""", new MapSqlParameterSource(),
                (rs, n) -> new StockLot(rs.getString("product_id"), rs.getString("product_code"),
                        rs.getString("product_name"), rs.getInt("quantity"), ldt(rs, "expiry_date"),
                        rs.getBigDecimal("unit_price"), ldt(rs, "transaction_date")));
    }

    public record StockLevel(String productId, String productCode, String name, long opening, long imported,
                             long exported, BigDecimal price) {
        public long onHand() {
            return opening + imported - exported;
        }
    }

    /** Tồn = đầu kỳ + nhập − xuất theo phiếu (HANDOFF 2.3: bán sản phẩm không tự trừ kho). */
    public List<StockLevel> stockLevels() {
        return jdbc.query("""
                SELECT p.id, p.product_code, p.name, p.price,
                       (SELECT COALESCE(SUM(os.quantity), 0) FROM warehouse_opening_stock os WHERE os.product_id = p.id) AS opening,
                       (SELECT COALESCE(SUM(it.quantity), 0) FROM warehouse_slip_item it JOIN warehouse_slip sl ON sl.id = it.slip_id
                         WHERE it.product_id = p.id AND sl.deleted = false AND sl.slip_type = 'IMPORT') AS imported,
                       (SELECT COALESCE(SUM(it.quantity), 0) FROM warehouse_slip_item it JOIN warehouse_slip sl ON sl.id = it.slip_id
                         WHERE it.product_id = p.id AND sl.deleted = false AND sl.slip_type = 'EXPORT') AS exported
                FROM product p
                WHERE p.deleted = false
                ORDER BY p.name""", new MapSqlParameterSource(),
                (rs, n) -> new StockLevel(rs.getString("id"), rs.getString("product_code"), rs.getString("name"),
                        rs.getLong("opening"), rs.getLong("imported"), rs.getLong("exported"), rs.getBigDecimal("price")));
    }

    public record CustomerLastVisit(String customerId, LocalDateTime lastOrder, int orders, BigDecimal spent) {
    }

    /** Lần mua gần nhất và tổng chi tiêu của mỗi khách (toàn bộ lịch sử). */
    public List<CustomerLastVisit> customerActivity() {
        return jdbc.query("""
                SELECT o.customer_id, MAX(o.order_date) AS last_order, COUNT(*) AS cnt, SUM(o.total_amount) AS spent
                FROM orders o
                WHERE o.deleted = false AND o.status NOT IN (:excluded) AND o.customer_id IS NOT NULL
                GROUP BY o.customer_id""", new MapSqlParameterSource("excluded", EXCLUDED_ORDER_STATUSES),
                (rs, n) -> new CustomerLastVisit(rs.getString("customer_id"), ldt(rs, "last_order"), rs.getInt("cnt"),
                        rs.getBigDecimal("spent")));
    }
}
