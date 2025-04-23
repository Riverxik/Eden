package ru.riverx.eden.parser.middle;

public class OpPushConstant implements VM2Asm {

    private final String constant;

    public OpPushConstant(String constant) {
        this.constant = constant;
    }

    @Override
    public String getAsmCode() {
        return "; Push " + constant + "\n\tpush " + constant;
    }
}
