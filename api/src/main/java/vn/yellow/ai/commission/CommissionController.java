package vn.yellow.ai.commission;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.yellow.ai.auth.AuthInterceptor;
import vn.yellow.ai.commission.engine.CommissionRule;
import vn.yellow.ai.commission.engine.DefaultRules;
import vn.yellow.ai.commission.engine.RuleCondition;
import vn.yellow.ai.common.ApiException;
import vn.yellow.ai.config.AppProperties;
import vn.yellow.ai.spa.SpaReader;

@RestController
@RequestMapping("/api")
public class CommissionController {

    private final CommissionService service;
    private final CommissionStore store;
    private final SpaReader spa;
    private final AppProperties props;

    public CommissionController(CommissionService service, CommissionStore store, SpaReader spa, AppProperties props) {
        this.service = service;
        this.store = store;
        this.spa = spa;
        this.props = props;
    }

    private static String user(HttpServletRequest req) {
        return String.valueOf(req.getAttribute(AuthInterceptor.USER_ATTR));
    }

    @GetMapping("/commission/meta")
    public Map<String, Object> meta() {
        var c = props.commission();
        return Map.of("effectiveDate", c.effectiveDay(), "toleranceVnd", c.toleranceVnd(),
                "packageBase", c.packageBase(), "consultantRoles", c.consultantRoles(), "today", service.today(),
                "mock", props.mock().enabled());
    }

    // ---------------------------------------------------------------- kiểm tra

    public record SyncRequest(LocalDate from, LocalDate to) {
    }

    @PostMapping("/commission/sync")
    public CommissionService.SyncResult sync(@RequestBody(required = false) SyncRequest body, HttpServletRequest req) {
        return service.sync(body == null ? null : body.from(), body == null ? null : body.to(), user(req));
    }

    @GetMapping("/commission/results")
    public CommissionService.Results results() {
        return service.latest();
    }

    @GetMapping("/commission/runs")
    public List<CommissionStore.Run> runs() {
        return store.runs();
    }

    public record ReviewRequest(String orderItemStaffId, String status, String note) {
    }

    @PostMapping("/commission/review")
    public Map<String, Boolean> review(@RequestBody ReviewRequest body, HttpServletRequest req) {
        service.review(body.orderItemStaffId(), body.status(), body.note(), user(req));
        return Map.of("ok", true);
    }

    @PostMapping("/commission/lookup")
    public CommissionService.LookupResult lookup(@RequestBody CommissionService.LookupRequest body) {
        return service.lookup(body);
    }

    // ---------------------------------------------------------------- bảng luật (D.6)

    public record RuleView(CommissionRule rule, List<String> conditionText) {
    }

    @GetMapping("/commission/rules")
    public List<RuleView> rules() {
        Map<String, String> labels = store.groups().stream()
                .collect(Collectors.toMap(CommissionStore.GroupDef::code, CommissionStore.GroupDef::label, (a, b) -> a));
        Map<String, String> units = store.groups().stream()
                .collect(Collectors.toMap(CommissionStore.GroupDef::code, g -> g.unit() == null ? "" : g.unit(), (a, b) -> a));
        Function<String, String> lab = code -> labels.getOrDefault(code, code);
        return store.rules().stream().map(r -> new RuleView(r, r.conditions().stream()
                        .map(c -> c.describe(lab, c.groups().size() == 1 ? units.get(c.groups().get(0)) : ""))
                        .toList()))
                .toList();
    }

    public record RuleRequest(CommissionRule rule, String note) {
    }

    private void validate(CommissionRule r) {
        if (r == null || r.id() == null || !r.id().matches("[A-Z0-9_]{2,50}")) {
            throw ApiException.badRequest("Mã tiêu chí chỉ gồm chữ in hoa, số, gạch dưới (2–50 ký tự)");
        }
        if (r.label() == null || r.label().isBlank()) throw ApiException.badRequest("Cần tên tiêu chí");
        if (r.percent() == null || r.percent().signum() < 0 || r.percent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("% phải từ 0 đến 100");
        }
        if (r.conditions().isEmpty()) throw ApiException.badRequest("Cần ít nhất 1 điều kiện");
        for (RuleCondition c : r.conditions()) {
            if (c.qtyMin() != null && c.qtyMax() != null && c.qtyMin() > c.qtyMax()) {
                throw ApiException.badRequest("Số lượng tối thiểu lớn hơn tối đa");
            }
        }
    }

    private static String requireNote(String note) {
        if (note == null || note.isBlank()) throw ApiException.badRequest("Cần ghi lý do thay đổi (lưu vào lịch sử)");
        return note.trim();
    }

    @PostMapping("/commission/rules")
    public Map<String, Boolean> createRule(@RequestBody RuleRequest body, HttpServletRequest req) {
        validate(body.rule());
        if (store.rule(body.rule().id()).isPresent()) throw ApiException.badRequest("Mã tiêu chí đã tồn tại");
        store.createRule(body.rule(), user(req), requireNote(body.note()));
        return Map.of("ok", true);
    }

    @PutMapping("/commission/rules/{id}")
    public Map<String, Boolean> updateRule(@PathVariable String id, @RequestBody RuleRequest body, HttpServletRequest req) {
        validate(body.rule());
        if (!id.equals(body.rule().id())) throw ApiException.badRequest("Không đổi được mã tiêu chí");
        CommissionRule before = store.rule(id).orElseThrow(() -> ApiException.notFound("Không có tiêu chí " + id));
        store.updateRule(before, body.rule(), user(req), requireNote(body.note()));
        return Map.of("ok", true);
    }

    @GetMapping("/commission/rules/history")
    public List<CommissionStore.HistoryRow> history() {
        return store.ruleHistory();
    }

    public record DryRunRequest(List<CommissionRule> rules, LocalDate from, LocalDate to) {
    }

    @PostMapping("/commission/rules/dry-run")
    public CommissionService.DryRunResult dryRun(@RequestBody DryRunRequest body) {
        if (body.rules() == null) throw ApiException.badRequest("Thiếu bộ luật");
        body.rules().forEach(this::validate);
        return service.dryRun(body.rules(), body.from(), body.to());
    }

    // ---------------------------------------------------------------- nhóm dịch vụ (D.1)

    @GetMapping("/commission/groups")
    public List<CommissionStore.GroupDef> groups() {
        return store.groups();
    }

    @PutMapping("/commission/groups")
    public Map<String, Boolean> upsertGroup(@RequestBody CommissionStore.GroupDef g) {
        if (g.code() == null || !g.code().matches("[A-Z0-9_]{2,50}")) {
            throw ApiException.badRequest("Mã nhóm chỉ gồm chữ in hoa, số, gạch dưới");
        }
        if (g.label() == null || g.label().isBlank()) throw ApiException.badRequest("Cần tên nhóm");
        store.upsertGroup(g);
        return Map.of("ok", true);
    }

    public record ServiceWithGroup(SpaReader.ServiceRow service, String groupCode) {
    }

    @GetMapping("/commission/services")
    public List<ServiceWithGroup> services() {
        Map<String, String> map = store.groupMap();
        return spa.services().stream()
                .map(s -> new ServiceWithGroup(s, map.getOrDefault(s.id(), DefaultRules.GROUP_OTHER)))
                .toList();
    }

    public record AssignRequest(String groupCode) {
    }

    @PutMapping("/commission/services/{serviceId}/group")
    public Map<String, Boolean> assign(@PathVariable String serviceId, @RequestBody AssignRequest body,
                                       HttpServletRequest req) {
        store.assignGroup(serviceId, body.groupCode(), user(req));
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------- tra cứu danh mục Spa

    @GetMapping("/spa/customers")
    public List<SpaReader.CustomerRow> customers(@RequestParam(defaultValue = "") String q) {
        return spa.searchCustomers(q);
    }

    @GetMapping("/spa/staff")
    public List<SpaReader.StaffRow> staff() {
        return spa.staff();
    }
}
