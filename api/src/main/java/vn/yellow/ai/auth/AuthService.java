package vn.yellow.ai.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import vn.yellow.ai.config.AppProperties;
import vn.yellow.ai.config.DataSourceConfig;

/**
 * Đăng nhập bằng tài khoản riêng của app AI (HANDOFF 7.6), không dùng ms-auth.
 * Phiên không tự hết hạn, chỉ mất khi bấm Đăng xuất. Chỉ lưu SHA-256 của token.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String username;
    private final String passwordHash;

    public AuthService(JdbcTemplate jdbc, AppProperties props, DataSourceConfig.FlywayRunner migrated) {
        this.jdbc = jdbc;
        this.username = props.auth().username();
        String h = props.auth().passwordHash();
        if (h == null || h.isBlank()) {
            log.warn("APP_ADMIN_PASSWORD_HASH trống → dùng mật khẩu mặc định của V1. Hãy đặt biến này trước khi go-live.");
            h = encoder.encode("yellow123");
        }
        this.passwordHash = h;
    }

    public Optional<String> login(String user, String password) {
        if (user == null || password == null) return Optional.empty();
        if (!username.equals(user.trim()) || !encoder.matches(password, passwordHash)) return Optional.empty();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_session (token_hash, username, created_at, last_seen_at) VALUES (?, ?, ?, ?)",
                sha256(token), username, now, now);
        return Optional.of(token);
    }

    public Optional<String> userOf(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        List<String> users = jdbc.queryForList("SELECT username FROM app_session WHERE token_hash = ?",
                String.class, sha256(token));
        return users.stream().findFirst();
    }

    public void logout(String token) {
        if (token != null) jdbc.update("DELETE FROM app_session WHERE token_hash = ?", sha256(token));
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
