package ru.riverx.eden.tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Tokenizer {

    private static final String[] KEYWORDS = {
            "use", "class", "constructor", "func", "method", "field",
            "static", "var", "int", "char", "bool", "void", "win",
            "true", "false", "null", "this", "asm", "alloc", "free",
            "let", "do", "if", "else", "while", "return"
    };
    private static final String SYMBOLS = "{}()[].,;:+-*/&|<>=~";
    private final List<Token> tokenList;
    private final String input;
    private final int length;
    private final List<String> keywordList;
    private int charCount;
    private final String filename;
    private int line;

    public Tokenizer(String filename, String input) {
        this.filename = filename;
        this.input = input;
        this.length = input.length();
        this.charCount = 0;
        this.line = 1;
        this.keywordList = Arrays.asList(KEYWORDS);
        this.tokenList = tokenize();
    }

    public void printTokens() {
        for (Token token : tokenList) {
            System.out.println(token.toString());
        }
    }

    public List<Token> getTokenList() {
        return this.tokenList;
    }

    private List<Token> tokenize() {
        List<Token> tokenizedList = new ArrayList<>();
        while (hasNextChar()) {
            char curr = getNextChar();
            if (Character.isWhitespace(curr)) {
                if (curr == '\n') { line++; }
            } else if (curr == '/' && isComment()) { ignoreSpecialComments(); }
            else if (Character.isDigit(curr)) { tokenizedList.add(tokenizeDigit(curr)); }
            else if (Character.isLetter(curr)) { tokenizedList.add(tokenizeWord(curr)); }
            else if (curr == '"') { tokenizedList.add(tokenizeStringConstant()); }
            else { tokenizedList.add(tokenizeSymbol(curr)); }
        }

        return tokenizedList;
    }

    private boolean isComment() {
        if (!hasNextChar()) { return false; }
        char next = getNextChar();
        charCount--;
        return next == '/' || next == '*';
    }

    private void ignoreSpecialComments() {
        if (!hasNextChar()) { return; }
        switch (getNextChar()) {
            case '/': processSingleLineComment(); break;
            case '*': processMultiLineComment(); break;
            default: System.err.println("ERROR: unknown character in special comment");
        }
    }

    private void processSingleLineComment() {
        if (hasNextChar()) {
            char next = getNextChar();
            while (!"\n".equals(String.valueOf(next))) {
                if (hasNextChar()) next = getNextChar();
                else break;
            }
            line++;
        }
    }

    private void processMultiLineComment() {
        while (hasNextChar()) {
            char next = getNextChar();
            if (next == '\n') { line++; }
            if (next == '*') {
                char second = getNextChar();
                if (second == '/') break;
            }
        }
    }

    private Token tokenizeDigit(char curr) {
        StringBuilder sb = new StringBuilder().append(curr);
        while (hasNextChar()) {
            char next = getNextChar();
            if (Character.isDigit(next)) {
                sb.append(next);
            } else {
                charCount--;
                break;
            }
        }

        return new Token(sb.toString(), Token.TokenType.INTEGER_CONSTANT, filename, line);
    }

    private Token tokenizeWord(char curr) {
        StringBuilder sb = new StringBuilder().append(curr);
        while (hasNextChar()) {
            char next = getNextChar();
            if (Character.isSpaceChar(next) || SYMBOLS.indexOf(next) != -1) {
                break;
            } else if (Character.isLetterOrDigit(next) || next == '_') {
                sb.append(next);
            }
        }

        charCount--;
        String result = sb.toString();
        Token.TokenType type = keywordList.contains(result) ? Token.TokenType.KEYWORD : Token.TokenType.IDENTIFIER;

        return new Token(result, type, filename, line);
    }

    private Token tokenizeStringConstant() {
        StringBuilder sb = new StringBuilder();
        while (hasNextChar()) {
            char next = getNextChar();
            if (next == '"' || (Character.isSpaceChar(next) && !Character.isWhitespace(next))) { break; }
            sb.append(next);
        }

        return new Token(sb.toString(), Token.TokenType.STRING_CONSTANT, filename, line);
    }

    private Token tokenizeSymbol(char curr) {
        int index = SYMBOLS.indexOf(curr);
        if (index == -1) {
            System.err.println("ERROR: unsupported symbol: " + curr);
            System.exit(-1);
        }

        return new Token(String.valueOf(curr), Token.TokenType.SYMBOL, filename, line);
    }

    private boolean hasNextChar() { return charCount < length; }

    private char getNextChar() { return input.charAt(charCount++); }
}
