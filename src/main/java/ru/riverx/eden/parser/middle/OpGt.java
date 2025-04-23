package ru.riverx.eden.parser.middle;

public class OpGt implements VM2Asm {

    private final String uniqueLabel;

    public OpGt(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpGt: " + uniqueLabel + "\n\tmov eax, " + uniqueLabel + "\n\tjmp eden_comp_gt\n" + uniqueLabel + ":";
    }
}
