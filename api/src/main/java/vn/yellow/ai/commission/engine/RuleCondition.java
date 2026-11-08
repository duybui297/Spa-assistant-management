package vn.yellow.ai.commission.engine;

import java.util.List;

/**
 * Một điều kiện của tiêu chí. Mọi trường để trống (null / ANY) nghĩa là "không xét".
 *
 * @param groups   các nhóm dịch vụ áp dụng (mã nhóm, ví dụ FILLER). Rỗng = mọi nhóm.
 * @param kind     lẻ / gói / bất kỳ
 * @param sessions số buổi chính của gói (ví dụ 10 trong 10+2). null = không xét
 * @param bonus    số buổi tặng của gói (ví dụ 2 trong 10+2). null = không xét
 * @param visit    lần đầu / từ lần 2 / bất kỳ
 * @param qtyMin   số lượng tối thiểu (cc, vùng…), tính cả biên. null = không xét
 * @param qtyMax   số lượng tối đa, tính cả biên. null = không xét
 */
public record RuleCondition(
        List<String> groups,
        Kind kind,
        Integer sessions,
        Integer bonus,
        Visit visit,
        Integer qtyMin,
        Integer qtyMax) {

    public enum Kind { ANY, SINGLE, PACKAGE }

    public enum Visit { ANY, FIRST, REPEAT }

    public RuleCondition {
        groups = groups == null ? List.of() : List.copyOf(groups);
        kind = kind == null ? Kind.ANY : kind;
        visit = visit == null ? Visit.ANY : visit;
    }

    public boolean matches(LineFacts f) {
        if (!groups.isEmpty() && !groups.contains(f.groupCode())) return false;
        if (kind == Kind.SINGLE && f.isPackage()) return false;
        if (kind == Kind.PACKAGE && !f.isPackage()) return false;
        if (sessions != null && (!f.isPackage() || !sessions.equals(f.sessions()))) return false;
        if (bonus != null && (!f.isPackage() || !bonus.equals(f.bonus()))) return false;
        if (visit == Visit.FIRST && f.visit() != 1) return false;
        if (visit == Visit.REPEAT && f.visit() < 2) return false;
        if (qtyMin != null && f.quantity() < qtyMin) return false;
        if (qtyMax != null && f.quantity() > qtyMax) return false;
        return true;
    }

    /** Mô tả điều kiện bằng lời, dùng cho màn Bảng luật. */
    public String describe(java.util.function.Function<String, String> groupLabel, String unitHint) {
        StringBuilder sb = new StringBuilder();
        sb.append(groups.isEmpty() ? "Mọi nhóm" : String.join(" / ", groups.stream().map(groupLabel).toList()));
        if (kind == Kind.SINGLE) sb.append(", lẻ");
        if (kind == Kind.PACKAGE) {
            sb.append(", gói");
            if (sessions != null || bonus != null) {
                sb.append(' ').append(sessions == null ? "?" : sessions).append('+').append(bonus == null ? "?" : bonus);
            }
        }
        if (visit == Visit.FIRST) sb.append(", lần đầu");
        if (visit == Visit.REPEAT) sb.append(", từ lần 2");
        String u = unitHint == null || unitHint.isBlank() ? "" : " " + unitHint;
        if (qtyMin != null && qtyMax != null) sb.append(", SL ").append(qtyMin).append("–").append(qtyMax).append(u);
        else if (qtyMin != null) sb.append(", SL ≥ ").append(qtyMin).append(u);
        else if (qtyMax != null) sb.append(", SL ≤ ").append(qtyMax).append(u);
        return sb.toString();
    }
}
