package ru.riverx.eden.parser.middle;

public class OpSub implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; Op Sub\n\tpop eax\n\tpop ebx\n\tsub ebx, eax\n\tpush ebx";
    }
}
