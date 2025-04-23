package ru.riverx.eden.parser;

import ru.riverx.eden.parser.middle.*;

import java.util.ArrayList;
import java.util.List;

public class VMWriter {
    private final List<VM2Asm> vmCode;

    public VMWriter() {
        this.vmCode = new ArrayList<>();
    }

    public List<VM2Asm> getCode() {
        return this.vmCode;
    }

    // segment: CONST, ARG, LOCAL, STATIC, THIS, THAT, POINTER, TEMP
    public void writePush(String segment, int index) {
        vmCode.add(new OpPush(segment, index));
    }

    public void writePop(String segment, int index) {
        vmCode.add(new OpPop(segment, index));
    }

    public void writeArithmetic(VMCommand cmd) {
        writeArithmetic(cmd, "");
    }

    public void writeArithmetic(VMCommand cmd, String uniqueLabel) {
        switch (cmd) {
            case ADD: vmCode.add(new OpAdd()); break;
            case SUB: vmCode.add(new OpSub()); break;
            case NEG: vmCode.add(new OpNeg()); break;
            case EQ: vmCode.add(new OpEq(uniqueLabel)); break;
            case GT: vmCode.add(new OpGt(uniqueLabel)); break;
            case LT: vmCode.add(new OpLt(uniqueLabel)); break;
//            case AND: vmCode.add("and"); break;
//            case OR: vmCode.add("or"); break;
            case NOT: vmCode.add(new OpNot()); break;
//            case MULTIPLY: writeCall("Math.multiply", 2); break;
//            case DIVIDE: writeCall("Math.divide", 2); break;
            default: throw new IllegalArgumentException("Not implemented cmd: " + cmd);
        }
    }

    public void writeConstant(String constant) {
        vmCode.add(new OpPushConstant(constant));
    }

    public void writeLabel(String label) {
        vmCode.add(new OpLabel(label));
    }

    public void writeGoto(String label) {
        vmCode.add(new OpGoto(label));
    }

    public void writeIfGoto(String label) {
        vmCode.add(new OpIfGoto(label));
    }

    public void writeCall(String name, int nArgs) {
        vmCode.add(new OpCall(name, nArgs));
    }

    public void writeFunction(String name, int nLocals) {
        vmCode.add(new OpFunctionInfo(name, nLocals));
    }

    public void writeReturn() {
        vmCode.add(new OpReturn());
    }

    public void writeRawCall(String name) {
        vmCode.add(new OpRawCall(name));
    }
}
