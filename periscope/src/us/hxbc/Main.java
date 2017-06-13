package us.hxbc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

public class Main {
    static Map<String, Map<String, Integer>> wordCounts = new HashMap<>();

    private static boolean isWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 'a' || s.charAt(i) > 'z') {
                return false;
            }
        }
        return true;
    }

    private static void parseWords(String line) {
        StringTokenizer token = new StringTokenizer(line);
        String prevWord = null;
        while (token.hasMoreElements()) {
            String w = token.nextToken().toLowerCase();
            if (!isWord(w)) {
                continue;
            }

            if (prevWord != null) {
                Map<String, Integer> m = wordCounts.get(prevWord);
                if (m == null) {
                    m = new HashMap<>();
                    wordCounts.put(prevWord, m);
                }

                Integer i = m.getOrDefault(w, 0);
                m.put(w, i + 1);
            }

            prevWord = w;
        }
    }

    private static void parseFile(File f) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(f));
        String line;
        while ((line = r.readLine()) != null) {
            parseWords(line);
        }
    }

    private static String[] sortResult(Map<String, Integer> count) {
        String[] keys = new String[count.size()];
        count.keySet().toArray(keys);
        Arrays.sort(keys, (a, b) -> {
            return count.get(b) -  count.get(a);
        });

        return keys;
    }

    private static String generateSentence(String startingWord, int numWords, Map<String, Map<String, Integer>> wordCounts) {
        String sentence = startingWord;
        String nextWord = startingWord;
        Random r = new Random();
        for (int i = 1; i < numWords; i++) {
            Map<String, Integer> counts = wordCounts.get(nextWord);
            int sum = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                sum += e.getValue();
            }
            int ticket = r.nextInt(sum);
            String chosenWord = null;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                ticket -= e.getValue();
                chosenWord = e.getKey();
                if (ticket <= 0) {
                    break;
                }
            }

            sentence += " " + chosenWord;
            nextWord = chosenWord;
        }

        return sentence;
    }

    public static void main(String[] args) throws IOException {
        // write your code here
        parseFile(new File(args[0]));
        /*
        String[] wordFreqOrder = sortResult(wordCounts);
        for (int i = 0; i < 10; i++) {
            System.out.format("%s: %s\n", wordFreqOrder[i], wordCounts.get(wordFreqOrder[i]));
        }
        */

        System.out.println(generateSentence("said", 10, wordCounts));
    }
}
