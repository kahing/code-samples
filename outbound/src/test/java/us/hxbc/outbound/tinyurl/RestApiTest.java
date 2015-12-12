package us.hxbc.outbound.tinyurl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RestApiTest {
    private RestApi api;
    private Path db;

    @Before
    public void setUp() throws Exception {
        db = Paths.get(Utils.randomString(6));
        //api = new RestApi(0, Paths.get(":memory:"));
        api = new RestApi(0, db);
    }

    @After
    public void tearDown() throws Exception {
        if (api != null) {
            api.stop();
        }
        Files.delete(db);
    }

    @Test
    public void testUpdateURL() throws Exception {
        initGOOG();

        try (Connection conn = api.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT url FROM urls where shortcut=?")) {
                sql.setString(1, "goog");
                assertThat(sql.execute()).isTrue();
                try (ResultSet rs = sql.getResultSet()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("url")).isEqualTo("http://www.google.com");
                }
            }
        }

        assertThatThrownBy(() -> initGOOG())
                .isInstanceOf(ClientErrorException.class);
    }

    private void initGOOG() throws SQLException, MalformedURLException {
        String result = api.updateURL(new URL("http://www.google.com"), "GOOG", null);
        assertThat(result).isEqualTo("goog");
    }

    @Test
    public void testGetURL() throws Exception {
        Response r = api.getURL("p", "GOOG", "http://www.bing.com", "Mozilla/5.0 (X11; Linux x86_64; rv:43.0) Gecko/20100101 Firefox/43.0");
        assertThat(r.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        initGOOG();
        visit();
    }

    private void visit() throws SQLException, URISyntaxException {
        Response r = api.getURL("p", "GOOG", "http://www.bing.com", "Mozilla/5.0 (X11; Linux x86_64; rv:43.0) Gecko/20100101 Firefox/43.0");
        assertThat(r.getStatus()).isEqualTo(Response.Status.TEMPORARY_REDIRECT.getStatusCode());
        assertThat(r.getLocation().toString()).isEqualTo("http://www.google.com");
    }

    @Test
    public void testGetAnalytics() throws Exception {
        Map<String, Long> stats = api.getAnalytics("p", "GOOG", null);
        assertThat(stats).isEmpty();

        initGOOG();
        visit();
        visit();

        api.dumpTable("visits");

        stats = api.getAnalytics("p", "GOOG", null);
        assertThat(stats).hasSize(2);
        assertThat(stats.get("www.bing.com")).isEqualTo(2);
        assertThat(stats.get("Firefox")).isEqualTo(2);
    }
}