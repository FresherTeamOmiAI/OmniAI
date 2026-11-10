package com.omniai.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bật LOGIN cho chatbot_reader. Mật khẩu chỉ đến từ env, không ghi vào migration. */
@Component
public class ChatbotReaderSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatbotReaderSetup.class);

    private final JdbcTemplate jdbc;
    private final String password;

    public ChatbotReaderSetup(JdbcTemplate jdbc,
                              @Value("${omniai.chatbot-db-password:}") String password) {
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Thiếu CHATBOT_DB_PASSWORD, chatbot_reader vẫn NOLOGIN");
            return;
        }
        jdbc.execute("ALTER ROLE chatbot_reader LOGIN PASSWORD " + quote(password));
        log.info("chatbot_reader đã được phép đăng nhập, chỉ SELECT chatbot_catalog");
    }

    static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
