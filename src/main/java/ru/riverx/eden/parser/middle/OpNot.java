package ru.riverx.eden.parser.middle;

public class OpNot implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; OpNot\n\tmov eax, [esp]\n\tnot eax\n\tmov dword [esp], eax";
    }
}
