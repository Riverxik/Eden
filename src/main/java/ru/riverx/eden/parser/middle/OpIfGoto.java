package ru.riverx.eden.parser.middle;

public class OpIfGoto implements VM2Asm {

    private final String uniqueLabel;

    public OpIfGoto(String uniqueLabel) {
        this.uniqueLabel = uniqueLabel;
    }

    @Override
    public String getAsmCode() {
        return "; OpIfGoto: " + uniqueLabel + "\n\tpop eax\n\tcmp eax, -1\n\t je " + uniqueLabel;
    }
}
