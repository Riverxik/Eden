import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * I will try to make Eden self-hosted as soon as possible, so forgive me for this mess
 * I will try it without OOP style. After I self-hosted it I will refactor all of this. Maybe, maybe not.
 * I just started and i see that it's really hard to do without OOP features and stuff...
 */
public class Eden {
    public static void main(String[] args) throws IOException {
        String allowedCharacters = "~+-*/;";
        List<Token> tokenList = new ArrayList<>();

        // Args
        if (args.length == 0) {
            System.out.println("Usage: eden -s scrName.eden");
            System.exit(1);
        }
        int i = 0;
        String sourceName = "";
        while (i < args.length) {
            if (args[i].equalsIgnoreCase("-s")) {
                if (i+1 < args.length) {
                    sourceName = args[i+1];
                } else {
                    System.out.println("You must provide path to the source file");
                    System.exit(1);
                }
            }
            i++;
        }

        // Reading the source
        if (!Files.exists(Paths.get(sourceName))) {
            System.out.println("File does not exists: " + sourceName);
            System.exit(1);
        }
        String source = new String(Files.readAllBytes(Paths.get(sourceName)), StandardCharsets.UTF_8);

        // Lexing.
        int sourceLen = source.length();
        i = 0;
        int line = 1;
        int column = 1;
        char c;
        while (i < sourceLen) {
            c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    column = 1;
                }
            } else if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                while (Character.isDigit(c)) {
                    sb.append(c);
                    i++;
                    column++;
                    c = source.charAt(i);
                }
                i--;
                column--;
                tokenList.add(new Token("INT", sb.toString(), new Location(line, column)));
            } else if (allowedCharacters.indexOf(c) != -1) {
                tokenList.add(new Token("CHAR", c, new Location(line, column)));
            } else {
                System.err.printf("Unknown char: '%c'", c);
                System.exit(1);
            }
            i++;
            column++;
        }

        // For testing.
        for (Token t : tokenList) {
            System.out.println(t.toString());
        }
    }

    static class Token {
        String type;
        Object value;
        Location loc;

        Token(String type, Object value, Location loc) {
            this.type = type;
            this.value = value;
            this.loc = loc;
        }

        @Override
        public String toString() {
            return String.format("%s[%s][%d:%d]", type, value, loc.line, loc.column);
        }
    }

    static class Location {
        int line;
        int column;

        Location(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }
}
