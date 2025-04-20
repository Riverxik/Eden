package ru.riverx.eden.parser.middle;

public class OpFunctionInfo implements VM2Asm {

    private final String funcName;
    private final int funcArgCount;

    public OpFunctionInfo(String funcName, int funcArgCount) {
        this.funcName = funcName;
        this.funcArgCount = funcArgCount;
    }

    @Override
    public String getAsmCode() {
        return String.format("; Function %s: %d locals%n%s:", funcName, funcArgCount, funcName);
    }

    public String getFuncName() {
        return funcName;
    }
}
