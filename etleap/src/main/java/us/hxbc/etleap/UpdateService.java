package us.hxbc.etleap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.hxbc.etleap.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

import static java.util.Objects.requireNonNull;

public class UpdateService {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private URL url;
    private Object cv = new Object();
    private boolean running;
    private Thread runner;

    public UpdateService(URL url) {
        url = requireNonNull(url);
    }

    Reader openURL() throws IOException {
        return new InputStreamReader(url.openStream());
    }

    void update() {
        logger.info("updating from {}", url);

        try (Reader reader = openURL()) {
            try {
                Config c = new Config(reader);
                c.apply();
            } catch (IOException e) {
                logger.error("unable to parse config", e);
            }
        } catch (IOException e) {
            logger.error("unable to download config", e);
        }
    }

    public void start() {
        runner = new Thread(() -> {
            logger.info("started, polling {} every 5 minutes", url);

            synchronized (cv) {
                while (running) {
                    try {
                        update();
                    } catch (RuntimeException e) {
                        logger.error("unknown error", e);
                    }

                    try {
                        cv.wait(5 * 60 * 1000); // 5 minutes
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            }
        });
        runner.start();
    }

    public void stop() throws InterruptedException {
        logger.info("stopping");

        synchronized (cv) {
            running = false;
            cv.notify();
        }

        runner.join();
    }
}
