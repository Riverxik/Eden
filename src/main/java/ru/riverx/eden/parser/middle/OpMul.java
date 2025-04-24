package ru.riverx.eden.parser.middle;

public class OpMul implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpMul\n\tpop ebx\n\tpop eax\n\timul eax, ebx\n\tpush eax";
    }
}
