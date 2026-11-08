package vn.yellow.ai.assistant;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.yellow.ai.common.ApiException;

/** Dữ liệu nhập tay trong spa_ai (HANDOFF 7.2): chi phí vận hành và nợ nhà cung cấp — Spa không có. */
@Repository
public class ManualStore {

    private final JdbcTemplate jdbc;

    public ManualStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static LocalDate d(Date v) {
        return v == null ? null : v.toLocalDate();
    }

    public record Expense(String id, LocalDate expenseDate, String category, String description, BigDecimal amount,
                          boolean recurring) {
    }

    public List<Expense> expenses(LocalDate from, LocalDate toExcl) {
        return jdbc.query("""
                SELECT id, expense_date, category, description, amount, recurring FROM manual_expense
                WHERE expense_date >= ? AND expense_date < ? ORDER BY expense_date DESC, category""",
                (rs, n) -> new Expense(rs.getString("id"), d(rs.getDate("expense_date")), rs.getString("category"),
                        rs.getString("description"), rs.getBigDecimal("amount"), rs.getBoolean("recurring")),
                Date.valueOf(from), Date.valueOf(toExcl));
    }

    public Expense addExpense(Expense e, String user) {
        if (e.expenseDate() == null || e.category() == null || e.category().isBlank() || e.amount() == null
                || e.amount().signum() <= 0) {
            throw ApiException.badRequest("Cần ngày, loại chi phí và số tiền > 0");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO manual_expense (id, expense_date, category, description, amount, recurring, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, Date.valueOf(e.expenseDate()), e.category().trim(),
                e.description(), e.amount(), e.recurring(), user, Timestamp.valueOf(LocalDateTime.now()));
        return new Expense(id, e.expenseDate(), e.category().trim(), e.description(), e.amount(), e.recurring());
    }

    public void deleteExpense(String id) {
        jdbc.update("DELETE FROM manual_expense WHERE id = ?", id);
    }

    public record SupplierDebt(String id, String supplierName, String description, BigDecimal amount,
                               BigDecimal paidAmount, LocalDate invoiceDate, LocalDate dueDate, boolean critical) {
        public BigDecimal outstanding() {
            return amount.subtract(paidAmount == null ? BigDecimal.ZERO : paidAmount);
        }
    }

    public List<SupplierDebt> supplierDebts() {
        return jdbc.query("""
                SELECT id, supplier_name, description, amount, paid_amount, invoice_date, due_date, critical
                FROM supplier_debt ORDER BY due_date""",
                (rs, n) -> new SupplierDebt(rs.getString("id"), rs.getString("supplier_name"),
                        rs.getString("description"), rs.getBigDecimal("amount"), rs.getBigDecimal("paid_amount"),
                        d(rs.getDate("invoice_date")), d(rs.getDate("due_date")), rs.getBoolean("critical")));
    }

    public void saveSupplierDebt(SupplierDebt s, String user) {
        if (s.supplierName() == null || s.supplierName().isBlank() || s.amount() == null || s.amount().signum() <= 0) {
            throw ApiException.badRequest("Cần tên nhà cung cấp và số tiền > 0");
        }
        BigDecimal paid = s.paidAmount() == null ? BigDecimal.ZERO : s.paidAmount();
        Date inv = s.invoiceDate() == null ? null : Date.valueOf(s.invoiceDate());
        Date due = s.dueDate() == null ? null : Date.valueOf(s.dueDate());
        if (s.id() == null || s.id().isBlank()) {
            jdbc.update("""
                    INSERT INTO supplier_debt (id, supplier_name, description, amount, paid_amount, invoice_date, due_date,
                      critical, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID().toString(), s.supplierName().trim(), s.description(), s.amount(), paid, inv, due,
                    s.critical(), user, Timestamp.valueOf(LocalDateTime.now()));
        } else {
            jdbc.update("""
                    UPDATE supplier_debt SET supplier_name = ?, description = ?, amount = ?, paid_amount = ?,
                      invoice_date = ?, due_date = ?, critical = ? WHERE id = ?""",
                    s.supplierName().trim(), s.description(), s.amount(), paid, inv, due, s.critical(), s.id());
        }
    }

    public void deleteSupplierDebt(String id) {
        jdbc.update("DELETE FROM supplier_debt WHERE id = ?", id);
    }
}
