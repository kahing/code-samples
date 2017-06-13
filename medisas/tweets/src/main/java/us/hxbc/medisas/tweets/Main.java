package us.hxbc.medisas.tweets;

import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Main <window>");
            System.exit(1);
        }

        int windowMinutes = Integer.parseInt(args[0]);
        Stats stats = new Stats(windowMinutes * 60 * 1000, 10 * 1000);

        StatusListener listener = new StatusListener(){
            public void onStatus(Status status) {
                Status retweet = status.getRetweetedStatus();
                if (retweet != null) {
                    stats.addTweet(retweet);
                }
                //System.out.println(status.getUser().getName() + " : " + status.getText());
                //status.getId()
                //status.getRetweetedStatus()
                //status.getCreatedAt()
                //status.is
            }
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {}
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {}

            @Override
            public void onScrubGeo(long l, long l1) {

            }

            @Override
            public void onStallWarning(StallWarning stallWarning) {

            }

            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(listener);
        // sample() method internally creates a thread which manipulates TwitterStream and calls these adequate listener methods continuously.

        new Thread(() -> {
            long lastTime = System.currentTimeMillis();

            while (true) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                }

                long thisTime = System.currentTimeMillis();
                System.out.println((thisTime - lastTime) / 1000);
                lastTime = thisTime;
                for (Map.Entry<Stats.StatusKey, Long> entry: stats.top10()) {
                    Status s = entry.getKey().status;
                    long count = entry.getValue();
                    System.out.println(s.getId() + " : " + count + " : " + s.getUser().getName() + " : " + s.getText());
                }
                System.out.println();
            }
        }).start();

        twitterStream.sample();

    }
}
