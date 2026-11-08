package vn.yellow.ai.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.yellow.ai.common.ApiException;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/auth/login")
    public Map<String, String> login(@RequestBody LoginRequest req) {
        String token = auth.login(req.username(), req.password())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu"));
        return Map.of("token", token, "username", req.username().trim());
    }

    @PostMapping("/auth/logout")
    public Map<String, Boolean> logout(HttpServletRequest req) {
        auth.logout(AuthInterceptor.bearer(req));
        return Map.of("ok", true);
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(HttpServletRequest req) {
        return Map.of("username", req.getAttribute(AuthInterceptor.USER_ATTR));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
