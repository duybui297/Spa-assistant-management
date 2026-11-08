package vn.yellow.ai.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.yellow.ai.commission.CommissionStore;
import vn.yellow.ai.commission.engine.CommissionRule;
import vn.yellow.ai.commission.engine.DefaultRules;
import vn.yellow.ai.commission.engine.Evaluation;
import vn.yellow.ai.commission.engine.LineFacts;
import vn.yellow.ai.commission.engine.OldConfig;
import vn.yellow.ai.commission.engine.RuleEngine;
import vn.yellow.ai.config.AppProperties;

/**
 * V1: sinh dữ liệu GIẢ vào schema spa_mock (tên bảng / cột giống DB Spa). Không dùng dữ liệu thật của khách.
 * Dữ liệu tính theo ngày hiện tại, cố định hạt giống ngẫu nhiên nên lần nào sinh cũng giống nhau.
 * Đơn từ ngày áp dụng luật trở đi: hoa hồng Tư vấn đa số ghi đúng luật, ~15% cố tình ghi sai để demo cảnh báo.
 */
@Component
public class MockSeeder {

    private static final Logger log = LoggerFactory.getLogger(MockSeeder.class);

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final CommissionStore store;
    private final String s; // schema prefix

    public MockSeeder(JdbcTemplate jdbc, AppProperties props, CommissionStore store) {
        this.jdbc = jdbc;
        this.props = props;
        this.store = store;
        this.s = props.mock().schema() + ".";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.mock().enabled()) return;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + s + "orders", Integer.class);
        if (n != null && n > 0) return;
        seed();
    }

    public synchronized void reset() {
        if (!props.mock().enabled()) throw new IllegalStateException("Chỉ dùng được khi APP_MOCK_ENABLED=true");
        for (String t : List.of("order_item_staff", "order_items", "order_payments", "order_refunds", "debt_payment",
                "treatment_card_payment", "treatment_card_commission", "service_card", "orders", "commission",
                "service", "category", "staff", "customers", "branch", "warehouse_slip_item", "warehouse_slip",
                "warehouse_opening_stock", "product", "appointment")) {
            jdbc.update("DELETE FROM " + s + t);
        }
        seed();
    }

    // ================================================================== bộ ghi theo lô

    private final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
    private int seq = 0;

    private String id() {
        // UUID giả nhưng ổn định giữa các lần sinh
        return String.format("00000000-0000-4000-8000-%012d", ++seq);
    }

    private Map<String, Object> row(String table, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        // bỏ cột null (DB tự để NULL / mặc định) để không phải đoán kiểu khi gửi tham số null
        for (int i = 0; i < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        rows.computeIfAbsent(table, k -> new ArrayList<>()).add(m);
        return m;
    }

    private void flush() {
        for (var e : rows.entrySet()) {
            Map<String, List<Map<String, Object>>> byCols = new LinkedHashMap<>();
            for (Map<String, Object> r : e.getValue()) byCols.computeIfAbsent(String.join(",", r.keySet()), k -> new ArrayList<>()).add(r);
            for (var g : byCols.entrySet()) {
                List<String> cols = List.of(g.getKey().split(","));
                String sql = "INSERT INTO " + s + e.getKey() + " (" + g.getKey() + ") VALUES ("
                        + String.join(",", cols.stream().map(c -> "?").toList()) + ")";
                List<Object[]> args = g.getValue().stream().map(r -> cols.stream().map(r::get).toArray()).toList();
                jdbc.batchUpdate(sql, args);
            }
        }
        rows.clear();
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    // ================================================================== dữ liệu

    record Svc(String id, String code, String name, String group, long price, String cat, BigDecimal oldPct, Long oldCash) {
    }

    record Cust(String id, String code, String name, String phone) {
    }

    record Person(String id, String code, String name, String job) {
    }

    private static final String[] HO = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ"};
    private static final String[] DEM = {"Thị", "Ngọc", "Thu", "Minh", "Thanh", "Mỹ", "Bảo", "Khánh", "Hồng", "Phương"};
    private static final String[] TEN = {"Anh", "Hạnh", "Lan", "Mai", "Trang", "Hương", "Linh", "Thảo", "Vy", "Nhi", "Hà",
            "Yến", "Ngân", "Quỳnh", "Tâm", "Uyên", "Châu", "Diễm", "Giang", "Hiền"};

    private void seed() {
        long t0 = System.currentTimeMillis();
        Random rnd = new Random(20261108L);
        seq = 0;
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        LocalDate eff = props.commission().effectiveDay();
        LocalDateTime now = LocalDateTime.now(ZoneId.of(props.timezone()));

        // ---------------- chi nhánh, danh mục
        String br1 = id(), br2 = id();
        row("branch", "id", br1, "code", "CN1", "name", "Yellow Beauty Q.1", "active", true, "created_at", ts(now));
        row("branch", "id", br2, "code", "CN2", "name", "Yellow Beauty Q.7", "active", true, "created_at", ts(now));
        Map<String, String> cats = new LinkedHashMap<>();
        for (String c : List.of("Tiêm thẩm mỹ", "Laser công nghệ cao", "Chăm sóc da", "Triệt lông", "Tóc", "Thư giãn")) {
            String cid = id();
            cats.put(c, cid);
            row("category", "id", cid, "name", c, "code", "cat_" + cats.size(), "active", true, "priority_order", cats.size(),
                    "created_at", ts(now));
        }

        // ---------------- dịch vụ (giá gốc niêm yết) + % cấu hình cũ cho Tư vấn
        BigDecimal p3 = new BigDecimal("3");
        List<Svc> svcs = new ArrayList<>();
        Object[][] defs = {
                {"FILLER", "Filler Restylane (1cc)", 4_500_000L, "Tiêm thẩm mỹ", p3, null},
                {"BOTOX", "Botox Allergan (1 vùng)", 3_000_000L, "Tiêm thẩm mỹ", p3, null},
                {"MESO", "Meso căng bóng", 2_500_000L, "Tiêm thẩm mỹ", p3, null},
                {"PRP", "PRP trẻ hóa da", 3_500_000L, "Tiêm thẩm mỹ", p3, null},
                {"MESO_PRP", "Meso – PRP phục hồi", 3_000_000L, "Tiêm thẩm mỹ", p3, null},
                {"CFU", "CFU nâng cơ trẻ hóa", 12_000_000L, "Laser công nghệ cao", p3, null},
                {"LASER", "Laser Pico trẻ hóa", 1_500_000L, "Laser công nghệ cao", p3, null},
                {"LASER_SAC_TO", "Laser trị nám – sắc tố", 1_800_000L, "Laser công nghệ cao", p3, null},
                {"TRIET_LONG", "Triệt lông Diode toàn mặt", 800_000L, "Triệt lông", p3, null},
                {"SEO_RO", "Điều trị sẹo rỗ", 2_200_000L, "Laser công nghệ cao", p3, null},
                {"CSD", "Aquapeel làm sạch sâu", 700_000L, "Chăm sóc da", p3, null},
                {"LASER_FRACTION", "Laser Fraction CO2", 2_500_000L, "Laser công nghệ cao", p3, null},
                {"RF_NEEDLE", "RF Needle se khít lỗ chân lông", 3_000_000L, "Laser công nghệ cao", p3, null},
                {"TRICOPAT", "Tricopat kích thích mọc tóc", 1_200_000L, "Tóc", p3, null},
                {"KHAC", "Massage body thảo dược", 600_000L, "Thư giãn", new BigDecimal("5"), null},
                {"KHAC", "Gội đầu dưỡng sinh", 250_000L, "Thư giãn", null, 20_000L},
                {"KHAC", "Tắm trắng phi thuyền", 1_500_000L, "Chăm sóc da", new BigDecimal("4"), null},
        };
        int k = 0;
        for (Object[] d : defs) {
            String sid = id();
            Svc sv = new Svc(sid, String.format("DV-%05d", 10001 + k++), (String) d[1], (String) d[0], (Long) d[2],
                    (String) d[3], (BigDecimal) d[4], (Long) d[5]);
            svcs.add(sv);
            row("service", "id", sid, "service_code", sv.code(), "service_name", sv.name(), "category_id", cats.get(sv.cat()),
                    "price", vnd(sv.price()), "duration_minutes", 60, "blocked", false, "booking_online_active", true,
                    "ecommerce_active", false, "priority_order", k,
                    "commission_method", sv.oldCash() != null ? "FIXED_CASH_BONUS" : "COMMISSION_PERCENT",
                    "created_at", ts(now), "updated_at", ts(now));
            if (sv.oldPct() != null) {
                row("commission", "id", id(), "role", "CONSULTANT", "commission_type", "PERCENT_SERVICES_REVENUE",
                        "percentage_revenue", sv.oldPct(), "active", true, "service_id", sid, "created_at", ts(now),
                        "updated_at", ts(now));
            } else {
                row("commission", "id", id(), "role", "CONSULTANT", "commission_type", "CASH_BONUS",
                        "cash_bonus_amount", vnd(sv.oldCash()), "active", true, "service_id", sid, "created_at", ts(now),
                        "updated_at", ts(now));
            }
            row("commission", "id", id(), "role", "TECHNICIAN", "commission_type", "CASH_BONUS",
                    "cash_bonus_amount", vnd(Math.max(30_000, sv.price() / 50)), "active", true, "service_id", sid,
                    "created_at", ts(now), "updated_at", ts(now));
        }
        // app AI: gán nhóm cho dịch vụ mẫu (thực tế admin gán trên màn Nhóm dịch vụ)
        if (store.groupMap().isEmpty()) {
            for (Svc sv : svcs) store.assignGroup(sv.id(), sv.group(), "mock");
        }

        // ---------------- nhân viên
        List<Person> consultants = new ArrayList<>();
        List<Person> doers = new ArrayList<>();
        String[][] staff = {
                {"Ngô Thị Thu Trang", "CONSULTANT"}, {"Lý Bảo Ngọc", "CONSULTANT"}, {"Trịnh Mỹ Duyên", "CONSULTANT"},
                {"Đinh Khánh Linh", "CONSULTANT"}, {"BS. Nguyễn Hồng Hạnh", "DOCTOR"}, {"BS. Lê Minh Quân", "DOCTOR"},
                {"Phạm Thị Hoa", "TECHNICIAN"}, {"Võ Ngọc Hân", "TECHNICIAN"}, {"Trần Thị Bích", "TECHNICIAN"},
                {"Mai Thanh Thảo", "RECEPTIONIST"}};
        k = 0;
        for (String[] st : staff) {
            Person pp = new Person(id(), String.format("NV-%04d", ++k), st[0], st[1]);
            if ("CONSULTANT".equals(st[1])) consultants.add(pp);
            if ("DOCTOR".equals(st[1]) || "TECHNICIAN".equals(st[1])) doers.add(pp);
            row("staff", "version", 0L, "id", pp.id(), "staff_code", pp.code(), "full_name", pp.name(),
                    "phone_number", "09" + (10000000 + rnd.nextInt(89999999)), "job_position", pp.job(),
                    "booking_online", true, "active", true, "role", "USER", "created_date", ts(now), "deleted", false);
        }

        // ---------------- khách hàng (một vài người sinh nhật trong tuần này)
        List<Cust> custs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String name = HO[rnd.nextInt(HO.length)] + " " + DEM[rnd.nextInt(DEM.length)] + " " + TEN[rnd.nextInt(TEN.length)];
            Cust c = new Cust(id(), String.format("KH-%05d", 20001 + i), name, "09" + (10000000 + rnd.nextInt(89999999)));
            custs.add(c);
            LocalDate dob = i < 4 ? today.plusDays(i * 2L).minusYears(25 + i) : LocalDate.of(1975 + rnd.nextInt(30), 1 + rnd.nextInt(12), 1 + rnd.nextInt(28));
            Person cons = consultants.get(i % consultants.size());
            row("customers", "id", c.id(), "customer_code", c.code(), "full_name", c.name(), "phone_number", c.phone(),
                    "date_of_birth", ts(dob.atStartOfDay()), "gender", "Nữ", "consultant_person", cons.name(),
                    "consultant_id", cons.id(), "branch_id", i % 3 == 0 ? br2 : br1, "status", "ACTIVE",
                    "join_date", ts(today.minusDays(400 - i * 3L).atStartOfDay()), "created_date", ts(now), "deleted", false);
        }

        // ---------------- đơn hàng 180 ngày gần nhất
        List<CommissionRule> rules = DefaultRules.RULES;
        Map<String, Integer> visits = new HashMap<>();
        int orderNo = 0;
        int cardNo = 0;
        LocalDate start = today.minusDays(180);
        List<String[]> openCards = new ArrayList<>(); // [cardId, customerId, serviceName, remaining]
        String[] methods = {"CASH", "TRANSFER", "TRANSFER", "VISA_MASTER", "MOMO"};

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            int nOrders = 2 + rnd.nextInt(4) + (day.getDayOfWeek().getValue() >= 6 ? 2 : 0);
            List<LocalDateTime> times = new ArrayList<>();
            for (int o = 0; o < nOrders; o++) times.add(day.atTime(9 + rnd.nextInt(10), rnd.nextInt(60)));
            times.sort(null); // đơn sinh theo đúng thứ tự thời gian, khớp cách engine đếm "lần thứ mấy"
            for (LocalDateTime when : times) {
                Cust c = custs.get((int) Math.floor(Math.pow(rnd.nextDouble(), 1.6) * custs.size()));
                if (when.isAfter(now)) continue;
                String oid = id();
                String ocode = String.format("DH-%05d", 30001 + orderNo++);
                Person cons = consultants.get(rnd.nextInt(consultants.size()));
                String branch = rnd.nextInt(3) == 0 ? br2 : br1;
                int nItems = 1 + (rnd.nextInt(10) < 3 ? 1 : 0);
                long sub = 0;
                List<Runnable> itemRows = new ArrayList<>();
                int[] itemIdx = {0};
                // trạng thái quyết định trước: đơn hủy không được tính vào "lần thứ mấy"
                int st = rnd.nextInt(100);
                String status = st < 3 ? "CANCELLED" : st < 15 ? "PARTIAL_PAID" : st < 19 ? "DEBT" : "PAID";

                // check-in thẻ liệu trình (đơn 0đ)
                if (!openCards.isEmpty() && rnd.nextInt(6) == 0) {
                    String[] card = openCards.get(rnd.nextInt(openCards.size()));
                    int remaining = Integer.parseInt(card[3]);
                    if (remaining > 0) {
                        card[3] = String.valueOf(remaining - 1);
                        String iid = id();
                        row("orders", "id", oid, "order_code", ocode, "customer_id", card[1], "customer_name", card[5],
                                "customer_type", "REGULAR", "order_date", ts(when), "sub_total", vnd(0), "total_amount", vnd(0),
                                "paid_amount", vnd(0), "debt_amount", vnd(0), "status", "COMPLETED", "branch_id", branch,
                                "created_by", "letan", "created_at", ts(when), "updated_at", ts(when), "deleted", false);
                        row("order_items", "id", iid, "order_id", oid, "item_type", "TREATMENT_CARD_USAGE", "item_id", card[4],
                                "item_name", card[2] + " (check-in)", "quantity", 1, "unit_price", vnd(0), "total_amount", vnd(0),
                                "tc_card_id", card[0], "created_at", ts(when));
                        row("treatment_card_payment", "id", id(), "card_id", card[0], "payment_code",
                                String.format("CI-%05d", orderNo), "order_id", oid, "sessions_used", 1, "created_by", "letan",
                                "created_at", ts(when));
                        Person doer = doers.get(rnd.nextInt(doers.size()));
                        row("treatment_card_commission", "id", id(), "card_id", card[0], "staff_id", doer.id(),
                                "staff_name", doer.name(), "staff_role", "Thực hiện", "commission_amount", vnd(50_000),
                                "created_at", ts(when));
                        continue;
                    }
                }

                for (int it = 0; it < nItems; it++) {
                    Svc sv = svcs.get(rnd.nextInt(svcs.size()));
                    boolean pkg = false;
                    int sessions = 0, bonus = 0, qty = 1;
                    // gói theo đúng các mẫu trong bảng luật, cùng vài gói "lệch số buổi"
                    switch (sv.group()) {
                        case "LASER" -> { if (rnd.nextInt(3) == 0) { pkg = true; sessions = 10; bonus = rnd.nextInt(5) == 0 ? 1 : 2; } }
                        case "LASER_SAC_TO" -> { if (rnd.nextInt(2) == 0) { pkg = true; sessions = rnd.nextBoolean() ? 5 : 10; bonus = sessions == 5 ? 1 : 2; } }
                        case "TRIET_LONG" -> { pkg = rnd.nextInt(3) > 0; sessions = 5; bonus = 1; }
                        case "MESO_PRP" -> { if (rnd.nextInt(2) == 0) { pkg = true; sessions = 5; bonus = 1; } }
                        case "SEO_RO" -> { pkg = rnd.nextInt(3) > 0; sessions = rnd.nextBoolean() ? 5 : 3; bonus = 1; }
                        case "CSD" -> { pkg = rnd.nextInt(2) == 0; sessions = 5; bonus = rnd.nextInt(4) == 0 ? 0 : 1; }
                        case "LASER_FRACTION", "RF_NEEDLE" -> { pkg = rnd.nextInt(2) == 0; sessions = 3; bonus = 1; }
                        case "FILLER" -> qty = 1 + rnd.nextInt(3);
                        case "BOTOX" -> qty = 1 + rnd.nextInt(3);
                        default -> { }
                    }
                    long unitPrice = pkg ? Math.round(sv.price() * sessions * (0.8 + rnd.nextInt(3) * 0.05) / 1000.0) * 1000
                            : sv.price();
                    long discountPct = rnd.nextInt(12) == 0 ? 30 + rnd.nextInt(3) * 10 : (rnd.nextInt(4) == 0 ? 10 : 0);
                    long lineGross = unitPrice * qty;
                    long lineDisc = lineGross * discountPct / 100;
                    long lineTotal = lineGross - lineDisc;
                    sub += lineTotal;

                    String vKey = c.id() + "|" + sv.group();
                    int visit = "CANCELLED".equals(status) ? visits.getOrDefault(vKey, 0) + 1 : visits.merge(vKey, 1, Integer::sum);

                    // tiền hoa hồng Tư vấn đã ghi
                    BigDecimal base = RuleEngine.base(pkg, vnd(sv.price()), vnd(unitPrice), qty, pkg ? sessions : null,
                            props.commission().packageBase());
                    OldConfig old = sv.oldPct() != null ? OldConfig.percent(sv.oldPct()) : OldConfig.cash(vnd(sv.oldCash()));
                    BigDecimal entered;
                    if (day.isBefore(eff)) {
                        // trước ngày áp dụng: cách tính cũ (FE tính trên giá bán)
                        entered = sv.oldPct() != null ? RuleEngine.amount(vnd(lineGross), sv.oldPct()) : vnd(sv.oldCash() * qty);
                    } else {
                        LineFacts f = new LineFacts(sv.group(), sv.group(), "", pkg, pkg ? sessions : null, pkg ? bonus : null,
                                visit, qty);
                        Evaluation e = RuleEngine.evaluate(f, rules, old, base);
                        entered = e.expectedAmount();
                        int err = rnd.nextInt(100);
                        if (err < 6) entered = RuleEngine.amount(vnd(lineGross), new BigDecimal("3"));        // quen dùng % cũ
                        else if (err < 10) entered = RuleEngine.amount(base, new BigDecimal("2"));            // chọn nhầm mức 2%
                        else if (err < 13) entered = RuleEngine.amount(base, new BigDecimal("0.5"));          // nhầm thành nhắc lại
                        else if (err < 15) entered = entered.add(vnd(100_000));                               // gõ nhầm số
                        else if (err < 16) entered = entered.add(vnd(500));                                   // lệch nhỏ trong ngưỡng
                    }

                    final boolean fPkg = pkg;
                    final int fSessions = sessions, fBonus = bonus, fQty = qty;
                    final long fUnit = unitPrice, fDisc = lineDisc, fDiscPct = discountPct, fTotal = lineTotal;
                    final BigDecimal fEntered = entered;
                    final Svc fSv = sv;
                    final LocalDate fDay = day;
                    final int fCard = pkg ? ++cardNo : 0;
                    itemRows.add(() -> {
                        String iid = id();
                        String cardId = fPkg ? id() : null;
                        row("order_items", "id", iid, "order_id", oid, "item_type", fPkg ? "TREATMENT_CARD" : "SERVICE",
                                "item_id", fSv.id(), "item_name", fPkg ? fSv.name() + " – gói " + fSessions + "+" + fBonus : fSv.name(),
                                "quantity", fQty, "unit_price", vnd(fUnit), "vat", vnd(0),
                                "item_discount_percent", BigDecimal.valueOf(fDiscPct), "discount", vnd(fDisc),
                                "total_amount", vnd(fTotal), "is_treatment_card", fPkg,
                                "tc_total_sessions", fPkg ? fSessions : null, "tc_bonus_sessions", fPkg ? fBonus : 0,
                                "tc_unlimited", false, "tc_expiry_date", fPkg ? Date.valueOf(fDay.plusMonths(12)) : null,
                                "tc_card_id", cardId, "created_at", ts(when.plusSeconds(itemIdx[0]++)));
                        row("order_item_staff", "id", id(), "order_item_id", iid, "order_id", oid, "staff_id", cons.id(),
                                "staff_name", cons.name(), "staff_role", "Tư vấn", "commission_amount", fEntered,
                                "created_at", ts(when));
                        Person doer = doers.get(Math.abs(iid.hashCode()) % doers.size());
                        row("order_item_staff", "id", id(), "order_item_id", iid, "order_id", oid, "staff_id", doer.id(),
                                "staff_name", doer.name(), "staff_role", "Thực hiện",
                                "commission_amount", vnd(Math.max(30_000, fSv.price() / 50) * fQty), "created_at", ts(when));
                        if (fPkg) {
                            int total = fSessions + fBonus;
                            int used = Math.min(total, (int) (java.time.temporal.ChronoUnit.DAYS.between(fDay, today) / 21));
                            LocalDate expiry = fCard % 9 == 0 ? today.plusDays(10 + fCard % 20) : fDay.plusMonths(12);
                            row("service_card", "id", cardId, "customer_id", c.id(), "customer_name", c.name(),
                                    "customer_phone", c.phone(), "customer_code", c.code(), "service_id", fSv.id(),
                                    "service_name", fSv.name(), "order_id", oid, "order_code", ocode,
                                    "total_sessions", fSessions, "bonus_sessions", fBonus, "used_sessions", used,
                                    "remaining_sessions", total - used, "price", vnd(fUnit), "total_amount", vnd(fUnit * fQty),
                                    "discount", vnd(fDisc), "final_amount", vnd(fTotal), "purchase_date", ts(when),
                                    "expiry_date", ts(expiry.atStartOfDay()), "card_code", String.format("TLT-%05d", fCard),
                                    "active", true, "created_by", "letan", "created_at", ts(when), "updated_at", ts(when));
                            if (total - used > 0) openCards.add(new String[]{cardId, c.id(), fSv.name(), String.valueOf(total - used), fSv.id(), c.name()});
                        }
                    });
                }

                // trạng thái, thanh toán, công nợ
                long paid = switch (status) {
                    case "PARTIAL_PAID" -> Math.round(sub * (0.3 + rnd.nextInt(5) * 0.1) / 1000.0) * 1000;
                    case "PAID" -> sub;
                    default -> 0;
                };
                long debt = "CANCELLED".equals(status) ? 0 : sub - paid;
                Map<String, Object> orderRow = row("orders", "id", oid, "order_code", ocode, "customer_id", c.id(), "customer_name", c.name(),
                        "customer_phone", c.phone(), "customer_code", c.code(), "customer_type", "REGULAR",
                        "order_date", ts(when), "sub_total", vnd(sub), "discount_percent", BigDecimal.ZERO, "vat", vnd(0),
                        "discount", vnd(0), "total_amount", vnd(sub), "paid_amount", vnd(paid), "debt_amount", vnd(debt),
                        "payment_method", methods[rnd.nextInt(methods.length)], "status", status, "branch_id", branch,
                        "created_by", "letan", "created_at", ts(when), "updated_at", ts(when), "deleted", false);
                itemRows.forEach(Runnable::run);
                if (paid > 0) {
                    row("order_payments", "id", id(), "order_id", oid, "paid_amount", vnd(paid), "remaining_amount", vnd(debt),
                            "payment_method", methods[rnd.nextInt(methods.length)], "paid_at", ts(when), "created_by", "letan",
                            "created_at", ts(when));
                }
                // trả nợ sau đó cho một phần đơn nợ cũ
                if (debt > 0 && day.isBefore(today.minusDays(20)) && rnd.nextInt(2) == 0) {
                    LocalDateTime later = when.plusDays(5 + rnd.nextInt(15));
                    // giống DebtService của Spa: ghi debt_payment + order_payments, cập nhật paid/debt của đơn
                    row("debt_payment", "id", id(), "order_id", oid, "customer_id", c.id(), "amount", vnd(debt),
                            "payment_method", "TRANSFER", "payment_date", ts(later), "created_by", "letan", "created_at", ts(later));
                    row("order_payments", "id", id(), "order_id", oid, "paid_amount", vnd(debt), "remaining_amount", vnd(0),
                            "payment_method", "TRANSFER", "paid_at", ts(later), "created_by", "letan", "created_at", ts(later));
                    orderRow.put("paid_amount", vnd(sub));
                    orderRow.put("debt_amount", vnd(0));
                    orderRow.put("status", "PAID");
                }
                if ("PAID".equals(status) && rnd.nextInt(60) == 0) {
                    row("order_refunds", "id", id(), "order_id", oid, "refund_amount", vnd(Math.round(sub * 0.5 / 1000.0) * 1000),
                            "refund_date", ts(when.plusDays(2)), "note", "Khách không hài lòng, hoàn 50%", "created_by", "quanly",
                            "created_at", ts(when.plusDays(2)));
                }
            }
        }

        // ---------------- kho: sản phẩm, tồn đầu kỳ, phiếu nhập có hạn dùng
        String[][] prods = {{"Kem chống nắng SPF50", "380000"}, {"Serum B5 phục hồi", "650000"}, {"Mặt nạ collagen", "120000"},
                {"Sữa rửa mặt dịu nhẹ", "290000"}, {"Ống Filler Restylane", "2200000"}, {"Lọ Botox 100UI", "1500000"}};
        k = 0;
        for (String[] pr : prods) {
            String pid = id();
            String pcode = String.format("SP-%04d", ++k);
            row("product", "id", pid, "product_code", pcode, "name", pr[0], "quantity", 0L, "price", new BigDecimal(pr[1]),
                    "supplier", k <= 4 ? "Công ty Mỹ phẩm An Nhiên" : "Dược phẩm Thiên Phúc", "active", true,
                    "created_date", ts(now), "deleted", false);
            row("warehouse_opening_stock", "id", id(), "product_id", pid, "product_code", pcode, "quantity", 10 + rnd.nextInt(20),
                    "period_date", ts(start.atStartOfDay()), "created_by", "kho", "created_at", ts(now));
            String slip = id();
            LocalDateTime imp = start.plusDays(30 + k * 10L).atTime(10, 0);
            row("warehouse_slip", "id", slip, "slip_code", String.format("PN-%04d", k), "slip_type", "IMPORT",
                    "transaction_date", ts(imp), "status", "COMPLETED", "created_by", "kho", "created_at", ts(imp), "deleted", false);
            LocalDate exp = k % 3 == 0 ? today.minusDays(5) : (k % 3 == 1 ? today.plusDays(25) : today.plusMonths(10));
            row("warehouse_slip_item", "id", id(), "slip_id", slip, "product_id", pid, "product_code", pcode,
                    "product_name", pr[0], "quantity", 20, "unit_price", new BigDecimal(pr[1]).multiply(new BigDecimal("0.55"))
                            .setScale(0, RoundingMode.HALF_UP), "expiry_date", ts(exp.atStartOfDay()));
            String out = id();
            row("warehouse_slip", "id", out, "slip_code", String.format("PX-%04d", k), "slip_type", "EXPORT",
                    "transaction_date", ts(imp.plusDays(20)), "status", "COMPLETED", "created_by", "kho",
                    "created_at", ts(imp.plusDays(20)), "deleted", false);
            row("warehouse_slip_item", "id", id(), "slip_id", out, "product_id", pid, "product_code", pcode,
                    "product_name", pr[0], "quantity", 5 + rnd.nextInt(15));
        }

        // ---------------- lịch hẹn hôm nay và 7 ngày tới
        String[] statuses = {"CONFIRMED", "CONFIRMED", "UNCONFIRMED", "ONLINE_BOOKING"};
        k = 0;
        for (int d = -2; d <= 7; d++) {
            LocalDate day = today.plusDays(d);
            int n = d == 0 ? 9 : 3 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                Cust c = custs.get(rnd.nextInt(custs.size()));
                Svc sv = svcs.get(rnd.nextInt(svcs.size()));
                Person doer = doers.get(rnd.nextInt(doers.size()));
                String time = String.format("%02d:%02d", 9 + rnd.nextInt(10), rnd.nextBoolean() ? 0 : 30);
                String st = d < 0 ? (rnd.nextInt(4) == 0 ? "NO_SHOW" : "ARRIVED") : statuses[rnd.nextInt(statuses.length)];
                row("appointment", "id", id(), "appointment_code", String.format("LH%05d", ++k), "customer_id", c.id(),
                        "customer_name", c.name(), "date", day.toString(), "appointment_time", time, "duration", 60,
                        "phone", c.phone(), "branch", "Yellow Beauty Q.1", "service", sv.name(), "performed_by", doer.name(),
                        "status", st, "assigned_staff_id", doer.id(), "created_by", "letan", "created_at", ts(now));
            }
        }

        flush();
        seedManual(today);
        log.info("Đã sinh dữ liệu giả spa_mock: {} đơn, {} ms", orderNo, System.currentTimeMillis() - t0);
    }

    /** Dữ liệu nhập tay mẫu trong spa_ai (chi phí, nợ nhà cung cấp) để các màn Lợi nhuận / Công nợ có số. */
    private void seedManual(LocalDate today) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM manual_expense", Integer.class);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (n != null && n == 0) {
            Object[][] monthly = {{"Mặt bằng", "Thuê mặt bằng 2 chi nhánh (mẫu)", 120_000_000L},
                    {"Lương", "Lương nhân viên (mẫu)", 260_000_000L},
                    {"Điện nước", "Điện, nước, internet (mẫu)", 18_000_000L},
                    {"Marketing", "Quảng cáo Facebook / TikTok (mẫu)", 45_000_000L},
                    {"Vật tư", "Vật tư tiêu hao (mẫu)", 35_000_000L}};
            for (int m = 5; m >= 0; m--) {
                LocalDate d = today.minusMonths(m).withDayOfMonth(5);
                if (d.isAfter(today)) continue;
                for (Object[] e : monthly) {
                    jdbc.update("INSERT INTO manual_expense (id, expense_date, category, description, amount, recurring, created_by, created_at) VALUES (?,?,?,?,?,?,?,?)",
                            java.util.UUID.randomUUID().toString(), Date.valueOf(d), e[0], e[1],
                            vnd((Long) e[2] + (m * 1_500_000L)), true, "mock", now);
                }
            }
        }
        Integer dn = jdbc.queryForObject("SELECT COUNT(*) FROM supplier_debt", Integer.class);
        if (dn != null && dn == 0) {
            Object[][] debts = {{"Dược phẩm Thiên Phúc", "Filler + Botox lô tháng này (mẫu)", 185_000_000L, 60_000_000L, 5, true},
                    {"Công ty Mỹ phẩm An Nhiên", "Mỹ phẩm bán lẻ (mẫu)", 42_000_000L, 0L, 18, false},
                    {"Thiết bị Laser Việt", "Trả góp máy Pico kỳ này (mẫu)", 95_000_000L, 0L, -3, true},
                    {"Vật tư y tế Minh Khang", "Kim, găng, bông (mẫu)", 12_500_000L, 0L, 30, false}};
            for (Object[] d : debts) {
                jdbc.update("INSERT INTO supplier_debt (id, supplier_name, description, amount, paid_amount, invoice_date, due_date, critical, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        java.util.UUID.randomUUID().toString(), d[0], d[1], vnd((Long) d[2]), vnd((Long) d[3]),
                        Date.valueOf(today.minusDays(20)), Date.valueOf(today.plusDays((Integer) d[4])), d[5], "mock", now);
            }
        }
    }
}
