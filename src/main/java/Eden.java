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
        Lexer lexer = new Lexer(source, tokenList);
        lexer.tokenize();

        program();
    }

    static void program() {
        while (getCurrent().type != TokenType.END) {
            statements();
        }
    }

    static void statements() {
        statement();
        Token current = getCurrent();
        if (assertToken(current,TokenType.SEMICOLON)) {
            advanceToken();
        } else {
            printErr(current, "Expected ';' but found");
        }
    }

    static void statement() {
        Token current = getCurrent();
        if (String.valueOf(current.value).equals("~")) {
            printStatement();
        } else {
            printErr(current, "Unknown statement");
        }
    }

    static void printStatement() {
        // ~ expr ;
        Token current = getCurrent();
        if (assertToken(current, TokenType.PRINT_STATEMENT)) {
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
        if (assertToken(current, TokenType.PLUS)) {
            advanceToken();
            part();
            opPlus();
            sum();
        }
        if (assertToken(current, TokenType.MINUS)) {
            advanceToken();
            part();
            opMinus();
            sum();
        }
    }

    static void logical() {
        Token current = getCurrent();
        if (assertToken(current, TokenType.GREATER)) {
            advanceToken();
            part();
            sum();
            opMore();
        }
        if (assertToken(current, TokenType.LESS)) {
            advanceToken();
            part();
            sum();
            opLess();
        }
        if (assertToken(current, TokenType.EQUALS)) {
            advanceToken();
            part();
            sum();
            opEqual();
        }
    }

    static void unary() {
        boolean isPositive = true;
        Token current = getCurrent();
        if (assertToken(current, TokenType.PLUS) || assertToken(current, TokenType.MINUS)) {
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
        /* TODO: ^ op?
        if (String.valueOf(current.value).equalsIgnoreCase("^")) {
            opPower();
            starSlash();
        }
        */
    }

    static void arg(boolean isPositive) {
        Token current = getCurrent();
        if (current.type == TokenType.NUMBER) {
            if (assertToken(current, TokenType.NUMBER)) {
                int tmp = getInt(current.value);
                int value = isPositive ? tmp : -tmp;
                stack.push(value);
                advanceToken();
            } else {
                printErr(current, "Expected integer, but found");
            }
        }
        if (current.type == TokenType.CHAR) {
            if (assertToken(current, TokenType.OPEN_BRACKET)) {
                advanceToken(); // (
                expression();
                current = getCurrent();
                if (assertToken(current, TokenType.CLOSE_BRACKET)) {
                    advanceToken(); // )
                } else {
                    printErr(current, "Expression with ( should end with )");
                }
            }
        }
        // TODO: IDENTIFIER, STRING, BOOLEAN?
    }

    static void opPlus() {
        if (stack.size() < 2) {
            printErr(getCurrent(), "Plus operation needs two integers, but found");
        }
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
        if (stack.size() < 2) {
            printErr(getCurrent(), "Minus operation needs two integers, but found");
        }
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
        if (stack.size() < 2) {
            printErr(getCurrent(), "Multiplication operation needs two integers, but found");
        }
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
        if (stack.size() < 2) {
            printErr(getCurrent(), "Dividing operation needs two integers, but found");
        }
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
        if (stack.size() < 2) {
            printErr(getCurrent(), "Compare operation '>' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a > _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "More operation supports only integer for now");
        }
    }

    static void opLess() {
        if (stack.size() < 2) {
            printErr(getCurrent(), "Compare operation '<' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a < _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "Less operation supports only integer for now");
        }
    }

    static void opEqual() {
        if (stack.size() < 2) {
            printErr(getCurrent(), "Compare operation '=' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a == _b ? 1 : 0;
            stack.push(value);
        } else {
            printErr(getCurrent(), "Equal operation supports only integer for now");
        }
    }

    static int getInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }

    static boolean assertToken(Token token, TokenType type) {
        return token.type == type;
    }

    static void printErr(Token token, String errMessage) {
        System.err.printf("ERROR: [%d:%d] %s: (%s)'%s'%n", token.loc.line, token.loc.column, errMessage, token.type, token.value);
        System.exit(1);
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

    enum TokenType {
        CHAR,
        SEMICOLON,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        PRINT_STATEMENT,
        PLUS,
        MINUS,
        STAR,
        DIVIDE,
        GREATER,
        LESS,
        EQUALS,
        NUMBER,
        STRING,
        KEYWORD,
        END
    }

    static class Token {
        TokenType type;
        Object value;
        Location loc;

        Token(TokenType type, Object value, Location loc) {
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

    static class Lexer {
        String allowedCharacters = "~+-*/;()=><!{}().'\"";
        String source;
        List<Token> tokenList;
        List<String> keywordList;
        int sourceLength;
        int currentCharIndex;
        int line;
        int column;
        boolean isEndOfFile;
        char currentChar;

        Lexer(String source, List<Token> tokenList) {
            this.source = source;
            this.sourceLength = source.length();
            this.tokenList = tokenList;
            this.currentCharIndex = 0;
            this.line = 0;
            this.column = 0;
            this.isEndOfFile = false;
            this.keywordList = new ArrayList<>();
            initKeywords();
        }

        void initKeywords() {
            keywordList.add("class");
            keywordList.add("void");
            keywordList.add("char");
            keywordList.add("bool");
            keywordList.add("int");
            keywordList.add("if");
            keywordList.add("else");
            keywordList.add("while");
            keywordList.add("true");
            keywordList.add("false");
        }

        void tokenize() {
            while (!isEndOfFile) {
                currentChar = peek();
                // White spaces.
                if (Character.isWhitespace(currentChar)) {
                    column++;
                    if ('\n' == currentChar) {
                        column = 0;
                        line++;
                    }
                    next();
                    continue;
                }
                // Numbers.
                if (Character.isDigit(currentChar)) {
                    tokenizeNumber();
                    continue;
                }
                if (!Character.isLetter(currentChar)) {
                    // Characters.
                    tokenizeCharacter();
                } else {
                    // Strings.
                    tokenizeString();
                }
            }
            tokenList.add(new Token(TokenType.END, "End of File", new Location(line, column)));
        }

        void tokenizeNumber() {
            StringBuilder sb = new StringBuilder();
            currentChar = peek();
            while (!isEndOfFile && Character.isDigit(currentChar)) {
                sb.append(currentChar);
                next();
            }
            tokenList.add(new Token(TokenType.NUMBER, sb.toString(), new Location(line, column)));
        }

        void tokenizeString() {
            StringBuilder sb = new StringBuilder();
            currentChar = peek();
            while (!isEndOfFile && Character.isLetter(currentChar)) {
                sb.append(currentChar);
                next();
            }
            String tokenValue = sb.toString();
            if (keywordList.contains(tokenValue)) {
                tokenList.add(new Token(TokenType.KEYWORD, tokenValue, new Location(line, column)));
            } else {
                tokenList.add(new Token(TokenType.STRING, tokenValue, new Location(line, column)));
            }
        }

        void tokenizeCharacter() {
            if (allowedCharacters.indexOf(currentChar) == -1) {
                printErr(new Token(TokenType.CHAR, currentChar, new Location(line, column)), "Lexer: Character is not allowed");
            }
            switch (currentChar) {
                case ';': {
                    tokenList.add(new Token(TokenType.SEMICOLON, currentChar, new Location(line, column)));
                    break;
                }
                case '(': {
                    tokenList.add(new Token(TokenType.OPEN_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case ')': {
                    tokenList.add(new Token(TokenType.CLOSE_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '~': {
                    tokenList.add(new Token(TokenType.PRINT_STATEMENT, currentChar, new Location(line, column)));
                    break;
                }
                case '+': {
                    tokenList.add(new Token(TokenType.PLUS, currentChar, new Location(line, column)));
                    break;
                }
                case '-': {
                    tokenList.add(new Token(TokenType.MINUS, currentChar, new Location(line, column)));
                    break;
                }
                case '*': {
                    tokenList.add(new Token(TokenType.STAR, currentChar, new Location(line, column)));
                    break;
                }
                case '/': {
                    tokenList.add(new Token(TokenType.DIVIDE, currentChar, new Location(line, column)));
                    break;
                }
                case '>': {
                    tokenList.add(new Token(TokenType.GREATER, currentChar, new Location(line, column)));
                    break;
                }
                case '<': {
                    tokenList.add(new Token(TokenType.LESS, currentChar, new Location(line, column)));
                    break;
                }
                case '=': {
                    tokenList.add(new Token(TokenType.EQUALS, currentChar, new Location(line, column)));
                    break;
                }
                default: {
                    tokenList.add(new Token(TokenType.CHAR, currentChar, new Location(line, column)));
                }
            }
            next();
        }

        void next() {
            currentCharIndex++;
            column++;
            currentChar = peek();
        }

        char peek() {
            if (currentCharIndex == sourceLength) {
                isEndOfFile = true;
            }
            return isEndOfFile ? '\0' : source.charAt(currentCharIndex);
        }
    }
}
