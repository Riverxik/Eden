import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * I will try to make Eden self-hosted as soon as possible, so forgive me for this mess
 * I will try it without OOP style. After I self-hosted it I will refactor all of this. Maybe, maybe not.
 * I just started and i see that it's really hard to do without OOP features and stuff...
 */
public class Eden {
//    private static final long MAX_C_INTEGER = Integer.MAX_VALUE;
//    private static final String HDL_STD_OUT = "2147483636";
//    private static final String HDL_STD_ERR = "2147483635";
    static List<Token> tokenList = new ArrayList<>();
    static List<String> stringConstants = new ArrayList<>();
    static List<String> externWinCallList = new ArrayList<>();
    static StringBuilder programCode = new StringBuilder();
    static boolean isInterpreter = true;
    static boolean isRunAfterCompilation = false;

    public static void main(String[] args) throws IOException {
        // Args
        if (args.length == 0) {
            System.out.println("Usage: eden -s scrName.eden");
            System.exit(1);
        }
        int i = 0;
        String sourceName = "";
        while (i < args.length) {
            if (args[i].equalsIgnoreCase("-s")) {
                if (i+1 < args.length) {
                    sourceName = args[i+1];
                } else {
                    System.out.println("You must provide path to the source file");
                    System.exit(1);
                }
            }
            if (args[i].equalsIgnoreCase("-c")) {
                isInterpreter = false;
            }
            if (args[i].equalsIgnoreCase("-r")) {
                isRunAfterCompilation = true;
            }
            i++;
        }

        // Reading the source
        if (!Files.exists(Paths.get(sourceName))) {
            System.out.println("File does not exists: " + sourceName);
            System.exit(1);
        }
        String source = new String(Files.readAllBytes(Paths.get(sourceName)), StandardCharsets.UTF_8);

        // Lexing.
        Lexer lexer = new Lexer(source, tokenList);
        lexer.tokenize();
        lexer.clearComments();

        lexer.tokenList.forEach(System.out::println);

        // Parsing.
        // Analysis.
        // Optimising.
        // IR
        // Interpret || CodeGen
    }

    private static void run(String execCmd) throws IOException, InterruptedException {
        System.out.println("[CMD] " + execCmd);
        Process process = Runtime.getRuntime().exec("cmd.exe /c " + execCmd);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Error while execute command: " + execCmd + "\n" + readInputStream(process.getErrorStream()));
        }
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (inputStream.available() > 0) {
            sb.append((char)(inputStream.read()));
        }
        return sb.toString();
    }

    @Deprecated
    static int compileToExe(String sourceName) {
        try {
            String name = sourceName.split("[.]")[0];
            System.out.printf("[INFO] Compiling %s...\n", sourceName);
            String cmdNasm = String.format("nasm -f win32 %s.asm", name);
            String cmdGoLink = String.format("golink /entry:Start /console kernel32.dll user32.dll %s.obj", name);
            run(cmdNasm);
            run(cmdGoLink);
            return 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Deprecated
    static void writeAsmFile(String sourceName) {
        try {
            File output = new File(sourceName.split("[.]")[0]+".asm");
            FileWriter fw = new FileWriter(output);
            writeHeader(fw);
            writeData(fw);
//            writeVariables(fw);
            writeText(fw);
            fw.write(programCode.toString());
            fw.write("\tcall exit\n");
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void writeHeader(FileWriter fw) throws IOException {
        for (String externItem : externWinCallList) {
            fw.write("extern " + externItem + "\n");
        }
        fw.write("extern GetStdHandle\n" +
                     "extern WriteFile\n" +
                     "extern ExitProcess\n" +
                     "\n" +
                     "global Start\n");
    }

    static void writeData(FileWriter fw) throws IOException {
        int index = 0;
        fw.write("section .data\n");
        fw.write("\tstr_NL db `\\r\\n`\n");
        for (String stringConstant : stringConstants) {
            String fullName = "str_" + index++;
            fw.write("\t" + fullName + " db `" + stringConstant.replaceAll("\\\\n", "\\\\r\\\\n") + "`,0\n");
        }
    }

//    static void writeVariables(FileWriter fw) throws IOException {
//        fw.write("section .bss\n");
//        fw.write("\tStdHandle resd 1\n");
//        fw.write("\tdigitBuffer resb 100\n");
//        fw.write("\tdigitBufferPos resb 8\n");
//        for (EdenVar var : edenVarList) {
//            switch (var.type) {
//                case STRING:
//                case INT: {
//                    fw.write("\t" + var.identifier + " resb 4\n");
//                    break;
//                }
//                case BOOL: {
//                    fw.write("\t" + var.identifier + " resb 1\n");
//                    break;
//                }
//                case CHAR: {
//                    fw.write("\t" + var.identifier + " resb 2\n");
//                    break;
//                }
//                case NONE:
//                default: fw.write("\t; There is some problem with var declaration in compiler, probably\n");
//            }
//        }
//    }

    static void writeText(FileWriter fw) throws IOException {
        fw.write("section .text\n");
        // print
        fw.write(";In:\n");
        fw.write(";eax - pointerToMessageToPrint\n");
        fw.write("print:\n");
        fw.write("\tcmp eax, 4000000 ;IDK WTF IS THIS, SORRY\n");
        fw.write("\tjge _str\n");
        fw.write("\tcall printInt\n");
        fw.write("\t_str:\n");
        fw.write("\tcall printStr\n");
        fw.write("\tret\n");
        // printStr
        fw.write(";In:\n");
        fw.write(";eax - message\n");
        fw.write("printStr:\n");
        fw.write("\tpush eax\n");
        fw.write("\tpush eax\n");
        fw.write("\tmov ebx, 0\n");
        fw.write("_printCountLoop:\n");
        fw.write("\tinc eax\n");
        fw.write("\tinc ebx\n");
        fw.write("\tmov cl, [eax]\n");
        fw.write("\tcmp cl, 0\n");
        fw.write("\tjne _printCountLoop\n");
        fw.write("\tpop eax\n");
        fw.write("\tpush 0\n");
        fw.write("\tpush 0\n");
        fw.write("\tpush ebx\n");
        fw.write("\tpush eax\n");
        fw.write("\tpush dword [StdHandle]\n");
        fw.write("\tcall WriteFile\n");
        fw.write("\tpop eax\n");
        fw.write("\tret\n");
        // printInt
        fw.write(";In:\n");
        fw.write(";eax - number\n");
        fw.write(";ebx - lengthOfNumber\n");
        fw.write("printInt:\n");
        fw.write("\tcall numCountLen\n");
        fw.write("\tpush eax\n");
        fw.write("\tpush ebx\n");
        fw.write("\tmov ecx, digitBuffer\n");
        fw.write("\tdec ebx\n");
        fw.write("\tadd ecx, ebx\n");
        fw.write("\tmov [ecx+1], byte 0\n");
        fw.write("\tmov [digitBufferPos], ecx\n");
        fw.write("_intToStrLoop:\n");
        fw.write("\tmov edx, 0\n");
        fw.write("\tmov ebx, 10\n");
        fw.write("\tdiv ebx\n");
        fw.write("\tpush eax\n");
        fw.write("\tadd edx, 48 ; Convert last character to digit\n");
        fw.write("\tmov ecx, [digitBufferPos]\n");
        fw.write("\tmov [ecx], dl\n");
        fw.write("\tdec ecx\n");
        fw.write("\tmov [digitBufferPos], ecx\n");
        fw.write("\tpop eax\n");
        fw.write("\tcmp eax, 0\n");
        fw.write("\tjne _intToStrLoop\n");
        fw.write("\tmov [digitBufferPos], eax\n");
        fw.write("\tpop ebx\n");
        fw.write("\tpop eax\n");
        fw.write("\tmov eax, digitBuffer\n");
        fw.write("\tret\n");
        // numCountLen
        fw.write("numCountLen:\n");
        fw.write("\tpush eax\n");
        fw.write("\tmov ecx, 0\n");
        fw.write("\t_numCountLoop:\n");
        fw.write("\tmov edx, 0\n");
        fw.write("\tmov ebx, 10\n");
        fw.write("\tdiv ebx\n");
        fw.write("\tinc ecx\n");
        fw.write("\tcmp eax, 0\n");
        fw.write("\tjne _numCountLoop\n");
        fw.write("\tmov ebx, ecx\n");
        fw.write("\tpop eax\n");
        fw.write("\tret\n");
        // Exit
        fw.write("exit:\n");
        fw.write("\t;End of the program\n");
        fw.write("\tpush 0\n");
        fw.write("\tcall ExitProcess\n");
        // Start
        fw.write("Start:\n");
        fw.write("\t;Get the console handler\n");
        fw.write("\tpush -11\n");
        fw.write("\tcall GetStdHandle\n");
        fw.write("\tmov dword [StdHandle], eax\n");
    }

//    @Deprecated
//    static void doOpWinCall() {
//        if (isInterpreter) {
//            String nameOfWinCall = String.valueOf(programStack.get(stackSizeBeforeWinCallName));
//            switch (nameOfWinCall) {
//                case "GetStdHandle": {
//                    int handleValue = Integer.parseInt(String.valueOf(programStack.pop()));
//                    programStack.pop();
//                    programStack.push(MAX_C_INTEGER + handleValue);
//                } break;
//                case "WriteFile": {
//                    String handler = String.valueOf(programStack.pop());
//                    String strToPrint = String.valueOf(programStack.pop());
//                    int sizeOfStr = Integer.parseInt(String.valueOf(programStack.pop()));
//                    programStack.pop();
//                    programStack.pop();
//                    programStack.pop();
//                    if (sizeOfStr < strToPrint.length()) { strToPrint = strToPrint.substring(0, sizeOfStr); }
//                    switch (handler) {
//                        case HDL_STD_OUT: printWithNewLine(strToPrint, System.out); break;
//                        case HDL_STD_ERR: printWithNewLine(strToPrint, System.err); break;
//                        default: {
//                            printErr("Unsupported handler: " + handler);
//                        }
//                    }
//                    programStack.push(1);
//                } break;
//                default: {
//                    printErr("Unknown wincall name: " + nameOfWinCall);
//                }
//            }
//        } else {
//            String nameOfWinCall = String.valueOf(programStack.pop());
//            programCode.append("\t;OpWinCall\n");
//            programCode.append("\tcall ").append(nameOfWinCall).append("\n");
//            programCode.append("\tpush eax\n");
//        }
//    }
//
//    static void printWithNewLine(String strToPrint, PrintStream printStream) {
//        printStream.printf(strToPrint.replaceAll("[\\\\]", "%"));
//    }

    static void printErrToken(Token token, String errMessage) {
        int offset = String.valueOf(token.value).length();
        System.err.printf("ERROR: [%d:%d] %s: (%s)'%s'%n", token.loc.line + 1, token.loc.column - offset, errMessage, token.type, token.value);
        System.exit(1);
    }

    static void printErr(String errMessage) {
        System.err.printf("ERROR: %s%n", errMessage);
        System.exit(2);
    }

    enum TokenType {
        //CHARACTER,
        SEMICOLON,
        COMMA,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        OPEN_CURLY_BRACKET,
        CLOSE_CURLY_BRACKET,
        OPEN_SQUARE_BRACKET,
        CLOSE_SQUARE_BRACKET,
        PRINT_STATEMENT,
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        GREATER,
        LESS,
        EQUALS,
        L_SHIFT,
        R_SHIFT,
        B_AND,
        B_OR,
        NUMBER,
        STRING,
        SYMBOL,
        KEYWORD,
        END
    }

    static class Token {
        TokenType type;
        Object value;
        Location loc;
        int index;
        int linkIp;

        Token(TokenType type, Object value, Location loc) {
            this.type = type;
            this.value = value;
            this.loc = loc;
        }

        void setIndex(int index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return String.format("%s[%s][%d:%d][%d<->%d]", type, value, loc.line, loc.column, index, linkIp);
        }
    }

    static class Location {
        int line;
        int column;

        Location(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }

    static class Lexer {
        String allowedCharacters = "~+-*/;()=><!{}().,'\"|&[]";
        String source;
        List<Token> tokenList;
        List<String> keywordList;
        int sourceLength;
        int currentCharIndex;
        int line;
        int column;
        boolean isEndOfFile;
        char currentChar;

        Lexer(String source, List<Token> tokenList) {
            this.source = source;
            this.sourceLength = source.length();
            this.tokenList = tokenList;
            this.currentCharIndex = 0;
            this.line = 0;
            this.column = 0;
            this.isEndOfFile = false;
            this.keywordList = new ArrayList<>();
            initKeywords();
        }

        @Deprecated
        void crossReference() {
            Stack<Integer> stack = new Stack<>();
            for (Token t : tokenList) {
                if (String.valueOf(t.value).equalsIgnoreCase("if")) {
                    stack.push(t.index);
                }
                if (String.valueOf(t.value).equalsIgnoreCase("while")) {
                    stack.push(t.index);
                }
                if (String.valueOf(t.value).equalsIgnoreCase("else")) {
                    int closeBracketIp = t.index - 1;
                    Token cbToken = tokenList.get(closeBracketIp);
                    if (cbToken.type == TokenType.CLOSE_CURLY_BRACKET) {
                        t.linkIp = cbToken.linkIp;
                        stack.push(t.index);
                    } else {
                        printErr("else can only be used in `if`-blocks");
                    }
                }
                if (t.type == TokenType.OPEN_CURLY_BRACKET) {
                    int blockIp = stack.pop();
                    String blockName = String.valueOf(tokenList.get(blockIp).value);
                    if (blockName.equalsIgnoreCase("if")) {
                        tokenList.get(blockIp).linkIp = t.index;
                        t.linkIp = blockIp;
                        stack.push(t.index);
                    }
                    if (blockName.equalsIgnoreCase("else")) {
                        tokenList.get(tokenList.get(blockIp).linkIp).linkIp = t.index;
                        stack.push(blockIp);
                    }
                    if (blockName.equalsIgnoreCase("while")) {
                        tokenList.get(blockIp).linkIp = t.index;
                        t.linkIp = blockIp;
                        stack.push(t.index);
                    }
                }
                if (t.type == TokenType.CLOSE_CURLY_BRACKET) {
                    int openBracketIp = stack.pop();
                    if (tokenList.get(openBracketIp).type == TokenType.OPEN_CURLY_BRACKET) {
                        Token keywordToken = tokenList.get(tokenList.get(openBracketIp).linkIp);
                        String keywordValue = String.valueOf(keywordToken.value);
                        if (keywordValue.equalsIgnoreCase("if")) {
                            tokenList.get(openBracketIp).linkIp = t.index;
                            t.linkIp = openBracketIp;
                        } else if (keywordValue.equalsIgnoreCase("while")) {
                            tokenList.get(openBracketIp).linkIp = t.index + 1;
                            t.linkIp = keywordToken.index; // +1
                        }
                    }
                    if (String.valueOf(tokenList.get(openBracketIp).value).equalsIgnoreCase("else")) {
                        tokenList.get(openBracketIp).linkIp = t.index + 1; // +1
                    }
                }
            }
        }

        void initKeywords() {
            keywordList.add("class");
            keywordList.add("void");
            keywordList.add("char");
            keywordList.add("bool");
            keywordList.add("string");
            keywordList.add("int");
            keywordList.add("if");
            keywordList.add("else");
            keywordList.add("while");
            keywordList.add("true");
            keywordList.add("false");
            keywordList.add("wincall");
        }

        void clearComments() {
            List<Token> comments = new ArrayList<>();
            int commentLine = -1;
            for (int i = 0, tokenListSize = tokenList.size() - 1; i < tokenListSize; i++) {
                Token token = tokenList.get(i);
                Token nextToken = tokenList.get(i + 1);
                if (token.type == TokenType.DIVIDE && nextToken.type == TokenType.DIVIDE) {
                    commentLine = token.loc.line;
                }
                if (token.loc.line == commentLine) {
                    comments.add(token);
                }
            }
            tokenList.removeAll(comments);
            int i = 0;
            for (Token t : tokenList) {
                t.setIndex(i++);
            }
        }

        void tokenize() {
            while (!isEndOfFile) {
                currentChar = peek();
                // White spaces.
                if (Character.isWhitespace(currentChar)) {
                    column++;
                    if ('\n' == currentChar) {
                        column = 0;
                        line++;
                    }
                    next();
                    continue;
                }
                // Numbers.
                if (Character.isDigit(currentChar)) {
                    tokenizeNumber();
                    continue;
                }
                if (!Character.isLetter(currentChar)) {
                    // Characters.
                    if (currentChar == '\"') {
                        tokenizeString();
                    } else {
                        tokenizeSpecialCharacters();
                    }
                } else {
                    // Symbols.
                    tokenizeSymbol();
                }
            }
            tokenList.add(new Token(TokenType.END, "End of File", new Location(line, column)));
        }

        void tokenizeNumber() {
            StringBuilder sb = new StringBuilder();
            currentChar = peek();
            while (!isEndOfFile && Character.isDigit(currentChar)) {
                sb.append(currentChar);
                next();
            }
            tokenList.add(new Token(TokenType.NUMBER, sb.toString(), new Location(line, column)));
        }

        void tokenizeSymbol() {
            StringBuilder sb = new StringBuilder();
            currentChar = peek();
            while (!isEndOfFile && (Character.isLetter(currentChar) || Character.isDigit(currentChar))) {
                sb.append(currentChar);
                next();
            }
            String tokenValue = sb.toString();
            if (keywordList.contains(tokenValue)) {
                tokenList.add(new Token(TokenType.KEYWORD, tokenValue, new Location(line, column)));
            } else {
                tokenList.add(new Token(TokenType.SYMBOL, "Eden_" + tokenValue, new Location(line, column)));
            }
        }

        void tokenizeString() {
            StringBuilder sb = new StringBuilder();
            next();
            currentChar = peek();
            while (!isEndOfFile && currentChar != '\"') {
                sb.append(currentChar);
                next();
            }
            next();
            String tokenValue = sb.toString();
            tokenList.add(new Token(TokenType.STRING, tokenValue, new Location(line, column)));
        }

        void tokenizeSpecialCharacters() {
            if (allowedCharacters.indexOf(currentChar) == -1) {
                printErrToken(new Token(TokenType.SYMBOL, currentChar, new Location(line, column)), "Lexer: Symbol is not allowed");
            }
            switch (currentChar) {
                case ';': {
                    tokenList.add(new Token(TokenType.SEMICOLON, currentChar, new Location(line, column)));
                    break;
                }
                case '(': {
                    tokenList.add(new Token(TokenType.OPEN_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case ')': {
                    tokenList.add(new Token(TokenType.CLOSE_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '{': {
                    tokenList.add(new Token(TokenType.OPEN_CURLY_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '}': {
                    tokenList.add(new Token(TokenType.CLOSE_CURLY_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '[': {
                    tokenList.add(new Token(TokenType.OPEN_SQUARE_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case ']': {
                    tokenList.add(new Token(TokenType.CLOSE_SQUARE_BRACKET, currentChar, new Location(line, column)));
                    break;
                }
                case '~': {
                    tokenList.add(new Token(TokenType.PRINT_STATEMENT, currentChar, new Location(line, column)));
                    break;
                }
                case '+': {
                    tokenList.add(new Token(TokenType.PLUS, currentChar, new Location(line, column)));
                    break;
                }
                case '-': {
                    tokenList.add(new Token(TokenType.MINUS, currentChar, new Location(line, column)));
                    break;
                }
                case '*': {
                    tokenList.add(new Token(TokenType.MULTIPLY, currentChar, new Location(line, column)));
                    break;
                }
                case '/': {
                    tokenList.add(new Token(TokenType.DIVIDE, currentChar, new Location(line, column)));
                    break;
                }
                case '>': {
                    int cIndex = currentCharIndex;
                    next();
                    if (currentChar == '>') {
                        tokenList.add(new Token(TokenType.R_SHIFT, ">>", new Location(line, column - 1)));
                    } else {
                        currentCharIndex = cIndex;
                        column--;
                        tokenList.add(new Token(TokenType.GREATER, currentChar, new Location(line, column)));
                    }
                    break;
                }
                case '<': {
                    int cIndex = currentCharIndex;
                    next();
                    if (currentChar == '<') {
                        tokenList.add(new Token(TokenType.L_SHIFT, "<<", new Location(line, column - 1)));
                    } else {
                        currentCharIndex = cIndex;
                        column--;
                        tokenList.add(new Token(TokenType.LESS, currentChar, new Location(line, column)));
                    }
                    break;
                }
                case '|': {
                    tokenList.add(new Token(TokenType.B_OR, currentChar, new Location(line, column)));
                    break;
                }
                case '&': {
                    tokenList.add(new Token(TokenType.B_AND, currentChar, new Location(line, column)));
                    break;
                }
                case '=': {
                    tokenList.add(new Token(TokenType.EQUALS, currentChar, new Location(line, column)));
                    break;
                }
                case ',': {
                    tokenList.add(new Token(TokenType.COMMA, currentChar, new Location(line, column)));
                    break;
                }
                case '.': break;
                default: {
                    printErrToken(new Token(TokenType.SYMBOL, currentChar, new Location(line, column)), "Lexer: Unknown character");
                }
            }
            next();
        }

        void next() {
            currentCharIndex++;
            column++;
            currentChar = peek();
        }

        char peek() {
            if (currentCharIndex == sourceLength) {
                isEndOfFile = true;
            }
            return isEndOfFile ? '\0' : source.charAt(currentCharIndex);
        }
    }
}
