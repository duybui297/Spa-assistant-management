package vn.yellow.ai.commission.engine;

import static vn.yellow.ai.commission.engine.RuleCondition.Kind.ANY;
import static vn.yellow.ai.commission.engine.RuleCondition.Kind.PACKAGE;
import static vn.yellow.ai.commission.engine.RuleCondition.Kind.SINGLE;
import static vn.yellow.ai.commission.engine.RuleCondition.Visit.FIRST;
import static vn.yellow.ai.commission.engine.RuleCondition.Visit.REPEAT;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng luật mặc định theo "Bảng % nhân viên.docx" của khách (HANDOFF mục 4.1) và Q&A (mục 5).
 * Dùng để khởi tạo bảng spa_ai.commission_rule lần đầu và làm chuẩn cho bộ test.
 * Sau khi go-live, admin sửa luật trên màn Bảng luật; file này không còn là nguồn sự thật.
 */
public final class DefaultRules {

    private DefaultRules() {
    }

    public static final String GROUP_OTHER = "KHAC";

    public record Group(String code, String label, String unit) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("FILLER", "Filler", "cc"),
            new Group("BOTOX", "Botox", "vùng"),
            new Group("MESO", "Meso", "lần"),
            new Group("PRP", "PRP", "lần"),
            new Group("MESO_PRP", "Meso–PRP", "lần"),
            new Group("CFU", "CFU", "lần"),
            new Group("LASER", "Laser", "lần"),
            new Group("LASER_SAC_TO", "Laser sắc tố / nám / thâm", "lần"),
            new Group("TRIET_LONG", "Triệt lông (Thunder / Diot / DPL)", "lần"),
            new Group("SEO_RO", "Sẹo rỗ", "lần"),
            new Group("CSD", "CSD / Aquapeel / TCA / Điện di", "lần"),
            new Group("LASER_FRACTION", "Laser fraction", "lần"),
            new Group("RF_NEEDLE", "RF Needle", "lần"),
            new Group("TRICOPAT", "Tricopat tóc", "lần"),
            new Group(GROUP_OTHER, "Khác (ngoài bảng)", ""));

    private static RuleCondition c(List<String> groups, RuleCondition.Kind kind, Integer sessions, Integer bonus,
                                   RuleCondition.Visit visit, Integer qtyMin, Integer qtyMax) {
        return new RuleCondition(groups, kind, sessions, bonus, visit, qtyMin, qtyMax);
    }

    private static CommissionRule r(String id, String label, String pct, int priority, RuleCondition... conds) {
        return new CommissionRule(id, label, new BigDecimal(pct), true, priority, List.of(conds));
    }

    public static final List<CommissionRule> RULES = List.of(
            // ---- 2% ----
            r("LASER_10_2", "Gói Laser 10+2 (mọi loại laser)", "2", 10,
                    c(List.of("LASER", "LASER_SAC_TO"), PACKAGE, 10, 2, null, null, null)),
            r("MESO_PRP_5_1", "Gói Meso–PRP 5+1", "2", 20,
                    c(List.of("MESO_PRP"), PACKAGE, 5, 1, null, null, null)),
            r("CFU_1", "CFU — khách làm lần đầu", "2", 30,
                    c(List.of("CFU"), ANY, null, null, FIRST, null, null)),
            // ---- 1,5% ----
            r("LE_LAN_DAU", "Filler / Botox / Meso / Laser / PRP — lẻ, lần đầu", "1.5", 40,
                    c(List.of("FILLER", "BOTOX", "MESO", "LASER", "PRP"), SINGLE, null, null, FIRST, null, null)),
            r("FILLER_2_DU", "Filler từ lần 2 — từ 2cc trở lên", "1.5", 50,
                    c(List.of("FILLER"), SINGLE, null, null, REPEAT, 2, null)),
            r("BOTOX_2_DU", "Botox từ lần 2 — từ 2 vùng trở lên", "1.5", 60,
                    c(List.of("BOTOX"), SINGLE, null, null, REPEAT, 2, null)),
            r("CFU_2", "CFU — từ lần 2 trở đi", "1.5", 70,
                    c(List.of("CFU"), ANY, null, null, REPEAT, null, null)),
            r("TRICOPAT", "Tricopat tóc", "1.5", 80,
                    c(List.of("TRICOPAT"), ANY, null, null, null, null, null)),
            r("LASER_ST_5_1", "Gói Laser sắc tố / nám / thâm 5+1, Triệt lông 5+1", "1.5", 90,
                    c(List.of("LASER_SAC_TO", "TRIET_LONG"), PACKAGE, 5, 1, null, null, null)),
            r("SEO_RO", "Gói sẹo rỗ", "1.5", 100,
                    c(List.of("SEO_RO"), PACKAGE, null, null, null, null, null)),
            r("CSD_5_1", "Gói CSD / Aquapeel / TCA / Điện di 5+1", "1.5", 110,
                    c(List.of("CSD"), PACKAGE, 5, 1, null, null, null)),
            r("GOI_3_1", "Gói 3+1: Laser fraction, RF Needle", "1.5", 120,
                    c(List.of("LASER_FRACTION", "RF_NEEDLE"), PACKAGE, 3, 1, null, null, null)),
            // ---- 0,5% ----
            r("NHAC_LAI", "Meso / Botox / Filler lẻ — tiêm nhắc lại (Filler < 2cc, Botox < 2 vùng)", "0.5", 130,
                    c(List.of("FILLER"), SINGLE, null, null, REPEAT, null, 1),
                    c(List.of("BOTOX"), SINGLE, null, null, REPEAT, null, 1),
                    c(List.of("MESO"), SINGLE, null, null, REPEAT, null, null)));
}
