package ru.riverx.eden.parser.middle;

public class OpLabel implements VM2Asm {

    private final String uniqueLabel;

    public OpLabel(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpLabel: " + uniqueLabel + "\n" + uniqueLabel + ":";
    }
}
