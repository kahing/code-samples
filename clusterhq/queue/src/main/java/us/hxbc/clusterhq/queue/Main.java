package us.hxbc.clusterhq.queue;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: Main <port> <dir>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        Path dir = Paths.get(args[1]);
        if (!Files.isDirectory(dir)) {
            System.err.format("%s is not a directory\n", dir);
            System.exit(1);
        }

        ResourceConfig rc = new ResourceConfig();
        rc.registerInstances(new Api(dir, 4096));
        if (logger.isDebugEnabled()) {
            rc.register(new LoggingFilter(java.util.logging.Logger.getGlobal(), false));
        }
        GrizzlyHttpServerFactory.createHttpServer(URI.create("http://0.0.0.0:" + port), rc);
    }
}
