package us.hxbc.medisas.tweets;

import twitter4j.Status;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public class Stats {
    private final LinkedList<StatsWindow> stats = new LinkedList<>();
    private final int buckets;
    private final long refreshWindow;

    Stats(long totalWindow, long refreshWindow) {
        this.refreshWindow = refreshWindow;
        if (totalWindow < refreshWindow) {
            throw new IllegalArgumentException("total window < refresh window");
        }

        buckets = (int) (totalWindow / refreshWindow);
        stats.add(new StatsWindow());
    }

    void addTweet(Status tweet) {
        // find the stats window to operate on
        //System.err.println(tweet.getId() + " : " + tweet.getUser().getName() + " : " + tweet.getText());
        synchronized (stats) {
            stats.getLast().add(new StatusKey(tweet), 1);
        }
    }

    Map.Entry<StatusKey, Long>[] top10() {
        // calculate the top 10 retweets, then rollover one bucket

        StatsWindow aggregate = new StatsWindow();
        stats.stream().forEach(w -> {
            w.retweetCount.forEach((k, v) -> {
                aggregate.add(k, v);
            });
        });

        Map.Entry<Status, Long>[] arr = new Map.Entry[aggregate.retweetCount.size()];
        aggregate.retweetCount.entrySet().toArray(arr);
        Arrays.sort(arr, (a, b) -> {
            if (a.getValue() > b.getValue()) {
                return -1;
            }
            if (a.getValue() == b.getValue()) {
                return 0;
            }
            return 1;
        });

        synchronized (stats) {
            stats.addLast(new StatsWindow());
            if (stats.size() > buckets) {
                stats.removeFirst();
            }
        }

        return Arrays.stream(arr).limit(10).toArray(n -> new Map.Entry[n]);
    }

    static class StatusKey {
        Status status;
        StatusKey(Status status) {
            this.status = requireNonNull(status);
        }

        public boolean equals(Object other) {
            return status.getId() == ((StatusKey) other).status.getId();
        }

        public int hashCode() {
            return Long.valueOf(status.getId()).hashCode();
        }
    }

    static class StatsWindow {
        ConcurrentMap<StatusKey, Long> retweetCount = new ConcurrentHashMap<>();

        void add(StatusKey id, long count) {
            retweetCount.compute(id, (k, v) -> {
                if (v == null) {
                    return count;
                } else {
                    return v + count;
                }
            });
        }
    }
}
