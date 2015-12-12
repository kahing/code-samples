package us.hxbc.outbound.tinyurl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.sqlite.SQLiteErrorCode;

import javax.validation.constraints.NotNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Path("/u")
public class UserApi {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @VisibleForTesting
    final RestApi api;
    private Map<String, Map.Entry<String, String>> sessions = new HashMap<>();

    public UserApi(RestApi api) {
        this.api = requireNonNull(api);
    }

    void stop() {
        api.stop();
    }

    @Path("{user}")
    @PUT
    public void createAccount(@NotNull @PathParam("user") String user,
                              @NotNull @FormParam("password") String password,
                              @NotNull @FormParam("domain") String domain) throws SQLException {
        logger.debug("create {} {} {}", user, password, domain);

        try (Connection conn = api.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("INSERT INTO users (uid, hash, domain) VALUES (?, ?, ?)")) {
                sql.setString(1, user);
                String salt = BCrypt.gensalt();
                sql.setString(2, BCrypt.hashpw(password, salt));
                sql.setString(3, domain);
                sql.execute();
            } catch (SQLException e) {
                if (Utils.sqlExceptionGetCode(e) == SQLiteErrorCode.SQLITE_CONSTRAINT.code) {
                    throw new ClientErrorException(Response.Status.CONFLICT);
                }
            }
        }
    }

    @Path("{user}")
    @POST
    public Response login(@PathParam("user") String user,
                          @FormParam("password") String password) throws SQLException {
        logger.debug("login {} {}", user, password);

        try (Connection conn = api.getConnection()) {
            try (PreparedStatement sql = conn.prepareStatement("SELECT hash,domain FROM users where uid=?")) {
                sql.setString(1, user);
                if (sql.execute()) {
                    try (ResultSet rs = sql.getResultSet()) {
                        if (rs.next()) {
                            String hash = rs.getString("hash");
                            String domain = rs.getString("domain");
                            if (BCrypt.checkpw(password, hash)) {
                                NewCookie auth = new NewCookie("auth", generateAuthToken(user, domain));
                                return Response.noContent().cookie(auth).build();
                            }
                        }
                    }
                }
            }
        }

        throw new ClientErrorException(Response.Status.NOT_FOUND);
    }

    private String generateAuthToken(String user, String domain) {
        synchronized (sessions) {
            String token = Utils.randomString(16);
            sessions.put(token, new AbstractMap.SimpleImmutableEntry<>(user, domain));
            return token;
        }
    }

    Map.Entry<String, String> tokenToUser(String token) {
        synchronized (sessions) {
            Map.Entry<String, String> user = sessions.get(token);
            if (user == null) {
                throw new ClientErrorException(Response.Status.FORBIDDEN);
            }
            return user;
        }
    }
}
