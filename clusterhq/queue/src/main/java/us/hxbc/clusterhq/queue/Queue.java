package us.hxbc.clusterhq.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;

/**
 * Queue manages subscriptions. It uses DataStore to store the actual
 * messages. Each subscription contains the next LSN to start
 * retrieving messages at. This LSN is updated each time a message is
 * retrieved.
 */
public class Queue {
    private static final long CHUNK_SIZE = 4 * 1024; // 4KB
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Path dataDir, subscriptionDir;
    private final Map<String, Subscriber> subscriptions = new HashMap<>();
    private final DataStore dataStore;
    private final Thread gcThread;
    private long minLSN = 0;
    private boolean shutdown = false;

    public Queue(Path dir, long chunkSize) throws IOException {
        requireNonNull(dir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }

        dataDir = dir.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            Files.createDirectory(dataDir);
        }
        subscriptionDir = dir.resolve("subscriptions");
        if (!Files.isDirectory(subscriptionDir)) {
            Files.createDirectory(subscriptionDir);
        }
        gcThread = new Thread(() -> {
            while (true) {
                synchronized (this) {
                    if (shutdown) {
                        break;
                    }
                }

                gcNow();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
            }
        });

        init();
        long maxLSN = 0;
        synchronized (subscriptions) {
            for (Subscriber s : subscriptions.values()) {
                synchronized (s) {
                    if (s.nextLSN > maxLSN) {
                        maxLSN = s.nextLSN;
                    }
                }
            }
        }

        dataStore = new DataStore(dataDir, chunkSize, maxLSN);
    }

    private void init() throws IOException {
        Files.list(subscriptionDir).forEach(p -> {
            String name = p.getFileName().toString();
            try (FileChannel in = FileChannel.open(p,
                    StandardOpenOption.READ)) {
                ByteBuffer buf = ByteBuffer.allocate(8);
                in.read(buf);
                buf.position(0);
                long nextLSN = buf.getLong();
                subscriptions.put(name, new Subscriber(name, nextLSN));
                logger.info("discovered subscriber {} @ {}", name, nextLSN);
            } catch (IOException e) {
                throw propagate(e);
            }
        });
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

        DataStore.Message m = dataStore.get(subscriber.nextLSN);
        if (m.in != null) {
            if (m.nextLSN <= subscriber.nextLSN) {
                throw new StreamCorruptedException(m.nextLSN + " <= " + subscriber.nextLSN);
            }

            synchronized (subscriber) {
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
            }
        }

        return m;
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

    void spawnGCThread() {
        shutdown = false;
        gcThread.start();
    }

    void stop() {
        shutdown = true;
        try {
            gcThread.interrupt();
            gcThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    synchronized void gcNow() {
        long curMinLSN = dataStore.getNextLSN();

        synchronized (subscriptions) {
            for (Subscriber s : subscriptions.values()) {
                synchronized (s) {
                    if (s.nextLSN < curMinLSN) {
                        curMinLSN = s.nextLSN;
                    }
                }
            }
        }

        logger.info("GC up to {}", curMinLSN);
        if (curMinLSN > minLSN) {
            dataStore.gc(curMinLSN);
            minLSN = curMinLSN;
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
