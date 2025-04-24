package ru.riverx.eden.parser.middle;

public class OpAnd implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpAnd\n\tpop eax\n\tpop ebx\n\tand eax, ebx\n\tpush eax";
    }
}
