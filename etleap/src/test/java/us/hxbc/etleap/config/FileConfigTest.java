package us.hxbc.etleap.config;

import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.io.ByteArrayInputStream;
import java.io.StreamTokenizer;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class FileConfigTest {
    FileConfig config;

    @Before
    public void setup() throws Exception {
        String data = "\"/tmp/hello\":\n" +
                "source => \"http://example.com/path/to/hello\"\n" +
                "}";
        StreamTokenizer tokens = new StreamTokenizer(new StringReader(data));
        tokens.quoteChar('"');
        /*
        while (tokens.nextToken() != StreamTokenizer.TT_EOF) {
            System.err.println(tokens.ttype + ": " + tokens.sval);
        }
        */
        config = new FileConfig(tokens);
    }

    @Test
    public void testGetPath() throws Exception {
        assertThat(config.getPath().toString()).isEqualTo("/tmp/hello");
    }

    @Test
    public void testGetUpdatePath() throws Exception {
        assertThat(config.getUpdatePath().toString()).isEqualTo("http://example.com/path/to/hello");
    }

    @Test
    public void testApply() throws Exception {
        FileConfig configMock = mock(FileConfig.class);
        when(configMock.getPath()).thenReturn(config.getPath());
        when(configMock.getUpdatePath()).thenReturn(config.getUpdatePath());
        when(configMock.openStream()).thenReturn(new ByteArrayInputStream("hello world".getBytes()));
        configMock.apply();
        verify(configMock, atLeastOnce()).apply();
        assertThat(configMock.getPath()).hasContent("hello world");
    }
}
