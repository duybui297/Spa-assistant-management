package vn.yellow.ai.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.yellow.ai.commission.engine.CommissionRule;
import vn.yellow.ai.commission.engine.DefaultRules;
import vn.yellow.ai.commission.engine.Evaluation;
import vn.yellow.ai.commission.engine.LineFacts;
import vn.yellow.ai.commission.engine.OldConfig;
import vn.yellow.ai.commission.engine.RuleEngine;
import vn.yellow.ai.commission.engine.VisitCounter;
import vn.yellow.ai.common.ApiException;
import vn.yellow.ai.config.AppProperties;
import vn.yellow.ai.spa.SpaReader;

/**
 * Module hoa hồng Tư vấn, kiểu "kiểm tra sau" (HANDOFF 6.1, 7.1, 7.7):
 * chỉ đọc DB Spa khi bấm nút, chấm từng dòng bằng bộ luật, lưu kết quả vào spa_ai.
 */
@Service
public class CommissionService {

    private final SpaReader spa;
    private final CommissionStore store;
    private final AppProperties.Commission cfg;
    private final ZoneId zone;

    public CommissionService(SpaReader spa, CommissionStore store, AppProperties props) {
        this.spa = spa;
        this.store = store;
        this.cfg = props.commission();
        this.zone = ZoneId.of(props.timezone() == null ? "Asia/Ho_Chi_Minh" : props.timezone());
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public LocalDate effectiveDate() {
        return cfg.effectiveDay();
    }

    private LocalDate historySince(LocalDate to) {
        int months = cfg.historyMonths() == null ? 120 : cfg.historyMonths();
        return to.minusMonths(months);
    }

    private Map<String, CommissionStore.GroupDef> groupDefs() {
        return store.groups().stream().collect(Collectors.toMap(CommissionStore.GroupDef::code, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }

    private static String groupOf(Map<String, String> groupMap, String serviceId) {
        String g = serviceId == null ? null : groupMap.get(serviceId);
        return g == null ? DefaultRules.GROUP_OTHER : g;
    }

    // ------------------------------------------------------------------ chấm hàng loạt

    /** Chấm toàn bộ dòng Tư vấn trong [from, to] với bộ luật cho trước. Không ghi gì. */
    public List<CommissionStore.Check> evaluate(LocalDate from, LocalDate to, List<CommissionRule> rules, String runId) {
        LocalDate fromEff = from.isBefore(cfg.effectiveDay()) ? cfg.effectiveDay() : from;
        LocalDate toExcl = to.plusDays(1);
        if (!fromEff.isBefore(toExcl)) return List.of();

        List<SpaReader.ConsultantLine> lines = spa.consultantLines(fromEff, toExcl, cfg.consultantRoles());
        if (lines.isEmpty()) return List.of();

        Map<String, String> groupMap = store.groupMap();
        Map<String, CommissionStore.GroupDef> defs = groupDefs();

        Set<String> customers = lines.stream().map(SpaReader.ConsultantLine::customerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<VisitCounter.HistoryLine> hist = new ArrayList<>();
        Set<String> histIds = new HashSet<>();
        for (SpaReader.ServiceHistoryLine h : spa.serviceHistory(customers, historySince(toExcl), toExcl)) {
            hist.add(new VisitCounter.HistoryLine(h.orderItemId(), h.customerId(), groupOf(groupMap, h.serviceId()),
                    h.orderDate(), h.orderId(), h.itemCreatedAt()));
            histIds.add(h.orderItemId());
        }
        // dòng của khách vãng lai không có trong lịch sử theo khách → thêm để luôn có số lần
        for (SpaReader.ConsultantLine l : lines) {
            if (!histIds.contains(l.orderItemId())) {
                hist.add(new VisitCounter.HistoryLine(l.orderItemId(), l.customerId(), groupOf(groupMap, l.serviceId()),
                        l.orderDate(), l.orderId(), null));
                histIds.add(l.orderItemId());
            }
        }
        Map<String, Integer> visits = VisitCounter.assign(hist);

        Set<String> serviceIds = lines.stream().map(SpaReader.ConsultantLine::serviceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, SpaReader.OldCommissionConfig> olds = spa.oldConfigs(serviceIds, cfg.oldConfigRole());

        List<CommissionStore.Check> out = new ArrayList<>();
        for (SpaReader.ConsultantLine l : lines) {
            String g = groupOf(groupMap, l.serviceId());
            CommissionStore.GroupDef def = defs.get(g);
            boolean pkg = "TREATMENT_CARD".equals(l.itemType());
            int visit = visits.getOrDefault(l.orderItemId(), 1);
            LineFacts facts = new LineFacts(g, def == null ? g : def.label(), def == null ? "" : def.unit(), pkg,
                    l.tcTotalSessions(), l.tcBonusSessions() == null ? 0 : l.tcBonusSessions(), visit, l.quantity());
            SpaReader.OldCommissionConfig oc = olds.get(l.serviceId());
            OldConfig old = oc == null ? OldConfig.NONE
                    : OldConfig.fromSpa(oc.commissionType(), oc.percentageRevenue(), oc.cashBonusAmount());
            BigDecimal base = RuleEngine.base(pkg, l.listPrice(), l.unitPrice(), l.quantity(), l.tcTotalSessions(),
                    cfg.packageBase());
            Evaluation e = RuleEngine.evaluate(facts, rules, old, base);
            BigDecimal entered = l.commissionAmount() == null ? BigDecimal.ZERO : l.commissionAmount();
            RuleEngine.Verdict v = RuleEngine.verdict(e.expectedAmount(), entered, cfg.toleranceVnd());
            String reason = e.reason();
            if (l.listPrice() == null) reason = reason + " · Không tìm thấy dịch vụ trong danh mục, căn cứ = 0";
            out.add(new CommissionStore.Check(runId, l.orderItemStaffId(), l.orderId(), l.orderCode(), l.orderDate(),
                    l.branchId(), l.orderItemId(), l.itemType(), l.serviceId(),
                    l.serviceName() != null ? l.serviceName() : l.itemName(), l.customerId(), l.customerName(),
                    l.staffId(), l.staffName(), g, pkg, l.tcTotalSessions(), facts.bonus(), visit, l.quantity(),
                    l.listPrice(), l.unitPrice(), base, entered, RuleEngine.impliedPercent(entered, base),
                    e.percent(), e.expectedAmount(), entered.subtract(e.expectedAmount()), v.name(), e.source().name(),
                    e.ruleId(), reason));
        }
        return out;
    }

    // ------------------------------------------------------------------ nút "Lấy dữ liệu & kiểm tra"

    public record SyncResult(CommissionStore.Run run, int total, int mismatch) {
    }

    public synchronized SyncResult sync(LocalDate from, LocalDate to, String user) {
        LocalDate t = to == null ? today() : to;
        LocalDate f = from == null ? cfg.effectiveDay() : from;
        if (f.isAfter(t)) throw ApiException.badRequest("Ngày bắt đầu sau ngày kết thúc");
        List<CommissionRule> rules = store.rules();
        String id = UUID.randomUUID().toString();
        store.startRun(id, f, t, user, store.toJson(rules));
        try {
            List<CommissionStore.Check> checks = evaluate(f, t, rules, id);
            store.insertChecks(checks);
            int mismatch = (int) checks.stream().filter(c -> !"OK".equals(c.verdict())).count();
            store.finishRun(id, checks.size(), mismatch);
            CommissionStore.Run run = store.runs().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
            return new SyncResult(run, checks.size(), mismatch);
        } catch (RuntimeException e) {
            store.failRun(id, e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ kết quả lần chạy gần nhất

    public record CheckView(CommissionStore.Check check, CommissionStore.Review review, String groupLabel) {
    }

    public record Results(CommissionStore.Run run, List<CheckView> rows, LocalDate effectiveDate) {
    }

    public Results latest() {
        Map<String, CommissionStore.GroupDef> defs = groupDefs();
        var run = store.latestDoneRun().orElse(null);
        if (run == null) return new Results(null, List.of(), cfg.effectiveDay());
        Map<String, CommissionStore.Review> reviews = store.reviews();
        List<CheckView> rows = store.checks(run.id()).stream()
                .map(c -> new CheckView(c, reviews.get(c.orderItemStaffId()),
                        defs.containsKey(c.groupCode()) ? defs.get(c.groupCode()).label() : c.groupCode()))
                .toList();
        return new Results(run, rows, cfg.effectiveDay());
    }

    public void review(String oisId, String status, String note, String user) {
        if (status != null && !List.of("FIX_NEEDED", "ACCEPTED").contains(status)) {
            throw ApiException.badRequest("Trạng thái không hợp lệ");
        }
        if (status != null && (note == null || note.isBlank())) throw ApiException.badRequest("Cần ghi chú khi xác nhận");
        var run = store.latestDoneRun().orElseThrow(() -> ApiException.badRequest("Chưa có lần kiểm tra nào"));
        var c = store.checks(run.id()).stream().filter(x -> x.orderItemStaffId().equals(oisId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng"));
        store.saveReview(new CommissionStore.Review(oisId, status, note, c.enteredAmount(), c.expectedAmount(), user, null));
    }

    // ------------------------------------------------------------------ tra cứu "% đúng cho khách này, dịch vụ này"

    public record LookupRequest(String customerId, String serviceId, Boolean isPackage, Integer sessions, Integer bonus,
                                Integer quantity) {
    }

    public record PastLine(String orderCode, java.time.LocalDateTime orderDate, String itemName, String itemType,
                           int quantity, Integer sessions, Integer bonus) {
    }

    public record LookupResult(String groupCode, String groupLabel, String unit, int visit, BigDecimal listPrice,
                               BigDecimal base, Evaluation evaluation, List<String> alsoMatched, List<PastLine> history,
                               boolean beforeEffectiveDate, LocalDate effectiveDate) {
    }

    public LookupResult lookup(LookupRequest req) {
        if (req.serviceId() == null) throw ApiException.badRequest("Chọn dịch vụ");
        SpaReader.ServiceRow svc = spa.services().stream().filter(s -> s.id().equals(req.serviceId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dịch vụ"));
        Map<String, String> groupMap = store.groupMap();
        Map<String, CommissionStore.GroupDef> defs = groupDefs();
        String g = groupOf(groupMap, svc.id());
        CommissionStore.GroupDef def = defs.get(g);

        List<PastLine> past = new ArrayList<>();
        if (req.customerId() != null && !req.customerId().isBlank()) {
            LocalDate toExcl = today().plusDays(1);
            spa.serviceHistory(List.of(req.customerId()), historySince(toExcl), toExcl).stream()
                    .filter(h -> g.equals(groupOf(groupMap, h.serviceId())))
                    .sorted(Comparator.comparing(SpaReader.ServiceHistoryLine::orderDate,
                            Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                    .forEach(h -> past.add(new PastLine(h.orderCode(), h.orderDate(), h.itemName(), h.itemType(),
                            h.quantity(), h.tcTotalSessions(), h.tcBonusSessions())));
        }
        int visit = past.size() + 1;
        boolean pkg = Boolean.TRUE.equals(req.isPackage());
        int qty = req.quantity() == null || req.quantity() < 1 ? 1 : req.quantity();
        Integer sessions = pkg ? req.sessions() : null;
        Integer bonus = pkg ? (req.bonus() == null ? 0 : req.bonus()) : null;
        LineFacts facts = new LineFacts(g, def == null ? g : def.label(), def == null ? "" : def.unit(), pkg,
                sessions, bonus, visit, qty);
        List<CommissionRule> rules = store.rules();
        SpaReader.OldCommissionConfig oc = spa.oldConfigs(List.of(svc.id()), cfg.oldConfigRole()).get(svc.id());
        OldConfig old = oc == null ? OldConfig.NONE
                : OldConfig.fromSpa(oc.commissionType(), oc.percentageRevenue(), oc.cashBonusAmount());
        BigDecimal base = RuleEngine.base(pkg, svc.price(), null, qty, sessions, RuleEngine.BaseMode.LIST_PRICE_X_SESSIONS);
        Evaluation e = RuleEngine.evaluate(facts, rules, old, base);
        List<String> also = RuleEngine.matchAll(facts, rules).stream().map(CommissionRule::label).toList();
        return new LookupResult(g, def == null ? g : def.label(), def == null ? "" : def.unit(), visit, svc.price(),
                base, e, also.size() > 1 ? also.subList(1, also.size()) : List.of(), past,
                today().isBefore(cfg.effectiveDay()), cfg.effectiveDay());
    }

    // ------------------------------------------------------------------ chạy thử bộ luật (D.6)

    public record DryRunChange(CommissionStore.Check before, CommissionStore.Check after) {
    }

    public record DryRunResult(int totalLines, int changedLines, BigDecimal expectedBefore, BigDecimal expectedAfter,
                               List<DryRunChange> samples) {
    }

    public DryRunResult dryRun(List<CommissionRule> proposed, LocalDate from, LocalDate to) {
        LocalDate t = to == null ? today() : to;
        LocalDate f = from == null ? t.minusMonths(1) : from;
        List<CommissionStore.Check> before = evaluate(f, t, store.rules(), "dry");
        List<CommissionStore.Check> after = evaluate(f, t, proposed, "dry");
        Map<String, CommissionStore.Check> afterById = after.stream()
                .collect(Collectors.toMap(CommissionStore.Check::orderItemStaffId, Function.identity()));
        List<DryRunChange> changes = new ArrayList<>();
        BigDecimal sb = BigDecimal.ZERO;
        BigDecimal sa = BigDecimal.ZERO;
        for (CommissionStore.Check b : before) {
            CommissionStore.Check a = afterById.get(b.orderItemStaffId());
            sb = sb.add(b.expectedAmount());
            if (a == null) continue;
            sa = sa.add(a.expectedAmount());
            if (b.expectedAmount().compareTo(a.expectedAmount()) != 0 || !Objects.equals(b.ruleId(), a.ruleId())) {
                changes.add(new DryRunChange(b, a));
            }
        }
        return new DryRunResult(before.size(), changes.size(), sb, sa,
                changes.size() > 50 ? changes.subList(0, 50) : changes);
    }
}
