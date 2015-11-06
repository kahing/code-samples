package us.hxbc.clusterhq.queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    private Path dir;
    private Main main;
    private int port;
    private WebTarget target;
    private Client c;

    @Before
    public void setUp() throws Exception {
        dir = folder.newFolder().toPath();
        initClient();
    }

    private void initClient() throws Exception {
        main = new Main(0, dir);
        main.start();
        port = main.getPort();
        if (c != null) {
            c.close();
        }
        c = ClientBuilder.newClient();
        target = c.target("http://127.0.0.1:" + port);
    }

    @After
    public void tearDown() throws Exception {
        main.stop();
    }

    @Test
    public void testSubscribe() throws Exception {
        Response resp = target.path("/topic1/user1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
        resp = target.path("/topic1/user1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    public void testUnsubscribe() throws Exception {
        Response resp;
        // unsubscribe without subscribing
        resp = target.path("/topic1/user1").request().delete();
        assertThat(resp.getStatus()).isEqualTo(404);

        // unsubscribe after subscribing
        resp = target.path("/topic1/user1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
        resp = target.path("/topic1/user1").request().delete();
        assertThat(resp.getStatus()).isEqualTo(200);

        // unsubscribe again
        resp = target.path("/topic1/user1").request().delete();
        assertThat(resp.getStatus()).isEqualTo(404);
    }

    @Test
    public void testMessage() throws Exception {
        Response resp;
        // subscription doesn't exist
        resp = target.path("/topic1/user1").request().get();
        assertThat(resp.getStatus()).isEqualTo(404);

        // no message available
        resp = target.path("/topic1/user1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
        resp = target.path("/topic1/user1").request().get();
        assertThat(resp.getStatus()).isEqualTo(204);

        resp = target.path("/topic1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
        resp = target.path("/topic1/user1").request().get();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    public void testRestart() throws Exception {
        Response resp;
        resp = target.path("/topic1/user1").request().post(null);
        assertThat(resp.getStatus()).isEqualTo(200);
        main.stop();
        initClient();
        resp = target.path("/topic1/user1").request().get();
        assertThat(resp.getStatus()).isEqualTo(204);
    }
}
