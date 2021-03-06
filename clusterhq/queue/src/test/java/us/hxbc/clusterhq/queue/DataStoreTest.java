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

public class DataStoreTest {
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Path dir;
    private DataStore ds;

    @Before
    public void setup() throws Exception {
        dir = folder.newFolder().toPath();
        ds = new DataStore(dir, 16);
    }

    @Test
    public void testGetBaseLSN() throws Exception {
        assertThat(ds.getBaseLSN(0)).isEqualTo(0);
        assertThat(ds.getBaseLSN(1)).isEqualTo(0);
        assertThat(ds.getBaseLSN(4097)).isEqualTo(4096);
    }

    @Test
    public void testGetChunkPath() throws Exception {
        assertThat(ds.getChunkPath(0).getFileName().toString()).isEqualTo("0");
        assertThat(ds.getChunkPath(256).getFileName().toString()).isEqualTo("100");
    }

    @Test
    public void testPostEmpty() throws Exception {
        post1(new byte[0], 0);
        assertThat(ds.getNextLSN()).isEqualTo(8);
    }

    @Test
    public void testPost1() throws Exception {
        post1(new byte[]{9}, 0);
        assertThat(ds.getNextLSN()).isEqualTo(9);
    }

    @Test
    public void testPost2() throws Exception {
        long lsn = 0;
        lsn = post1(new byte[]{9}, lsn);
        lsn = post1(new byte[]{8, 9}, lsn);

        // should wrap to next chunk
        assertThat(ds.getNextLSN()).isEqualTo(32);
    }

    @Test
    public void testPost2Chunks() throws Exception {
        long lsn = 0;
        lsn = post1(new byte[]{9}, lsn);
        lsn = post1(new byte[]{8, 9}, lsn);

        byte[] payload = new byte[] { 7 };
        ds.post(new ByteArrayInputStream(payload));

        Path p = ds.getChunkPath(ds.getBaseLSN(lsn));
        assertThat(Files.size(p)).isEqualTo(8 + payload.length);
        try (InputStream in = ds.get(lsn).in) {
            assertThat(ByteStreams.toByteArray(in)).isEqualTo(payload);
        }
    }

    @Test
    public void testGetNothing() throws Exception {
        assertThat(ds.get(0).in).isNull();
    }

    @Test
    public void testPostMany() throws Exception {
        long lsn = 0;
        for (int i = 0; i < 10; i++) {
            lsn = post1(new byte[]{(byte) i}, lsn);
        }

        lsn = 0;
        int i = 0;
        while (true) {
            logger.info("retrieving lsn {}", lsn);
            DataStore.Message m = ds.get(lsn);
            if (m.in != null) {
                m.in.close();
                lsn = m.nextLSN;
                i++;
            } else {
                break;
            }
        }
        assertThat(i).isEqualTo(10);
    }

    @Test
    public void testGCAll() throws Exception {
        testPost2();
        long lsn = ds.getNextLSN();
        assertThat(Files.list(dir).count()).isEqualTo(1);
        ds.gc(lsn);
        assertThat(Files.list(dir).count()).isEqualTo(0);
    }

    @Test
    public void testGCOneChunk() throws Exception {
        testPost2Chunks();
        long lsn = ds.getNextLSN();
        assertThat(Files.list(dir).count()).isEqualTo(2);
        ds.gc(lsn);
        assertThat(Files.list(dir).count()).isEqualTo(1);
    }

    @Test
    public void testGCNone() throws Exception {
        long lsn = 0;
        lsn = post1(new byte[]{9}, lsn);
        post1(new byte[]{8, 9}, lsn);
        assertThat(Files.list(dir).count()).isEqualTo(1);
        ds.gc(lsn);
        assertThat(Files.list(dir).count()).isEqualTo(1);
    }

    @Test
    public void testRestart() throws Exception {
        long lsn = post1(new byte[]{9}, 0);
        ds = new DataStore(dir, 16);
        assertThat(ds.getNextLSN()).isEqualTo(lsn);
    }

    private void dumpFile(Path p) throws IOException {
        byte[] bytes = ByteStreams.toByteArray(Files.newInputStream(p));
        for (int i = 0; i < bytes.length; i++) {
            logger.info("b: {}", bytes[i]);
        }
    }

    private long post1(byte[] payload, long lsn) throws IOException {
        Path p = ds.getChunkPath(ds.getBaseLSN(lsn));
        long size = Files.exists(p) ? Files.size(p) : 0;
        long next = ds.post(new ByteArrayInputStream(payload));
        logger.info("lsn is now {}", next);
        assertThat(Files.size(p)).isEqualTo(size + 8 + payload.length);

        try (InputStream in = ds.get(lsn).in) {
            assertThat(ByteStreams.toByteArray(in)).isEqualTo(payload);
        }

        return next;
    }
}
