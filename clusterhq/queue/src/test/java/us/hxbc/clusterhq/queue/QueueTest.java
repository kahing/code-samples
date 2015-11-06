package us.hxbc.clusterhq.queue;

import com.google.common.io.ByteStreams;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ClientErrorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QueueTest {
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Path dir;
    private Queue queue;

    @Before
    public void setup() throws Exception {
        dir = folder.newFolder().toPath();
        queue = new Queue(dir, 16);
    }

    @Test
    public void testUnSubscribeNotSubscribed() throws Exception {
        assertThatThrownBy(() -> queue.unsubscribe("foo")).isInstanceOf(ClientErrorException.class);
    }

    @Test
    public void testSubscribe() throws Exception {
        queue.subscribe("foo");
        Path p = dir.resolve("subscriptions").resolve("foo");
        assertThat(Files.exists(p)).isTrue();
    }

    private InputStream string2Stream(String str) {
        return new ByteArrayInputStream(str.getBytes());
    }

    private String stream2String(InputStream in) throws IOException {
        try {
            return new String(ByteStreams.toByteArray(in));
        } finally {
            in.close();
        }
    }

    @Test
    public void testGetNotSubscribed() throws Exception {
        queue.post(string2Stream("hello"));
        assertThatThrownBy(() -> queue.get("foo")).isInstanceOf(ClientErrorException.class);
    }

    @Test
    public void testGetNothing() throws Exception {
        queue.subscribe("foo");
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testGetAfterPost() throws Exception {
        queue.post(string2Stream("hello"));
        queue.subscribe("foo");
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testGet() throws Exception {
        queue.subscribe("foo");
        queue.post(string2Stream("hello"));
        DataStore.Message m = queue.get("foo");
        assertThat(stream2String(m.in)).isEqualTo("hello");
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testGet2() throws Exception {
        queue.subscribe("foo");
        queue.post(string2Stream("hello"));
        queue.post(string2Stream("world"));
        DataStore.Message m = queue.get("foo");
        assertThat(stream2String(m.in)).isEqualTo("hello");
        m = queue.get("foo");
        assertThat(stream2String(m.in)).isEqualTo("world");
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testGetMany() throws Exception {
        queue.subscribe("foo");
        for (int i = 0; i < 10; i++) {
            queue.post(string2Stream("hello, "));
            queue.post(string2Stream("world!"));
        }
        DataStore.Message m;
        for (int i = 0; i < 10; i++) {
            m = queue.get("foo");
            assertThat(stream2String(m.in)).isEqualTo("hello, ");
            m = queue.get("foo");
            assertThat(stream2String(m.in)).isEqualTo("world!");
        }
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testResubscribeGet() throws Exception {
        queue.subscribe("foo");
        queue.unsubscribe("foo");
        queue.post(string2Stream("hello"));
        queue.subscribe("foo");
        assertThat(queue.get("foo").in).isNull();
    }

    @Test
    public void testUnsubscribe() throws Exception {
        queue.subscribe("foo");
        queue.unsubscribe("foo");
        Path p = dir.resolve("subscriptions").resolve("foo");
        assertThat(Files.exists(p)).isFalse();
    }
}
