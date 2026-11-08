package vn.yellow.ai.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import vn.yellow.ai.commission.engine.RuleEngine;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String corsOrigins,
        Auth auth,
        Db aiDb,
        Db spaDb,
        Mock mock,
        Commission commission) {

    public record Auth(String username, String passwordHash) {
    }

    public record Db(String url, String username, String password, Integer maxPoolSize, Integer queryTimeoutSeconds) {
    }

    public record Mock(boolean enabled, String schema) {
    }

    public record Commission(
            String effectiveDate,
            BigDecimal toleranceVnd,
            RuleEngine.BaseMode packageBase,
            String consultantRoleValues,
            String oldConfigRole,
            Integer historyMonths) {

        /** Ngày áp dụng luật mới (chuỗi ISO yyyy-MM-dd trong biến môi trường). */
        public LocalDate effectiveDay() {
            return LocalDate.parse(effectiveDate == null || effectiveDate.isBlank() ? "2026-12-01" : effectiveDate.trim());
        }

        public List<String> consultantRoles() {
            return Arrays.stream(consultantRoleValues == null ? new String[0] : consultantRoleValues.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public List<String> corsOriginList() {
        return Arrays.stream(corsOrigins == null ? new String[0] : corsOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
