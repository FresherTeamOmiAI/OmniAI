package com.omniai.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bật LOGIN cho chatbot_reader. Mật khẩu chỉ đến từ env, không ghi vào migration.
 *
 * <p>{@code omniai.chatbot-reader.overwrite-password} (mặc định true): DB local của từng máy thì đặt lại mật khẩu mỗi lần
 * khởi động. DB dùng chung (profile supabase) đặt false: role đã LOGIN thì giữ nguyên mật khẩu, vì mỗi máy có
 * {@code CHATBOT_DB_PASSWORD} khác nhau sẽ ghi đè lẫn nhau, ai-brain của người khác đăng nhập sai tới mức pooler chặn
 * role (decisions #45).
 */
@Component
public class ChatbotReaderSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatbotReaderSetup.class);

    private final JdbcTemplate jdbc;
    private final String password;
    private final boolean overwritePassword;

    public ChatbotReaderSetup(JdbcTemplate jdbc,
                              @Value("${omniai.chatbot-db-password:}") String password,
                              @Value("${omniai.chatbot-reader.overwrite-password:true}") boolean overwritePassword) {
        this.jdbc = jdbc;
        this.password = password;
        this.overwritePassword = overwritePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Thiếu CHATBOT_DB_PASSWORD, chatbot_reader vẫn NOLOGIN");
            return;
        }
        if (!overwritePassword && canLogin()) {
            log.info("chatbot_reader đã LOGIN, giữ mật khẩu hiện tại (DB dùng chung, không ghi đè)");
            return;
        }
        jdbc.execute("ALTER ROLE chatbot_reader LOGIN PASSWORD " + quote(password));
        log.info("chatbot_reader đã được phép đăng nhập, chỉ SELECT chatbot_catalog");
    }

    private boolean canLogin() {
        List<Boolean> rows = jdbc.queryForList(
                "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'chatbot_reader'", Boolean.class);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
