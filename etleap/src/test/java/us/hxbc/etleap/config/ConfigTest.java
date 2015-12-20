package us.hxbc.etleap.config;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigTest {
    Config config;

    @Before
    public void setUp() throws Exception {
        String data = "file { \"/tmp/hello\":\n" +
                "source => \"http://host.com/path/to/hello\"\n" +
                "}\n" +
                "file { \"/tmp/foo/bar\":\n" +
                "source => \"http://host.com/bar/foo\"\n" +
                "}";
        config = new Config(new StringReader(data));
    }

    @Test
    public void testGetConfigs() throws Exception {
        List<FileConfig> configs = config.getConfigs();
        assertThat(configs).hasSize(2);
    }
}
