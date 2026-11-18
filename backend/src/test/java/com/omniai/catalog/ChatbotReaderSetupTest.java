package com.omniai.catalog;

import com.omniai.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mật khẩu chatbot_reader: DB local đặt lại mỗi lần khởi động; DB dùng chung (overwrite-password=false) không ghi đè
 * khi role đã LOGIN (decisions #41). Đăng nhập thật bằng JDBC vào container để kiểm mật khẩu.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ChatbotReaderSetupTest {

    @Autowired
    JdbcTemplate jdbc;

    private String url;

    @BeforeEach
    void readUrl() {
        url = jdbc.execute((Connection c) -> c.getMetaData().getURL());
    }

    /** Trả role về trạng thái của context test (không có CHATBOT_DB_PASSWORD nên NOLOGIN). */
    @AfterEach
    void restoreRole() {
        jdbc.execute("ALTER ROLE chatbot_reader NOLOGIN PASSWORD NULL");
    }

    @Test
    void overwriteTrueResetsPasswordEveryStart() {
        setup("mat-khau-a", true);
        assertThat(canLogin("mat-khau-a")).isTrue();

        setup("mat-khau-b", true);
        assertThat(canLogin("mat-khau-b")).isTrue();
        assertThat(canLogin("mat-khau-a")).isFalse();
    }

    @Test
    void overwriteFalseKeepsPasswordWhenRoleAlreadyLogsIn() {
        setup("mat-khau-chu-project", true);

        setup("mat-khau-may-khac", false);
        assertThat(canLogin("mat-khau-chu-project")).isTrue();
        assertThat(canLogin("mat-khau-may-khac")).isFalse();
    }

    @Test
    void overwriteFalseStillEnablesLoginOnFreshDatabase() {
        setup("mat-khau-dau-tien", false);
        assertThat(canLogin("mat-khau-dau-tien")).isTrue();
    }

    @Test
    void blankPasswordLeavesRoleNologin() {
        setup("", true);
        assertThat(jdbc.queryForObject("SELECT rolcanlogin FROM pg_roles WHERE rolname = 'chatbot_reader'", Boolean.class))
                .isFalse();
    }

    @Test
    void quoteEscapesSingleQuotes() {
        assertThat(ChatbotReaderSetup.quote("a'b")).isEqualTo("'a''b'");
    }

    private void setup(String password, boolean overwrite) {
        new ChatbotReaderSetup(jdbc, password, overwrite).run(null);
    }

    private boolean canLogin(String password) {
        try (Connection c = DriverManager.getConnection(url, "chatbot_reader", password)) {
            return c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
