package ru.riverx.eden.parser.high;

import ru.riverx.eden.exceptions.UndefinedVariableException;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private final Map<String, Variable> classLevel;
    private final Map<String, Variable> subroutineLevel;

    public SymbolTable() {
        this.classLevel = new HashMap<>();
        this.subroutineLevel = new HashMap<>();
    }

    public Variable findVariable(String varName) {
        for (Map.Entry<String, Variable> entry : subroutineLevel.entrySet()) {
            if (varName.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, Variable> entry : classLevel.entrySet()) {
            if (varName.equals(entry.getKey())) {
                return entry.getValue();
            }
        }

        throw new UndefinedVariableException("Undefined variable: " + varName);
    }

    public void resetKindCountSubroutine() {
        Variable.setArgumentCountToZero();
        Variable.setLocalCountToZero();
        subroutineLevel.clear();
    }

    public void resetKindCountClass() {
        Variable.setStaticCountToZero();
        Variable.setFieldCountToZero();
        classLevel.clear();
        resetKindCountSubroutine();
    }

    public int varCount(SymbolKind kind) {
        switch (kind) {
            case STATIC: return Variable.getStaticCount();
            case FIELD: return Variable.getFieldCount();
            case ARG: return Variable.getArgumentCount();
            case VAR: return Variable.getLocalCount();
            default: throw new IllegalArgumentException("There is no kind: " + kind);
        }
    }

    public void defineClass(String name, String type, String kind) {
        SymbolKind k = getKindFromString(kind);
        Variable variable = new Variable(type, k);
        classLevel.put(name, variable);
    }

    public void defineSubroutine(String name, String type, String kind) {
        SymbolKind k = getKindFromString(kind);
        Variable variable = new Variable(type, k);
        subroutineLevel.put(name, variable);
    }

    private SymbolKind getKindFromString(String kind) {
        switch (kind) {
            case "field": return SymbolKind.FIELD;
            case "static": return SymbolKind.STATIC;
            case "arg": return SymbolKind.ARG;
            case "var": return SymbolKind.VAR;
            case "const": return SymbolKind.CONSTANT;
            default: throw new IllegalArgumentException("There is no kind: " + kind);
        }
    }

    private String kindOf(String name) {
        return findVariable(name).getKind();
    }

    private String typeOf(String name) {
        return findVariable(name).getType();
    }

    private int indexOf(String name) {
        return findVariable(name).getIndex();
    }

    public String writeDebugInfoFromTable(String objectName, boolean isClass) {
        Map<String, Variable> table = isClass ? classLevel : subroutineLevel;
        StringBuilder sb = new StringBuilder(objectName);
        sb.append(":");
        for (String varName : table.keySet()) {
            sb.append(String.format("[%s:%s:%s:%d]:", varName, typeOf(varName), kindOf(varName), indexOf(varName)));
        }
        return sb.toString();
    }
}
