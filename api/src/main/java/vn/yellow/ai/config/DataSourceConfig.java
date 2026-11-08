package vn.yellow.ai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Hai kết nối tách quyền (HANDOFF 6.1):
 * <ul>
 *   <li><b>aiDataSource</b> — có ghi, DB riêng của app AI (schema spa_ai).</li>
 *   <li><b>spaDataSource</b> — CHỈ ĐỌC, DB Spa. Kết nối đặt read-only; ở V2 user MySQL chỉ có quyền SELECT.</li>
 * </ul>
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    private static HikariDataSource build(String name, AppProperties.Db db, boolean readOnly) {
        HikariConfig c = new HikariConfig();
        c.setPoolName(name);
        c.setJdbcUrl(db.url());
        c.setUsername(db.username());
        c.setPassword(db.password());
        c.setMaximumPoolSize(db.maxPoolSize() == null ? 3 : db.maxPoolSize());
        c.setMinimumIdle(0);
        c.setIdleTimeout(60_000);
        c.setConnectionTimeout(20_000);
        c.setReadOnly(readOnly);
        // Supabase pooler (PgBouncer) không hỗ trợ prepared statement phía server ở chế độ transaction
        if (db.url() != null && db.url().startsWith("jdbc:postgresql")) {
            c.addDataSourceProperty("prepareThreshold", "0");
            c.addDataSourceProperty("reWriteBatchedInserts", "true");
            // pgjdbc mặc định chỉ chặn ghi khi autocommit=false; "always" chặn cả khi autocommit=true
            if (readOnly) c.addDataSourceProperty("readOnlyMode", "always");
        }
        return new HikariDataSource(c);
    }

    @Bean
    @Primary
    public DataSource aiDataSource(AppProperties props) {
        return build("ai-rw", props.aiDb(), false);
    }

    @Bean
    public DataSource spaDataSource(AppProperties props) {
        return build("spa-ro", props.spaDb(), true);
    }

    @Bean
    @Primary
    public JdbcTemplate aiJdbc(DataSource aiDataSource) {
        return new JdbcTemplate(aiDataSource);
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate aiNamedJdbc(DataSource aiDataSource) {
        return new NamedParameterJdbcTemplate(aiDataSource);
    }

    /** Chỉ dùng bên trong {@code vn.yellow.ai.spa.SpaReader}. */
    @Bean
    public NamedParameterJdbcTemplate spaNamedJdbc(@Qualifier("spaDataSource") DataSource spaDataSource,
                                                   AppProperties props) {
        JdbcTemplate t = new JdbcTemplate(spaDataSource);
        Integer timeout = props.spaDb().queryTimeoutSeconds();
        t.setQueryTimeout(timeout == null ? 30 : timeout);
        return new NamedParameterJdbcTemplate(t);
    }

    /** Migration cho spa_ai (luôn chạy) và spa_mock (chỉ khi app.mock.enabled = true, tức V1). */
    @Bean
    public FlywayRunner flywayRunner(DataSource aiDataSource, AppProperties props) {
        String vendor = props.aiDb().url().startsWith("jdbc:mysql") ? "mysql" : "postgresql";
        Map<String, String> placeholders = Map.of(
                "ts", "mysql".equals(vendor) ? "DATETIME" : "TIMESTAMP",
                "text", "TEXT");

        if (props.mock().enabled()) {
            log.warn("APP_MOCK_ENABLED=true → tạo schema {} với dữ liệu GIẢ (chỉ dùng cho V1)", props.mock().schema());
            Flyway.configure()
                    .dataSource(aiDataSource)
                    .schemas(props.mock().schema())
                    .createSchemas(true)
                    .table("flyway_history_mock")
                    .locations("classpath:db/mock")
                    .load()
                    .migrate();
        }

        Flyway.configure()
                .dataSource(aiDataSource)
                .schemas(aiSchema(props.aiDb().url()))
                .createSchemas(true)
                .table("flyway_history_ai")
                .placeholders(placeholders)
                .locations("classpath:db/ai")
                .load()
                .migrate();
        return new FlywayRunner();
    }

    /** Lấy schema từ ?currentSchema=… (Postgres). Với MySQL schema = database trong URL. */
    static String aiSchema(String url) {
        int i = url.indexOf("currentSchema=");
        if (i >= 0) {
            String s = url.substring(i + "currentSchema=".length());
            int amp = s.indexOf('&');
            return amp >= 0 ? s.substring(0, amp) : s;
        }
        String noParams = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return noParams.substring(noParams.lastIndexOf('/') + 1);
    }

    /** Đánh dấu migration đã chạy xong, để các bean khác phụ thuộc vào. */
    public static final class FlywayRunner {
    }
}
