package ru.riverx.eden.parser.high;

public class Variable {
    private final String type;
    private final SymbolKind kind;
    private int index;
    private static int staticCount = 0;
    private static int fieldCount = 0;
    private static int argumentCount = 0;
    private static int localCount = 0;

    public Variable(String type, SymbolKind kind) {
        this.type = type;
        this.kind = kind;
        increaseKindCount(this);
    }

    private static void increaseKindCount(Variable variable) {
        switch (variable.kind) {
            case STATIC: variable.index = staticCount++; break;
            case FIELD: variable.index = fieldCount++; break;
            case ARG: variable.index = argumentCount++; break;
            case VAR: variable.index = localCount++; break;
            default: break;
        }
    }

    public String getType() { return type; }

    public String getKind() {
        switch (kind) {
            case FIELD: return "this";
            case STATIC: return "static";
            case CONSTANT: return "constant";
            case ARG: return "argument";
            case VAR: return "local";
            case TEMP: return "temp";
            case POINTER: return "pointer";
            case THAT: return "that";
            default:
                throw new IllegalStateException("Unexpected value: " + kind);
        }
    }

    public int getIndex() {
        return index;
    }

    public static int getStaticCount() {
        return staticCount;
    }

    public static int getFieldCount() {
        return fieldCount;
    }

    public static int getArgumentCount() {
        return argumentCount;
    }

    public static int getLocalCount() {
        return localCount;
    }

    public static void setStaticCountToZero() {
        staticCount = 0;
    }
    public static void setFieldCountToZero() {
        fieldCount = 0;
    }

    public static void setArgumentCountToZero() {
        argumentCount = 0;
    }

    public static void setLocalCountToZero() {
        localCount = 0;
    }
}
