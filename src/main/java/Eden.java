import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    static List<Token> tokenList = new ArrayList<>();
    static Stack<EdenState> stackState = new Stack<>();
    static Stack<Object> programStack = new Stack<>();
    static List<String> stringConstants = new ArrayList<>();
    static StringBuilder programCode = new StringBuilder();
    static int index = 0;
    static boolean isInterpreter = true;

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

        // Parsing.
        stackState.push(EdenState.PROGRAM);
        Token currentToken = tokenList.get(index);
        while (currentToken.type != TokenType.END || stackState.peek() != EdenState.PROGRAM) {
            choseRule(currentToken);
            currentToken = tokenList.get(index);
        }
        if (!isInterpreter) {
            // Compilation
            writeFile(sourceName);
            compile(sourceName);
        }
    }

    static void compile(String sourceName) {
        try {
            String name = sourceName.split("[.]")[0];
            String cmdNasm = String.format("cmd.exe /c nasm -f win32 %s.asm", name);
            String cmdGoLink = String.format("cmd.exe /c golink /entry:Start /console kernel32.dll user32.dll %s.obj", name);
            System.out.println(cmdNasm);
            Process process = Runtime.getRuntime().exec(cmdNasm);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                printErr("Error while compiling with nasm");
            }
            System.out.println(cmdGoLink);
            process = Runtime.getRuntime().exec(cmdGoLink);
            exitCode = process.waitFor();
            if (exitCode != 0) {
                printErr("Error while linking with golink");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void writeFile(String sourceName) {
        try {
            File output = new File(sourceName.split("[.]")[0]+".asm");
            FileWriter fw = new FileWriter(output);
            writeHeader(fw);
            writeData(fw);
            writeVariables(fw);
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
        fw.write("extern _GetStdHandle@4\n" +
                     "extern _WriteFile@20\n" +
                     "extern _ExitProcess@4\n" +
                     "\n" +
                     "global Start\n");
    }

    static void writeData(FileWriter fw) throws IOException {
        int index = 0;
        fw.write("section .data\n");
        fw.write("\tstr_NL db `\\r\\n`\n");
        for (String stringConstant : stringConstants) {
            String fullName = "str_" + index++;
            fw.write("\t" + fullName + " db `" + stringConstant + "`,0\n");
            //fw.write("\t" + fullName + "Len EQU $-" + fullName + "\n");
        }
    }

    static void writeVariables(FileWriter fw) throws IOException {
        fw.write("section .bss\n");
        fw.write("\tStdHandle resd 1\n");
        fw.write("\tdigitBuffer resb 100\n");
        fw.write("\tdigitBufferPos resb 8\n");
    }

    static void writeText(FileWriter fw) throws IOException {
        fw.write("section .text\n");
        // print
        fw.write(";In:\n");
        fw.write(";eax - msgToPrint\n");
        fw.write(";ebx - 0 if it's number otherwise string\n");
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
        fw.write("\tcall _WriteFile@20\n");
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
        //fw.write("\tcall printStr\n");
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
        fw.write("\tcall _ExitProcess@4\n");
        // Start
        fw.write("Start:\n");
        fw.write("\t;Get the console handler\n");
        fw.write("\tpush -11\n");
        fw.write("\tcall _GetStdHandle@4\n");
        fw.write("\tmov dword [StdHandle], eax\n");
    }

    static void choseRule(Token currentToken) {
        EdenState state = stackState.pop();
        switch (state) {
            case PROGRAM: doStateProgram(); break;
            case STATEMENT: doStateStatement(currentToken); break;
            case PRINT_STATEMENT: doStatePrintStatement(); break;
            case TOKEN_SEMICOLON: doTokenSemicolon(currentToken); break;
            case TOKEN_CLOSE_BRACKET: doTokenCloseBracket(currentToken); break;
            case EXPRESSION: doStateExpression(currentToken); break;
            case LOGICAL: doStateLogical(currentToken); break;
            case ADDITION: doStateAddition(currentToken); break;
            case STARSLASH: doStateStarSlash(currentToken); break;
            case UNAR: doStateUnar(currentToken); break;
            case ARG: doStateArg(currentToken); break;
            case DO_PRINT: doOpPrint(); break;
            case DO_OP_PLUS: doOpPlus(); break;
            case DO_OP_MINUS: doOpMinus(); break;
            case DO_OP_MULTIPLY: doOpMultiply(); break;
            case DO_OP_DIVIDE: doOpDivide(); break;
            case DO_OP_UNAR_MINUS: doOpUnarMinus(); break;
            case DO_OP_EQUALS: doOpEquals(); break;
            case DO_OP_GREATER: doOpGreater(); break;
            case DO_OP_LESS: doOpLess(); break;
            case DO_SKIP: doStateSkip(currentToken); break;
            default: {
                System.err.printf("ERROR: Unknown State: %s with Token: %s", state, currentToken);
                System.exit(-1);
            }
        }
    }

    static void doStateProgram() {
        stackState.push(EdenState.PROGRAM);
        stackState.push(EdenState.STATEMENT);
    }

    static void doStateStatement(Token currentToken) {
        if (currentToken.type == TokenType.PRINT_STATEMENT) {
            stackState.push(EdenState.PRINT_STATEMENT);
            return;
        }
        printErrToken(currentToken, "Statement can't start with: ");
    }

    static void doStatePrintStatement() {
        index++;
        stackState.push(EdenState.DO_PRINT);
        stackState.push(EdenState.TOKEN_SEMICOLON);
        stackState.push(EdenState.EXPRESSION);
    }

    static void doTokenSemicolon(Token currentToken) {
        if (currentToken.type == TokenType.SEMICOLON) {
            index++;
            return;
        }
        printErrToken(currentToken, "Expression must ends with [;], but found: ");
    }

    static void doTokenCloseBracket(Token currentToken) {
        if (currentToken.type == TokenType.CLOSE_BRACKET) {
            index++;
            return;
        }
        printErrToken(currentToken, "Inner expression must ends with [)], but found: ");
    }

    static void doStateExpression(Token currentToken) {
        TokenType cType = currentToken.type;
        if (cType == TokenType.PLUS || cType == TokenType.MINUS || cType == TokenType.OPEN_BRACKET
                || cType == TokenType.NUMBER || cType == TokenType.STRING) {
            stackState.push(EdenState.LOGICAL);
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.UNAR);
            return;
        }
        printErrToken(currentToken, "Expression expects [+,-,NUMBER,(], but found: ");
    }

    static void doStateLogical(Token currentToken) {
        TokenType cType = currentToken.type;
        if (cType == TokenType.GREATER) {
            index++;
            stackState.push(EdenState.DO_OP_GREATER);
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.UNAR);
            return;
        }
        if (cType == TokenType.LESS) {
            index++;
            stackState.push(EdenState.DO_OP_LESS);
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.UNAR);
            return;
        }
        if (cType == TokenType.EQUALS) {
            index++;
            stackState.push(EdenState.DO_OP_EQUALS);
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.UNAR);
        }
    }

    static void doStateAddition(Token currentToken) {
        TokenType cType = currentToken.type;
        if (cType == TokenType.PLUS) {
            index++;
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.DO_OP_PLUS);
            stackState.push(EdenState.UNAR);
            return;
        }
        if (cType == TokenType.MINUS) {
            index++;
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.DO_OP_MINUS);
            stackState.push(EdenState.UNAR);
        }
    }

    static void doStateStarSlash(Token currentToken) {
        TokenType cType = currentToken.type;
        if (cType == TokenType.MULTIPLY) {
            index++;
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.DO_OP_MULTIPLY);
            stackState.push(EdenState.UNAR);
            return;
        }
        if (cType == TokenType.DIVIDE) {
            index++;
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.DO_OP_DIVIDE);
            stackState.push(EdenState.UNAR);
        }
        // TODO: ADD POWER OPERATION (^)
    }

    static void doStateUnar(Token currentToken) {
        if (currentToken.type == TokenType.PLUS) {
            index++;
            stackState.push(EdenState.DO_OP_UNAR_PLUS);
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.ARG);
            return;
        }
        if (currentToken.type == TokenType.MINUS) {
            index++;
            stackState.push(EdenState.DO_OP_UNAR_MINUS);
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.ARG);
            return;
        }
        if (currentToken.type == TokenType.NUMBER || currentToken.type == TokenType.STRING
                || currentToken.type == TokenType.OPEN_BRACKET) {
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.ARG);
        }
    }

    static void doStateArg(Token currentToken) {
        if (currentToken.type == TokenType.NUMBER || currentToken.type == TokenType.STRING) {
            stackState.push(EdenState.DO_SKIP);
            return;
        }
        if (currentToken.type == TokenType.OPEN_BRACKET) {
            index++;
            stackState.push(EdenState.TOKEN_CLOSE_BRACKET);
            stackState.push(EdenState.EXPRESSION);
            // Do not forget put return here, when add more code below
        }
    }

    static void doStateSkip(Token currentToken) {
        if (isInterpreter) {
             if (currentToken.type == TokenType.NUMBER || currentToken.type == TokenType.STRING) {
                 index++;
                 programStack.push(currentToken.value);
             }
        } else {
            index++;
            if (currentToken.type == TokenType.STRING) {
                String stringValue = String.valueOf(currentToken.value);
                stringConstants.add(stringValue);
                String address = "str_" + (stringConstants.size() - 1);
                programCode.append("\t;OpPushString\n");
                programCode.append("\tpush ").append(address).append("\n");
            } else {
                programCode.append("\t;OpPushNum\n");
                programCode.append("\tpush ").append(currentToken.value).append("\n");
            }
        }
    }

    static void doOpPrint() {
        if (isInterpreter) {
            if (programStack.size() < 1) {
                printErr("Nothing to print");
            }
            Object value = programStack.pop();
            System.out.println(value);
        } else {
            programCode.append("\t;OpPrint\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tcall print\n");
        }
    }

    static void doOpPlus() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Plus operation expected two integers, but found less");
            }
            Object first = programStack.pop();
            Object second = programStack.pop();
            int result = Integer.parseInt(String.valueOf(first)) + Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            programCode.append("\t;OpPlus\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tadd eax, ebx\n");
            programCode.append("\tpush eax\n");
        }
    }

    static void doOpMinus() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Minus operation expected two integers, but found less");
            }
            Object second = programStack.pop();
            Object first = programStack.pop();
            int result = Integer.parseInt(String.valueOf(first)) - Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpMultiply() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Multiply operation expected two integers, but found less");
            }
            Object first = programStack.pop();
            Object second = programStack.pop();
            int result = Integer.parseInt(String.valueOf(first)) * Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpDivide() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Divide operation expected two integers, but found less");
            }
            Object second = programStack.pop();
            Object first = programStack.pop();
            int result = Integer.parseInt(String.valueOf(first)) / Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpUnarMinus() {
        if (isInterpreter) {
            if (programStack.size() < 1) {
                printErr("Unar minus operation expected integer, but found nothing");
            }
            Object first = programStack.pop();
            int result = Integer.parseInt(String.valueOf(first)) * -1;
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpEquals() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Equals operation expected two operands, but found less");
            }
            Object second = programStack.pop();
            Object first = programStack.pop();
            boolean result = Integer.parseInt(String.valueOf(first)) == Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpGreater() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Greater operation expected two operands, but found less");
            }
            Object second = programStack.pop();
            Object first = programStack.pop();
            boolean result = Integer.parseInt(String.valueOf(first)) > Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void doOpLess() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Greater operation expected two operands, but found less");
            }
            Object second = programStack.pop();
            Object first = programStack.pop();
            boolean result = Integer.parseInt(String.valueOf(first)) < Integer.parseInt(String.valueOf(second));
            programStack.push(result);
        } else {
            throw new NotImplementedException();
        }
    }

    static void printErrToken(Token token, String errMessage) {
        int offset = String.valueOf(token.value).length();
        System.err.printf("ERROR: [%d:%d] %s: (%s)'%s'%n", token.loc.line + 1, token.loc.column - offset, errMessage, token.type, token.value);
        System.exit(1);
    }

    static void printErr(String errMessage) {
        System.err.printf("ERROR: %s", errMessage);
        System.exit(2);
    }

    enum EdenState {
        PROGRAM,
        STATEMENT,
        PRINT_STATEMENT,
        DO_PRINT,
        DO_SKIP,
        EXPRESSION,
        DO_OP_PLUS,
        DO_OP_MINUS,
        DO_OP_UNAR_PLUS,
        DO_OP_UNAR_MINUS,
        DO_OP_MULTIPLY,
        DO_OP_DIVIDE,
        DO_OP_GREATER,
        DO_OP_LESS,
        DO_OP_EQUALS,
        TOKEN_CLOSE_BRACKET,
        TOKEN_SEMICOLON,
        LOGICAL,
        ADDITION,
        STARSLASH,
        UNAR,
        ARG
    }

    enum TokenType {
        //CHARACTER,
        SEMICOLON,
        COMMA,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        OPEN_CURLY_BRACKET,
        CLOSE_CURLY_BRACKET,
        PRINT_STATEMENT,
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        GREATER,
        LESS,
        EQUALS,
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

        Token(TokenType type, Object value, Location loc) {
            this.type = type;
            this.value = value;
            this.loc = loc;
        }

        @Override
        public String toString() {
            return String.format("%s[%s][%d:%d]", type, value, loc.line, loc.column);
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
        String allowedCharacters = "~+-*/;()=><!{}().,'\"";
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

        void initKeywords() {
            keywordList.add("class");
            keywordList.add("void");
            keywordList.add("char");
            keywordList.add("bool");
            keywordList.add("int");
            keywordList.add("if");
            keywordList.add("else");
            keywordList.add("while");
            keywordList.add("true");
            keywordList.add("false");
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
                tokenList.add(new Token(TokenType.SYMBOL, tokenValue, new Location(line, column)));
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
                    tokenList.add(new Token(TokenType.GREATER, currentChar, new Location(line, column)));
                    break;
                }
                case '<': {
                    tokenList.add(new Token(TokenType.LESS, currentChar, new Location(line, column)));
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
