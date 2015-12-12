package us.hxbc.outbound.tinyurl;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);
    final HttpServer server;
    final RestApi api;

    Main(int port, Path db) throws IOException, SQLException {
        ResourceConfig rc = new ResourceConfig();
        api = new RestApi(port, db);
        UserApi userApi = new UserApi(api);
        api.setUserApi(userApi);

        rc.registerInstances(api, userApi);
        if (!rc.isRegistered(userApi)) {
            throw new RuntimeException();
        }

        if (logger.isDebugEnabled()) {
            rc.register(new LoggingFilter(java.util.logging.Logger.getGlobal(), false));
        }
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://0.0.0.0:" + port), rc, false);
    }

    void start() throws IOException {
        server.start();
    }

    public int getPort() {
        return server.getListeners().stream().findAny().map(n -> n.getPort()).orElse(0);
    }

    void stop() {
        server.shutdownNow();
        api.stop();
    }

    public static void main(String[] args) throws IOException, SQLException {
        if (args.length != 2) {
            System.err.println("Usage: Main <port> <db>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        Path db = Paths.get(args[1]);

        new Main(port, db).start();
    }
}
