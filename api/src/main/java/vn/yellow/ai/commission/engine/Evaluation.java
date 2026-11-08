package vn.yellow.ai.commission.engine;

import java.math.BigDecimal;

/**
 * Kết quả chấm một dòng.
 *
 * @param source         RULE (khớp bảng luật mới), OLD_PERCENT / OLD_CASH (giữ cấu hình cũ), NONE (không có cấu hình)
 * @param ruleId         mã tiêu chí khớp, null khi không khớp tiêu chí nào
 * @param ruleLabel      tên tiêu chí khớp
 * @param percent        % áp dụng (null khi tính theo tiền thưởng cố định)
 * @param base           số tiền làm căn cứ tính % (giá gốc × số lượng…)
 * @param expectedAmount tiền hoa hồng đúng
 * @param reason         lý do bằng lời
 */
public record Evaluation(
        Source source,
        String ruleId,
        String ruleLabel,
        BigDecimal percent,
        BigDecimal base,
        BigDecimal expectedAmount,
        String reason) {

    public enum Source { RULE, OLD_PERCENT, OLD_CASH, NONE }
}
