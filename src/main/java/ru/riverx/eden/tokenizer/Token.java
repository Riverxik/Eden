package ru.riverx.eden.tokenizer;

public class Token {
    public enum TokenType {
        KEYWORD,
        SYMBOL,
        INTEGER_CONSTANT,
        STRING_CONSTANT,
        IDENTIFIER
    }

    private final String value;
    private final TokenType type;
    private final String filename;
    private final int line;

    public Token(String value, TokenType type, String filename, int line) {
        this.value = value;
        this.type = type;
        this.filename = filename;
        this.line = line;
    }

    public String getValue() {
        return value;
    }

    public TokenType getType() {
        return type;
    }

    public String getFilename() {
        return filename;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return String.format("[%s:%d][%s][%s]", filename, line, type, value);
    }
}
