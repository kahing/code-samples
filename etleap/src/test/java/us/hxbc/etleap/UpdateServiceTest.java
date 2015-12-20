package us.hxbc.etleap;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.net.URL;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdateServiceTest {
    UpdateService service;

    @Before
    public void setUp() throws Exception {
        service = mock(UpdateService.class);
    }

    @Test
    public void testUpdate() throws Exception {
        String data = "file { \"/tmp/hello\":\n" +
                "source => \"http://host.com/path/to/hello\"\n" +
                "}\n" +
                "file { \"/tmp/foo/bar\":\n" +
                "source => \"http://host.com/bar/foo\"\n" +
                "}";

        when(service.openURL()).thenReturn(new StringReader(data));
        service.update();
    }
}