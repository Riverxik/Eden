package ru.riverx.eden.parser.middle;

public class OpRightShift implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpRightShift\n\tpop ecx\n\tpop ebx\n\tshr ebx, cl\n\tpush ebx";
    }
}
