package ru.riverx.eden.parser.middle;

public class OpGoto implements VM2Asm {

    private final String uniqueLabel;

    public OpGoto(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpGoto: " + uniqueLabel + "\n\tjmp " + uniqueLabel;
    }
}
