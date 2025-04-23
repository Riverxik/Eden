package ru.riverx.eden.parser.middle;

public class OpCall implements VM2Asm {

    private static int count = 0;
    private final String funcName;
    private final int nArgs;

    public OpCall(String funcName, int nArgs) {
        this.funcName = funcName;
        this.nArgs = nArgs;
    }

    @Override
    public String getAsmCode() {
        String s = "; OpCall " + funcName + ", nArgs: " + nArgs +
                "\n\tmov eax, " + funcName +
                "\n\tmov ebx, eax";
        if (nArgs == 0) {
            s += "\n\tpush 0";
        }
        int argCount = Math.max(1, nArgs);
        s += "\n\tmov dword [eden_r13], " + (argCount*4);
        String xRetLabel = funcName + "_" + "RT_" + incCount();
        s += "\n\tmov eax, " + xRetLabel;
        s += "\n\tjmp ebx";
        s += "\n" + xRetLabel + ":";

        return s;
    }

    private static int incCount() {
        return count++;
    }
}
