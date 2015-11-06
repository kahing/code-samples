package us.hxbc.clusterhq.queue;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Queue manages subscriptions. It uses DataStore to store the actual
 * messages. Each subscription contains the next LSN to start
 * retrieving messages at. This LSN is updated each time a message is
 * retrieved.
 */
public class Queue {
    private static final long CHUNK_SIZE = 4 * 1024; // 4KB
    private final Path dataDir, subscriptionDir;
    private final Map<String, Subscriber> subscriptions = new HashMap<>();
    private final DataStore dataStore;

    public Queue(Path dir, long chunkSize) throws IOException {
        requireNonNull(dir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }

        dataDir = dir.resolve("data");
        Files.createDirectory(dataDir);
        subscriptionDir = dir.resolve("subscriptions");
        Files.createDirectory(subscriptionDir);
        dataStore = new DataStore(dataDir, chunkSize);
    }

    public void subscribe(String user) throws IOException {
        synchronized (subscriptions) {
            if (subscriptions.containsKey(user)) {
                return;
            }
            Path p = subscriptionDir.resolve(user);
            long nextLSN = dataStore.getNextLSN();
            try (FileChannel out = FileChannel.open(p,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.allocate(8).putLong(nextLSN);
                buf.position(0);
                out.write(buf);
                out.force(true);
            }
            subscriptions.put(user, new Subscriber(user, nextLSN));
        }
    }

    public void post(InputStream data) throws IOException {
        dataStore.post(data);
    }

    public DataStore.Message get(String user) throws IOException {
        Subscriber subscriber;
        synchronized (subscriptions) {
            subscriber = subscriptions.get(user);
            if (subscriber == null) {
                throw new ClientErrorException(Response.Status.NOT_FOUND);
            }
        }

        synchronized (subscriber) {
            DataStore.Message m = dataStore.get(subscriber.nextLSN);
            Path p = subscriptionDir.resolve(user);
            try (FileChannel out = FileChannel.open(p,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.allocate(8).putLong(m.nextLSN);
                buf.position(0);
                out.write(buf);
                out.force(true);
            }
            subscriber.nextLSN = m.nextLSN;
            return m;
        }
    }

    public void unsubscribe(String user) throws IOException {
        synchronized (subscriptions) {
            Path p = subscriptionDir.resolve(user);
            if (!Files.deleteIfExists(p)) {
                throw new ClientErrorException(Response.Status.NOT_FOUND);
            }
            subscriptions.remove(user);
        }
    }

    static class Subscriber {
        final String name;
        long nextLSN;

        Subscriber(String name, long nextLSN) {
            this.name = requireNonNull(name);
            this.nextLSN = nextLSN;
        }
    }
}
