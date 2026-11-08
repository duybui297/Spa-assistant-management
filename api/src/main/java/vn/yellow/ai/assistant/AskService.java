package vn.yellow.ai.assistant;

import static vn.yellow.ai.assistant.AssistantService.money;
import static vn.yellow.ai.assistant.AssistantService.nz;
import static vn.yellow.ai.assistant.AssistantService.sum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import vn.yellow.ai.commission.CommissionService;
import vn.yellow.ai.common.ApiException;
import vn.yellow.ai.spa.SpaReader;
import vn.yellow.ai.spa.SpaReportReader;

/**
 * "Hỏi trợ lý" giai đoạn 1 (HANDOFF 7.13): bộ câu hỏi chọn sẵn, dropdown nhiều bước
 * (muốn xem gì → của ai / cái gì → khoảng thời gian → kết quả). Câu trả lời là mẫu câu cố định có điền số.
 * Danh sách câu hỏi là bản đề xuất, cần chốt với khách. Sau này có thể gắn LLM ở lớp này mà không đổi phần tính toán.
 */
@Service
public class AskService {

    private final SpaReader spa;
    private final SpaReportReader rep;
    private final CommissionService commission;
    private final AssistantService assistant;

    public AskService(SpaReader spa, SpaReportReader rep, CommissionService commission, AssistantService assistant) {
        this.spa = spa;
        this.rep = rep;
        this.commission = commission;
        this.assistant = assistant;
    }

    /** entity: none | customer | staff | service; needsPeriod: có hỏi khoảng thời gian không. */
    public record Question(String id, String group, String label, String entity, boolean needsPeriod) {
    }

    public static final List<Question> CATALOG = List.of(
            new Question("REVENUE_SUMMARY", "Doanh thu", "Doanh số và tiền thu trong kỳ", "none", true),
            new Question("REVENUE_BY_CATEGORY", "Doanh thu", "Doanh số theo nhóm dịch vụ", "none", true),
            new Question("TOP_SERVICES", "Doanh thu", "Dịch vụ bán chạy nhất", "none", true),
            new Question("SERVICE_DETAIL", "Doanh thu", "Một dịch vụ bán được bao nhiêu, cho ai", "service", true),
            new Question("TOP_CUSTOMERS", "Khách hàng", "Khách chi tiêu nhiều nhất", "none", true),
            new Question("CUSTOMER_PROFILE", "Khách hàng", "Hồ sơ một khách: đã làm gì, còn nợ, còn thẻ", "customer", false),
            new Question("LAPSED_CUSTOMERS", "Khách hàng", "Khách lâu chưa quay lại (trên 60 ngày)", "none", false),
            new Question("DEBTORS", "Công nợ", "Khách đang nợ", "none", false),
            new Question("EXPIRING_CARDS", "Thẻ liệu trình", "Thẻ sắp hết hạn mà còn buổi (30 ngày)", "none", false),
            new Question("STAFF_COMMISSION", "Nhân viên", "Hoa hồng một nhân viên đã được ghi", "staff", true),
            new Question("COMMISSION_MISMATCH", "Nhân viên", "Hoa hồng Tư vấn lệch luật theo nhân viên", "none", false),
            new Question("BIG_DISCOUNTS", "Rò rỉ", "Đơn giảm giá từ 30% trở lên", "none", true));

    public record Column(String key, String label, String type) {
    }

    public record Answer(String questionId, String title, List<String> sentences, List<Column> columns,
                         List<Map<String, Object>> rows) {
    }

    public record AskRequest(String questionId, String entityId, LocalDate from, LocalDate to) {
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Column c(String k, String l, String t) {
        return new Column(k, l, t);
    }

    private static String d(LocalDate x) {
        return x.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public Answer run(AskRequest req) {
        Question q = CATALOG.stream().filter(x -> x.id().equals(req.questionId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Câu hỏi không có trong danh sách"));
        if (!"none".equals(q.entity()) && (req.entityId() == null || req.entityId().isBlank())) {
            throw ApiException.badRequest("Chọn đối tượng cần xem");
        }
        LocalDate t = commission.today();
        LocalDate from = req.from() == null ? t.withDayOfMonth(1) : req.from();
        LocalDate to = req.to() == null ? t : req.to();
        if (from.isAfter(to)) throw ApiException.badRequest("Ngày bắt đầu sau ngày kết thúc");
        LocalDate toExcl = to.plusDays(1);
        String period = "từ " + d(from) + " đến " + d(to);

        switch (q.id()) {
            case "REVENUE_SUMMARY" -> {
                var orders = rep.orders(from, toExcl);
                BigDecimal sales = sum(orders, SpaReportReader.OrderRow::total);
                BigDecimal cash = assistant.cashIn(from, toExcl);
                BigDecimal debt = sum(orders, SpaReportReader.OrderRow::debt);
                long days = ChronoUnit.DAYS.between(from, toExcl);
                LocalDate pFrom = from.minusDays(days);
                BigDecimal prev = sum(rep.orders(pFrom, from), SpaReportReader.OrderRow::total);
                BigDecimal ch = AssistantService.pctChange(sales, prev);
                return new Answer(q.id(), q.label(), List.of(
                        String.format("Doanh số %s là %s trên %d đơn.", period, money(sales), orders.size()),
                        String.format("Tiền thực thu trong kỳ là %s (gồm cả khách trả nợ cũ, đã trừ hoàn tiền).", money(cash)),
                        String.format("Các đơn trong kỳ còn nợ %s.", money(debt)),
                        ch == null ? "Kỳ trước cùng độ dài chưa có doanh số để so."
                                : String.format("So với %d ngày liền trước: %s.", days, AssistantService.pct(ch))),
                        List.of(), List.of());
            }
            case "REVENUE_BY_CATEGORY", "TOP_SERVICES" -> {
                boolean byCat = q.id().equals("REVENUE_BY_CATEGORY");
                Map<String, BigDecimal> amt = new HashMap<>();
                Map<String, Integer> cnt = new HashMap<>();
                for (var it : rep.items(from, toExcl)) {
                    if ("TREATMENT_CARD_USAGE".equals(it.itemType())) continue;
                    String key = byCat ? (it.categoryName() != null ? it.categoryName()
                            : "PRODUCT".equals(it.itemType()) ? "Sản phẩm" : "Khác") : it.itemName();
                    amt.merge(key, nz(it.total()), BigDecimal::add);
                    cnt.merge(key, it.quantity(), Integer::sum);
                }
                BigDecimal total = amt.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                List<Map<String, Object>> rows = amt.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .limit(byCat ? 50 : 10)
                        .map(e -> row("name", e.getKey(), "quantity", cnt.get(e.getKey()), "amount", e.getValue(),
                                "share", total.signum() == 0 ? BigDecimal.ZERO
                                        : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)))
                        .toList();
                List<String> s = new ArrayList<>();
                s.add(String.format("Tổng doanh số %s là %s.", period, money(total)));
                if (!rows.isEmpty()) {
                    s.add(String.format("Đứng đầu là %s với %s (%s%% tổng).", rows.get(0).get("name"),
                            money((BigDecimal) rows.get(0).get("amount")),
                            rows.get(0).get("share").toString().replace('.', ',')));
                }
                return new Answer(q.id(), q.label(), s, List.of(c("name", byCat ? "Nhóm" : "Dịch vụ", "text"),
                        c("quantity", "Số lượng", "number"), c("amount", "Doanh số", "money"), c("share", "% tổng", "percent")),
                        rows);
            }
            case "SERVICE_DETAIL" -> {
                Map<String, BigDecimal> byCust = new HashMap<>();
                Map<String, Integer> times = new HashMap<>();
                BigDecimal total = BigDecimal.ZERO;
                int qty = 0;
                String name = null;
                for (var it : rep.items(from, toExcl)) {
                    if (!req.entityId().equals(it.itemId()) || "TREATMENT_CARD_USAGE".equals(it.itemType())) continue;
                    name = it.itemName();
                    String cn = it.customerName() == null ? "Khách vãng lai" : it.customerName();
                    byCust.merge(cn, nz(it.total()), BigDecimal::add);
                    times.merge(cn, 1, Integer::sum);
                    total = total.add(nz(it.total()));
                    qty += it.quantity();
                }
                String svcName = spa.services().stream().filter(sv -> sv.id().equals(req.entityId()))
                        .map(SpaReader.ServiceRow::name).findFirst().orElse(name == null ? "dịch vụ này" : name);
                List<Map<String, Object>> rows = byCust.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(20)
                        .map(e -> row("customer", e.getKey(), "times", times.get(e.getKey()), "amount", e.getValue()))
                        .toList();
                return new Answer(q.id(), svcName, List.of(
                        String.format("%s %s bán được %d lượt (số lượng), doanh số %s, cho %d khách.", svcName, period,
                                qty, money(total), byCust.size())),
                        List.of(c("customer", "Khách", "text"), c("times", "Số dòng đơn", "number"),
                                c("amount", "Doanh số", "money")), rows);
            }
            case "TOP_CUSTOMERS" -> {
                Map<String, BigDecimal> amt = new HashMap<>();
                Map<String, Integer> cnt = new HashMap<>();
                Map<String, String> names = new HashMap<>();
                for (var o : rep.orders(from, toExcl)) {
                    if (o.customerId() == null) continue;
                    amt.merge(o.customerId(), nz(o.total()), BigDecimal::add);
                    cnt.merge(o.customerId(), 1, Integer::sum);
                    names.put(o.customerId(), o.customerName());
                }
                List<Map<String, Object>> rows = amt.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(10)
                        .map(e -> row("customer", names.get(e.getKey()), "orders", cnt.get(e.getKey()), "amount", e.getValue()))
                        .toList();
                List<String> s = new ArrayList<>();
                s.add(String.format("%d khách mua hàng %s.", amt.size(), period));
                if (!rows.isEmpty()) {
                    BigDecimal top = rows.stream().map(r -> (BigDecimal) r.get("amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
                    s.add(String.format("10 khách chi nhiều nhất đóng góp %s.", money(top)));
                }
                return new Answer(q.id(), q.label(), s, List.of(c("customer", "Khách", "text"),
                        c("orders", "Số đơn", "number"), c("amount", "Chi tiêu", "money")), rows);
            }
            case "CUSTOMER_PROFILE" -> {
                var hist = spa.serviceHistory(List.of(req.entityId()), t.minusYears(10), t.plusDays(1));
                var debts = rep.openDebts().stream().filter(o -> req.entityId().equals(o.customerId())).toList();
                var cards = rep.activeCards().stream().filter(cd -> req.entityId().equals(cd.customerId())).toList();
                var act = rep.customerActivity().stream().filter(a -> req.entityId().equals(a.customerId())).findFirst();
                String nm = debts.isEmpty() ? null : debts.get(0).customerName();
                if (nm == null) {
                    nm = spa.allCustomers().stream().filter(cu -> cu.id().equals(req.entityId()))
                            .map(SpaReader.CustomerRow::name).findFirst().orElse("Khách");
                }
                List<String> s = new ArrayList<>();
                s.add(String.format("%s đã có %d đơn, tổng chi tiêu %s.", nm, act.map(a -> a.orders()).orElse(0),
                        money(act.map(a -> a.spent()).orElse(BigDecimal.ZERO))));
                act.ifPresent(a -> s.add(String.format("Lần mua gần nhất ngày %s (%d ngày trước).",
                        d(a.lastOrder().toLocalDate()), ChronoUnit.DAYS.between(a.lastOrder().toLocalDate(), t))));
                s.add(debts.isEmpty() ? "Khách không còn nợ." : String.format("Khách còn nợ %s trên %d đơn.",
                        money(sum(debts, SpaReportReader.OrderRow::debt)), debts.size()));
                s.add(cards.isEmpty() ? "Khách không có thẻ liệu trình còn buổi." : String.format(
                        "Khách có %d thẻ còn tổng %d buổi.", cards.size(),
                        cards.stream().mapToInt(SpaReportReader.CardRow::remainingSessions).sum()));
                List<Map<String, Object>> rows = hist.stream()
                        .sorted(Comparator.comparing(SpaReader.ServiceHistoryLine::orderDate,
                                Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                        .limit(30)
                        .map(h -> row("date", h.orderDate(), "order", h.orderCode(), "item", h.itemName(),
                                "type", "TREATMENT_CARD".equals(h.itemType()) ? "Gói" : "Lẻ", "quantity", h.quantity()))
                        .toList();
                return new Answer(q.id(), nm, s, List.of(c("date", "Ngày", "date"), c("order", "Đơn", "text"),
                        c("item", "Dịch vụ", "text"), c("type", "Lẻ/Gói", "text"), c("quantity", "SL", "number")), rows);
            }
            case "LAPSED_CUSTOMERS" -> {
                Map<String, SpaReader.CustomerRow> cs = new HashMap<>();
                spa.allCustomers().forEach(cu -> cs.put(cu.id(), cu));
                List<Map<String, Object>> rows = rep.customerActivity().stream()
                        .filter(a -> a.lastOrder() != null && ChronoUnit.DAYS.between(a.lastOrder().toLocalDate(), t) > 60)
                        .sorted(Comparator.comparing(SpaReportReader.CustomerLastVisit::spent).reversed())
                        .limit(50)
                        .map(a -> {
                            var cu = cs.get(a.customerId());
                            return row("customer", cu == null ? a.customerId() : cu.name(), "phone", cu == null ? "" : cu.phone(),
                                    "last", a.lastOrder().toLocalDate(), "days", ChronoUnit.DAYS.between(a.lastOrder().toLocalDate(), t),
                                    "amount", a.spent());
                        }).toList();
                return new Answer(q.id(), q.label(), List.of(String.format(
                        "%d khách đã hơn 60 ngày chưa quay lại; danh sách xếp theo tổng chi tiêu để ưu tiên gọi lại.", rows.size())),
                        List.of(c("customer", "Khách", "text"), c("phone", "SĐT", "text"), c("last", "Lần cuối", "date"),
                                c("days", "Số ngày", "number"), c("amount", "Tổng chi tiêu", "money")), rows);
            }
            case "DEBTORS" -> {
                var dd = assistant.debts();
                List<Map<String, Object>> rows = dd.receivables().stream()
                        .map(r -> row("customer", r.customerName(), "phone", r.phone(), "order", r.orderCode(),
                                "date", r.orderDate(), "days", r.ageDays(), "amount", r.debt())).toList();
                return new Answer(q.id(), q.label(), List.of(String.format("%d đơn đang nợ, tổng %s.", rows.size(),
                        money(dd.receivableTotal()))),
                        List.of(c("customer", "Khách", "text"), c("phone", "SĐT", "text"), c("order", "Đơn", "text"),
                                c("date", "Ngày", "date"), c("days", "Tuổi nợ", "number"), c("amount", "Còn nợ", "money")), rows);
            }
            case "EXPIRING_CARDS" -> {
                List<Map<String, Object>> rows = rep.activeCards().stream()
                        .filter(cd -> cd.expiryDate() != null
                                && ChronoUnit.DAYS.between(t, cd.expiryDate().toLocalDate()) <= 30
                                && !cd.expiryDate().toLocalDate().isBefore(t))
                        .map(cd -> row("customer", cd.customerName(), "phone", cd.customerPhone(), "service", cd.serviceName(),
                                "remaining", cd.remainingSessions(), "expiry", cd.expiryDate().toLocalDate())).toList();
                return new Answer(q.id(), q.label(), List.of(String.format(
                        "%d thẻ hết hạn trong 30 ngày tới mà khách còn buổi; nên gọi đặt lịch sớm.", rows.size())),
                        List.of(c("customer", "Khách", "text"), c("phone", "SĐT", "text"), c("service", "Dịch vụ", "text"),
                                c("remaining", "Còn buổi", "number"), c("expiry", "Hết hạn", "date")), rows);
            }
            case "STAFF_COMMISSION" -> {
                var list = rep.commissions(from, toExcl).stream()
                        .filter(x -> req.entityId().equals(x.staffId())).toList();
                Map<String, BigDecimal> byRole = new LinkedHashMap<>();
                Map<String, Integer> cnt = new LinkedHashMap<>();
                for (var x : list) {
                    String r = x.staffRole() == null ? "Khác" : x.staffRole();
                    byRole.merge(r, nz(x.amount()), BigDecimal::add);
                    cnt.merge(r, 1, Integer::sum);
                }
                String nm = spa.staff().stream().filter(s -> s.id().equals(req.entityId()))
                        .map(SpaReader.StaffRow::name).findFirst().orElse("Nhân viên");
                BigDecimal total = sum(list, SpaReportReader.StaffCommission::amount);
                List<String> s = new ArrayList<>();
                s.add(String.format("%s được ghi %s hoa hồng %s, trên %d dòng.", nm, money(total), period, list.size()));
                var cr = commission.latest();
                long mis = cr.rows().stream().filter(r -> req.entityId().equals(r.check().staffId())
                        && !"OK".equals(r.check().verdict())).count();
                if (cr.run() != null) s.add(String.format("Ở lần kiểm tra gần nhất có %d dòng Tư vấn của %s lệch luật.", mis, nm));
                List<Map<String, Object>> rows = byRole.entrySet().stream()
                        .map(e -> row("role", e.getKey(), "lines", cnt.get(e.getKey()), "amount", e.getValue())).toList();
                return new Answer(q.id(), nm, s, List.of(c("role", "Vai trò", "text"), c("lines", "Số dòng", "number"),
                        c("amount", "Hoa hồng", "money")), rows);
            }
            case "COMMISSION_MISMATCH" -> {
                var cr = commission.latest();
                if (cr.run() == null) {
                    return new Answer(q.id(), q.label(), List.of("Chưa có lần kiểm tra hoa hồng nào. Vào màn Hoa hồng và bấm nút kiểm tra."),
                            List.of(), List.of());
                }
                Map<String, int[]> cnt = new LinkedHashMap<>();
                Map<String, BigDecimal> over = new HashMap<>();
                Map<String, BigDecimal> under = new HashMap<>();
                for (var r : cr.rows()) {
                    String nm = Objects.requireNonNullElse(r.check().staffName(), "?");
                    int[] a = cnt.computeIfAbsent(nm, k -> new int[2]);
                    a[0]++;
                    if (!"OK".equals(r.check().verdict())) a[1]++;
                    if ("OVER".equals(r.check().verdict())) over.merge(nm, r.check().diffAmount(), BigDecimal::add);
                    if ("UNDER".equals(r.check().verdict())) under.merge(nm, r.check().diffAmount().negate(), BigDecimal::add);
                }
                List<Map<String, Object>> rows = cnt.entrySet().stream()
                        .sorted((x, y) -> y.getValue()[1] - x.getValue()[1])
                        .map(e -> row("staff", e.getKey(), "lines", e.getValue()[0], "wrong", e.getValue()[1],
                                "over", over.getOrDefault(e.getKey(), BigDecimal.ZERO),
                                "under", under.getOrDefault(e.getKey(), BigDecimal.ZERO))).toList();
                long wrong = cr.rows().stream().filter(r -> !"OK".equals(r.check().verdict())).count();
                return new Answer(q.id(), q.label(), List.of(String.format(
                        "Lần kiểm tra ngày %s: %d / %d dòng Tư vấn lệch luật.", cr.run().finishedAt().toLocalDate(), wrong,
                        cr.rows().size())),
                        List.of(c("staff", "Tư vấn viên", "text"), c("lines", "Số dòng", "number"),
                                c("wrong", "Dòng lệch", "number"), c("over", "Ghi dư", "money"), c("under", "Ghi thiếu", "money")),
                        rows);
            }
            case "BIG_DISCOUNTS" -> {
                var leak = assistant.leaks(from, to).items().stream().filter(l -> l.kind().equals("discount")).findFirst().orElseThrow();
                return new Answer(q.id(), q.label(), List.of(String.format("%s %s, tổng tiền giảm %s.", leak.detail(), period,
                        money(leak.amount()))),
                        List.of(c("orderCode", "Đơn", "text"), c("date", "Ngày", "date"), c("customer", "Khách", "text"),
                                c("item", "Dòng", "text"), c("percent", "% giảm", "percent"), c("amount", "Tiền giảm", "money")),
                        leak.rows());
            }
            default -> throw ApiException.badRequest("Chưa hỗ trợ câu hỏi này");
        }
    }

    // =================================================================== TÓM TẮT CUỐI THÁNG (7.14)

    public record Summary(String month, List<String> paragraphs, List<String> advice) {
    }

    public Summary monthly(YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate toExcl = ym.plusMonths(1).atDay(1);
        LocalDate t = commission.today();
        if (toExcl.isAfter(t.plusDays(1))) toExcl = t.plusDays(1);
        LocalDate pFrom = ym.minusMonths(1).atDay(1);
        LocalDate pTo = from;

        var orders = rep.orders(from, toExcl);
        BigDecimal sales = sum(orders, SpaReportReader.OrderRow::total);
        BigDecimal prevSales = sum(rep.orders(pFrom, pTo), SpaReportReader.OrderRow::total);
        BigDecimal cash = assistant.cashIn(from, toExcl);
        BigDecimal comm = sum(rep.commissions(from, toExcl), SpaReportReader.StaffCommission::amount);
        var profit = assistant.profit(3);
        var thisMonth = profit.months().stream().filter(m -> m.month().equals(ym.toString())).findFirst();
        var leaks = assistant.leaks(from, toExcl.minusDays(1));
        var debts = assistant.debts();
        var cr = commission.latest();
        long wrong = cr.rows().stream().filter(r -> !"OK".equals(r.check().verdict())
                && r.check().orderDate() != null && YearMonth.from(r.check().orderDate()).equals(ym)).count();

        List<String> p = new ArrayList<>();
        BigDecimal ch = AssistantService.pctChange(sales, prevSales);
        p.add(String.format("Tháng %s doanh số đạt %s trên %d đơn, %s so với tháng trước (%s).", ym.getMonthValue() + "/" + ym.getYear(),
                money(sales), orders.size(), ch == null ? "chưa có số tháng trước" : AssistantService.pct(ch), money(prevSales)));
        p.add(String.format("Tiền thực thu %s; hoa hồng đã ghi %s, bằng %s doanh số.", money(cash), money(comm),
                sales.signum() == 0 ? "—" : comm.multiply(BigDecimal.valueOf(100)).divide(sales, 1, RoundingMode.HALF_UP)
                        .toPlainString().replace('.', ',') + "%"));
        thisMonth.ifPresent(m -> p.add(m.expenseTotal().signum() == 0
                ? "Chưa nhập chi phí tháng này nên chưa tính được lợi nhuận."
                : String.format("Chi phí nhập tay %s, lợi nhuận ước tính %s.", money(m.expenseTotal()), money(m.profit()))));
        p.add(String.format("Khách đang nợ tổng %s; các khoản rò rỉ phát hiện trong tháng khoảng %s.",
                money(debts.receivableTotal()), money(leaks.total())));
        if (cr.run() != null) p.add(String.format("Hoa hồng Tư vấn: %d dòng của tháng lệch luật ở lần kiểm tra gần nhất.", wrong));

        List<String> adv = new ArrayList<>();
        if (ch != null && ch.signum() < 0) adv.add("Doanh số giảm: xem lại danh sách khách lâu chưa quay lại và thẻ sắp hết hạn để gọi đặt lịch.");
        if (wrong > 0) adv.add("Có hoa hồng lệch luật: duyệt ở màn Hoa hồng trước khi chốt lương.");
        if (debts.aging().getOrDefault("> 90 ngày", BigDecimal.ZERO).signum() > 0) adv.add("Có nợ trên 90 ngày: cân nhắc gọi trực tiếp thay vì nhắn tin.");
        leaks.items().stream().filter(l -> "discount".equals(l.kind()) && !l.rows().isEmpty()).findFirst()
                .ifPresent(l -> adv.add(String.format("Có %d dòng giảm giá từ 30%%: kiểm tra ai duyệt và lý do.", l.rows().size())));
        if (adv.isEmpty()) adv.add("Không có điểm bất thường lớn trong tháng.");
        return new Summary(ym.toString(), p, adv);
    }
}
