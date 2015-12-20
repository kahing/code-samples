package us.hxbc.etleap.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private List<FileConfig> configs = new ArrayList<>();

    Config(InputStream is) {
        StreamTokenizer tokens = new StreamTokenizer(new InputStreamReader(is));
        tokens.quoteChar('"');

    }

    static void expectSymbol(StreamTokenizer tokens, String symbol) throws IOException {
        for (int i = 0; i < symbol.length(); i++) {
            char c = symbol.charAt(i);
            int t = tokens.nextToken();
            if (t != c) {
                throw new IllegalStateException("expected \"" + c + "\" but got \"" + t + "\":" + tokens.lineno());
            }
        }
    }

    static String expect(StreamTokenizer tokens, String word) throws IOException {
        int t = tokens.nextToken();
        if (t != StreamTokenizer.TT_WORD && t != '"') {
            throw new IllegalStateException("expected word but got " + tokens.ttype + ": " + tokens.lineno());
        }

        if (word != null && !tokens.sval.equals(word)) {
            throw new IllegalStateException("expected \"" + word + "\":" + tokens.lineno());
        }

        return tokens.sval;
    }

}
