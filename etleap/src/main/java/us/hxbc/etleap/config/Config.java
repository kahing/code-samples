package us.hxbc.etleap.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private List<FileConfig> configs = new ArrayList<>();

    Config(Reader reader) throws IOException {
        StreamTokenizer tokens = new StreamTokenizer(reader);
        tokens.quoteChar('"');

        while (tokens.nextToken() != StreamTokenizer.TT_EOF) {
            tokens.pushBack();

            String section = expect(tokens, null);
            if (section.equals("file")) {
                expectSymbol(tokens, "{");

                configs.add(new FileConfig(tokens));
                expectSymbol(tokens, "}");
            }
        }
    }

    public void apply() {
        for (FileConfig c : configs) {
            c.apply();
        }
    }

    @VisibleForTesting
    List<FileConfig> getConfigs() {
        return ImmutableList.copyOf(configs);
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
