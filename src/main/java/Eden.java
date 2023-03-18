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
    private static final long MAX_C_INTEGER = 4294967296L;
    private static final String HDL_STD_OUT = "4294967285";
    private static final String HDL_STD_ERR = "4294967284";
    static List<Token> tokenList = new ArrayList<>();
    static Stack<EdenState> stackState = new Stack<>();
    static Stack<Object> programStack = new Stack<>();
    static List<String> stringConstants = new ArrayList<>();
    static List<EdenVar> edenVarList = new ArrayList<>();
    static List<String> externWinCallList = new ArrayList<>();
    static StringBuilder programCode = new StringBuilder();
    static EdenType typeToDeclare = EdenType.NONE;
    static int index = 0;
    static int stackSizeBeforeWinCallName = 0;
    static boolean isInterpreter = true;
    static boolean isRunAfterCompilation = false;

    public static void main(String[] args) throws IOException, InterruptedException {
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

        lexer.crossReference();

        // Parsing.
        stackState.push(EdenState.PROGRAM);
        Token currentToken = tokenList.get(index);
        while (currentToken.type != TokenType.END || stackState.peek() != EdenState.PROGRAM) {
            choseRule(currentToken);
            currentToken = tokenList.get(index);
        }
        if (programStack.size() > 0) {
            printErr("Program stack must be empty by this point, something went wrong");
        }
        if (!isInterpreter) {
            // Compilation
            writeFile(sourceName);
            int isSuccess = compile(sourceName);
            if (isSuccess == 0 && isRunAfterCompilation) {
                System.out.println(run(sourceName.split("[.]")[0]+".exe"));
            }
        }
    }

    private static String run(String execCmd) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec("cmd.exe /c " + execCmd);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Error while execute command: " + execCmd);
            InputStream errStream = process.getErrorStream();
            while (errStream.available() > 0) {
                System.err.print(readInputStream(errStream));
            }
            errStream.close();
        }
        return readInputStream(process.getInputStream());
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (inputStream.available() > 0) {
            sb.append((char)(inputStream.read()));
        }
        return sb.toString();
    }

    static int compile(String sourceName) {
        try {
            String name = sourceName.split("[.]")[0];
            System.out.printf("[INFO] Compiling %s...\n", sourceName);
            String cmdNasm = String.format("cmd.exe /c nasm -f win32 %s.asm", name);
            String cmdGoLink = String.format("cmd.exe /c golink /entry:Start /console kernel32.dll user32.dll %s.obj", name);
            System.out.println("[CMD] " + cmdNasm);
            Process process = Runtime.getRuntime().exec(cmdNasm);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                printErr("Error while compiling with nasm");
            }
            System.out.println("[CMD] " + cmdGoLink);
            process = Runtime.getRuntime().exec(cmdGoLink);
            exitCode = process.waitFor();
            if (exitCode != 0) {
                printErr("Error while linking with golink");
            }
            return 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return -1;
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
            //fw.write("\t" + fullName + "Len EQU $-" + fullName + "\n");
        }
    }

    static void writeVariables(FileWriter fw) throws IOException {
        fw.write("section .bss\n");
        fw.write("\tStdHandle resd 1\n");
        fw.write("\tdigitBuffer resb 100\n");
        fw.write("\tdigitBufferPos resb 8\n");
        for (EdenVar var : edenVarList) {
            switch (var.type) {
                case STRING:
                case INT: {
                    fw.write("\t" + var.identifier + " resb 4\n");
                    break;
                }
                case BOOL: {
                    fw.write("\t" + var.identifier + " resb 1\n");
                    break;
                }
                case CHAR: {
                    fw.write("\t" + var.identifier + " resb 2\n");
                    break;
                }
                case NONE:
                default: fw.write("\t; There is some problem with var declaration in compiler, probably\n");
            }
        }
    }

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

    static void choseRule(Token currentToken) {
        EdenState state = stackState.pop();
        switch (state) {
            case PROGRAM: doStateProgram(); break;
            case STATEMENT: doStateStatement(currentToken); break;
            case VAR_INITIALIZATION: doStateVarInitialization(currentToken); break;
            case VAR_DECLARATION: doStateVarDeclaration(currentToken); break;
            case PRINT_STATEMENT: doStatePrintStatement(); break;
            case WHILE_STATEMENT: doStateWhileStatement(); break;
            case WHILE_COND_STATEMENT: doStateWhileConditionStatement(currentToken); break;
            case END_WHILE: doStateEndWhile(currentToken); break;
            case IF_STATEMENT: doStateIfStatement(); break;
            case IF_COND_STATEMENT: doStateIfConditionStatement(currentToken); break;
            case BLOCK_STATEMENT: doStateBlockStatement(currentToken); break;
            case ELSE_STATEMENT: doStateEndIfElseStatement(currentToken); break;
            case NEXT_STATEMENT: doStateNextStatement(currentToken); break;
            case IDENTIFIER: doStateIdentifier(currentToken); break;
            case NEXT_IDENTIFIER: doStateNextIdentifier(currentToken); break;
            case INITIALIZATION: doStateInitialization(currentToken); break;
            case TOKEN_SEMICOLON: doTokenSemicolon(currentToken); break;
            case TOKEN_OPEN_BRACKET: doTokenOpenBracket(currentToken); break;
            case TOKEN_CLOSE_BRACKET: doTokenCloseBracket(currentToken); break;
            case EXPRESSION: doStateExpression(currentToken); break;
            case WINCALL_NAME: doStateWinCallName(currentToken); break;
            case EXPRESSION_LIST: doStateExpressionList(); break;
            case NEXT_EXPRESSION: doStateNextExpression(currentToken); break;
            case LOGICAL: doStateLogical(currentToken); break;
            case ADDITION: doStateAddition(currentToken); break;
            case STARSLASH: doStateStarSlash(currentToken); break;
            case UNAR: doStateUnar(currentToken); break;
            case ARG: doStateArg(currentToken); break;
            case DO_PRINT: doOpPrint(); break;
            case DO_WINCALL: doOpWinCall(); break;
            case DO_INITIALIZE: doInitialize(); break;
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
        if (currentToken.type == TokenType.KEYWORD) {
            String tValue = String.valueOf(currentToken.value);
            if (tValue.equalsIgnoreCase("while")) {
                stackState.push(EdenState.WHILE_STATEMENT);
            } else if (tValue.equalsIgnoreCase("if")) {
                stackState.push(EdenState.IF_STATEMENT);
            } else if (tValue.equalsIgnoreCase("int") || tValue.equalsIgnoreCase("bool")
                    || tValue.equalsIgnoreCase("string") || tValue.equalsIgnoreCase("char")){
                stackState.push(EdenState.VAR_DECLARATION);
            } else {
                printErrToken(currentToken, "Unknown keyword: ");
            }
            return;
        }
        if (currentToken.type == TokenType.SYMBOL) {
            stackState.push(EdenState.VAR_INITIALIZATION);
            return;
        }
        printErrToken(currentToken, "Statement can't start with: ");
    }

    static void doStateVarInitialization(Token currentToken) {
        String identifier = getIdentifier(currentToken);
        EdenVar var = getEdenVarByIdentifier(identifier);
        programStack.push(var.identifier);
        index++;
        stackState.push(EdenState.TOKEN_SEMICOLON);
        stackState.push(EdenState.INITIALIZATION);
    }

    static void doStateVarDeclaration(Token currentToken) {
        try {
            typeToDeclare = EdenType.valueOf(String.valueOf(currentToken.value).toUpperCase());
        } catch (IllegalArgumentException ignored) {
            printErrToken(currentToken, "Unknown Eden_Type keyword: ");
        }
        index++;
        edenVarList.add(new EdenVar(typeToDeclare));
        stackState.push(EdenState.TOKEN_SEMICOLON);
        stackState.push(EdenState.IDENTIFIER);
    }

    static void doStatePrintStatement() {
        index++;
        stackState.push(EdenState.DO_PRINT);
        stackState.push(EdenState.TOKEN_SEMICOLON);
        stackState.push(EdenState.EXPRESSION);
    }

    static void doStateWhileStatement() {
        if (!isInterpreter) {
            programCode.append("\t; OpWhile\n");
            programCode.append("addr_").append(index).append(":\n");
        }
        index++;
        stackState.push(EdenState.WHILE_COND_STATEMENT);
        stackState.push(EdenState.EXPRESSION);
    }

    static void doStateIfStatement() {
        index++;
        stackState.push(EdenState.ELSE_STATEMENT);
        stackState.push(EdenState.IF_COND_STATEMENT);
        stackState.push(EdenState.EXPRESSION);
    }

    static void doStateEndWhile(Token currentToken) {
        if (isInterpreter) {
            index = currentToken.linkIp; // goto while
            stackState.push(EdenState.WHILE_STATEMENT);
        } else {
            int linkIp = currentToken.linkIp;
            if (linkIp == 0) {
                printErrToken(currentToken, "while block instruction does not have a reference to the start of its block. Please call lexer.crossReference() before trying to compile it");
            }
            programCode.append("\t; OpEndWhile\n");
            programCode.append("\tjmp addr_").append(linkIp).append("\n");
            programCode.append("addr_").append(++index).append(":\n");
        }
    }

    static void doStateEndIfElseStatement(Token currentToken) {
        if (currentToken.type == TokenType.CLOSE_CURLY_BRACKET) {
            index++;
        }
        if (isInterpreter) {
            currentToken = tokenList.get(index);
            if (currentToken.type == TokenType.KEYWORD) {
                String tValue = String.valueOf(currentToken.value);
                if (tValue.equalsIgnoreCase("else")) {
                    index = tokenList.get(index).linkIp;
                }
            }
        } else {
            Token nextToken = tokenList.get(index);
            if (String.valueOf(nextToken.value).equalsIgnoreCase("else")) {
                if (nextToken.linkIp == 0) {
                    printErrToken(nextToken, "else block instruction does not have a reference to the end of its block. Please call lexer.crossReference() before trying to compile it");
                }
                programCode.append("\t; Else\n");
                programCode.append("\tjmp addr_").append(nextToken.linkIp - 1).append("\n");
                programCode.append("addr_").append(currentToken.index + 2).append(":\n");
                index++;
                stackState.push(EdenState.ELSE_STATEMENT);
                stackState.push(EdenState.BLOCK_STATEMENT);
            } else {
                programCode.append("\t; End if\n");
                programCode.append("addr_").append(currentToken.index).append(":\n");
            }
        }
    }

    static void doStateWhileConditionStatement(Token currentToken) {
        if (isInterpreter) {
            if (Integer.parseInt(String.valueOf(programStack.pop())) == 0) {
                int linkIp = currentToken.linkIp;
                if (linkIp == 0) {
                    printErrToken(currentToken, "while block instruction does not have a reference to the end of its block. Please call lexer.crossReference() before trying to interpret it");
                }
                index = currentToken.linkIp;
            } else {
                stackState.push(EdenState.END_WHILE);
                stackState.push(EdenState.BLOCK_STATEMENT);
            }
        } else {
            int linkIp = currentToken.linkIp;
            if (linkIp == 0) {
                printErrToken(currentToken, "while block instruction does not have a reference to the end of its block. Please call lexer.crossReference() before trying to compile it");
            }
            programCode.append("\t; OpJumpWhile\n");
            programCode.append("\tpop eax\n");
            programCode.append("\ttest eax, eax\n");
            programCode.append("\tjz addr_").append(linkIp).append("\n");
            stackState.push(EdenState.END_WHILE);
            stackState.push(EdenState.BLOCK_STATEMENT);
        }
    }

    static void doStateIfConditionStatement(Token currentToken) {
        if (isInterpreter) {
            if (Integer.parseInt(String.valueOf(programStack.pop())) == 0) {
                int linkIp = currentToken.linkIp;
                if (linkIp == 0) {
                    printErrToken(currentToken, "if block instruction does not have a reference to the end of its block. Please call lexer.crossReference() before trying to interpret it");
                }
                index = currentToken.linkIp;
            }
        } else {
            int linkIp = currentToken.linkIp;
            if (linkIp == 0) {
                printErrToken(currentToken, "if block instruction does not have a reference to the end of its block. Please call lexer.crossReference() before trying to compile it");
            }
            programCode.append("\t; OpIf\n");
            programCode.append("\tpop eax\n");
            programCode.append("\ttest eax, eax\n");
            programCode.append("\tjz addr_").append(linkIp).append("\n");
        }
        if (tokenList.get(index).type == TokenType.OPEN_CURLY_BRACKET) {
            stackState.push(EdenState.BLOCK_STATEMENT);
        }
    }

    static void doStateBlockStatement(Token currentToken) {
        if (currentToken.type == TokenType.OPEN_CURLY_BRACKET) {
            index++;
            stackState.push(EdenState.NEXT_STATEMENT);
            stackState.push(EdenState.STATEMENT);
        } else {
            printErrToken(currentToken, "Block of statements must start with [{], but found: ");
        }
    }

    static void doStateNextStatement(Token currentToken) {
        // There is might be a bug
        if (currentToken.type != TokenType.CLOSE_CURLY_BRACKET) {
            stackState.push(EdenState.NEXT_STATEMENT);
            stackState.push(EdenState.STATEMENT);
        }
    }

    static void doStateIdentifier(Token currentToken) {
        declareVar(currentToken);
        index++;
        stackState.push(EdenState.NEXT_IDENTIFIER);
        stackState.push(EdenState.INITIALIZATION);
    }

    static void doStateNextIdentifier(Token currentToken) {
        if (currentToken.type == TokenType.COMMA) {
            index++;
            edenVarList.add(new EdenVar(typeToDeclare));
            stackState.push(EdenState.IDENTIFIER);
        }
    }

    static void doStateInitialization(Token currentToken) {
        if (currentToken.type == TokenType.EQUALS) {
            index++;
            stackState.push(EdenState.DO_INITIALIZE);
            stackState.push(EdenState.EXPRESSION);
        } else {
            programStack.pop();
        }
    }

    static void doTokenSemicolon(Token currentToken) {
        if (currentToken.type == TokenType.SEMICOLON) {
            index++;
            return;
        }
        printErrToken(currentToken, "Expression must ends with [;], but found: ");
    }

    static void doTokenOpenBracket(Token currentToken) {
        if (currentToken.type == TokenType.OPEN_BRACKET) {
            index++;
            return;
        }
        printErrToken(currentToken, "Expected [(], but found: ");
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
                || cType == TokenType.NUMBER || cType == TokenType.STRING || cType == TokenType.SYMBOL) {
            stackState.push(EdenState.LOGICAL);
            stackState.push(EdenState.ADDITION);
            stackState.push(EdenState.UNAR);
            return;
        }
        if (cType == TokenType.KEYWORD) {
            String tValue = String.valueOf(currentToken.value);
            boolean isWincall = tValue.equalsIgnoreCase("wincall");
            if (isWincall) {
                index++;
                stackState.push(EdenState.DO_WINCALL);
                stackState.push(EdenState.TOKEN_CLOSE_BRACKET);
                stackState.push(EdenState.EXPRESSION_LIST);
                stackState.push(EdenState.WINCALL_NAME);
                stackState.push(EdenState.TOKEN_OPEN_BRACKET);
                return;
            }
        }
        printErrToken(currentToken, "Expression expects [+,-,NUMBER,(], but found: ");
    }

    static void doStateWinCallName(Token currentToken) {
        if (currentToken.type != TokenType.STRING) {
            printErrToken(currentToken, "Wincall name must be a STRING, but found: ");
        }
        index++;
        Token nextToken = tokenList.get(index);
        if (nextToken.type == TokenType.COMMA) {
            index++;
        } else {
            if (nextToken.type == TokenType.CLOSE_BRACKET) {
                stackState.pop();
            } else {
                printErrToken(nextToken, "After wincall name must be [,], but found: ");
            }
        }
        stackSizeBeforeWinCallName = programStack.size();
        programStack.push(currentToken.value);
        String cValue = String.valueOf(currentToken.value);
        if (!externWinCallList.contains(cValue)) {
            externWinCallList.add(cValue);
        }
    }

    static void doStateExpressionList() {
        stackState.push(EdenState.NEXT_EXPRESSION);
        stackState.push(EdenState.EXPRESSION);
    }

    static void doStateNextExpression(Token currentToken) {
        if (currentToken.type == TokenType.COMMA) {
            index++;
            stackState.push(EdenState.EXPRESSION_LIST);
        }
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
                || currentToken.type == TokenType.OPEN_BRACKET || currentToken.type == TokenType.SYMBOL) {
            stackState.push(EdenState.STARSLASH);
            stackState.push(EdenState.ARG);
        }
    }

    static void doStateArg(Token currentToken) {
        if (currentToken.type == TokenType.NUMBER || currentToken.type == TokenType.STRING
                || currentToken.type == TokenType.SYMBOL) {
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
            index++;
            if (currentToken.type == TokenType.NUMBER || currentToken.type == TokenType.STRING) {
                 programStack.push(currentToken.value);
             }
             if (currentToken.type == TokenType.SYMBOL) {
                 programStack.push(getEdenVarByIdentifier(String.valueOf(currentToken.value)).value);
             }
        } else {
            index++;
            if (currentToken.type == TokenType.STRING) {
                String stringValue = String.valueOf(currentToken.value);
                stringConstants.add(stringValue);
                String address = "str_" + (stringConstants.size() - 1);
                programCode.append("\t;OpPushString\n");
                programCode.append("\tpush ").append(address).append("\n");
            } else if (currentToken.type == TokenType.NUMBER) {
                programCode.append("\t;OpPushNum\n");
                programCode.append("\tpush ").append(currentToken.value).append("\n");
            } else if (currentToken.type == TokenType.SYMBOL) {
                programCode.append("\t;OpPushVar\n");
                programCode.append("\tmov eax, [").append(currentToken.value).append("]\n");
                programCode.append("\tpush eax\n");
            }
        }
    }

    static void doOpPrint() {
        if (isInterpreter) {
            if (programStack.size() < 1) {
                printErr("Nothing to print");
            }
            String value = String.valueOf(programStack.pop());
            printWithNewLine(value, System.out);
        } else {
            programCode.append("\t;OpPrint\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tcall print\n");
        }
    }

    static void doOpWinCall() {
        if (isInterpreter) {
            String nameOfWinCall = String.valueOf(programStack.get(stackSizeBeforeWinCallName));
            switch (nameOfWinCall) {
                case "GetStdHandle": {
                    int handleValue = Integer.parseInt(String.valueOf(programStack.pop()));
                    programStack.pop();
                    programStack.push(MAX_C_INTEGER + handleValue);
                } break;
                case "WriteFile": {
                    String handler = String.valueOf(programStack.pop());
                    String strToPrint = String.valueOf(programStack.pop());
                    int sizeOfStr = Integer.parseInt(String.valueOf(programStack.pop()));
                    programStack.pop();
                    programStack.pop();
                    programStack.pop();
                    if (sizeOfStr < strToPrint.length()) { strToPrint = strToPrint.substring(0, sizeOfStr); }
                    switch (handler) {
                        case HDL_STD_OUT: printWithNewLine(strToPrint, System.out); break;
                        case HDL_STD_ERR: printWithNewLine(strToPrint, System.err); break;
                        default: {
                            printErr("Unsupported handler: " + handler);
                        }
                    }
                    programStack.push(1);
                } break;
                default: {
                    printErr("Unknown wincall name: " + nameOfWinCall);
                }
            }
        } else {
            String nameOfWinCall = String.valueOf(programStack.pop());
            programCode.append("\t;OpWinCall\n");
            programCode.append("\tcall ").append(nameOfWinCall).append("\n");
            programCode.append("\tpush eax\n");
        }
    }

    static void printWithNewLine(String strToPrint, PrintStream printStream) {
        printStream.printf(strToPrint.replaceAll("[\\\\]", "%"));
    }

    static void doInitialize() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Initializations require two elements, but found less");
            }
            Object value = programStack.pop();
            String identifier = String.valueOf(programStack.pop());
            EdenVar var = getEdenVarByIdentifier(identifier);
            var.value = value;
        } else {
            String identifier = String.valueOf(programStack.pop());
            programCode.append("\t;OpInitialize\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tmov [").append(identifier).append("], eax\n");
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
            programCode.append("\t;OpMinus\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tsub eax, ebx\n");
            programCode.append("\tpush eax\n");
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
            programCode.append("\t;OpMultiply\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tmul ebx\n");
            programCode.append("\tpush eax\n");
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
            programCode.append("\t;OpDivide\n");
            programCode.append("\tpop ecx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\txor edx, edx\n");
            programCode.append("\tdiv ecx\n");
            programCode.append("\tpush eax\n");
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
            programCode.append("\t;OpUnarMinus\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tneg eax\n");
            programCode.append("\tpush eax\n");
        }
    }

    static void doOpEquals() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Equals operation expected two operands, but found less");
            }
            int second = Integer.parseInt(String.valueOf(programStack.pop()));
            int first = Integer.parseInt(String.valueOf(programStack.pop()));
            int result = first == second ? 1 : 0;
            programStack.push(result);
        } else {
            programCode.append("\t;OpEquals\n");
            programCode.append("\txor ecx, ecx\n");
            programCode.append("\tmov edx, 1\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tcmp eax, ebx\n");
            programCode.append("\tcmove ecx, edx\n");
            programCode.append("\tpush ecx\n");
        }
    }

    static void doOpGreater() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Greater operation expected two operands, but found less");
            }
            int second = Integer.parseInt(String.valueOf(programStack.pop()));
            int first = Integer.parseInt(String.valueOf(programStack.pop()));
            int result = first > second ? 1 : 0;
            programStack.push(result);
        } else {
            programCode.append("\t;OpGreater\n");
            programCode.append("\txor ecx, ecx\n");
            programCode.append("\tmov edx, 1\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tcmp eax, ebx\n");
            programCode.append("\tcmovg ecx, edx\n");
            programCode.append("\tpush ecx\n");
        }
    }

    static void doOpLess() {
        if (isInterpreter) {
            if (programStack.size() < 2) {
                printErr("Greater operation expected two operands, but found less");
            }
            int second = Integer.parseInt(String.valueOf(programStack.pop()));
            int first = Integer.parseInt(String.valueOf(programStack.pop()));
            int result = first < second ? 1 : 0;
            programStack.push(result);
        } else {
            programCode.append("\t;OpLess\n");
            programCode.append("\txor ecx, ecx\n");
            programCode.append("\tmov edx, 1\n");
            programCode.append("\tpop ebx\n");
            programCode.append("\tpop eax\n");
            programCode.append("\tcmp eax, ebx\n");
            programCode.append("\tcmovl ecx, edx\n");
            programCode.append("\tpush ecx\n");
        }
    }

    static String getIdentifier(Token currentToken) {
        if (currentToken.type != TokenType.SYMBOL) {
            printErrToken(currentToken, "Identifier must be type of SYMBOL, but found: ");
        }
        return String.valueOf(currentToken.value);
    }

    static void declareVar(Token currentToken) {
        String identifier = getIdentifier(currentToken);
        EdenVar var = edenVarList.get(edenVarList.size() - 1);
        if (var.identifier.equalsIgnoreCase(identifier)) {
            printErrToken(currentToken, "Identifier already declared: ");
        }
        var.identifier = identifier;
        programStack.push(identifier);
    }

    static EdenVar getEdenVarByIdentifier(String identifier) {
        for (EdenVar var : edenVarList) {
            if (var.identifier.equalsIgnoreCase(identifier)) {
                return var;
            }
        }
        printErr("Identifier is not declared: " + identifier);
        return new EdenVar(EdenType.NONE);
    }

    static void printErrToken(Token token, String errMessage) {
        int offset = String.valueOf(token.value).length();
        System.err.printf("ERROR: [%d:%d] %s: (%s)'%s'%n", token.loc.line + 1, token.loc.column - offset, errMessage, token.type, token.value);
        System.exit(1);
    }

    static void printErr(String errMessage) {
        System.err.printf("ERROR: %s%n", errMessage);
        System.exit(2);
    }

    enum EdenState {
        PROGRAM,
        STATEMENT,
        VAR_INITIALIZATION,
        VAR_DECLARATION,
        IDENTIFIER,
        INITIALIZATION,
        NEXT_IDENTIFIER,
        PRINT_STATEMENT,
        WHILE_STATEMENT,
        WHILE_COND_STATEMENT,
        END_WHILE,
        IF_STATEMENT,
        IF_COND_STATEMENT,
        ELSE_STATEMENT,
        NEXT_STATEMENT,
        BLOCK_STATEMENT,
        DO_INITIALIZE,
        DO_PRINT,
        DO_SKIP,
        EXPRESSION,
        NEXT_EXPRESSION,
        EXPRESSION_LIST,
        WINCALL_NAME,
        DO_WINCALL,
        DO_OP_PLUS,
        DO_OP_MINUS,
        DO_OP_UNAR_PLUS,
        DO_OP_UNAR_MINUS,
        DO_OP_MULTIPLY,
        DO_OP_DIVIDE,
        DO_OP_GREATER,
        DO_OP_LESS,
        DO_OP_EQUALS,
        TOKEN_OPEN_BRACKET,
        TOKEN_CLOSE_BRACKET,
        TOKEN_SEMICOLON,
        LOGICAL,
        ADDITION,
        STARSLASH,
        UNAR,
        ARG
    }

    enum EdenType {
        NONE,
        INT,
        BOOL,
        CHAR,
        STRING
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

    static class EdenVar {
        EdenType type;
        String identifier;
        Object value;

        EdenVar(EdenType type) {
            this.type = type;
            this.identifier = "";
            switch (type) {
                default:
                case STRING:
                case NONE: this.value = null;
                case CHAR:
                case BOOL:
                case INT: this.value = 0;
            }
        }
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
