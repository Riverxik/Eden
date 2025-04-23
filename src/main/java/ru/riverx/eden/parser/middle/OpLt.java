package ru.riverx.eden.parser.middle;

public class OpLt implements VM2Asm {

    private final String uniqueLabel;

    public OpLt(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpLt: " + uniqueLabel + "\n\tmov eax, " + uniqueLabel + "\n\tjmp eden_comp_lt\n" + uniqueLabel + ":";
    }
}
