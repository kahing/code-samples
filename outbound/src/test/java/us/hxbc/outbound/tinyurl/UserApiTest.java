package us.hxbc.outbound.tinyurl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserApiTest {
    private Path db;
    private UserApi api;

    @Before
    public void setUp() throws Exception {
        db = Paths.get(Utils.randomString(6));
        //api = new RestApi(0, Paths.get(":memory:"));

        api = new UserApi(new RestApi(0, db));
    }

    @After
    public void tearDown() throws Exception {
        if (api != null) {
            api.stop();
        }
        Files.delete(db);
    }

    @Test
    public void testCreateAccount() throws Exception {
        api.createAccount("hello", "password", "outbound.com");
        try (Connection conn = api.api.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT hash FROM users where uid=?")) {
                sql.setString(1, "hello");
                assertThat(sql.execute()).isTrue();
                try (ResultSet rs = sql.getResultSet()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.next()).isFalse();
                }
            }
        }

    }

    @Test
    public void testLogin() throws Exception {
        api.createAccount("hello", "password", "outbound.com");
        Response r = api.login("hello", "password");
        assertThat(r).isNotNull();
    }

    @Test
    public void testTokenToUser() throws Exception {
        assertThatThrownBy(() -> api.tokenToUser("foo"))
                .isInstanceOf(ClientErrorException.class);
        api.createAccount("hello", "password", "outbound.com");
        Response r = api.login("hello", "password");
        String token = r.getCookies().get("auth").getValue();
        assertThat(token).isNotNull();

        Map.Entry<String, String> data = api.tokenToUser(token);
        assertThat(data.getKey()).isEqualTo("hello");
        assertThat(data.getValue()).isEqualTo("outbound.com");
    }
}