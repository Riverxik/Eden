package ru.riverx.eden.parser.middle;

import ru.riverx.eden.exceptions.UnknownSegmentException;

public class OpPop implements VM2Asm {

    private final String segment;
    private final int index;

    public OpPop(String segment, int index) {
        this.segment = segment;
        this.index = index;
    }

    @Override
    public String getAsmCode() {
        StringBuilder sb = new StringBuilder();
        sb.append("; Pop ").append(segment).append(" ").append(index).append("\n");
        sb.append("\tpop eax\n");
        switch (segment) {
            // constant
            // static
            case "temp": {
                if (index != 0) { throw new IllegalArgumentException("Only Pop temp 0 is allowed!"); } // Add shift by index if needed.
                sb.append("\tmov dword ebx, eden_temp\n\tmov [ebx], eax");
            } break;
            case "pointer": {
                sb.append("\tmov dword ebx, ").append(index == 0 ? "eden_this" : "eden_that")
                        .append("\n\tmov dword [ebx], eax");
            } break;
            case "local": {
                sb.append("\tmov dword ebx, [eden_lcl]\n\tsub ebx, ").append((index+1)*4)
                        .append("\n\tmov dword [ebx], eax");
            } break;
            case "argument": {
                sb.append("\tmov dword ebx, [eden_arg]\n\tsub ebx, ").append(index*4).append("\n\tmov dword [ebx], eax");
            } break;
            case "this": {
                sb.append("\tmov dword ebx, [eden_this]\n\tadd ebx, ").append(index*4).append("\n\tmov dword [ebx], eax");
            } break;
            case "that": {
                sb.append("\tmov dword ebx, [eden_that]\n\tadd ebx, ").append(index*4).append("\n\tmov dword [ebx], eax");
            } break;
            default: throw new UnknownSegmentException("Unknown segment type: " + segment);
        }

        return sb.toString();
    }
}
