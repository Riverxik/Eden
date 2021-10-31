import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * I will try to make Eden self-hosted as soon as possible, so forgive me for this mess
 * I will try it without OOP style. After I self-hosted it I will refactor all of this. Maybe, maybe not.
 * I just started and i see that it's really hard to do without OOP features and stuff...
 */
public class Eden {
    static List<Token> tokenList = new ArrayList<>();
    static int tokenIndex = 0;
    static Stack<Object> stack = new Stack<>();

    public static void main(String[] args) throws IOException {
        String allowedCharacters = "~+-*/;()=><";

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
        tokenList.add(new Token("END", null, new Location(line, column)));

        program();
    }

    static void program() {
        while (!getCurrent().type.equalsIgnoreCase("END")) {
            statements();
        }
    }

    static void statements() {
        statement();
        Token current = getCurrent();
        if (assertToken(current,"CHAR", ";")) {
            advanceToken();
        } else {
            printErr(current, "Expected ';' but found");
            System.exit(1);
        }
    }

    static void statement() {
        Token current = getCurrent();
        if (String.valueOf(current.value).equals("~")) {
            printStatement();
        } else {
            System.err.println("Unknown statement");
            System.exit(1);
        }
    }

    static void printStatement() {
        // ~ expr ;
        Token current = getCurrent();
        if (assertToken(current, "CHAR", "~")) {
            advanceToken();
            expression();
        } else {
            printErr(current, "Print statement should starts with '~'");
        }
        // print
        System.out.println(stack.pop());
    }

    static void expression() {
        part();
        sum();
        logical();
        //keyword {read}
    }

    static void part() {
        unary();
    }

    static void sum() {
        Token current = getCurrent();
        if (assertToken(current, "CHAR", "+")) {
            advanceToken();
            part();
            opPlus();
            sum();
        }
        if (assertToken(current, "CHAR", "-")) {
            advanceToken();
            part();
            opMinus();
            sum();
        }
    }

    static void logical() {
        Token current = getCurrent();
        if (assertToken(current, "CHAR", ">")) {
            advanceToken();
            part();
            sum();
            opMore();
        }
        if (assertToken(current, "CHAR", "<")) {
            advanceToken();
            part();
            sum();
            opLess();
        }
        if (assertToken(current, "CHAR", "=")) {
            advanceToken();
            part();
            sum();
            opEqual();
        }
    }

    static void unary() {
        boolean isPositive = true;
        Token current = getCurrent();
        if (assertToken(current, "CHAR", "+") || assertToken(current, "CHAR", "-")) {
            if (String.valueOf(current.value).equalsIgnoreCase("-")) {
                isPositive = false;
            }
            advanceToken();
        }
        arg(isPositive);
        starSlash();
    }

    static void starSlash() {
        Token current = getCurrent();
        if (String.valueOf(current.value).equalsIgnoreCase("*")) {
            advanceToken();
            unary();
            opStar();
            starSlash();
        }
        if (String.valueOf(current.value).equalsIgnoreCase("/")) {
            advanceToken();
            unary();
            opSlash();
            starSlash();
        }
        // TODO: ^ op?
//        if (String.valueOf(current.value).equalsIgnoreCase("^")) {
//            opPower();
//            starSlash();
//        }
    }

    static void arg(boolean isPositive) {
        Token current = getCurrent();
        if (current.type.equalsIgnoreCase("INT")) {
            if (assertToken(current, "INT")) {
                int tmp = getInt(current.value);
                int value = isPositive ? tmp : -tmp;
                stack.push(value);
                advanceToken();
            } else {
                printErr(current, "Expected integer, but found");
            }
        }
        if (current.type.equalsIgnoreCase("CHAR")) {
            if (assertToken(current, "CHAR", "(")) {
                advanceToken(); // (
                expression();
                current = getCurrent();
                if (assertToken(current, "CHAR", ")")) {
                    advanceToken(); // )
                } else {
                    printErr(current, "Expression with ( should end with )");
                    System.exit(1);
                }
            }
        }
        // TODO: IDENTIFIER, STRING, BOOLEAN?
    }

    static void opPlus() { // TODO: Check the stack to be valid for all ops
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a + _b);
        } else {
            printErr(getCurrent(), "Expected two integers, but found");
        }
    }

    static void opMinus() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a - _b);
        } else {
            printErr(getCurrent(), "Expected two integers, but found");
        }
    }

    static void opStar() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a * _b);
        } else {
            printErr(getCurrent(), "Expected two integers, but found");
        }
    }

    static void opSlash() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a / _b);
        } else {
            printErr(getCurrent(), "Expected two integers, but found");
        }
    }

    static void opMore() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a > _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "More operation supports only integer for now");
            System.exit(1);
        }
    }

    static void opLess() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a < _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "Less operation supports only integer for now");
            System.exit(1);
        }
    }

    static void opEqual() {
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a == _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "Equal operation supports only integer for now");
            System.exit(1);
        }
    }

    static int getInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }

    static boolean assertToken(Token token, String type, Object value) {
        return token.type.equalsIgnoreCase(type) && token.value.toString().equals(value.toString());
    }

    static boolean assertToken(Token token, String type) {
        return token.type.equalsIgnoreCase(type);
    }

    static void printErr(Token token, String errMessage) {
        System.err.printf("[%d:%d] %s: (%s)'%s'%n", token.loc.line, token.loc.column, errMessage, token.type, token.value);
    }

    static void advanceToken() {
        if (tokenIndex < tokenList.size()) {
            tokenIndex++;
        } else {
            printErr(tokenList.get(tokenList.size()-1), "Can't advance the next token.");
        }
    }

    static Token getCurrent() {
        return tokenList.get(tokenIndex);
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
