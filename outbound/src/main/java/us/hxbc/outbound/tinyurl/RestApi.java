package us.hxbc.outbound.tinyurl;

import com.google.common.annotations.VisibleForTesting;
import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteErrorCode;

import javax.validation.constraints.NotNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;

@Path("/x")
public class RestApi {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private final int PORT;
    private UserAgentStringParser ua = UADetectorServiceFactory.getResourceModuleParser();
    private BoneCP connectionPool = null;
    private UserApi userApi;

    static {
        // load the database driver (make sure this is in your classpath!)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw propagate(e);
        }
    }

    void setUserApi(UserApi api) {
        this.userApi = requireNonNull(api);
    }

    public RestApi(int port, java.nio.file.Path db) throws SQLException {
        this.PORT = port;

        BoneCPConfig config = new BoneCPConfig();
        if (db.toString().equals(":memory:")) {
            db = Paths.get("memory");
            Properties p = new Properties();
            p.put("mode", "memory");
            p.put("cache", "shared");
            config.setDriverProperties(p);
        }

        boolean init = false;
        if (!Files.exists(db)) {
            init = true;
        }

        config.setJdbcUrl("jdbc:sqlite:" + db.toString());
        config.setMinConnectionsPerPartition(5);
        config.setMaxConnectionsPerPartition(10);
                    config.setPartitionCount(1);
        connectionPool = new BoneCP(config);

        if (init) {
            logger.info("creating DB {}", db);

            try (Connection conn = connectionPool.getConnection()) {
                try (Statement sql = conn.createStatement()) {
                    sql.execute("CREATE TABLE urls (shortcut VARCHAR PRIMARY KEY, url VARCHAR, uid VARCHAR(6));");
                    sql.execute("CREATE TABLE visits (shortcut VARCHAR, referer VARCHAR, useragent VARCHAR);");
                    sql.execute("CREATE INDEX shortcut_idx on visits (shortcut);");
                    sql.execute("CREATE TABLE users (uid VARCHAR(6) PRIMARY KEY, hash VARCHAR, domain VARCHAR)");
                    logger.info("DB {} created", db);
                }
            }
        }
    }

    void stop() {
        connectionPool.shutdown();
    }

    Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    private String sanitizeCustomShortcut(String shortcut) {
        return shortcut.toLowerCase();
    }

    @POST
    public String updateURL(@NotNull @FormParam("url") URL url,
                            @FormParam("shortcut") String shortcut,
                            @CookieParam("auth") String auth) throws SQLException {
        logger.debug("updateURL: {}", url);
        boolean customShortcut;
        if (shortcut != null) {
            customShortcut = true;
            shortcut = sanitizeCustomShortcut(shortcut);
        } else {
            customShortcut = false;
            shortcut = Utils.randomString(6);
        }

        Map.Entry<String, String> authData = null;
        if (auth != null) {
            authData = userApi.tokenToUser(auth);
        }

        try (Connection conn = connectionPool.getConnection()) {
            do {
                try (PreparedStatement sql = conn.prepareStatement("INSERT INTO urls (shortcut, url, uid) VALUES (?, ?, ?)")) {
                    if (authData != null) {
                        sql.setString(3, authData.getKey());
                        shortcut = authData.getValue() + "/" + shortcut;
                    }
                    sql.setString(1, shortcut);
                    sql.setString(2, url.toString());

                    try {
                        sql.execute();
                        break;
                    } catch (SQLException e) {
                        if (Utils.sqlExceptionGetCode(e) != SQLiteErrorCode.SQLITE_CONSTRAINT.code) {
                            throw e;
                        }
                        if (customShortcut) {
                            throw new ClientErrorException(Response.Status.CONFLICT);
                        } else {
                            shortcut = Utils.randomString(6);
                        }
                    }
                }
            } while (true);
        }

        return shortcut;
    }

    // XXX async update
    private void updateAnalytics(String shortcut, String referer, String userAgent) throws SQLException {
        try (Connection conn = connectionPool.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("INSERT INTO visits (shortcut, referer, useragent) VALUES (?, ?, ?)")) {
                sql.setString(1, shortcut);
                sql.setString(2, referer);
                sql.setString(3, userAgent);
                sql.execute();
            }
        }
    }

    @Path("{domain}/{shortcut}")
    @GET
    public Response getURL(@NotNull @PathParam("domain") String domain,
                           @NotNull @PathParam("shortcut") String shortcut,
                           @HeaderParam("Referer") String referer,
                           @HeaderParam("User-Agent") String userAgent) throws SQLException, URISyntaxException {
        logger.debug("getURL {} {}", domain, shortcut);

        shortcut = shortcut.toLowerCase();
        if (!"p".equals(domain)) {
            shortcut = domain + "/" + shortcut;
        }
        Response r = null;

        try (Connection conn = connectionPool.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT url FROM urls where shortcut=?")) {
                sql.setString(1, shortcut);
                if (sql.execute()) {
                    try (ResultSet rs = sql.getResultSet()) {
                        if (rs.next()) {
                            String url = rs.getString("url");

                            r = Response.temporaryRedirect(new URI(url)).build();
                        }
                    }
                }
            }

        }

        if (r != null) {
            updateAnalytics(shortcut, referer, userAgent);
            return r;
        } else {
            // there's no result
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    private String aggregateReferer(String referer) {
        try {
            return new URL(referer).getHost();
        } catch (MalformedURLException e) {
            return referer;
        }
    }

    @VisibleForTesting
    void dumpTable(String table) throws SQLException {
        logger.info("dumping {}", table);

        try (Connection conn = connectionPool.getConnection()) {
            try (Statement sql = conn.createStatement()) {
                if (sql.execute("select * from " + table)) {
                    try (ResultSet rs = sql.getResultSet()) {
                        while (rs.next()) {
                            StringBuffer buf = new StringBuffer();
                            try {
                                for (int i = 1; ; i++) {
                                    buf.append(rs.getObject(i).toString());
                                    buf.append(" | ");
                                }
                            } catch (SQLException e) {
                            }

                            logger.info("{}: {}", table, buf.toString());
                        }
                    }
                }
            }
        }
    }

    private String aggregateUserAgent(String userAgent) {
        return ua.parse(userAgent).getName();
    }

    @Path("s/{domain}/{shortcut}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Long> getAnalytics(@NotNull @PathParam("domain") String domain,
                                          @NotNull @PathParam("shortcut") String shortcut,
                                          @CookieParam("auth") String auth) throws SQLException {
        logger.debug("getAnalytics {}", shortcut);

        Map<String, Long> stats = new HashMap<>();
        shortcut = shortcut.toLowerCase();
        if (!"p".equals(domain)) {
            shortcut = domain + "/" + shortcut;
        }

        try (Connection conn = connectionPool.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT referer, useragent FROM visits WHERE shortcut=?")) {
                sql.setString(1, shortcut);
                if (sql.execute()) {
                    try (ResultSet rs = sql.getResultSet()) {
                        while (rs.next()) {
                            String referer = rs.getString("referer");
                            String userAgent = rs.getString("useragent");

                            referer = aggregateReferer(referer);
                            userAgent = aggregateUserAgent(userAgent);

                            logger.debug("referer={} useragent={}", referer, userAgent);

                            stats.compute(referer, (k, v) -> {
                                if (v == null) {
                                    return 1L;
                                } else {
                                    return v + 1;
                                }
                            });

                            stats.compute(userAgent, (k, v) -> {
                                if (v == null) {
                                    return 1L;
                                } else {
                                    return v + 1;
                                }
                            });
                        }
                    }
                }
            }
        }

        logger.debug("stats {}: {}", shortcut, stats);
        return stats;
    }
}
