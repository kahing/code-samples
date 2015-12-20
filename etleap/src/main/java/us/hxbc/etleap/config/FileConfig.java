package us.hxbc.etleap.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamTokenizer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static com.google.common.base.Throwables.propagate;
import static us.hxbc.etleap.config.Config.expect;
import static us.hxbc.etleap.config.Config.expectSymbol;

public class FileConfig {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private Path path;
    private URL updatePath;

    FileConfig(StreamTokenizer tokens) throws IOException {
        path = Paths.get(expect(tokens, null));
        expectSymbol(tokens, ":");
        expect(tokens, "source");
        expectSymbol(tokens, "=>");
        updatePath = new URL(expect(tokens, null));
        expectSymbol(tokens, "}");
        tokens.pushBack(); // give the closing brace to parent
    }

    void apply() {
        logger.info("replacing {} with {}", path, updatePath);
        try (InputStream is = updatePath.openStream()) {
            try (OutputStream os = Files.newOutputStream(path,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                ByteStreams.copy(is, os);
            } catch (IOException e) {
                logger.error("unable to replace file", e);
            }
        } catch (IOException e) {
            logger.error("unable to download update", e);
            throw propagate(e);
        }
    }

    @VisibleForTesting
    Path getPath() {
        return path;
    }

    @VisibleForTesting
    URL getUpdatePath() {
        return updatePath;
    }
}
