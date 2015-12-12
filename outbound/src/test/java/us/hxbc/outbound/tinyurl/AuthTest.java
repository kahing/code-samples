package us.hxbc.outbound.tinyurl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthTest {
    private Path db;
    private UserApi userApi;
    private RestApi api;

    @Before
    public void setUp() throws Exception {
        db = Paths.get(Utils.randomString(6));
        //userApi = new RestApi(0, Paths.get(":memory:"));

        api = new RestApi(0, db);
        userApi = new UserApi(api);
        api.setUserApi(userApi);
    }

    @After
    public void tearDown() throws Exception {
        if (userApi != null) {
            userApi.stop();
        }
        Files.delete(db);
    }

    @Test
    public void testAuthUpdateURL() throws Exception {
        userApi.createAccount("hello", "password", "outbound.com");
        Response r = userApi.login("hello", "password");
        String token = r.getCookies().get("auth").getValue();
        String result = api.updateURL(new URL("http://www.google.com"), "GOOG", token);
        assertThat(result).isEqualTo("outbound.com/goog");

        try (Connection conn = api.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT uid FROM urls where shortcut=?")) {
                sql.setString(1, "outbound.com/goog");
                assertThat(sql.execute()).isTrue();
                try (ResultSet rs = sql.getResultSet()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("uid")).isEqualTo("hello");
                    assertThat(rs.next()).isFalse();
                }
            }
        }

        r = api.getURL("p", "GOOG", "http://www.bing.com", "Mozilla/5.0 (X11; Linux x86_64; rv:43.0) Gecko/20100101 Firefox/43.0");
        assertThat(r.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        r = api.getURL("outbound.com", "goog", "http://www.bing.com", "Mozilla/5.0 (X11; Linux x86_64; rv:43.0) Gecko/20100101 Firefox/43.0");
        assertThat(r.getStatus()).isEqualTo(Response.Status.TEMPORARY_REDIRECT.getStatusCode());
        assertThat(r.getLocation().toString()).isEqualTo("http://www.google.com");
    }

    @Test
    public void testIncorrectAuthUpdateURL() throws Exception {
        assertThatThrownBy(() -> api.updateURL(new URL("http://www.google.com"), "GOOG", "foo"))
                .isInstanceOf(ClientErrorException.class);
    }
}
