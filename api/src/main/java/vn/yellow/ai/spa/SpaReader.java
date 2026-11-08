package vn.yellow.ai.spa;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * LỚP NGUỒN DỮ LIỆU SPA DUY NHẤT — CHỈ ĐỌC (HANDOFF 8, "Nguyên tắc code").
 * <ul>
 *   <li>Mọi truy cập DB Spa phải đi qua class này. Không có câu lệnh ghi nào.</li>
 *   <li>Chỉ dùng cú pháp SQL chung (chạy được trên Postgres V1 và MySQL V2): không DATE_TRUNC, ILIKE, ::cast, LIMIT OFFSET riêng…
 *       Phép tính ngày làm ở Java, truyền vào bằng tham số.</li>
 *   <li>Tên bảng không kèm schema: V1 chọn schema bằng currentSchema=spa_mock, V2 bằng tên database trong URL.</li>
 * </ul>
 */
@Component
public class SpaReader {

    /** Đơn không tính: đã hủy / đã hoàn (orders.status). */
    public static final List<String> EXCLUDED_ORDER_STATUSES = List.of("CANCELLED", "RETURNED");
    public static final List<String> SERVICE_ITEM_TYPES = List.of("SERVICE", "TREATMENT_CARD");

    private final NamedParameterJdbcTemplate jdbc;

    public SpaReader(@Qualifier("spaNamedJdbc") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ helpers

    static LocalDateTime ldt(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Timestamp ts(LocalDate d) {
        return Timestamp.valueOf(d.atStartOfDay());
    }

    private static MapSqlParameterSource p() {
        return new MapSqlParameterSource()
                .addValue("excluded", EXCLUDED_ORDER_STATUSES)
                .addValue("svcTypes", SERVICE_ITEM_TYPES);
    }

    /** Chia danh sách IN (...) thành từng lô để không vượt giới hạn tham số. */
    private static <T> List<List<T>> chunks(Collection<T> all, int size) {
        List<List<T>> out = new ArrayList<>();
        List<T> cur = new ArrayList<>();
        for (T t : all) {
            cur.add(t);
            if (cur.size() == size) {
                out.add(cur);
                cur = new ArrayList<>();
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    // ------------------------------------------------------------------ hoa hồng

    public record ConsultantLine(
            String orderItemStaffId, String staffId, String staffName, String staffRole, BigDecimal commissionAmount,
            String orderItemId, String itemType, String serviceId, String itemName, int quantity, BigDecimal unitPrice,
            Integer tcTotalSessions, Integer tcBonusSessions,
            String orderId, String orderCode, LocalDateTime orderDate, String branchId,
            String customerId, String customerName,
            BigDecimal listPrice, String serviceName) {
    }

    /** Các dòng hoa hồng của Tư vấn trên dòng dịch vụ / bán gói, trong [from, toExclusive). */
    public List<ConsultantLine> consultantLines(LocalDate from, LocalDate toExclusive, List<String> roles) {
        String sql = """
                SELECT s.id AS ois_id, s.staff_id, s.staff_name, s.staff_role, s.commission_amount,
                       i.id AS item_id, i.item_type, i.item_id AS service_id, i.item_name, i.quantity, i.unit_price,
                       i.tc_total_sessions, i.tc_bonus_sessions,
                       o.id AS order_id, o.order_code, o.order_date, o.branch_id, o.customer_id, o.customer_name,
                       sv.price AS list_price, sv.service_name
                FROM order_item_staff s
                JOIN order_items i ON i.id = s.order_item_id
                JOIN orders o ON o.id = i.order_id
                LEFT JOIN service sv ON sv.id = i.item_id
                WHERE o.deleted = false
                  AND o.status NOT IN (:excluded)
                  AND i.item_type IN (:svcTypes)
                  AND s.staff_role IN (:roles)
                  AND o.order_date >= :from AND o.order_date < :to
                ORDER BY o.order_date, o.id, i.created_at, i.id
                """;
        return jdbc.query(sql, p().addValue("roles", roles).addValue("from", ts(from)).addValue("to", ts(toExclusive)),
                (rs, n) -> new ConsultantLine(
                        rs.getString("ois_id"), rs.getString("staff_id"), rs.getString("staff_name"),
                        rs.getString("staff_role"), rs.getBigDecimal("commission_amount"),
                        rs.getString("item_id"), rs.getString("item_type"), rs.getString("service_id"),
                        rs.getString("item_name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price"),
                        intOrNull(rs, "tc_total_sessions"), intOrNull(rs, "tc_bonus_sessions"),
                        rs.getString("order_id"), rs.getString("order_code"), ldt(rs, "order_date"),
                        rs.getString("branch_id"), rs.getString("customer_id"), rs.getString("customer_name"),
                        rs.getBigDecimal("list_price"), rs.getString("service_name")));
    }

    public record ServiceHistoryLine(
            String orderItemId, String customerId, String serviceId, String itemName, String itemType,
            int quantity, Integer tcTotalSessions, Integer tcBonusSessions,
            LocalDateTime orderDate, String orderId, String orderCode, LocalDateTime itemCreatedAt) {
    }

    /** Toàn bộ dòng dịch vụ / bán gói của các khách, trước {@code toExclusive} (để đếm "lần thứ mấy"). */
    public List<ServiceHistoryLine> serviceHistory(Collection<String> customerIds, LocalDate since, LocalDate toExclusive) {
        List<ServiceHistoryLine> out = new ArrayList<>();
        String sql = """
                SELECT i.id AS item_id, o.customer_id, i.item_id AS service_id, i.item_name, i.item_type, i.quantity,
                       i.tc_total_sessions, i.tc_bonus_sessions,
                       o.order_date, o.id AS order_id, o.order_code, i.created_at
                FROM order_items i
                JOIN orders o ON o.id = i.order_id
                WHERE o.deleted = false
                  AND o.status NOT IN (:excluded)
                  AND i.item_type IN (:svcTypes)
                  AND o.customer_id IN (:ids)
                  AND o.order_date >= :since AND o.order_date < :to
                """;
        for (List<String> batch : chunks(customerIds, 500)) {
            out.addAll(jdbc.query(sql, p().addValue("ids", batch).addValue("since", ts(since)).addValue("to", ts(toExclusive)),
                    (rs, n) -> new ServiceHistoryLine(
                            rs.getString("item_id"), rs.getString("customer_id"), rs.getString("service_id"),
                            rs.getString("item_name"), rs.getString("item_type"), rs.getInt("quantity"),
                            intOrNull(rs, "tc_total_sessions"), intOrNull(rs, "tc_bonus_sessions"),
                            ldt(rs, "order_date"), rs.getString("order_id"), rs.getString("order_code"),
                            ldt(rs, "created_at"))));
        }
        return out;
    }

    public record OldCommissionConfig(String serviceId, String commissionType, BigDecimal percentageRevenue,
                                      BigDecimal cashBonusAmount) {
    }

    /** "% cấu hình cũ" theo dịch vụ cho 1 chức vụ (bảng commission). */
    public Map<String, OldCommissionConfig> oldConfigs(Collection<String> serviceIds, String role) {
        Map<String, OldCommissionConfig> out = new java.util.HashMap<>();
        if (serviceIds.isEmpty()) return out;
        String sql = """
                SELECT service_id, commission_type, percentage_revenue, cash_bonus_amount
                FROM commission
                WHERE role = :role AND active = true AND service_id IN (:ids)
                ORDER BY updated_at
                """;
        for (List<String> batch : chunks(serviceIds, 500)) {
            jdbc.query(sql, new MapSqlParameterSource("role", role).addValue("ids", batch), rs -> {
                OldCommissionConfig c = new OldCommissionConfig(rs.getString("service_id"),
                        rs.getString("commission_type"), rs.getBigDecimal("percentage_revenue"),
                        rs.getBigDecimal("cash_bonus_amount"));
                out.put(c.serviceId(), c); // nếu trùng, lấy bản cập nhật sau cùng
            });
        }
        return out;
    }

    public record ServiceRow(String id, String code, String name, String categoryName, BigDecimal price,
                             Long totalSessions, boolean blocked) {
    }

    public List<ServiceRow> services() {
        String sql = """
                SELECT sv.id, sv.service_code, sv.service_name, c.name AS category_name, sv.price, sv.total_sessions,
                       sv.blocked
                FROM service sv
                LEFT JOIN category c ON c.id = sv.category_id
                ORDER BY c.name, sv.service_name
                """;
        return jdbc.query(sql, (rs, n) -> {
            long ts = rs.getLong("total_sessions");
            Long totalSessions = rs.wasNull() ? null : ts;
            return new ServiceRow(rs.getString("id"), rs.getString("service_code"), rs.getString("service_name"),
                    rs.getString("category_name"), rs.getBigDecimal("price"), totalSessions,
                    rs.getBoolean("blocked"));
        });
    }

    public record CustomerRow(String id, String code, String name, String phone, LocalDateTime dateOfBirth,
                              String consultantPerson) {
    }

    /** Tìm khách theo tên / SĐT / mã (không phân biệt hoa thường), tối đa 30 kết quả. */
    public List<CustomerRow> searchCustomers(String q) {
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        String sql = """
                SELECT id, customer_code, full_name, phone_number, date_of_birth, consultant_person
                FROM customers
                WHERE deleted = false
                  AND (LOWER(full_name) LIKE :q OR phone_number LIKE :q OR LOWER(customer_code) LIKE :q)
                ORDER BY full_name
                """;
        List<CustomerRow> rows = jdbc.query(sql, new MapSqlParameterSource("q", like), (rs, n) -> customer(rs));
        return rows.size() > 30 ? rows.subList(0, 30) : rows;
    }

    public List<CustomerRow> allCustomers() {
        return jdbc.query("""
                SELECT id, customer_code, full_name, phone_number, date_of_birth, consultant_person
                FROM customers WHERE deleted = false ORDER BY full_name
                """, (rs, n) -> customer(rs));
    }

    private static CustomerRow customer(ResultSet rs) throws SQLException {
        return new CustomerRow(rs.getString("id"), rs.getString("customer_code"), rs.getString("full_name"),
                rs.getString("phone_number"), ldt(rs, "date_of_birth"), rs.getString("consultant_person"));
    }

    public record StaffRow(String id, String code, String name, String jobPosition, boolean active) {
    }

    public List<StaffRow> staff() {
        return jdbc.query("""
                SELECT id, staff_code, full_name, job_position, active
                FROM staff WHERE deleted = false ORDER BY full_name
                """, (rs, n) -> new StaffRow(rs.getString("id"), rs.getString("staff_code"), rs.getString("full_name"),
                rs.getString("job_position"), rs.getBoolean("active")));
    }

    public record BranchRow(String id, String name) {
    }

    public List<BranchRow> branches() {
        return jdbc.query("SELECT id, name FROM branch ORDER BY name",
                (rs, n) -> new BranchRow(rs.getString("id"), rs.getString("name")));
    }
}
