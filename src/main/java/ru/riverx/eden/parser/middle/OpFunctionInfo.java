package ru.riverx.eden.parser.middle;

public class OpFunctionInfo implements VM2Asm {

    private final String funcName;
    private final int funcArgCount;

    public OpFunctionInfo(String funcName, int funcArgCount) {
        this.funcName = funcName;
        this.funcArgCount = funcArgCount;
    }

    @Override
    public String getAsmCode() {
        StringBuilder res = new StringBuilder("; Function " + funcName + ": " + funcArgCount + "\n" + funcName + ":" +
                // eax has return address from the caller
                "\n\tpush eax" +
                // save local, arg, this, that
                "\n\tpush dword [eden_lcl]\n\tpush dword [eden_arg]" +
                "\n\tpush dword [eden_this]\n\tpush dword [eden_that]" +
                // move current sp to 20 bytes (because we save 5 variables)
                "\n\tmov dword eax, [eden_r13]" +
                "\n\tadd eax, 20" +
                "\n\tmov ebx, esp" +
                "\n\tadd ebx, eax" +
                // set current frame for arg and local
                "\n\tmov dword [eden_arg], ebx" +
                "\n\tsub dword [eden_arg], 4" +
                "\n\tmov dword [eden_lcl], esp");
        // Push 0 for every local variable
        for (int i = 0; i < funcArgCount; i++) {
            res.append("\n\tpush 0");
        }

        return res.toString();
    }

    public String getFuncName() {
        return funcName;
    }
}
