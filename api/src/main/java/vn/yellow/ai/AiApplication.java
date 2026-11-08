package vn.yellow.ai;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiApplication {

    public static void main(String[] args) {
        // Tiện ích: java -jar app.jar hash <mat-khau>  → in BCrypt để đặt vào APP_ADMIN_PASSWORD_HASH
        if (args.length == 2 && "hash".equals(args[0])) {
            System.out.println(new BCryptPasswordEncoder().encode(args[1]));
            return;
        }
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(AiApplication.class, args);
    }
}
