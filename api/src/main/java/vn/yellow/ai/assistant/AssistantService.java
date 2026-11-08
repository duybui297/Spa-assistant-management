package vn.yellow.ai.assistant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.yellow.ai.commission.CommissionService;
import vn.yellow.ai.spa.SpaReader;
import vn.yellow.ai.spa.SpaReportReader;
import vn.yellow.ai.spa.SpaReportReader.Money;
import vn.yellow.ai.spa.SpaReportReader.OrderRow;

/**
 * Số liệu cho các màn assistant. Giai đoạn 1 không dùng AI/LLM (HANDOFF 7.12): mọi con số tính bằng công thức,
 * mọi câu chữ là mẫu câu cố định có điền số (7.14).
 */
@Service
public class AssistantService {

    static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SpaReader spa;
    private final SpaReportReader rep;
    private final ManualStore manual;
    private final CommissionService commission;

    public AssistantService(SpaReader spa, SpaReportReader rep, ManualStore manual, CommissionService commission) {
        this.spa = spa;
        this.rep = rep;
        this.manual = manual;
        this.commission = commission;
    }

    LocalDate today() {
        return commission.today();
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).map(AssistantService::nz).reduce(ZERO, BigDecimal::add);
    }

    static BigDecimal pctChange(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) return null;
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }

    /** Định dạng tiền kiểu "12,5 triệu" cho mẫu câu. */
    static String money(BigDecimal v) {
        BigDecimal a = nz(v);
        BigDecimal abs = a.abs();
        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            return a.divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString().replace('.', ',') + " tỷ";
        }
        if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return a.divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString().replace('.', ',') + " triệu";
        }
        return String.format("%,d đ", a.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', '.');
    }

    static String pct(BigDecimal p) {
        if (p == null) return "—";
        return (p.signum() > 0 ? "+" : "") + p.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    BigDecimal cashIn(LocalDate from, LocalDate toExcl) {
        return sum(rep.payments(from, toExcl), Money::amount)
                .subtract(sum(rep.refunds(from, toExcl), SpaReportReader.RefundRow::amount));
    }

    // =================================================================== HÔM NAY

    public record Kpi(String key, String label, BigDecimal value, BigDecimal compare, BigDecimal changePct, String note) {
    }

    public record Action(String title, String detail, String tab, String tone) {
    }

    public record HealthPart(String label, int score, int max, String note) {
    }

    public record Today(LocalDate date, List<Kpi> kpis, List<String> briefing, List<Action> actions,
                        int healthScore, List<HealthPart> health, List<SpaReportReader.AppointmentRow> appointments,
                        List<Map<String, Object>> birthdays, List<SpaReportReader.CardRow> expiringCards,
                        Map<String, Object> commission) {
    }

    public Today today(LocalDate d) {
        LocalDate t = d == null ? today() : d;
        LocalDate tomorrow = t.plusDays(1);
        LocalDate monthStart = t.withDayOfMonth(1);
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevSameDayExcl = prevMonthStart.plusDays(Math.min(t.getDayOfMonth(), prevMonthStart.lengthOfMonth()));

        BigDecimal cashToday = cashIn(t, tomorrow);
        BigDecimal cashYesterday = cashIn(t.minusDays(1), t);
        BigDecimal cashMonth = cashIn(monthStart, tomorrow);
        BigDecimal cashPrevMonth = cashIn(prevMonthStart, prevSameDayExcl);
        List<OrderRow> ordersToday = rep.orders(t, tomorrow);
        BigDecimal salesToday = sum(ordersToday, OrderRow::total);

        List<OrderRow> debts = rep.openDebts();
        BigDecimal receivable = sum(debts, OrderRow::debt);
        List<OrderRow> overdue = debts.stream()
                .filter(o -> o.date() != null && ChronoUnit.DAYS.between(o.date().toLocalDate(), t) > 30).toList();
        BigDecimal overdueAmt = sum(overdue, OrderRow::debt);

        List<SpaReportReader.AppointmentRow> appts = rep.appointments(t, t);
        long confirmed = appts.stream().filter(a -> "CONFIRMED".equals(a.status()) || "ARRIVED".equals(a.status())).count();

        List<SpaReportReader.CardRow> cards = rep.activeCards().stream()
                .filter(c -> c.expiryDate() != null && !c.expiryDate().toLocalDate().isBefore(t)
                        && ChronoUnit.DAYS.between(t, c.expiryDate().toLocalDate()) <= 30)
                .toList();

        List<Map<String, Object>> birthdays = new ArrayList<>();
        for (SpaReader.CustomerRow c : spa.allCustomers()) {
            if (c.dateOfBirth() == null) continue;
            MonthDay md = MonthDay.from(c.dateOfBirth());
            for (int i = 0; i <= 7; i++) {
                LocalDate dd = t.plusDays(i);
                if (MonthDay.from(dd).equals(md)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", c.id());
                    m.put("name", c.name());
                    m.put("phone", c.phone());
                    m.put("date", dd);
                    m.put("inDays", i);
                    birthdays.add(m);
                }
            }
        }
        birthdays.sort(Comparator.comparing(m -> (Integer) m.get("inDays")));

        CommissionService.Results cr = commission.latest();
        long mismatch = cr.rows().stream()
                .filter(r -> !"OK".equals(r.check().verdict()) && r.review() == null).count();
        BigDecimal mismatchAmt = cr.rows().stream()
                .filter(r -> !"OK".equals(r.check().verdict()) && r.review() == null)
                .map(r -> nz(r.check().diffAmount()).abs()).reduce(ZERO, BigDecimal::add);

        List<ManualStore.SupplierDebt> payables = manual.supplierDebts().stream()
                .filter(s -> s.outstanding().signum() > 0).toList();
        List<ManualStore.SupplierDebt> dueSoon = payables.stream()
                .filter(s -> s.dueDate() != null && !s.dueDate().isAfter(t.plusDays(7))).toList();
        BigDecimal dueSoonAmt = sum(dueSoon, ManualStore.SupplierDebt::outstanding);

        List<Kpi> kpis = List.of(
                new Kpi("cashToday", "Tiền thu hôm nay", cashToday, cashYesterday, pctChange(cashToday, cashYesterday),
                        "so với hôm qua"),
                new Kpi("cashMonth", "Tiền thu tháng này", cashMonth, cashPrevMonth, pctChange(cashMonth, cashPrevMonth),
                        "so với cùng kỳ tháng trước"),
                new Kpi("salesToday", "Doanh số đơn hôm nay", salesToday, null, null, ordersToday.size() + " đơn"),
                new Kpi("receivable", "Khách đang nợ", receivable, overdueAmt, null,
                        overdue.size() + " đơn nợ quá 30 ngày"));

        // ---- mẫu câu bản tin (7.14)
        List<String> brief = new ArrayList<>();
        brief.add(String.format("Hôm nay có %d lịch hẹn (%d đã xác nhận / đã đến).", appts.size(), confirmed));
        BigDecimal ch = pctChange(cashMonth, cashPrevMonth);
        brief.add(String.format("Từ đầu tháng đã thu %s, %s so với cùng kỳ tháng trước.", money(cashMonth),
                ch == null ? "chưa có số tháng trước để so" : (ch.signum() >= 0 ? "tăng " : "giảm ") + pct(ch.abs())
                        .replace("+", "")));
        if (receivable.signum() > 0) {
            brief.add(String.format("Khách đang nợ %s trên %d đơn; %s đã quá 30 ngày.", money(receivable), debts.size(),
                    money(overdueAmt)));
        }
        if (!cards.isEmpty()) {
            brief.add(String.format("%d thẻ liệu trình hết hạn trong 30 ngày tới mà khách vẫn còn buổi.", cards.size()));
        }
        if (!birthdays.isEmpty()) brief.add(String.format("%d khách sinh nhật trong 7 ngày tới.", birthdays.size()));
        if (cr.run() != null) {
            brief.add(String.format("Lần kiểm tra hoa hồng gần nhất: %d dòng lệch chưa xác nhận, chênh lệch %s.",
                    mismatch, money(mismatchAmt)));
        }

        // ---- 3 việc nên làm
        List<Action> actions = new ArrayList<>();
        if (!dueSoon.isEmpty()) {
            actions.add(new Action("Chuẩn bị " + money(dueSoonAmt) + " trả nhà cung cấp",
                    dueSoon.size() + " khoản đến hạn trong 7 ngày, ưu tiên khoản bắt buộc trước.", "debts", "bad"));
        }
        if (overdueAmt.signum() > 0) {
            actions.add(new Action("Nhắc " + overdue.size() + " khách nợ quá 30 ngày",
                    "Tổng " + money(overdueAmt) + ". Có sẵn mẫu tin nhắn Zalo ở màn Công nợ.", "debts", "warn"));
        }
        if (mismatch > 0) {
            actions.add(new Action("Duyệt " + mismatch + " dòng hoa hồng lệch luật",
                    "Chênh lệch " + money(mismatchAmt) + " so với bảng luật mới.", "commission", "warn"));
        }
        if (!cards.isEmpty()) {
            actions.add(new Action("Gọi " + cards.size() + " khách có thẻ sắp hết hạn",
                    "Đặt lịch để khách dùng hết buổi, tránh khiếu nại.", "leaks", "info"));
        }
        if (!birthdays.isEmpty()) {
            actions.add(new Action("Gửi lời chúc " + birthdays.size() + " khách sinh nhật",
                    "Kèm ưu đãi sinh nhật để kéo khách quay lại.", "today", "info"));
        }

        // ---- điểm sức khỏe (công thức cố định)
        List<HealthPart> parts = new ArrayList<>();
        int growth = ch == null ? 15 : ch.compareTo(BigDecimal.ZERO) >= 0 ? 25 : ch.compareTo(BigDecimal.valueOf(-15)) >= 0 ? 15 : 5;
        parts.add(new HealthPart("Tăng trưởng tiền thu", growth, 25, ch == null ? "chưa đủ dữ liệu" : pct(ch)));
        BigDecimal monthSales = sum(rep.orders(monthStart, tomorrow), OrderRow::total);
        BigDecimal ratio = monthSales.signum() == 0 ? ZERO : overdueAmt.divide(monthSales.max(BigDecimal.ONE), 3, RoundingMode.HALF_UP);
        int debtScore = ratio.compareTo(new BigDecimal("0.05")) <= 0 ? 25 : ratio.compareTo(new BigDecimal("0.15")) <= 0 ? 15 : 5;
        parts.add(new HealthPart("Nợ quá hạn", debtScore, 25, money(overdueAmt)));
        int commScore = cr.run() == null ? 15 : mismatch == 0 ? 25 : mismatch <= 5 ? 18 : 8;
        parts.add(new HealthPart("Hoa hồng đúng luật", commScore, 25, cr.run() == null ? "chưa kiểm tra" : mismatch + " dòng lệch"));
        int payScore = dueSoon.isEmpty() ? 25 : dueSoonAmt.compareTo(cashMonth.max(BigDecimal.ONE)) <= 0 ? 15 : 5;
        parts.add(new HealthPart("Khả năng trả NCC 7 ngày tới", payScore, 25, money(dueSoonAmt)));
        int score = parts.stream().mapToInt(HealthPart::score).sum();

        Map<String, Object> comm = new LinkedHashMap<>();
        comm.put("lastRunAt", cr.run() == null ? null : cr.run().finishedAt());
        comm.put("mismatch", mismatch);
        comm.put("mismatchAmount", mismatchAmt);

        return new Today(t, kpis, brief, actions.size() > 3 ? actions.subList(0, 3) : actions, score, parts, appts,
                birthdays, cards, comm);
    }

    // =================================================================== DÒNG TIỀN

    public record DayFlow(LocalDate date, BigDecimal in, BigDecimal out, BigDecimal balance, boolean forecast) {
    }

    public record Outflow(LocalDate date, String label, BigDecimal amount, String kind) {
    }

    public record Cashflow(BigDecimal openingBalance, List<DayFlow> days, List<Outflow> upcoming,
                           BigDecimal avgDailyIn, LocalDate firstNegative, BigDecimal minBalance, List<String> notes) {
    }

    public Cashflow cashflow(BigDecimal opening, int horizonDays) {
        LocalDate t = today();
        BigDecimal open = nz(opening);
        int horizon = Math.max(30, Math.min(horizonDays, 120));

        // tiền vào trung bình theo thứ trong tuần, 8 tuần gần nhất
        LocalDate histFrom = t.minusWeeks(8);
        Map<LocalDate, BigDecimal> histIn = new TreeMap<>();
        for (Money m : rep.payments(histFrom, t)) histIn.merge(m.at().toLocalDate(), nz(m.amount()), BigDecimal::add);
        for (SpaReportReader.RefundRow r : rep.refunds(histFrom, t)) histIn.merge(r.at().toLocalDate(), nz(r.amount()).negate(), BigDecimal::add);
        Map<DayOfWeek, BigDecimal> byDow = new EnumMap<>(DayOfWeek.class);
        for (var e : histIn.entrySet()) byDow.merge(e.getKey().getDayOfWeek(), e.getValue(), BigDecimal::add);
        Map<DayOfWeek, BigDecimal> avgDow = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek w : DayOfWeek.values()) {
            avgDow.put(w, nz(byDow.get(w)).divide(BigDecimal.valueOf(8), 0, RoundingMode.HALF_UP));
        }
        BigDecimal avg = avgDow.values().stream().reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP);

        // tiền ra dự kiến: chi phí định kỳ (lặp lại hằng tháng theo ngày lần gần nhất) + nợ NCC theo hạn
        List<Outflow> outs = new ArrayList<>();
        Map<String, ManualStore.Expense> lastRecurring = new LinkedHashMap<>();
        for (ManualStore.Expense e : manual.expenses(t.minusMonths(3), t.plusDays(1))) {
            if (!e.recurring()) continue;
            String key = e.category() + "|" + e.description();
            ManualStore.Expense prev = lastRecurring.get(key);
            if (prev == null || e.expenseDate().isAfter(prev.expenseDate())) lastRecurring.put(key, e);
        }
        LocalDate end = t.plusDays(horizon);
        for (ManualStore.Expense e : lastRecurring.values()) {
            LocalDate next = e.expenseDate().plusMonths(1);
            while (!next.isAfter(end)) {
                if (next.isAfter(t)) outs.add(new Outflow(next, e.category() + (e.description() == null ? "" : " · " + e.description()), e.amount(), "expense"));
                next = next.plusMonths(1);
            }
        }
        for (ManualStore.SupplierDebt s : manual.supplierDebts()) {
            if (s.outstanding().signum() <= 0) continue;
            LocalDate due = s.dueDate() == null || s.dueDate().isBefore(t.plusDays(1)) ? t.plusDays(1) : s.dueDate();
            if (!due.isAfter(end)) outs.add(new Outflow(due, "Trả " + s.supplierName() + (s.critical() ? " (bắt buộc)" : ""), s.outstanding(), "supplier"));
        }
        outs.sort(Comparator.comparing(Outflow::date));
        Map<LocalDate, BigDecimal> outByDay = outs.stream().collect(Collectors.groupingBy(Outflow::date, TreeMap::new,
                Collectors.reducing(ZERO, Outflow::amount, BigDecimal::add)));

        List<DayFlow> days = new ArrayList<>();
        // 30 ngày thực tế gần nhất (chỉ tiền vào; tiền ra chưa có nguồn số liệu thực)
        for (LocalDate d = t.minusDays(30); d.isBefore(t); d = d.plusDays(1)) {
            days.add(new DayFlow(d, nz(histIn.get(d)), ZERO, null, false));
        }
        BigDecimal bal = open;
        LocalDate firstNeg = null;
        BigDecimal min = open;
        for (LocalDate d = t.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal in = avgDow.get(d.getDayOfWeek());
            BigDecimal out = nz(outByDay.get(d));
            bal = bal.add(in).subtract(out);
            if (bal.compareTo(min) < 0) min = bal;
            if (firstNeg == null && bal.signum() < 0) firstNeg = d;
            days.add(new DayFlow(d, in, out, bal, true));
        }
        List<String> notes = new ArrayList<>();
        notes.add("Tiền vào dự báo = trung bình tiền thu theo thứ trong tuần của 8 tuần gần nhất (đã trừ hoàn tiền).");
        notes.add("Tiền ra dự báo = chi phí định kỳ nhập tay + nợ nhà cung cấp theo hạn trả. Phần mềm Spa không có số chi phí.");
        if (open.signum() == 0) notes.add("Chưa nhập số dư hiện tại — đường số dư đang bắt đầu từ 0.");
        if (firstNeg != null) {
            notes.add(String.format("Cảnh báo: số dư dự kiến âm từ ngày %s, thấp nhất %s.", firstNeg, money(min)));
        }
        return new Cashflow(open, days, outs, avg, firstNeg, min, notes);
    }

    // =================================================================== LỢI NHUẬN

    public record MonthPnl(String month, BigDecimal sales, BigDecimal refunds, BigDecimal commissions,
                           Map<String, BigDecimal> expenses, BigDecimal expenseTotal, BigDecimal profit,
                           boolean partial) {
    }

    public record Step(String label, BigDecimal delta) {
    }

    public record Profit(List<MonthPnl> months, List<Step> waterfall, String compareLabel, List<String> notes,
                         Map<String, BigDecimal> salesByCategory) {
    }

    public Profit profit(int monthsBack) {
        LocalDate t = today();
        YearMonth cur = YearMonth.from(t);
        int n = Math.max(2, Math.min(monthsBack, 12));
        List<MonthPnl> months = new ArrayList<>();
        Map<String, BigDecimal> catSales = new TreeMap<>();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = cur.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate toExcl = ym.plusMonths(1).atDay(1);
            if (toExcl.isAfter(t.plusDays(1))) toExcl = t.plusDays(1);
            BigDecimal sales = sum(rep.orders(from, toExcl), OrderRow::total);
            BigDecimal refunds = sum(rep.refunds(from, toExcl), SpaReportReader.RefundRow::amount);
            BigDecimal comm = sum(rep.commissions(from, toExcl), SpaReportReader.StaffCommission::amount);
            Map<String, BigDecimal> exp = new TreeMap<>();
            for (ManualStore.Expense e : manual.expenses(from, toExcl)) exp.merge(e.category(), e.amount(), BigDecimal::add);
            BigDecimal expTotal = exp.values().stream().reduce(ZERO, BigDecimal::add);
            months.add(new MonthPnl(ym.toString(), sales, refunds, comm, exp, expTotal,
                    sales.subtract(refunds).subtract(comm).subtract(expTotal), i == 0));
            if (i == 1) {
                for (SpaReportReader.ItemRow it : rep.items(from, toExcl)) {
                    String cat = it.categoryName() != null ? it.categoryName()
                            : "PRODUCT".equals(it.itemType()) ? "Sản phẩm" : "Khác";
                    catSales.merge(cat, nz(it.total()), BigDecimal::add);
                }
            }
        }
        // thác nước: tháng trước so với tháng trước nữa (2 tháng đủ ngày)
        MonthPnl a = months.get(months.size() - 3 >= 0 ? months.size() - 3 : 0);
        MonthPnl b = months.get(months.size() - 2);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Lãi " + a.month(), a.profit()));
        steps.add(new Step("Doanh số", b.sales().subtract(a.sales())));
        steps.add(new Step("Hoàn tiền", a.refunds().subtract(b.refunds())));
        steps.add(new Step("Hoa hồng", a.commissions().subtract(b.commissions())));
        java.util.Set<String> cats = new java.util.TreeSet<>(a.expenses().keySet());
        cats.addAll(b.expenses().keySet());
        for (String c : cats) {
            steps.add(new Step("Chi phí: " + c, nz(a.expenses().get(c)).subtract(nz(b.expenses().get(c)))));
        }
        steps.add(new Step("Lãi " + b.month(), b.profit()));
        List<String> notes = List.of(
                "Doanh số = tổng tiền đơn (sau giảm giá) theo ngày đơn, không tính đơn hủy / hoàn.",
                "Hoa hồng = số đã ghi trên đơn + hoa hồng check-in thẻ liệu trình.",
                "Chi phí lấy từ phần nhập tay. Tháng chưa nhập chi phí thì lãi đang bị tính cao hơn thực tế.");
        return new Profit(months, steps, b.month() + " so với " + a.month(), notes, catSales);
    }

    // =================================================================== CÔNG NỢ

    public record Receivable(String orderId, String orderCode, LocalDate orderDate, String customerId,
                             String customerName, String phone, BigDecimal total, BigDecimal paid, BigDecimal debt,
                             long ageDays, String bucket, String reminder) {
    }

    public record Payable(ManualStore.SupplierDebt debt, BigDecimal outstanding, long daysToDue, int priority,
                          String advice) {
    }

    public record Debts(List<Receivable> receivables, Map<String, BigDecimal> aging, BigDecimal receivableTotal,
                        List<Payable> payables, BigDecimal payableTotal, List<String> notes) {
    }

    static String bucket(long age) {
        return age <= 30 ? "0–30 ngày" : age <= 60 ? "31–60 ngày" : age <= 90 ? "61–90 ngày" : "> 90 ngày";
    }

    public Debts debts() {
        LocalDate t = today();
        List<Receivable> rec = new ArrayList<>();
        Map<String, BigDecimal> aging = new LinkedHashMap<>();
        for (String b : List.of("0–30 ngày", "31–60 ngày", "61–90 ngày", "> 90 ngày")) aging.put(b, ZERO);
        for (OrderRow o : rep.openDebts()) {
            long age = o.date() == null ? 0 : ChronoUnit.DAYS.between(o.date().toLocalDate(), t);
            String b = bucket(age);
            aging.merge(b, nz(o.debt()), BigDecimal::add);
            String msg = String.format(
                    "Dạ Yellow Beauty Center xin chào chị %s. Đơn %s ngày %s còn %s chưa thanh toán. "
                            + "Chị tiện thanh toán khi ghé lần tới hoặc chuyển khoản giúp em nhé. Em cảm ơn chị!",
                    o.customerName() == null ? "" : o.customerName(), o.code(),
                    o.date() == null ? "" : o.date().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    money(o.debt()));
            rec.add(new Receivable(o.id(), o.code(), o.date() == null ? null : o.date().toLocalDate(), o.customerId(),
                    o.customerName(), o.customerPhone(), o.total(), o.paid(), o.debt(), age, b, msg));
        }
        rec.sort(Comparator.comparingLong(Receivable::ageDays).reversed());
        BigDecimal recTotal = sum(rec, Receivable::debt);

        List<Payable> pay = new ArrayList<>();
        for (ManualStore.SupplierDebt s : manual.supplierDebts()) {
            BigDecimal out = s.outstanding();
            if (out.signum() <= 0) continue;
            long dd = s.dueDate() == null ? 999 : ChronoUnit.DAYS.between(t, s.dueDate());
            // ưu tiên: khoản bắt buộc → quá hạn → hạn gần → số nhỏ trả dứt điểm
            int prio = (s.critical() ? 0 : 1000) + (int) Math.max(-500, Math.min(dd, 500));
            String advice = dd < 0 ? "Đã quá hạn " + (-dd) + " ngày — trả ngay"
                    : dd <= 7 ? "Đến hạn trong " + dd + " ngày"
                    : s.critical() ? "Khoản bắt buộc, giữ đúng hạn" : "Có thể đàm phán giãn nếu thiếu tiền";
            pay.add(new Payable(s, out, dd, prio, advice));
        }
        pay.sort(Comparator.comparingInt(Payable::priority));
        BigDecimal payTotal = sum(pay, Payable::outstanding);
        return new Debts(rec, aging, recTotal, pay, payTotal, List.of(
                "Phải thu lấy từ orders.debt_amount của phần mềm Spa (đã trừ các lần khách trả nợ).",
                "Phải trả nhà cung cấp là số nhập tay, Spa không có dữ liệu này."));
    }

    // =================================================================== RÒ RỈ TIỀN

    public record Leak(String kind, String title, String detail, BigDecimal amount, String severity,
                       List<Map<String, Object>> rows) {
    }

    public record Leaks(LocalDate from, LocalDate to, List<Leak> items, BigDecimal total) {
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    public Leaks leaks(LocalDate from, LocalDate to) {
        LocalDate t = today();
        LocalDate f = from == null ? t.minusDays(30) : from;
        LocalDate e = to == null ? t : to;
        LocalDate toExcl = e.plusDays(1);
        List<Leak> items = new ArrayList<>();

        // 1. giảm giá bất thường (≥ 30% trên dòng)
        List<Map<String, Object>> disc = new ArrayList<>();
        BigDecimal discAmt = ZERO;
        for (SpaReportReader.ItemRow it : rep.items(f, toExcl)) {
            BigDecimal p = nz(it.discountPercent());
            BigDecimal gross = nz(it.unitPrice()).multiply(BigDecimal.valueOf(it.quantity()));
            BigDecimal dAmt = nz(it.discount());
            if (p.signum() == 0 && gross.signum() > 0) p = dAmt.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP);
            if (p.compareTo(BigDecimal.valueOf(30)) >= 0) {
                disc.add(m("orderCode", it.orderCode(), "date", it.orderDate(), "customer", it.customerName(),
                        "item", it.itemName(), "percent", p, "amount", dAmt));
                discAmt = discAmt.add(dAmt);
            }
        }
        items.add(new Leak("discount", "Giảm giá từ 30% trở lên", disc.size() + " dòng đơn giảm sâu trong kỳ", discAmt,
                disc.isEmpty() ? "ok" : "warn", disc));

        // 2. hoa hồng lệch luật (lần kiểm tra gần nhất)
        CommissionService.Results cr = commission.latest();
        List<Map<String, Object>> comm = new ArrayList<>();
        BigDecimal over = ZERO;
        for (var r : cr.rows()) {
            if (!"OVER".equals(r.check().verdict())) continue;
            comm.add(m("orderCode", r.check().orderCode(), "date", r.check().orderDate(), "staff", r.check().staffName(),
                    "item", r.check().serviceName(), "amount", r.check().diffAmount(),
                    "reviewed", r.review() != null));
            over = over.add(nz(r.check().diffAmount()));
        }
        items.add(new Leak("commission", "Hoa hồng ghi dư so với luật",
                cr.run() == null ? "Chưa bấm kiểm tra hoa hồng lần nào" : comm.size() + " dòng ghi dư ở lần kiểm tra gần nhất",
                over, comm.isEmpty() ? "ok" : "bad", comm));

        // 3. hoàn tiền
        List<SpaReportReader.RefundRow> refunds = rep.refunds(f, toExcl);
        items.add(new Leak("refund", "Hoàn tiền", refunds.size() + " lần hoàn tiền trong kỳ",
                sum(refunds, SpaReportReader.RefundRow::amount), refunds.isEmpty() ? "ok" : "warn",
                refunds.stream().map(r -> m("orderCode", r.orderCode(), "date", r.at(), "customer", r.customerName(),
                        "amount", r.amount(), "note", r.note())).toList()));

        // 4. lô hàng hết hạn / sắp hết hạn (60 ngày)
        List<Map<String, Object>> lots = new ArrayList<>();
        BigDecimal lotAmt = ZERO;
        for (SpaReportReader.StockLot l : rep.importLots()) {
            LocalDate exp = l.expiryDate().toLocalDate();
            long days = ChronoUnit.DAYS.between(t, exp);
            if (days > 60) continue;
            BigDecimal value = nz(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity()));
            lots.add(m("product", l.productName(), "expiry", exp, "days", days, "quantity", l.quantity(), "amount", value,
                    "expired", days < 0));
            lotAmt = lotAmt.add(value);
        }
        items.add(new Leak("stock", "Lô hàng hết hạn / sắp hết hạn (60 ngày)",
                lots.size() + " lô theo phiếu nhập; số lượng còn lại thực tế cần kiểm kho", lotAmt,
                lots.stream().anyMatch(x -> Boolean.TRUE.equals(x.get("expired"))) ? "bad" : lots.isEmpty() ? "ok" : "warn",
                lots));

        // 5. thẻ liệu trình sắp hết hạn còn buổi (rủi ro khiếu nại / phải gia hạn miễn phí)
        List<Map<String, Object>> cards = new ArrayList<>();
        BigDecimal cardAmt = ZERO;
        for (SpaReportReader.CardRow c : rep.activeCards()) {
            if (c.expiryDate() == null) continue;
            long days = ChronoUnit.DAYS.between(t, c.expiryDate().toLocalDate());
            if (days > 30) continue;
            int totalS = Math.max(1, c.totalSessions() + c.bonusSessions());
            BigDecimal value = nz(c.finalAmount()).multiply(BigDecimal.valueOf(c.remainingSessions()))
                    .divide(BigDecimal.valueOf(totalS), 0, RoundingMode.HALF_UP);
            cards.add(m("card", c.code(), "customer", c.customerName(), "phone", c.customerPhone(), "service", c.serviceName(),
                    "remaining", c.remainingSessions(), "expiry", c.expiryDate().toLocalDate(), "days", days, "amount", value));
            cardAmt = cardAmt.add(value);
        }
        items.add(new Leak("cards", "Thẻ liệu trình sắp hết hạn còn buổi", cards.size() + " thẻ hết hạn trong 30 ngày",
                cardAmt, cards.isEmpty() ? "ok" : "warn", cards));

        // 6. nợ khó đòi (> 60 ngày)
        List<Map<String, Object>> bad = new ArrayList<>();
        BigDecimal badAmt = ZERO;
        for (OrderRow o : rep.openDebts()) {
            long age = o.date() == null ? 0 : ChronoUnit.DAYS.between(o.date().toLocalDate(), t);
            if (age <= 60) continue;
            bad.add(m("orderCode", o.code(), "date", o.date(), "customer", o.customerName(), "amount", o.debt(), "days", age));
            badAmt = badAmt.add(nz(o.debt()));
        }
        items.add(new Leak("baddebt", "Nợ khách quá 60 ngày", bad.size() + " đơn có nguy cơ khó đòi", badAmt,
                bad.isEmpty() ? "ok" : "bad", bad));

        BigDecimal total = items.stream().map(Leak::amount).reduce(ZERO, BigDecimal::add);
        return new Leaks(f, e, items, total);
    }
}
