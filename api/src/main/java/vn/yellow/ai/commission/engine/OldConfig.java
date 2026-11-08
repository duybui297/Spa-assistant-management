package vn.yellow.ai.commission.engine;

import java.math.BigDecimal;

/**
 * "% cấu hình cũ" của dịch vụ cho chức vụ Tư vấn, đọc từ bảng {@code commission} của Spa
 * (role = CONSULTANT, active = true, service_id = dịch vụ).
 *
 * @param type        PERCENT (PERCENT_SERVICES_REVENUE), CASH (CASH_BONUS) hoặc NONE (không cấu hình)
 * @param percent     % doanh thu dịch vụ khi type = PERCENT
 * @param cashPerUnit tiền thưởng cho 1 đơn vị khi type = CASH
 */
public record OldConfig(Type type, BigDecimal percent, BigDecimal cashPerUnit) {

    public enum Type { PERCENT, CASH, NONE }

    public static final OldConfig NONE = new OldConfig(Type.NONE, null, null);

    public static OldConfig percent(BigDecimal p) {
        return new OldConfig(Type.PERCENT, p, null);
    }

    public static OldConfig cash(BigDecimal perUnit) {
        return new OldConfig(Type.CASH, null, perUnit);
    }

    /** Chuyển từ giá trị cột commission.commission_type của Spa. */
    public static OldConfig fromSpa(String commissionType, BigDecimal percentageRevenue, BigDecimal cashBonusAmount) {
        if ("PERCENT_SERVICES_REVENUE".equals(commissionType) && percentageRevenue != null) return percent(percentageRevenue);
        if ("CASH_BONUS".equals(commissionType) && cashBonusAmount != null) return cash(cashBonusAmount);
        return NONE;
    }
}
