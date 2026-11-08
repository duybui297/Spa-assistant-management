package vn.yellow.ai.commission.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bộ chấm hoa hồng Tư vấn. Không dùng AI: cùng đầu vào luôn ra cùng kết quả, kèm lý do.
 * Không phụ thuộc Spring hay DB để test độc lập.
 */
public final class RuleEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RuleEngine() {
    }

    /** Tiêu chí đầu tiên (priority nhỏ nhất) khớp với dòng. */
    public static Optional<CommissionRule> match(LineFacts facts, List<CommissionRule> rules) {
        return rules.stream()
                .sorted(Comparator.comparingInt(CommissionRule::priority).thenComparing(CommissionRule::id))
                .filter(r -> r.matches(facts))
                .findFirst();
    }

    /** Tất cả tiêu chí khớp (để cảnh báo trùng tiêu chí khi admin chạy thử). */
    public static List<CommissionRule> matchAll(LineFacts facts, List<CommissionRule> rules) {
        return rules.stream()
                .sorted(Comparator.comparingInt(CommissionRule::priority).thenComparing(CommissionRule::id))
                .filter(r -> r.matches(facts))
                .toList();
    }

    public static Evaluation evaluate(LineFacts facts, List<CommissionRule> rules, OldConfig old, BigDecimal base) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        Optional<CommissionRule> hit = match(facts, rules);
        if (hit.isPresent()) {
            CommissionRule r = hit.get();
            return new Evaluation(Evaluation.Source.RULE, r.id(), r.label(), r.percent(), b,
                    amount(b, r.percent()), facts.describe() + " → " + r.label());
        }
        OldConfig o = old == null ? OldConfig.NONE : old;
        String prefix = facts.describe() + " → không có trong bảng mới, giữ cấu hình cũ";
        return switch (o.type()) {
            case PERCENT -> new Evaluation(Evaluation.Source.OLD_PERCENT, null, null, o.percent(), b,
                    amount(b, o.percent()), prefix + " (" + o.percent().stripTrailingZeros().toPlainString() + "%)");
            case CASH -> new Evaluation(Evaluation.Source.OLD_CASH, null, null, null, b,
                    o.cashPerUnit().multiply(BigDecimal.valueOf(facts.quantity())).setScale(0, RoundingMode.HALF_UP),
                    prefix + " (thưởng tiền cố định × số lượng)");
            case NONE -> new Evaluation(Evaluation.Source.NONE, null, null, BigDecimal.ZERO, b, BigDecimal.ZERO,
                    facts.describe() + " → không có trong bảng mới và dịch vụ chưa cấu hình hoa hồng Tư vấn");
        };
    }

    /** base × % / 100, làm tròn đến đồng. */
    public static BigDecimal amount(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) return BigDecimal.ZERO;
        return base.multiply(percent).divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /** % đã ghi, tính ngược: commission_amount ÷ base × 100 (2 chữ số thập phân). null khi base = 0. */
    public static BigDecimal impliedPercent(BigDecimal entered, BigDecimal base) {
        if (entered == null || base == null || base.signum() == 0) return null;
        return entered.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }

    public enum BaseMode {
        /** Gói: giá gốc dịch vụ × số buổi chính × số lượng. */
        LIST_PRICE_X_SESSIONS,
        /** Gói: giá bán trên dòng đơn (unit_price × số lượng). */
        ORDER_UNIT_PRICE
    }

    /**
     * Căn cứ tính % (B.7: giá gốc; D.5: gói tính một lần trên tổng tiền gói).
     *
     * @param listPrice  service.price (giá gốc niêm yết 1 đơn vị / 1 buổi)
     * @param unitPrice  order_items.unit_price (giá bán trên dòng)
     */
    public static BigDecimal base(boolean isPackage, BigDecimal listPrice, BigDecimal unitPrice,
                                  int quantity, Integer sessions, BaseMode packageMode) {
        BigDecimal q = BigDecimal.valueOf(quantity);
        if (!isPackage) return nz(listPrice).multiply(q);
        if (packageMode == BaseMode.ORDER_UNIT_PRICE) return nz(unitPrice).multiply(q);
        int s = sessions == null || sessions <= 0 ? 1 : sessions;
        return nz(listPrice).multiply(BigDecimal.valueOf(s)).multiply(q);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public enum Verdict { OK, OVER, UNDER }

    /** So tiền đã ghi với tiền đúng, cho phép lệch trong {@code toleranceVnd}. */
    public static Verdict verdict(BigDecimal expected, BigDecimal entered, BigDecimal toleranceVnd) {
        BigDecimal diff = nz(entered).subtract(nz(expected));
        BigDecimal tol = toleranceVnd == null ? BigDecimal.ZERO : toleranceVnd;
        if (diff.abs().compareTo(tol) <= 0) return Verdict.OK;
        return diff.signum() > 0 ? Verdict.OVER : Verdict.UNDER;
    }
}
