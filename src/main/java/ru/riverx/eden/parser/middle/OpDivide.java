package ru.riverx.eden.parser.middle;

public class OpDivide implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpDivide\n\tpop ebx\n\tpop eax\n\txor edx, edx\n\tcdq\n\tidiv ebx\n\tpush eax";
    }
}
