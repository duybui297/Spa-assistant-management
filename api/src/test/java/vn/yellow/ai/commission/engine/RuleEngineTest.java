package vn.yellow.ai.commission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bộ test case cho từng quy tắc hoa hồng Tư vấn (HANDOFF mục 4.1 + Q&A mục 5).
 * Mỗi test là một tình huống anh Duy và khách có thể đọc để duyệt logic.
 * Quy ước: "lẻ" = dòng SERVICE, "gói a+b" = dòng TREATMENT_CARD có tc_total_sessions = a, tc_bonus_sessions = b.
 */
class RuleEngineTest {

    private static final List<CommissionRule> RULES = DefaultRules.RULES;
    private static final OldConfig OLD_3PCT = OldConfig.percent(new BigDecimal("3"));

    private static LineFacts single(String group, int visit, int qty) {
        return new LineFacts(group, group, "", false, null, null, visit, qty);
    }

    private static LineFacts pkg(String group, int sessions, int bonus, int visit) {
        return new LineFacts(group, group, "", true, sessions, bonus, visit, 1);
    }

    private static Evaluation eval(LineFacts f) {
        return RuleEngine.evaluate(f, RULES, OLD_3PCT, new BigDecimal("10000000"));
    }

    private static void expectRule(LineFacts f, String ruleId, String percent) {
        Evaluation e = eval(f);
        assertEquals(Evaluation.Source.RULE, e.source(), "phải khớp bảng luật mới: " + f.describe());
        assertEquals(ruleId, e.ruleId(), f.describe());
        assertEquals(0, new BigDecimal(percent).compareTo(e.percent()), f.describe());
    }

    private static void expectOld(LineFacts f) {
        Evaluation e = eval(f);
        assertEquals(Evaluation.Source.OLD_PERCENT, e.source(), "phải giữ % cấu hình cũ: " + f.describe());
        assertNull(e.ruleId());
        assertEquals(0, new BigDecimal("3").compareTo(e.percent()));
    }

    // ===================== Mức 2% =====================

    @Test
    @DisplayName("2% — Gói Laser 10+2")
    void laserPackage10plus2() {
        expectRule(pkg("LASER", 10, 2, 1), "LASER_10_2", "2");
        expectRule(pkg("LASER", 10, 2, 3), "LASER_10_2", "2");
    }

    @Test
    @DisplayName("2% — Gói Laser sắc tố 10+2 cũng là 2% (B.4: trùng tiêu chí, gói 10+2 được 2%)")
    void laserSacTo10plus2Wins() {
        expectRule(pkg("LASER_SAC_TO", 10, 2, 1), "LASER_10_2", "2");
    }

    @Test
    @DisplayName("2% — Gói Meso–PRP 5+1")
    void mesoPrp5plus1() {
        expectRule(pkg("MESO_PRP", 5, 1, 1), "MESO_PRP_5_1", "2");
    }

    @Test
    @DisplayName("2% — CFU lần đầu (lẻ hay gói đều được)")
    void cfuFirst() {
        expectRule(single("CFU", 1, 1), "CFU_1", "2");
        expectRule(pkg("CFU", 5, 1, 1), "CFU_1", "2");
    }

    // ===================== Mức 1,5% =====================

    @Test
    @DisplayName("1,5% — Filler / Botox / Meso / Laser / PRP lẻ, lần đầu")
    void singleFirstVisit() {
        for (String g : List.of("FILLER", "BOTOX", "MESO", "LASER", "PRP")) {
            expectRule(single(g, 1, 1), "LE_LAN_DAU", "1.5");
        }
        // Filler lần đầu 1cc vẫn là lần đầu → 1,5%
        expectRule(single("FILLER", 1, 1), "LE_LAN_DAU", "1.5");
    }

    @Test
    @DisplayName("1,5% — Filler từ lần 2 và ≥ 2cc (biên đúng 2cc)")
    void fillerRepeatAtLeast2cc() {
        expectRule(single("FILLER", 2, 2), "FILLER_2_DU", "1.5");
        expectRule(single("FILLER", 4, 3), "FILLER_2_DU", "1.5");
    }

    @Test
    @DisplayName("1,5% — Botox từ lần 2 và ≥ 2 vùng (D.2)")
    void botoxRepeatAtLeast2Areas() {
        expectRule(single("BOTOX", 2, 2), "BOTOX_2_DU", "1.5");
    }

    @Test
    @DisplayName("1,5% — CFU từ lần 2; lần 3, lần 4… tính như lần 2")
    void cfuRepeat() {
        expectRule(single("CFU", 2, 1), "CFU_2", "1.5");
        expectRule(single("CFU", 5, 1), "CFU_2", "1.5");
    }

    @Test
    @DisplayName("1,5% — Tricopat tóc (mọi lần, lẻ hay gói)")
    void tricopat() {
        expectRule(single("TRICOPAT", 1, 1), "TRICOPAT", "1.5");
        expectRule(single("TRICOPAT", 3, 1), "TRICOPAT", "1.5");
        expectRule(pkg("TRICOPAT", 5, 1, 1), "TRICOPAT", "1.5");
    }

    @Test
    @DisplayName("1,5% — Gói Laser sắc tố/nám/thâm 5+1 và Triệt lông 5+1")
    void sacToAndTrietLong5plus1() {
        expectRule(pkg("LASER_SAC_TO", 5, 1, 1), "LASER_ST_5_1", "1.5");
        expectRule(pkg("TRIET_LONG", 5, 1, 2), "LASER_ST_5_1", "1.5");
    }

    @Test
    @DisplayName("1,5% — Gói sẹo rỗ (mọi số buổi)")
    void seoRo() {
        expectRule(pkg("SEO_RO", 5, 1, 1), "SEO_RO", "1.5");
        expectRule(pkg("SEO_RO", 3, 0, 1), "SEO_RO", "1.5");
    }

    @Test
    @DisplayName("1,5% — Gói CSD / Aquapeel / TCA / Điện di 5+1")
    void csd5plus1() {
        expectRule(pkg("CSD", 5, 1, 1), "CSD_5_1", "1.5");
    }

    @Test
    @DisplayName("1,5% — Gói 3+1 Laser fraction, RF Needle")
    void package3plus1() {
        expectRule(pkg("LASER_FRACTION", 3, 1, 1), "GOI_3_1", "1.5");
        expectRule(pkg("RF_NEEDLE", 3, 1, 1), "GOI_3_1", "1.5");
    }

    // ===================== Mức 0,5% =====================

    @Test
    @DisplayName("0,5% — Filler lần 2 dưới 2cc (B.5)")
    void fillerRepeatUnder2cc() {
        expectRule(single("FILLER", 2, 1), "NHAC_LAI", "0.5");
    }

    @Test
    @DisplayName("0,5% — Botox lần 2 dưới 2 vùng (D.2)")
    void botoxRepeatUnder2() {
        expectRule(single("BOTOX", 3, 1), "NHAC_LAI", "0.5");
    }

    @Test
    @DisplayName("0,5% — Meso lẻ tiêm nhắc lại (mọi số lượng)")
    void mesoRepeat() {
        expectRule(single("MESO", 2, 1), "NHAC_LAI", "0.5");
        expectRule(single("MESO", 2, 3), "NHAC_LAI", "0.5");
    }

    // ===================== Không khớp → % cấu hình cũ (B.6) =====================

    @Test
    @DisplayName("Cũ — Dịch vụ chưa gán nhóm / ngoài bảng")
    void outsideTable() {
        expectOld(single(DefaultRules.GROUP_OTHER, 1, 1));
        expectOld(pkg(DefaultRules.GROUP_OTHER, 10, 2, 1));
    }

    @Test
    @DisplayName("Cũ — Gói phải đúng số buổi: Laser 10+1, Laser 5+1, Meso–PRP 10+2 không khớp")
    void packageMustMatchSessionsExactly() {
        expectOld(pkg("LASER", 10, 1, 1));
        expectOld(pkg("LASER", 5, 1, 1));
        expectOld(pkg("MESO_PRP", 10, 2, 1));
        expectOld(pkg("CSD", 3, 1, 1));
        expectOld(pkg("LASER_FRACTION", 5, 1, 1));
    }

    @Test
    @DisplayName("Cũ — Laser / PRP lẻ từ lần 2 không có trong bảng")
    void laserPrpSingleRepeat() {
        expectOld(single("LASER", 2, 1));
        expectOld(single("PRP", 2, 1));
    }

    @Test
    @DisplayName("Cũ — Laser sắc tố, Triệt lông, CSD lẻ không có trong bảng")
    void otherSingles() {
        expectOld(single("LASER_SAC_TO", 1, 1));
        expectOld(single("TRIET_LONG", 1, 1));
        expectOld(single("CSD", 1, 1));
        expectOld(single("MESO_PRP", 1, 1));
    }

    @Test
    @DisplayName("Cũ — Gói Filler / Botox / Meso (không phải lẻ) không có trong bảng")
    void injectablePackages() {
        expectOld(pkg("FILLER", 3, 1, 1));
        expectOld(pkg("BOTOX", 3, 1, 2));
        expectOld(pkg("MESO", 5, 1, 1));
    }

    @Test
    @DisplayName("Cũ — Dịch vụ cấu hình thưởng tiền cố định: tiền = thưởng × số lượng")
    void oldCashBonus() {
        Evaluation e = RuleEngine.evaluate(single(DefaultRules.GROUP_OTHER, 1, 3), RULES,
                OldConfig.cash(new BigDecimal("50000")), new BigDecimal("900000"));
        assertEquals(Evaluation.Source.OLD_CASH, e.source());
        assertEquals(0, new BigDecimal("150000").compareTo(e.expectedAmount()));
    }

    @Test
    @DisplayName("Cũ — Dịch vụ không cấu hình hoa hồng Tư vấn: hoa hồng đúng = 0")
    void noOldConfig() {
        Evaluation e = RuleEngine.evaluate(single(DefaultRules.GROUP_OTHER, 1, 1), RULES, OldConfig.NONE,
                new BigDecimal("900000"));
        assertEquals(Evaluation.Source.NONE, e.source());
        assertEquals(0, BigDecimal.ZERO.compareTo(e.expectedAmount()));
    }

    // ===================== Tắt / sửa tiêu chí (D.6) =====================

    @Test
    @DisplayName("D.6 — Tắt tiêu chí thì dòng rơi về tiêu chí kế tiếp hoặc % cấu hình cũ")
    void disabledRuleFallsThrough() {
        List<CommissionRule> rules = RULES.stream()
                .map(r -> r.id().equals("LE_LAN_DAU")
                        ? new CommissionRule(r.id(), r.label(), r.percent(), false, r.priority(), r.conditions())
                        : r)
                .toList();
        Evaluation e = RuleEngine.evaluate(single("FILLER", 1, 1), rules, OLD_3PCT, new BigDecimal("1000000"));
        assertEquals(Evaluation.Source.OLD_PERCENT, e.source());
    }

    @Test
    @DisplayName("D.6 — Sửa % tiêu chí thì tiền hoa hồng tính lại theo % mới")
    void editedPercent() {
        List<CommissionRule> rules = RULES.stream()
                .map(r -> r.id().equals("NHAC_LAI")
                        ? new CommissionRule(r.id(), r.label(), new BigDecimal("0.8"), true, r.priority(), r.conditions())
                        : r)
                .toList();
        Evaluation e = RuleEngine.evaluate(single("MESO", 2, 1), rules, OLD_3PCT, new BigDecimal("2000000"));
        assertEquals(0, new BigDecimal("16000").compareTo(e.expectedAmount()));
    }

    @Test
    @DisplayName("Trùng tiêu chí — tiêu chí đứng trên (priority nhỏ hơn) thắng; matchAll trả đủ để cảnh báo")
    void overlapPriority() {
        CommissionRule extra = new CommissionRule("X", "Mọi Laser gói 10+2", new BigDecimal("1"), true, 5,
                List.of(new RuleCondition(List.of("LASER"), RuleCondition.Kind.PACKAGE, 10, 2, null, null, null)));
        List<CommissionRule> rules = new java.util.ArrayList<>(RULES);
        rules.add(extra);
        LineFacts f = pkg("LASER", 10, 2, 1);
        assertEquals("X", RuleEngine.match(f, rules).orElseThrow().id());
        assertEquals(2, RuleEngine.matchAll(f, rules).size());
    }

    // ===================== Tiền hoa hồng & căn cứ tính =====================

    @Test
    @DisplayName("B.7 — Lẻ: căn cứ = giá gốc × số lượng (không theo giá đã giảm)")
    void baseSingle() {
        BigDecimal base = RuleEngine.base(false, new BigDecimal("4500000"), new BigDecimal("3000000"), 2, null,
                RuleEngine.BaseMode.LIST_PRICE_X_SESSIONS);
        assertEquals(0, new BigDecimal("9000000").compareTo(base));
        assertEquals(0, new BigDecimal("135000").compareTo(RuleEngine.amount(base, new BigDecimal("1.5"))));
    }

    @Test
    @DisplayName("D.5 — Gói: tính một lần trên tổng tiền gói (giá gốc 1 buổi × số buổi chính)")
    void basePackage() {
        BigDecimal base = RuleEngine.base(true, new BigDecimal("1200000"), new BigDecimal("9000000"), 1, 10,
                RuleEngine.BaseMode.LIST_PRICE_X_SESSIONS);
        assertEquals(0, new BigDecimal("12000000").compareTo(base));
        BigDecimal base2 = RuleEngine.base(true, new BigDecimal("1200000"), new BigDecimal("9000000"), 1, 10,
                RuleEngine.BaseMode.ORDER_UNIT_PRICE);
        assertEquals(0, new BigDecimal("9000000").compareTo(base2));
    }

    @Test
    @DisplayName("Làm tròn đến đồng (HALF_UP)")
    void rounding() {
        assertEquals(0, new BigDecimal("16667").compareTo(RuleEngine.amount(new BigDecimal("3333333"), new BigDecimal("0.5"))));
    }

    @Test
    @DisplayName("% đã ghi được tính ngược = commission_amount ÷ căn cứ")
    void impliedPercent() {
        assertEquals(0, new BigDecimal("1.50").compareTo(
                RuleEngine.impliedPercent(new BigDecimal("135000"), new BigDecimal("9000000"))));
        assertNull(RuleEngine.impliedPercent(new BigDecimal("1"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Kết luận: đúng / ghi dư / ghi thiếu, có ngưỡng chênh lệch cho phép")
    void verdicts() {
        BigDecimal tol = new BigDecimal("1000");
        assertEquals(RuleEngine.Verdict.OK, RuleEngine.verdict(new BigDecimal("135000"), new BigDecimal("135800"), tol));
        assertEquals(RuleEngine.Verdict.OVER, RuleEngine.verdict(new BigDecimal("135000"), new BigDecimal("180000"), tol));
        assertEquals(RuleEngine.Verdict.UNDER, RuleEngine.verdict(new BigDecimal("135000"), new BigDecimal("45000"), tol));
    }

    // ===================== Lần thứ mấy (B.2, D.1, D.3) =====================

    private static VisitCounter.HistoryLine h(String item, String cust, String group, String date, String order) {
        return new VisitCounter.HistoryLine(item, cust, group, LocalDateTime.parse(date + "T10:00"), order,
                LocalDateTime.parse(date + "T10:00"));
    }

    @Test
    @DisplayName("Lần thứ mấy — đếm theo NHÓM dịch vụ, tính cả gói lẫn lẻ, theo thứ tự ngày")
    void visitByGroup() {
        Map<String, Integer> v = VisitCounter.assign(List.of(
                h("i3", "C1", "FILLER", "2026-12-20", "o3"),
                h("i1", "C1", "FILLER", "2026-12-01", "o1"),
                h("i2", "C1", "BOTOX", "2026-12-05", "o2"),
                h("i4", "C2", "FILLER", "2026-12-21", "o4")));
        assertEquals(1, (int) v.get("i1"));
        assertEquals(1, (int) v.get("i2"));
        assertEquals(2, (int) v.get("i3"));
        assertEquals(1, (int) v.get("i4"), "khách khác đếm riêng");
    }

    @Test
    @DisplayName("Lần thứ mấy — 2 dòng cùng nhóm trong 1 đơn: dòng sau là lần 2")
    void visitSameOrder() {
        Map<String, Integer> v = VisitCounter.assign(List.of(
                new VisitCounter.HistoryLine("a", "C1", "MESO", LocalDateTime.parse("2026-12-01T09:00"), "o1",
                        LocalDateTime.parse("2026-12-01T09:00:01")),
                new VisitCounter.HistoryLine("b", "C1", "MESO", LocalDateTime.parse("2026-12-01T09:00"), "o1",
                        LocalDateTime.parse("2026-12-01T09:00:02"))));
        assertEquals(1, (int) v.get("a"));
        assertEquals(2, (int) v.get("b"));
    }

    @Test
    @DisplayName("Lần thứ mấy — khách vãng lai (không có mã khách) luôn tính lần đầu")
    void visitGuest() {
        Map<String, Integer> v = VisitCounter.assign(List.of(
                h("a", null, "FILLER", "2026-12-01", "o1"),
                h("b", null, "FILLER", "2026-12-02", "o2")));
        assertEquals(1, (int) v.get("a"));
        assertEquals(1, (int) v.get("b"));
    }

    @Test
    @DisplayName("Lý do luôn ghi rõ nhóm, lẻ/gói, lần thứ mấy, số lượng")
    void reasonText() {
        LineFacts f = new LineFacts("FILLER", "Filler", "cc", false, null, null, 2, 1);
        Evaluation e = eval(f);
        assertTrue(e.reason().contains("Filler lẻ, lần 2, SL 1 cc"), e.reason());
    }
}
