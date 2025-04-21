package ru.riverx.eden.parser.middle;

import ru.riverx.eden.exceptions.UnknownSegmentException;

public class OpPush implements VM2Asm {

    private final String segment;
    private final int index;
    private final String filename;

    public OpPush(String segment, int index) {
        this.segment = segment;
        this.index = index;
        this.filename = "";
    }

    public OpPush(String segment, int index, String filename) {
        this.segment = segment;
        this.index = index;
        this.filename = filename;
    }

    @Override
    public String getAsmCode() {
        String res = "; Push " + segment + " " + index;
        switch (segment) {
            case "constant": { return res + "\n\tpush " + index; }
//            case "static": { return "PUSH DWORD " + filename + index; }
            case "temp": { return res + "\n\tmov dword eax, [eden_temp]\n\tpush eax"; }
            case "pointer": { return res + "\n\tmov eax, " + (index == 0 ? "[eden_this]" : "[eden_that]") + "\n\tpush eax"; }
            case "local": { return res + "\n\tmov dword eax, [eden_lcl]\n\tsub eax, " + (index+1)*4 + "\n\tpush dword [eax]"; }
            case "argument": { return res + "\n\tmov dword eax, [eden_arg]\n\tsub eax, " + index * 4 + "\n\tpush dword [eax]"; }
            case "this": {
                return res + "\n\tmov dword eax, [eden_this]\n\tadd eax, " + (index*4) + "\n\tpush dword [eax]";
            }
            case "that": {
                return res + "\n\tmov dword eax, [eden_that]\n\tadd eax, " + (index*4) + "\n\tpush dword [eax]";
            }
            default: throw new UnknownSegmentException("Unknown segment type: " + segment);
        }
    }
}
