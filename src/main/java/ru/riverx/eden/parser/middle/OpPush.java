package ru.riverx.eden.parser.middle;

import ru.riverx.eden.exceptions.UnknownSegmentException;

public class OpPush implements VM2Asm {

    private final String segment;
    private final int index;
    private final String filename;

    public OpPush(String segment, int index) {
        this.segment = segment;
        this.index = index;
        this.filename = "";
    }

    public OpPush(String segment, int index, String filename) {
        this.segment = segment;
        this.index = index;
        this.filename = filename;
    }

    @Override
    public String getAsmCode() {
        if (segment.equals("static")) {
            return "PUSH static " + filename + ", " + index;
        }
        return "PUSH " + segment + " " + index;
//        switch (segment) {
//            case "constant": { return getCodeConstByIndex(index); }
//            case "static": { return "PUSH DWORD " + filename + index; }
//            case "temp": { return "MOV EAX, tempSeg\nADD " + index + "\nPUSH EAX"; }
//            case "pointer": { return index == 0 ? "PUSH thisSeg" : "PUSH thatSeg"; }
//            case "local": { return "PUSH localSeg"; }
//            case "argument": { return "PUSH argSeg"; }
//            case "this": { return "PUSH thisSeg"; }
//            case "that": { return "PUSH thatSeg"; }
//            default: throw new UnknownSegmentException("Unknown segment type: " + segment);
//        }
    }

//    private String getCodeConstByIndex(int index) {
//        return "push edx\nxor edx, edx\nmov eax, " + index + "\nimul eax, 4\npop edx\npush eax";
//    }
}
