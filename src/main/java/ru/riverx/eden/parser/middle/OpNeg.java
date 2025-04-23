package ru.riverx.eden.parser.middle;

public class OpNeg implements VM2Asm {

    @Override
    public String getAsmCode() {
        return "; Neg\n\tmov eax, [esp]\n\tneg eax\n\tmov [esp], eax";
    }
}
