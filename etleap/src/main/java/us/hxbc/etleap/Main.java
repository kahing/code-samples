package us.hxbc.etleap;

import java.net.MalformedURLException;
import java.net.URL;

public class Main {
    public static void main(String[] args) throws MalformedURLException {
        if (args.length < 1) {
            System.err.println("Main <URL>");
            System.exit(1);
        }

        URL url = new URL(args[0]);
        new UpdateService(url).start();
    }
}
