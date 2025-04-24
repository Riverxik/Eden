package ru.riverx.eden.parser.middle;

public class OpOr implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpOr\n\tpop eax\n\tpop ebx\n\tor eax, ebx\n\tpush eax";
    }
}
