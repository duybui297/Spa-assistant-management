package vn.yellow.ai.commission.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một tiêu chí trong bảng luật. Khớp khi có ít nhất một điều kiện khớp.
 * Nhiều tiêu chí cùng khớp thì lấy tiêu chí có {@code priority} nhỏ nhất (đứng trên cùng bảng).
 */
public record CommissionRule(
        String id,
        String label,
        BigDecimal percent,
        boolean active,
        int priority,
        List<RuleCondition> conditions) {

    public CommissionRule {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean matches(LineFacts f) {
        return active && conditions.stream().anyMatch(c -> c.matches(f));
    }
}
