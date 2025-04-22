package ru.riverx.eden.parser.middle;

public class OpRawCall implements VM2Asm {

    private final String callName;

    public OpRawCall(String callName) {
        this.callName = callName;
    }

    @Override
    public String getAsmCode() {
        return "; RawCall\n\tcall " + callName + "\n\tpush eax";
    }
}
