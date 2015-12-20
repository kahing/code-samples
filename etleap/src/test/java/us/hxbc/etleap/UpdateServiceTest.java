package us.hxbc.etleap;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.net.URL;

import static org.mockito.Mockito.*;

public class UpdateServiceTest {
    UpdateService service;

    @Before
    public void setUp() throws Exception {
        service = spy(new UpdateService(new URL("http://www.example.com/neverused")));
        String data = "file { \"/tmp/hello\":\n" +
                "source => \"http://host.com/path/to/hello\"\n" +
                "}\n" +
                "file { \"/tmp/foo/bar\":\n" +
                "source => \"http://host.com/bar/foo\"\n" +
                "}";

        doReturn(new StringReader(data)).when(service).openURL();
    }

    @Test
    public void testUpdate() throws Exception {
        service.update();
        verify(service, atLeast(1)).update();
    }

    @Test
    public void testStart() throws Exception {
        doNothing().when(service).update();
        service.start(1000);
        Thread.sleep(1500);
        service.stop();
        verify(service, atLeast(2)).update();
    }
}
