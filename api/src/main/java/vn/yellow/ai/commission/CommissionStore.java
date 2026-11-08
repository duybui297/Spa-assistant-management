package vn.yellow.ai.commission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.yellow.ai.commission.engine.CommissionRule;
import vn.yellow.ai.commission.engine.DefaultRules;
import vn.yellow.ai.commission.engine.RuleCondition;
import vn.yellow.ai.config.DataSourceConfig;

/** Đọc / ghi dữ liệu module hoa hồng trong schema spa_ai (kết nối có ghi). */
@Repository
public class CommissionStore {

    private static final Logger log = LoggerFactory.getLogger(CommissionStore.class);
    private static final TypeReference<List<RuleCondition>> CONDS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CommissionStore(JdbcTemplate jdbc, ObjectMapper json, DataSourceConfig.FlywayRunner migrated) {
        this.jdbc = jdbc;
        this.json = json;
        seedDefaults();
    }

    static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    static LocalDateTime ldt(ResultSet rs, String c) throws SQLException {
        Timestamp t = rs.getTimestamp(c);
        return t == null ? null : t.toLocalDateTime();
    }

    String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lần chạy đầu: nạp nhóm dịch vụ và bảng luật mặc định (HANDOFF 4.1). */
    private void seedDefaults() {
        Integer groups = jdbc.queryForObject("SELECT COUNT(*) FROM service_group_def", Integer.class);
        if (groups != null && groups == 0) {
            int i = 0;
            for (DefaultRules.Group g : DefaultRules.GROUPS) {
                jdbc.update("INSERT INTO service_group_def (code, label, unit, sort_order, active) VALUES (?, ?, ?, ?, ?)",
                        g.code(), g.label(), g.unit(), (i++) * 10, true);
            }
            log.info("Đã nạp {} nhóm dịch vụ mặc định", DefaultRules.GROUPS.size());
        }
        Integer rules = jdbc.queryForObject("SELECT COUNT(*) FROM commission_rule", Integer.class);
        if (rules != null && rules == 0) {
            for (CommissionRule r : DefaultRules.RULES) {
                insertRule(r, "system");
                history(r.id(), "CREATE", null, r, "Khởi tạo theo Bảng % nhân viên của khách", "system");
            }
            log.info("Đã nạp {} tiêu chí hoa hồng mặc định", DefaultRules.RULES.size());
        }
    }

    // ------------------------------------------------------------------ luật

    public List<CommissionRule> rules() {
        return jdbc.query("SELECT id, label, pct, active, priority, conditions_json FROM commission_rule ORDER BY priority, id",
                (rs, n) -> {
                    try {
                        return new CommissionRule(rs.getString("id"), rs.getString("label"), rs.getBigDecimal("pct"),
                                rs.getBoolean("active"), rs.getInt("priority"),
                                json.readValue(rs.getString("conditions_json"), CONDS));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("conditions_json hỏng ở tiêu chí " + rs.getString("id"), e);
                    }
                });
    }

    public Optional<CommissionRule> rule(String id) {
        return rules().stream().filter(r -> r.id().equals(id)).findFirst();
    }

    private void insertRule(CommissionRule r, String user) {
        jdbc.update("""
                INSERT INTO commission_rule (id, label, pct, active, priority, conditions_json, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                r.id(), r.label(), r.percent(), r.active(), r.priority(), toJson(r.conditions()), user, now());
    }

    @Transactional
    public void createRule(CommissionRule r, String user, String note) {
        insertRule(r, user);
        history(r.id(), "CREATE", null, r, note, user);
    }

    @Transactional
    public void updateRule(CommissionRule before, CommissionRule after, String user, String note) {
        jdbc.update("""
                UPDATE commission_rule SET label = ?, pct = ?, active = ?, priority = ?, conditions_json = ?,
                       updated_by = ?, updated_at = ? WHERE id = ?""",
                after.label(), after.percent(), after.active(), after.priority(), toJson(after.conditions()),
                user, now(), after.id());
        String type = before.active() != after.active() ? (after.active() ? "ENABLE" : "DISABLE") : "UPDATE";
        history(after.id(), type, before, after, note, user);
    }

    private void history(String ruleId, String type, Object before, Object after, String note, String user) {
        jdbc.update("""
                INSERT INTO commission_rule_history (id, rule_id, change_type, before_json, after_json, note, changed_by, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID().toString(), ruleId, type, before == null ? null : toJson(before),
                after == null ? null : toJson(after), note, user, now());
    }

    public record HistoryRow(String id, String ruleId, String changeType, String beforeJson, String afterJson,
                             String note, String changedBy, LocalDateTime changedAt) {
    }

    public List<HistoryRow> ruleHistory() {
        return jdbc.query("""
                SELECT id, rule_id, change_type, before_json, after_json, note, changed_by, changed_at
                FROM commission_rule_history ORDER BY changed_at DESC""",
                (rs, n) -> new HistoryRow(rs.getString("id"), rs.getString("rule_id"), rs.getString("change_type"),
                        rs.getString("before_json"), rs.getString("after_json"), rs.getString("note"),
                        rs.getString("changed_by"), ldt(rs, "changed_at")));
    }

    // ------------------------------------------------------------------ nhóm dịch vụ

    public record GroupDef(String code, String label, String unit, int sortOrder, boolean active) {
    }

    public List<GroupDef> groups() {
        return jdbc.query("SELECT code, label, unit, sort_order, active FROM service_group_def ORDER BY sort_order, code",
                (rs, n) -> new GroupDef(rs.getString("code"), rs.getString("label"), rs.getString("unit"),
                        rs.getInt("sort_order"), rs.getBoolean("active")));
    }

    public void upsertGroup(GroupDef g) {
        int n = jdbc.update("UPDATE service_group_def SET label = ?, unit = ?, sort_order = ?, active = ? WHERE code = ?",
                g.label(), g.unit(), g.sortOrder(), g.active(), g.code());
        if (n == 0) {
            jdbc.update("INSERT INTO service_group_def (code, label, unit, sort_order, active) VALUES (?, ?, ?, ?, ?)",
                    g.code(), g.label(), g.unit(), g.sortOrder(), g.active());
        }
    }

    public Map<String, String> groupMap() {
        Map<String, String> m = new HashMap<>();
        jdbc.query("SELECT service_id, group_code FROM service_group_map",
                rs -> {
                    m.put(rs.getString("service_id"), rs.getString("group_code"));
                });
        return m;
    }

    public void assignGroup(String serviceId, String groupCode, String user) {
        if (groupCode == null || groupCode.isBlank() || DefaultRules.GROUP_OTHER.equals(groupCode)) {
            jdbc.update("DELETE FROM service_group_map WHERE service_id = ?", serviceId);
            return;
        }
        int n = jdbc.update("UPDATE service_group_map SET group_code = ?, updated_by = ?, updated_at = ? WHERE service_id = ?",
                groupCode, user, now(), serviceId);
        if (n == 0) {
            jdbc.update("INSERT INTO service_group_map (service_id, group_code, updated_by, updated_at) VALUES (?, ?, ?, ?)",
                    serviceId, groupCode, user, now());
        }
    }

    // ------------------------------------------------------------------ lần chạy & kết quả

    public record Run(String id, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDate fromDate, LocalDate toDate,
                      int totalLines, int mismatchLines, String status, String errorMessage, String triggeredBy) {
    }

    public void startRun(String id, LocalDate from, LocalDate to, String user, String rulesSnapshot) {
        jdbc.update("""
                INSERT INTO commission_run (id, started_at, from_date, to_date, status, triggered_by, rules_snapshot)
                VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)""",
                id, now(), Date.valueOf(from), Date.valueOf(to), user, rulesSnapshot);
    }

    public void finishRun(String id, int total, int mismatch) {
        jdbc.update("UPDATE commission_run SET finished_at = ?, total_lines = ?, mismatch_lines = ?, status = 'DONE' WHERE id = ?",
                now(), total, mismatch, id);
    }

    public void failRun(String id, String error) {
        String e = error == null ? "" : error;
        jdbc.update("UPDATE commission_run SET finished_at = ?, status = 'FAILED', error_message = ? WHERE id = ?",
                now(), e.length() > 1900 ? e.substring(0, 1900) : e, id);
    }

    private static final String RUN_COLS =
            "id, started_at, finished_at, from_date, to_date, total_lines, mismatch_lines, status, error_message, triggered_by";

    private static Run run(ResultSet rs) throws SQLException {
        Date f = rs.getDate("from_date");
        Date t = rs.getDate("to_date");
        return new Run(rs.getString("id"), ldt(rs, "started_at"), ldt(rs, "finished_at"),
                f == null ? null : f.toLocalDate(), t == null ? null : t.toLocalDate(),
                rs.getInt("total_lines"), rs.getInt("mismatch_lines"), rs.getString("status"),
                rs.getString("error_message"), rs.getString("triggered_by"));
    }

    public Optional<Run> latestDoneRun() {
        return jdbc.query("SELECT " + RUN_COLS + " FROM commission_run WHERE status = 'DONE' ORDER BY started_at DESC",
                (rs, n) -> run(rs)).stream().findFirst();
    }

    public List<Run> runs() {
        List<Run> all = jdbc.query("SELECT " + RUN_COLS + " FROM commission_run ORDER BY started_at DESC",
                (rs, n) -> run(rs));
        return all.size() > 20 ? all.subList(0, 20) : all;
    }

    public record Check(
            String runId, String orderItemStaffId, String orderId, String orderCode, LocalDateTime orderDate,
            String branchId, String orderItemId, String itemType, String serviceId, String serviceName,
            String customerId, String customerName, String staffId, String staffName, String groupCode,
            @JsonProperty("isPackage") boolean isPackage, Integer sessions, Integer bonus, int visitNo, int quantity,
            BigDecimal listPrice, BigDecimal unitPrice, BigDecimal baseAmount, BigDecimal enteredAmount,
            BigDecimal enteredPct, BigDecimal expectedPct, BigDecimal expectedAmount, BigDecimal diffAmount,
            String verdict, String source, String ruleId, String reason) {
    }

    public void insertChecks(List<Check> checks) {
        String sql = """
                INSERT INTO commission_check (run_id, order_item_staff_id, order_id, order_code, order_date, branch_id,
                  order_item_id, item_type, service_id, service_name, customer_id, customer_name, staff_id, staff_name,
                  group_code, is_package, sessions, bonus, visit_no, quantity, list_price, unit_price, base_amount,
                  entered_amount, entered_pct, expected_pct, expected_amount, diff_amount, verdict, source, rule_id, reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        jdbc.batchUpdate(sql, checks, 200, (ps, c) -> {
            int i = 1;
            ps.setString(i++, c.runId());
            ps.setString(i++, c.orderItemStaffId());
            ps.setString(i++, c.orderId());
            ps.setString(i++, c.orderCode());
            ps.setTimestamp(i++, c.orderDate() == null ? null : Timestamp.valueOf(c.orderDate()));
            ps.setString(i++, c.branchId());
            ps.setString(i++, c.orderItemId());
            ps.setString(i++, c.itemType());
            ps.setString(i++, c.serviceId());
            ps.setString(i++, cut(c.serviceName(), 500));
            ps.setString(i++, c.customerId());
            ps.setString(i++, cut(c.customerName(), 255));
            ps.setString(i++, c.staffId());
            ps.setString(i++, cut(c.staffName(), 255));
            ps.setString(i++, c.groupCode());
            ps.setBoolean(i++, c.isPackage());
            if (c.sessions() == null) ps.setNull(i++, java.sql.Types.INTEGER); else ps.setInt(i++, c.sessions());
            if (c.bonus() == null) ps.setNull(i++, java.sql.Types.INTEGER); else ps.setInt(i++, c.bonus());
            ps.setInt(i++, c.visitNo());
            ps.setInt(i++, c.quantity());
            ps.setBigDecimal(i++, c.listPrice());
            ps.setBigDecimal(i++, c.unitPrice());
            ps.setBigDecimal(i++, c.baseAmount());
            ps.setBigDecimal(i++, c.enteredAmount());
            ps.setBigDecimal(i++, c.enteredPct());
            ps.setBigDecimal(i++, c.expectedPct());
            ps.setBigDecimal(i++, c.expectedAmount());
            ps.setBigDecimal(i++, c.diffAmount());
            ps.setString(i++, c.verdict());
            ps.setString(i++, c.source());
            ps.setString(i++, c.ruleId());
            ps.setString(i, cut(c.reason(), 2000));
        });
    }

    private static String cut(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    public List<Check> checks(String runId) {
        return jdbc.query("SELECT * FROM commission_check WHERE run_id = ? ORDER BY order_date DESC, order_code, order_item_id",
                (rs, n) -> new Check(rs.getString("run_id"), rs.getString("order_item_staff_id"), rs.getString("order_id"),
                        rs.getString("order_code"), ldt(rs, "order_date"), rs.getString("branch_id"),
                        rs.getString("order_item_id"), rs.getString("item_type"), rs.getString("service_id"),
                        rs.getString("service_name"), rs.getString("customer_id"), rs.getString("customer_name"),
                        rs.getString("staff_id"), rs.getString("staff_name"), rs.getString("group_code"),
                        rs.getBoolean("is_package"), rs.getObject("sessions", Integer.class),
                        rs.getObject("bonus", Integer.class), rs.getInt("visit_no"), rs.getInt("quantity"),
                        rs.getBigDecimal("list_price"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("base_amount"),
                        rs.getBigDecimal("entered_amount"), rs.getBigDecimal("entered_pct"),
                        rs.getBigDecimal("expected_pct"), rs.getBigDecimal("expected_amount"),
                        rs.getBigDecimal("diff_amount"), rs.getString("verdict"), rs.getString("source"),
                        rs.getString("rule_id"), rs.getString("reason")), runId);
    }

    // ------------------------------------------------------------------ chủ xác nhận

    public record Review(String orderItemStaffId, String status, String note, BigDecimal enteredAmount,
                         BigDecimal expectedAmount, String reviewedBy, LocalDateTime reviewedAt) {
    }

    public Map<String, Review> reviews() {
        Map<String, Review> m = new HashMap<>();
        jdbc.query("SELECT * FROM commission_review", rs -> {
            Review r = new Review(rs.getString("order_item_staff_id"), rs.getString("status"), rs.getString("note"),
                    rs.getBigDecimal("entered_amount"), rs.getBigDecimal("expected_amount"),
                    rs.getString("reviewed_by"), ldt(rs, "reviewed_at"));
            m.put(r.orderItemStaffId(), r);
        });
        return m;
    }

    @Transactional
    public void saveReview(Review r) {
        jdbc.update("DELETE FROM commission_review WHERE order_item_staff_id = ?", r.orderItemStaffId());
        if (r.status() == null) return; // bỏ xác nhận
        jdbc.update("""
                INSERT INTO commission_review (order_item_staff_id, status, note, entered_amount, expected_amount,
                  reviewed_by, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, ?)""",
                r.orderItemStaffId(), r.status(), r.note(), r.enteredAmount(), r.expectedAmount(), r.reviewedBy(), now());
    }
}
