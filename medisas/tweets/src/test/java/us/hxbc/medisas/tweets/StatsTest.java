package us.hxbc.medisas.tweets;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StatsTest {
    Stats stats = new Stats(10 * 60 * 1000, 10 * 1000);

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testTop10() throws Exception {
        stats.addTweet(StatsWindowTest.tweet);
        Map.Entry<Stats.StatusKey, Long>[] top10 = stats.top10();
        assertThat(top10).hasSize(1);
        assertThat(top10[0].getValue()).isEqualTo(1);

        stats.addTweet(StatsWindowTest.tweet);
        top10 = stats.top10();
        assertThat(top10).hasSize(1);
        assertThat(top10[0].getValue()).isEqualTo(2);
    }
}