package ru.riverx.eden.parser.middle;

public class OpEq implements VM2Asm {

    private final String uniqueLabel;

    public OpEq(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpEq: " + uniqueLabel + "\n\tmov eax, " + uniqueLabel + "\n\tjmp eden_comp_eq\n" + uniqueLabel + ":";
    }
}
