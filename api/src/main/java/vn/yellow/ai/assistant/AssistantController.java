package vn.yellow.ai.assistant;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.yellow.ai.auth.AuthInterceptor;
import vn.yellow.ai.common.ApiException;
import vn.yellow.ai.mock.MockSeeder;
import vn.yellow.ai.spa.SpaReader;

@RestController
@RequestMapping("/api")
public class AssistantController {

    private final AssistantService assistant;
    private final AskService ask;
    private final ManualStore manual;
    private final MockSeeder mock;
    private final SpaReader spa;

    public AssistantController(AssistantService assistant, AskService ask, ManualStore manual, MockSeeder mock,
                               SpaReader spa) {
        this.assistant = assistant;
        this.ask = ask;
        this.manual = manual;
        this.mock = mock;
        this.spa = spa;
    }

    private static String user(HttpServletRequest req) {
        return String.valueOf(req.getAttribute(AuthInterceptor.USER_ATTR));
    }

    @GetMapping("/assistant/today")
    public AssistantService.Today today() {
        return assistant.today(null);
    }

    @GetMapping("/assistant/cashflow")
    public AssistantService.Cashflow cashflow(@RequestParam(defaultValue = "0") BigDecimal opening,
                                              @RequestParam(defaultValue = "90") int days) {
        return assistant.cashflow(opening, days);
    }

    @GetMapping("/assistant/profit")
    public AssistantService.Profit profit(@RequestParam(defaultValue = "6") int months) {
        return assistant.profit(months);
    }

    @GetMapping("/assistant/debts")
    public AssistantService.Debts debts() {
        return assistant.debts();
    }

    @GetMapping("/assistant/leaks")
    public AssistantService.Leaks leaks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return assistant.leaks(from, to);
    }

    // ---------------------------------------------------------------- hỏi trợ lý

    @GetMapping("/ask/catalog")
    public List<AskService.Question> catalog() {
        return AskService.CATALOG;
    }

    @PostMapping("/ask/run")
    public AskService.Answer run(@RequestBody AskService.AskRequest req) {
        return ask.run(req);
    }

    @GetMapping("/ask/services")
    public List<SpaReader.ServiceRow> services() {
        return spa.services();
    }

    @GetMapping("/summary/monthly")
    public AskService.Summary monthly(@RequestParam(required = false) String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.from(assistant.today()).minusMonths(1) : YearMonth.parse(month);
        } catch (Exception e) {
            throw ApiException.badRequest("Tháng phải dạng YYYY-MM");
        }
        return ask.monthly(ym);
    }

    // ---------------------------------------------------------------- nhập tay

    @GetMapping("/manual/expenses")
    public List<ManualStore.Expense> expenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = assistant.today();
        return manual.expenses(from == null ? t.minusMonths(6).withDayOfMonth(1) : from,
                (to == null ? t.plusMonths(1) : to).plusDays(1));
    }

    @PostMapping("/manual/expenses")
    public ManualStore.Expense addExpense(@RequestBody ManualStore.Expense e, HttpServletRequest req) {
        return manual.addExpense(e, user(req));
    }

    @DeleteMapping("/manual/expenses/{id}")
    public Map<String, Boolean> deleteExpense(@PathVariable String id) {
        manual.deleteExpense(id);
        return Map.of("ok", true);
    }

    @GetMapping("/manual/supplier-debts")
    public List<ManualStore.SupplierDebt> supplierDebts() {
        return manual.supplierDebts();
    }

    @PostMapping("/manual/supplier-debts")
    public Map<String, Boolean> saveSupplierDebt(@RequestBody ManualStore.SupplierDebt s, HttpServletRequest req) {
        manual.saveSupplierDebt(s, user(req));
        return Map.of("ok", true);
    }

    @DeleteMapping("/manual/supplier-debts/{id}")
    public Map<String, Boolean> deleteSupplierDebt(@PathVariable String id) {
        manual.deleteSupplierDebt(id);
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------- V1: sinh lại dữ liệu giả

    @PostMapping("/mock/reset")
    public Map<String, Boolean> resetMock() {
        try {
            mock.reset();
        } catch (IllegalStateException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        return Map.of("ok", true);
    }
}
