package vn.yellow.ai.commission.engine;

/**
 * Dữ kiện của một dòng đơn dùng để chấm %.
 *
 * @param groupCode  mã nhóm dịch vụ (admin gán, D.1). Chưa gán = {@link DefaultRules#GROUP_OTHER}
 * @param groupLabel tên nhóm để ghi lý do
 * @param unit       đơn vị số lượng (cc, vùng, lần…) để ghi lý do
 * @param isPackage  dòng bán gói (order_items.item_type = TREATMENT_CARD)
 * @param sessions   số buổi chính của gói (order_items.tc_total_sessions)
 * @param bonus      số buổi tặng (order_items.tc_bonus_sessions)
 * @param visit      khách làm nhóm này lần thứ mấy (1 = lần đầu)
 * @param quantity   order_items.quantity (Filler = số cc, Botox = số vùng, B.3)
 */
public record LineFacts(
        String groupCode,
        String groupLabel,
        String unit,
        boolean isPackage,
        Integer sessions,
        Integer bonus,
        int visit,
        int quantity) {

    public String describe() {
        StringBuilder sb = new StringBuilder(groupLabel == null ? groupCode : groupLabel);
        if (isPackage) {
            sb.append(" gói ").append(sessions == null ? "?" : sessions).append('+').append(bonus == null ? 0 : bonus);
        } else {
            sb.append(" lẻ");
        }
        sb.append(", lần ").append(visit);
        sb.append(", SL ").append(quantity);
        if (unit != null && !unit.isBlank()) sb.append(' ').append(unit);
        return sb.toString();
    }
}
