import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * I will try to make Eden self-hosted as soon as possible, so forgive me for this mess
 * I will try it without OOP style. After I self-hosted it I will refactor all of this. Maybe, maybe not.
 * I just started and i see that it's really hard to do without OOP features and stuff...
 */
public class Eden {
    static List<Token> tokenList = new ArrayList<>();
    static int tokenIndex = 0;
    static Stack<Object> stack = new Stack<>();
    static List<Scope> scopeList = new ArrayList<>();
    static int scopeLevel = 0;
    static Token currentToken;

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
        lexer.clearComments();

        program();
    }

    static void program() {
        // Initialization.
        scopeList.add(new Scope());
        currentToken = getCurrent();
        while (currentToken.type != TokenType.END) {
            specialBlockStatements();
        }
    }

    static void specialBlockStatements() {
        if (assertKeyWord("if")) {
            ifStatement();
        } else if (assertKeyWord("while")) {
            whileStatement();
        } else {
            blockStatements(false);
        }
    }

    static void whileStatement() {
        if (assertTokenAndAdvance(TokenType.OPEN_BRACKET)) {
            int startExpressionIndex = tokenIndex;
            int endWhileExpressionIndex = -1;
            expression();
            int value = Integer.parseInt(String.valueOf(stack.pop()));
            if (assertTokenAndAdvance(TokenType.CLOSE_BRACKET)) {
                while (value == 1) {
                    blockStatements(false);
                    endWhileExpressionIndex = tokenIndex;
                    tokenIndex = startExpressionIndex;
                    currentToken = getCurrent();
                    expression();
                    value = Integer.parseInt(String.valueOf(stack.pop()));
                    advanceToken(); // skip )
                }
                if (endWhileExpressionIndex != -1) {
                    tokenIndex = endWhileExpressionIndex;
                    currentToken = getCurrent();
                }
            } else {
                printErrCurrentToken("Expected close bracket ')' but found");
            }
        } else {
            printErrCurrentToken("Expected open bracket '(' but found");
        }
    }

    static void blockStatements(boolean isNewScope) {
        if (assertTokenAndAdvance(TokenType.OPEN_CURLY_BRACKET)) {
            if (isNewScope) {
                scopeList.add(new Scope());
                scopeLevel++;
            }
            // START BLOCK.
            while (!assertToken(TokenType.CLOSE_CURLY_BRACKET) && !assertToken(TokenType.END)) {
                specialBlockStatements();
            }
            // CLOSE BLOCK.
            if (assertTokenAndAdvance(TokenType.CLOSE_CURLY_BRACKET)) {
                if (isNewScope) {
                    scopeList.remove(scopeLevel);
                    scopeLevel--;
                }
            } else {
                printErrCurrentToken("Block statement must close with curly bracket '}' but found");
            }
        } else {
            statements();
        }
    }

    static void statements() {
        statement();
        if (assertToken(TokenType.SEMICOLON)) {
            advanceToken();
        } else {
            printErrCurrentToken("Expected ';' but found");
        }
    }

    static void statement() {
        if (assertToken(TokenType.PRINT_STATEMENT)) {
            printStatement();
        } else if (assertToken(TokenType.KEYWORD)) {
            keywordStatement();
        } else if (assertToken(TokenType.STRING)) {
            valueAssignment();
        } else {
            printErrCurrentToken("Unknown statement");
        }
    }

    static void keywordStatement() {
        if (assertKeyWord("int")) {
            defineVariable(EdenType.INT);
        } else if (assertKeyWord("bool")) {
            defineVariable(EdenType.BOOL);
        } else if (assertKeyWord("char")) {
            defineVariable(EdenType.CHAR);
        } else {
            printErrCurrentToken("Unknown keyword");
        }
    }

    static void ifStatement() {
        if (assertTokenAndAdvance(TokenType.OPEN_BRACKET)) {
            expression();
            int isFirstBlock = Integer.parseInt(String.valueOf(stack.pop()));
            if (assertTokenAndAdvance(TokenType.CLOSE_BRACKET)) {
                if (isFirstBlock == 1) {
                    blockStatements(false);
                    if (currentToken.type == TokenType.KEYWORD && assertTokenValue("else")) {
                        advanceToken();
                        skipBlock();
                    }
                } else {
                    skipBlock();
                    if (currentToken.type == TokenType.KEYWORD && assertTokenValue("else")) {
                        advanceToken();
                        blockStatements(false);
                    }
                }
            } else {
                printErrCurrentToken("Expected close bracket ')' but found");
            }
        } else {
            printErrCurrentToken("Expected open bracket '(' but found");
        }
    }

    static void skipBlock() {
        if (assertToken(TokenType.OPEN_CURLY_BRACKET)) {
            advanceToken();
            int indexBracket = 1;
            while (indexBracket != 0 && !assertToken(TokenType.END)) {
                if (assertToken(TokenType.CLOSE_CURLY_BRACKET)) {
                    indexBracket--;
                }
                if (assertToken(TokenType.OPEN_CURLY_BRACKET)) {
                    indexBracket++;
                }
                if (indexBracket != 0) {
                    advanceToken();
                }
            }
            if (assertToken(TokenType.CLOSE_CURLY_BRACKET)) {
                advanceToken();
            } else {
                printErrCurrentToken("Expected close curly bracket '}' but found");
            }
        } else {
            printErrCurrentToken("Expected open curly bracket '{' but found");
        }
    }

    static void defineVariable(EdenType defineType) {
        if (assertToken(TokenType.STRING)) {
            String variableName = String.valueOf(currentToken.value);
            checkVariableNameAndAddToMap(variableName, defineType);
            valueAssignment();
            if (assertTokenAndAdvance(TokenType.COMMA)) {
                defineVariable(defineType);
            }
        } else {
            printErrCurrentToken("Expected variable name but found");
        }
    }

    static void checkVariableNameAndAddToMap(String variableName, EdenType tokenType) {
        if (variableName.length() < 1) {
            printErrCurrentToken("Variable name must contain at least one symbol");
        }
        if (!Character.isLetter(variableName.charAt(0))) {
            printErrCurrentToken("Variable name must starts with the letter");
        }
        if (!Character.isLowerCase(variableName.charAt(0))) {
            printErrCurrentToken("Variable name must starts with the lower case letter");
        }
        if (scopeList.get(scopeLevel).variableMap.containsKey(variableName)) {
            String error = String.format("Variable name %s is already defined", variableName);
            printErrCurrentToken(error);
        }
        scopeList.get(scopeLevel).variableMap.put(variableName, tokenType);
    }

    static void valueAssignment() {
        String variableName = String.valueOf(currentToken.value);
        if (scopeList.get(scopeLevel).variableMap.containsKey(variableName)) {
            advanceToken();
            if (assertTokenAndAdvance(TokenType.EQUALS)) {
                EdenType variableType = scopeList.get(scopeLevel).variableMap.get(variableName);
                initializeVariable(variableName, variableType);
            }
        } else {
            printErrCurrentToken("Variable is not defined");
        }
    }

    static void initializeVariable(String variableName, EdenType variableType) {
        expression();
        Object value = stack.pop();
        scopeList.get(scopeLevel).variableValue.put(variableName, value);
        if (assertTokenAndAdvance(TokenType.COMMA)) {
            defineVariable(variableType);
        }
    }

    static void printStatement() {
        // ~ expr ;
        if (assertTokenAndAdvance(TokenType.PRINT_STATEMENT)) {
            expression();
        } else {
            printErrCurrentToken("Print statement should starts with '~'");
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
        if (assertToken(TokenType.PLUS)) {
            advanceToken();
            part();
            opPlus();
            sum();
        }
        if (assertToken(TokenType.MINUS)) {
            advanceToken();
            part();
            opMinus();
            sum();
        }
    }

    static void logical() {
        if (assertToken(TokenType.GREATER)) {
            advanceToken();
            part();
            sum();
            opMore();
        }
        if (assertToken(TokenType.LESS)) {
            advanceToken();
            part();
            sum();
            opLess();
        }
        if (assertToken(TokenType.EQUALS)) {
            advanceToken();
            part();
            sum();
            opEqual();
        }
    }

    static void unary() {
        boolean isPositive = true;
        if (assertToken(TokenType.PLUS) || assertToken(TokenType.MINUS)) {
            if (assertTokenValue("-")) {
                isPositive = false;
            }
            advanceToken();
        }
        arg(isPositive);
        starSlash();
    }

    static void starSlash() {
        if (assertTokenValue("*")) {
            advanceToken();
            unary();
            opStar();
            starSlash();
        }
        if (assertTokenValue("/")) {
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
        if (currentToken.type == TokenType.NUMBER) {
            if (assertToken(TokenType.NUMBER)) {
                int tmp = getInt(currentToken.value);
                int value = isPositive ? tmp : -tmp;
                stack.push(value);
                advanceToken();
            } else {
                printErrCurrentToken("Expected integer, but found");
            }
        }
        if (assertToken(TokenType.OPEN_BRACKET)) {
            advanceToken(); // (
            expression();
            if (assertToken(TokenType.CLOSE_BRACKET)) {
                advanceToken(); // )
            } else {
                printErrCurrentToken("Expected close bracket but found");
            }
        }
        if (assertToken(TokenType.STRING)) {
            // IDENTIFIER
            String variableName = String.valueOf(currentToken.value);
            if (scopeList.get(scopeLevel).variableMap.containsKey(variableName)) {
                if (scopeList.get(scopeLevel).variableValue.containsKey(variableName)) {
                    EdenType type = scopeList.get(scopeLevel).variableMap.get(variableName);
                    Object rawValue = scopeList.get(scopeLevel).variableValue.get(variableName);
                    setValueFromIdentifier(rawValue, type);
                    advanceToken();
                } else {
                    printErrCurrentToken("Variable is not initialized");
                }
            } else {
                printErrCurrentToken("Undefined variable");
            }
        }
        if (assertToken(TokenType.KEYWORD)) {
            // true or false
            String value = String.valueOf(currentToken.value);
            if ("true".equalsIgnoreCase(value)) {
                stack.push(1);
            } else if ("false".equalsIgnoreCase(value)) {
                stack.push(0);
            } else {
                printErrCurrentToken("Expected boolean but found");
            }
            advanceToken();
        }
        // TODO: STRING, BOOLEAN?
    }

    static void setValueFromIdentifier(Object rawValue, EdenType variableType) {
        switch (variableType) {
            case INT: {
                if (rawValue instanceof Integer) {
                    int value = (int) rawValue;
                    stack.push(value);
                } else {
                    printErrCurrentToken("Expected integer value but found");
                }
                break;
            }
            case BOOL: {
                if (rawValue instanceof Integer) {
                    int value = (int) rawValue;
                    if (value == 1) {
                        stack.push(value);
                    } else {
                        stack.push(0);
                    }
                } else {
                    printErrCurrentToken("Expected integer value but found");
                }
                break;
            }
            default: {
                String error = String.format("variableType %s is not implemented", variableType);
                printErrCurrentToken(error);
            }
        }
    }

    static void opPlus() {
        if (stack.size() < 2) {
            printErrCurrentToken("Plus operation needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a + _b);
        } else {
            printErrCurrentToken("Expected two integers, but found");
        }
    }

    static void opMinus() {
        if (stack.size() < 2) {
            printErrCurrentToken("Minus operation needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a - _b);
        } else {
            printErrCurrentToken("Expected two integers, but found");
        }
    }

    static void opStar() {
        if (stack.size() < 2) {
            printErrCurrentToken("Multiplication operation needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a * _b);
        } else {
            printErrCurrentToken("Expected two integers, but found");
        }
    }

    static void opSlash() {
        if (stack.size() < 2) {
            printErrCurrentToken("Dividing operation needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            stack.push(_a / _b);
        } else {
            printErrCurrentToken("Expected two integers, but found");
        }
    }

    static void opMore() {
        if (stack.size() < 2) {
            printErrCurrentToken("Compare operation '>' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a > _b ? 1 : 0;
            stack.push(value);
        } else {
            printErrCurrentToken("More operation supports only integer for now");
        }
    }

    static void opLess() {
        if (stack.size() < 2) {
            printErrCurrentToken("Compare operation '<' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a < _b ? 1 : 0;
            stack.push(value);
        } else {
            printErrCurrentToken("Less operation supports only integer for now");
        }
    }

    static void opEqual() {
        if (stack.size() < 2) {
            printErrCurrentToken("Compare operation '=' needs two integers, but found");
        }
        Object b = stack.pop();
        Object a = stack.pop();
        if (a instanceof Integer && b instanceof Integer) {
            int _a = getInt(a);
            int _b = getInt(b);
            int value = _a == _b ? 1 : 0;
            stack.push(value);
        } else {
            printErrCurrentToken("Equal operation supports only integer for now");
        }
    }

    static int getInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }

    static boolean assertKeyWord(String value) {
        if (assertToken(TokenType.KEYWORD) && assertTokenValue(value)) {
            advanceToken();
            return true;
        }
        return false;
    }

    static boolean assertToken(TokenType type) {
        return currentToken.type == type;
    }

    static boolean assertTokenValue(String value) {
        return String.valueOf(currentToken.value).equalsIgnoreCase(value);
    }

    static boolean assertTokenAndAdvance(TokenType type) {
        if (assertToken(type)) {
            advanceToken();
            return true;
        }
        return false;
    }

    static void printErrCurrentToken(String errMessage) {
        printErr(currentToken, errMessage);
    }

    static void printErr(Token token, String errMessage) {
        int offset = String.valueOf(token.value).length();
        System.err.printf("ERROR: [%d:%d] %s: (%s)'%s'%n", token.loc.line + 1, token.loc.column - offset, errMessage, token.type, token.value);
        System.exit(1);
    }

    static void advanceToken() {
        if (tokenIndex < tokenList.size()) {
            tokenIndex++;
            currentToken = getCurrent();
        } else {
            printErr(tokenList.get(tokenList.size()-1), "Can't advance the next token.");
        }
    }

    static Token getCurrent() {
        return tokenList.get(tokenIndex);
    }

    static class Scope {
        Map<String, EdenType> variableMap = new HashMap<>();
        Map<String, Object> variableValue = new HashMap<>();
    }

    enum EdenType {
        INT,
        CHAR,
        BOOL
    }

    enum TokenType {
        CHAR,
        SEMICOLON,
        COMMA,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        OPEN_CURLY_BRACKET,
        CLOSE_CURLY_BRACKET,
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
        String allowedCharacters = "~+-*/;()=><!{}().,'\"";
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

        void clearComments() {
            List<Token> comments = new ArrayList<>();
            int commentLine = -1;
            for (int i = 0, tokenListSize = tokenList.size() - 1; i < tokenListSize; i++) {
                Token token = tokenList.get(i);
                Token nextToken = tokenList.get(i + 1);
                if (token.type == TokenType.DIVIDE && nextToken.type == TokenType.DIVIDE) {
                    commentLine = token.loc.line;
                }
                if (token.loc.line == commentLine) {
                    comments.add(token);
                }
            }
            tokenList.removeAll(comments);
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
                case '{': {
                    tokenList.add(new Token(TokenType.OPEN_CURLY_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '}': {
                    tokenList.add(new Token(TokenType.CLOSE_CURLY_BRACKET, currentChar, new Location(line, column)));
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
                case ',': {
                    tokenList.add(new Token(TokenType.COMMA, currentChar, new Location(line, column)));
                    break;
                }
                default: {
                    if (Character.isLetter(currentChar)) {
                        tokenList.add(new Token(TokenType.CHAR, currentChar, new Location(line, column)));
                    } else {
                        printErr(new Token(TokenType.CHAR, currentChar, new Location(line, column)), "Lexer: Unknown character");
                    }
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
