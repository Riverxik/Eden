package ru.riverx.eden.parser.middle;

public class OpAdd implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpAdd\n\tpop eax\n\tpop ebx\n\tadd eax, ebx\n\tpush eax";
    }
}
