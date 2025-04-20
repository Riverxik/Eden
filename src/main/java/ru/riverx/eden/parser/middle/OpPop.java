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
            case "local": {
                sb.append("\tmov dword ebx, [eden_lcl]\n\tsub ebx, ").append((index+1)*4).append("\n\tmov dword [ebx], eax");
            } break;
            default: throw new UnknownSegmentException("Unknown segment type: " + segment);
        }

        return sb.toString();
    }
}
