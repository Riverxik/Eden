package ru.riverx.eden.parser.middle;

public class OpReturn implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpReturn\n\tpop eax\n\tjmp eden_return";
    }
}
