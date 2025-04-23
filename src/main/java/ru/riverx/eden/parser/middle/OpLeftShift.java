package ru.riverx.eden.parser.middle;

public class OpLeftShift implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpLeftShift\n\tpop ecx\n\tpop ebx\n\tshl ebx, cl\n\tpush ebx";
    }
}
