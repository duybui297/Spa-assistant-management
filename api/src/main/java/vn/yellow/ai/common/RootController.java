package vn.yellow.ai.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trả lời ở "/" để kiểm tra sức khỏe mặc định của Render không sinh lỗi. */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("service", "spa-ai-api", "status", "UP");
    }
}
