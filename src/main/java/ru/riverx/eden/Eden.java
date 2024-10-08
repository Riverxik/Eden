package ru.riverx.eden;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int MAX_INTERPRET_MEMORY_SIZE = 1024 * 32;
    static List<Token> tokenList = new ArrayList<>();
    static List<String> stringConstants = new ArrayList<>();
    static List<String> externWinCallList = new ArrayList<>();
    static StringBuilder programCode = new StringBuilder();
    static int tokenIndex = 0;
    static String currentClassName;
    static List<Op> intermediateRepresentation = new ArrayList<>();
    static List<SymbolTableElem> symbolTable = new ArrayList<>();
    static int localVarShift = 0;
    static List<SymbolTableElem> usedVariables = new ArrayList<>();
    static List<String> subroutineNames = new ArrayList<>();
    static String mainFuncName;
    static int uniqueIndex = 0;
    static byte[] Memory = new byte[MAX_INTERPRET_MEMORY_SIZE];
    static Stack<Object> programStack = new Stack<>();
    static Object registerA = 0;
    static Stack<Integer> stackSizeList = new Stack<>();
    static boolean isInterpreter = true;
    static boolean isRunAfterCompilation = false;
    static int irPointer = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        // Args
        if (args.length == 0) {
            System.out.println("Usage: Eden -s scrName.eden");
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

        // TODO: Почему мы читаем только 1 файл, хочется читать папку с классами
        // TODO: Дополнительно, необходимо проверять имя файла и имя класса на совпадение (есть тест)
        // Reading the source
        Path sourcePath = Paths.get(sourceName);
        if (!Files.exists(sourcePath)) {
            System.out.println("File does not exists: " + sourceName);
            System.exit(1);
        }
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        // Lexing.
        Lexer lexer = new Lexer(source, tokenList);
        lexer.tokenize();
        lexer.clearComments();

        //tokenList.forEach(System.out::println);

        // Parsing.
        parse();
        // Analysis - Type checking
        // IR
        intermediateRepresentation.forEach(Op::generate);
        // Optimising.
        // Interpret || CodeGen
        if (!isInterpreter) {
            writeAsmFile(sourceName);
            boolean isSuccess = compileToExe(sourceName) == 0;
            if (isSuccess && isRunAfterCompilation) {
                System.out.println(run(sourceName.replaceAll("[/]","\\\\").split("[.]")[0]+".exe"));
            }
        } else {
            while (irPointer >= 0) {
                intermediateRepresentation.get(irPointer).interpret();
            }
        }
    }

    private static String run(String execCmd) throws IOException, InterruptedException {
        System.out.println("[CMD] " + execCmd);
        Process process = Runtime.getRuntime().exec("cmd.exe /c " + execCmd);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Error while execute command: " + execCmd + "\n" + readInputStream(process.getErrorStream()));
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

    static void writeAsmFile(String sourceName) {
        try {
            File output = new File(sourceName.split("[.]")[0]+".asm");
            FileWriter fw = new FileWriter(output);
            writeHeader(fw);
            writeData(fw);
            writeVariables(fw);
            writeText(fw);
            fw.write(programCode.toString());
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
        fw.write("\tstr_NL db `\\r\\n`, 0\n");
        for (String stringConstant : stringConstants) {
            String fullName = "str_" + index++;
            fw.write("\t" + fullName + " db `" + stringConstant.replaceAll("\\\\n", "\\\\r\\\\n") + "`, 0\n");
        }
    }

    static void writeVariables(FileWriter fw) throws IOException {
        fw.write("section .bss\n");
        fw.write("\tStdHandle resd 1\n");
        fw.write("\tdigitBuffer resb 100\n");
        fw.write("\tdigitBufferPos resb 8\n");
//        for (SymbolTableElem var : usedVariables) {
//            switch (var.type) {
//                case "string":
//                case "int": {
//                    fw.write("\t" + name + " resb 4\n");
//                    break;
//                }
//                case "bool": {
//                    fw.write("\t" + var.identifier + " resb 1\n");
//                    break;
//                }
////                case CHAR: {
////                    fw.write("\t" + var.identifier + " resb 2\n");
////                    break;
////                }
//                default: fw.write("\t; There is some problem with var declaration in compiler, probably\n");
//            }
//        }
    }

    static void writeText(FileWriter fw) throws IOException {
        fw.write("section .text\n");
        // RetFromLogic
        fw.write("retFrom:\n");
        fw.write("\tret\n");
        // strOrInt
        fw.write(";In:\n");
        fw.write(";eax - pointerToFirstVariable\n");
        fw.write(";ebx - pointerToSecondVariable\n");
        fw.write(";Out:\n");
        fw.write(";ecx - 0 - string, 1 - integer\n");
        fw.write("strOrInt:\n");
        fw.write("\tcmp eax, 4000000 ;IDK WTF IS THIS, SORRY\n");
        fw.write("\tjge _strNE\n");
        fw.write("\t;INTEGER\n");
        fw.write("\tcmp ebx, 4000000 ; XD\n");
        fw.write("\tjge _strNE\n");
        fw.write("\t; TWO INTEGERS\n");
        fw.write("\tmov ecx, 1\n");
        fw.write("\tret\n");
        fw.write("\t_strNE:\n");
        fw.write("\tmov ecx, 0\n");
        fw.write("\tret\n");
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
        fw.write(";Out\n");
        fw.write(";eax - pointer to the number buffer\n");
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
        fw.write("\tpush ebp\n");
        fw.write("\tmov ebp, esp\n");
        fw.write("\tsub esp, 10\n");
        fw.write("\t\n");
        fw.write("\tpush -11\n");
        fw.write("\tcall GetStdHandle\n");
        fw.write("\tmov dword [StdHandle], eax\n");
        fw.write("\tcall " + mainFuncName + "\n");
        fw.write("\tmov esp, ebp\n");
        fw.write("\tpop ebp\n");
        fw.write("\tjmp exit\n");
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

    static void parse() {
        parseClass();
        checkForMainFunc();
    }

    static void checkForMainFunc() {
        boolean isMain = false;
        for (String name : subroutineNames) {
            if (name.split("[.]")[1].equals("Eden_main")) {
                isMain = true;
                mainFuncName = name;
                break;
            }
        }
        if (!isMain) {
            printErr("Valid Eden program must have entry point function main()");
        }
    }

    static void parseClass() {
        expectKeyword("class");
        expectClassName();
        expectOpenCurly();
        expectClassDec();
        expectClassBody();
        expectCloseCurly();
    }

    static void expectClassDec() {
        // TODO:
    }

    static void expectClassBody() {
        // new | func | method
        String[] legalTokens = new String[]{"new", "func", "method"};
        while (hasNextTokenValues(legalTokens)) {
            Optional<String> procType = expectTokensOrEmpty(legalTokens);
            assert procType.isPresent();
            switch (procType.get()) {
                case "func": {
                    expectFunc();
                    break;
                }
                case "new": {
                    expectConstructor();
                    break;
                }
                case "method": {
                    expectMethod();
                    break;
                }
                case "}": { break; }
                default: {
                    printErrToken(tokenList.get(tokenIndex), "Expected constructor, function or method declarations, but found");
                }
            }
        }
    }

    static void expectFunc() {
        EdenType type = expectType("void", "int", "bool");
        Optional<String> rawName = expectSymbol();
        assert rawName.isPresent();
        String name = currentClassName + "." + rawName.get();
        if (!subroutineNames.contains(name)) {
            subroutineNames.add(name);
        } else {
            printErrToken(tokenList.get(tokenIndex - 1), "Already defined in this scope: ");
        }
        int countArgs = expectParametersDec();
        OpFunc func = new OpFunc(name, type.name(), countArgs);
        localVarShift = 0;
        intermediateRepresentation.add(func);
        func.localVarCount = expectBlockStatement();
        clearLocalVarsSymbolTable(name);
        checkForReturn(type, name);
    }

    static void expectConstructor() {
        // TODO
    }

    static void expectMethod() {
        // TODO
    }

    static void checkForReturn(EdenType type, String name) {
        Op lastOp = intermediateRepresentation.get(intermediateRepresentation.size() - 1);
        if (!(lastOp instanceof OpReturn)) {
            if (type.equals(EdenType.VOID)) {
                intermediateRepresentation.add(new OpReturn());
            } else {
                printErrToken(tokenList.get(tokenIndex - 1), "Missing return statement " + name.replaceAll("Eden_", ""));
            }
        }
    }

    static boolean checkForArray() {
        return hasNextTokenValues("[");
    }

    static boolean checkForFuncCall() {
        return hasNextTokenValues(".", "(");
    }

    static void expectArray(String identifierName) {
        SymbolTableElem var = getVarByName(identifierName);
        if (var == null) {
            printErrToken(tokenList.get(tokenIndex - 1), "Undeclared variable: " + identifierName);
        }
        assert var != null;
        assert var.shift != 0;
        tokenIndex++; // [
        expectExpression();
        tokenIndex++;
        OpAssign opAssign = new OpAssign(identifierName, var.kind, var.shift, true);
        tokenIndex++; // =
        expectExpression();
        intermediateRepresentation.add(opAssign);
    }

    static void expectArrayExpr(String identifierName) {
        SymbolTableElem var = getVarByName(identifierName);
        if (var == null) {
            printErrToken(tokenList.get(tokenIndex - 1), "Undeclared variable: " + identifierName);
        }
        assert var != null;
        assert var.shift != 0;
        intermediateRepresentation.add(new OpPushVar(var.name, var.kind, var.shift, false));
        tokenIndex++; // [
        expectExpression();
        tokenIndex++; // ]
        intermediateRepresentation.add(new OpPushNumber(4)); // INT 4 bytes
        intermediateRepresentation.add(new OpMultiply());
        intermediateRepresentation.add(new OpMinus());
        intermediateRepresentation.add(new OpDereference());
    }

    static void expectFuncCall(String identifierName) {
        String callName;
        int nArgs = 0;
        if (hasNextTokenValues(".")) {
            expectTokenType(TokenType.DOT); // . for member func call
            String subName = expectSymbol().get();
            try {
                // a.calc();
                SymbolTableElem var = getVarByName(identifierName);
                intermediateRepresentation.add(new OpPushVar(var.name, var.kind, var.shift, false));
                nArgs++;
                callName = var.type + "." + subName;
            } catch (RuntimeException ignored) {
                // Math.sqrt();
                callName = identifierName + "." + subName;
            }
        } else {
            // sqrt(); // Assume that in this case func sqrt belongs to the current class
            callName = currentClassName + "." + identifierName;
        }
        expectTokenType(TokenType.OPEN_BRACKET);
        nArgs += expectExpressionList();
        expectTokenType(TokenType.CLOSE_BRACKET);
        if (nArgs != 0) {
            List<Op> tmpList = new ArrayList<>();
            for (int i = intermediateRepresentation.size() - 1, c = 0; c < nArgs; c++, i--) {
                tmpList.add(intermediateRepresentation.get(i));
                intermediateRepresentation.remove(i);
            }
            intermediateRepresentation.addAll(tmpList);
        }
        intermediateRepresentation.add(new OpCall(callName, nArgs));
        // pop temp?
    }

    static int expectExpressionList() {
        if (hasNextTokenValues(")")) {
            return 0;
        }
        int count = 1;
        expectExpression();
        while (!hasNextTokenValues(")")) {
            if (hasNextTokenValues(",")) {
                expectTokenType(TokenType.COMMA);
                expectExpression();
                count++;
            } else {
                printErr("Expected expression list, but found EOF");
            }
        }
        return count;
    }

    static int expectParametersDec() {
        int count = 0;
        int shift = 0;
        expectTokenType(TokenType.OPEN_BRACKET);
        while (!hasNextTokenValues(")")) {
            EdenType type = expectType("int", "bool", "string");
            Optional<String> rawName = expectSymbol();
            assert rawName.isPresent();
            String name = rawName.get();
            shift += calculateShift(type.name());
            symbolTable.add(new SymbolTableElem(name, type.name(), ElemKind.ARGUMENT, shift, true));
            count++;
            if (hasNextTokenValues(",")) {
                tokenIndex++;
            }
        }
        expectTokenType(TokenType.CLOSE_BRACKET);
        return count;
    }

    static int expectBlockStatement() {
        expectTokenType(TokenType.OPEN_CURLY_BRACKET);
        int localVarCount = 0;
        while (!hasNextTokenValues("}")) {
            if (expectIfBlock()) {
                continue;
            }
            if (expectWhileBlock()) {
                continue;
            }
            localVarCount += expectStatement();
        }
        expectTokenType(TokenType.CLOSE_CURLY_BRACKET);
        return localVarCount;
    }

    static boolean expectIfBlock() {
        if (!hasNextTokenValues("if")) {
            return false;
        }
        int uniqueIndex = getUniqueIndex();
        String ifLabelStart = String.format("if_start_%d", uniqueIndex);
        String ifLabelEnd = String.format("if_end_%d", uniqueIndex);
        expectKeyword("if");
        expectExpression();
        intermediateRepresentation.add(new OpLogicalNeg());
        intermediateRepresentation.add(new OpIfGoto(ifLabelStart));
        expectBlockStatement();
        intermediateRepresentation.add(new OpGoto(ifLabelEnd));
        intermediateRepresentation.add(new OpLabel(ifLabelStart));
        if (hasNextTokenValues("else")) {
            expectKeyword("else");
            expectBlockStatement();
        }
        intermediateRepresentation.add(new OpLabel(ifLabelEnd));
        return true;
    }

    static boolean expectWhileBlock() {
        if (!hasNextTokenValues("while")) {
            return false;
        }
        int uniqueIndex = getUniqueIndex();
        String whileLabelStart = String.format("while_start_%d", uniqueIndex);
        String whileLabelEnd = String.format("while_end_%d", uniqueIndex);
        expectKeyword("while");
        intermediateRepresentation.add(new OpLabel(whileLabelStart));
        expectExpression();
        intermediateRepresentation.add(new OpLogicalNeg());
        intermediateRepresentation.add(new OpIfGoto(whileLabelEnd));
        expectBlockStatement();
        intermediateRepresentation.add(new OpGoto(whileLabelStart));
        intermediateRepresentation.add(new OpLabel(whileLabelEnd));
        return true;
    }

    static int expectStatement() {
        int localVarCount = 0;
        String tValue = String.valueOf(tokenList.get(tokenIndex++).value);
        switch (tValue) {
            case "~": {
                expectPrintStatement();
                break;
            }
            case "int":
            case "bool":
            case "string": {
                localVarCount = expectLocalVarDeclaration(tValue, localVarCount);
                break;
            }
            case "Eden_return": {
                expectReturnStatement();
                break;
            }
            default: {
                if (tValue.startsWith("Eden_")) {
                    if (checkForArray()) {
                        expectArray(tValue);
                    } else if (checkForFuncCall()) {
                        expectFuncCall(tValue);
                    } else {
                        expectLocalVarInit(tValue);
                    }
                    break;
                }
                printErrToken(tokenList.get(tokenIndex), "Unknown statement");
            }
        }
        expectTokenType(TokenType.SEMICOLON);
        return localVarCount;
    }

    static void expectReturnStatement() {
        expectExpression();
        intermediateRepresentation.add(new OpReturn());
    }

    static int expectLocalVarDeclaration(String varType, int localVarCount) {
        Optional<String> localVarName = expectSymbol();
        assert localVarName.isPresent();
        String lVarName = localVarName.get();
        SymbolTableElem existingVar = getVarByName(lVarName);
        if (existingVar == null) {
            localVarShift += calculateShift(varType);
            symbolTable.add(new SymbolTableElem(lVarName, varType, ElemKind.LOCAL, localVarShift, false));
            localVarCount++;
        } else {
            printErrToken(tokenList.get(tokenIndex - 1), "Variable already defined in this scope: " + lVarName);
        }
        if (tokenList.get(tokenIndex).type.equals(TokenType.EQUALS)) {
            expectLocalVarInit(lVarName);
        }
        if (tokenList.get(tokenIndex).type.equals(TokenType.COMMA)) {
            tokenIndex++;
            return expectLocalVarDeclaration(varType, localVarCount);
        }
        return localVarCount;
    }

    static void expectLocalVarInit(String varName) {
        SymbolTableElem var = getVarByName(varName);
        if (var == null) {
            printErrToken(tokenList.get(tokenIndex - 1), "Undeclared variable: " + varName);
        }
        assert var != null;
        assert var.kind.equals(ElemKind.LOCAL);
        usedVariables.add(var);
        OpPushVar opPushVar = new OpPushVar(var.name, var.kind, var.shift, false);
        OpAssign opAssign = new OpAssign(varName, var.kind, var.shift);
        tokenIndex++; // =
        intermediateRepresentation.add(opPushVar);
        expectExpression();
        intermediateRepresentation.add(opAssign);
    }

    static void expectPrintStatement() {
        expectExpression();
        intermediateRepresentation.add(new OpPrint());
    }

    static void expectExpression() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.COMMA) || t.type.equals(TokenType.CLOSE_BRACKET)) {
            return; //break; // For several variables declaration + initialization.
        }
        part();
        sum();
        logical();
    }

    static void sum() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.PLUS)) {
            tokenIndex++;
            part();
            intermediateRepresentation.add(new OpPlus());
            sum();
        }
        if (t.type.equals(TokenType.MINUS)) {
            tokenIndex++;
            part();
            intermediateRepresentation.add(new OpMinus());
            sum();
        }
    }

    static void logical() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.GREATER)) {
            tokenIndex++;
            part();
            sum();
            intermediateRepresentation.add(new OpMore());
        }
        if (t.type.equals(TokenType.LESS)) {
            tokenIndex++;
            part();
            sum();
            intermediateRepresentation.add(new OpLess());
        }
        if (t.type.equals(TokenType.EQUALS)) {
            tokenIndex++;
            part();
            sum();
            intermediateRepresentation.add(new OpEqual());
        }
        if (t.type.equals(TokenType.NEGATE)) {
            tokenIndex++;
            part();
            sum();
            intermediateRepresentation.add(new OpLogicalNeg());
        }
    }

    static void part() {
        unary();
    }

    static void unary() {
        boolean isPositive = true;
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.PLUS) || t.type.equals(TokenType.MINUS)) {
            if (t.type.equals(TokenType.MINUS)) {
                isPositive = false;
            }
            tokenIndex++;
        }
        arg();
        if (!isPositive) {
            intermediateRepresentation.add(new OpNeg());
        }
        starSlash();
    }

    static void starSlash() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.MULTIPLY)) {
            tokenIndex++;
            unary();
            intermediateRepresentation.add(new OpMultiply());
            starSlash();
        }
        if (t.type.equals(TokenType.DIVIDE)) {
            tokenIndex++;
            unary();
            intermediateRepresentation.add(new OpDivide());
            starSlash();
        }
        /* TODO: ^ op?
        if (String.valueOf(current.value).equalsIgnoreCase("^")) {
            opPower();
            starSlash();
        }
        */
    }

    static void arg() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.STRING)) {
            intermediateRepresentation.add(new OpPushString(t.value));
            tokenIndex++;
        } else if (t.type.equals(TokenType.OPEN_BRACKET)) {
            tokenIndex++;
            expectExpression();
            t = tokenList.get(tokenIndex);
            if (t.type.equals(TokenType.CLOSE_BRACKET)) {
                tokenIndex++;
            } else {
                printErrToken(t, "Expected close bracket, but found");
            }
        } else if (t.type.equals(TokenType.SYMBOL)) {
            String identifierName = String.valueOf(t.value);
            Token next = tokenList.get(tokenIndex + 1);
            if (next.type == TokenType.OPEN_SQUARE_BRACKET) {
                // Array
                tokenIndex++;
                expectArrayExpr(identifierName);
            } else if (next.type == TokenType.DOT || next.type == TokenType.OPEN_BRACKET) {
                // Subroutine call
                tokenIndex++;
                expectFuncCall(identifierName);
            } else {
                // Variable
                SymbolTableElem var = getVarByName(identifierName);
                if (var == null) {
                    printErrToken(tokenList.get(tokenIndex - 1), "Undeclared variable: " + identifierName);
                }
                assert var != null;
                usedVariables.add(var);
                intermediateRepresentation.add(new OpPushVar(var.name, var.kind, var.shift, true));
                tokenIndex++;
            }
        } else if (t.type.equals(TokenType.NUMBER)) {
            intermediateRepresentation.add(new OpPushNumber(t.value));
            tokenIndex++;
        } else if (t.type.equals(TokenType.KEYWORD)) {
            if (t.value.equals("true")) {
                intermediateRepresentation.add(new OpPushTrue());
            } else if (t.value.equals("false")) {
                intermediateRepresentation.add(new OpPushFalse());
            }
            tokenIndex++;
        }
        // TODO
    }

    static EdenType expectType(String... types) {
        Optional<String> rawValue = expectRawType(types);
        assert rawValue.isPresent();
        return EdenType.valueOf(rawValue.get().toUpperCase());
    }

    static Optional<String> expectRawType(String... types) {
        return expectKeywords(types);
    }

    static void expectCloseCurly() {
        expectTokenType(TokenType.CLOSE_CURLY_BRACKET);
    }

    static void expectOpenCurly() {
        expectTokenType(TokenType.OPEN_CURLY_BRACKET);
    }

    static void expectClassName() {
        Optional<String> name = expectSymbol();
        assert name.isPresent();
        currentClassName = name.get();
    }

    static Optional<String> expectSymbol() {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(TokenType.SYMBOL)) {
            tokenIndex++;
            return Optional.of(String.valueOf(t.value));
        } else {
            printErrToken(t, "Expected symbol, but found");
        }
        return Optional.empty();
    }

    static void expectKeyword(String value) {
        Token t = tokenList.get(tokenIndex);
        if (value.equals(t.value)) {
            tokenIndex++;
        } else {
            printErrToken(t, "Expected " + value + ", but found");
        }
    }

    static Optional<String> expectKeywords(String... values) {
        Token t = tokenList.get(tokenIndex);
        assert t.type == TokenType.KEYWORD;
        String tokenValue = String.valueOf(t.value);
        for (String v : values) {
            if (v.equals(tokenValue)) {
                tokenIndex++;
                return Optional.of(v);
            }
        }
        printErrToken(t, "Expected " + Arrays.toString(values) + ", but found");
        return Optional.empty();
    }

    static boolean hasNextTokenValues(String... values) {
        Token t = tokenList.get(tokenIndex);
        String tokenValue = String.valueOf(t.value);
        for (String v : values) {
            if (v.equals(tokenValue)) {
                return true;
            }
        }
        return false;
    }

    static Optional<String> expectTokensOrEmpty(String... values) {
        Token t = tokenList.get(tokenIndex);
        String tokenValue = String.valueOf(t.value);
        for (String v : values) {
            if (v.equals(tokenValue)) {
                tokenIndex++;
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    static void expectTokenType(TokenType type) {
        Token t = tokenList.get(tokenIndex);
        if (t.type.equals(type)) {
            tokenIndex++;
        } else {
            printErrToken(t, "Expected '%', but found:".replaceAll("[%]", String.valueOf(type)));
        }
    }

    static int calculateShift(String type) {
        return 4;
    }

    static int getUniqueIndex() {
        return uniqueIndex++;
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

    static void printWarnToken(Token token, String warnMessage) {
        int offset = String.valueOf(token.value).length();
        System.err.printf("WARN: [%d:%d] %s: (%s)'%s'%n", token.loc.line + 1, token.loc.column - offset, warnMessage.replaceAll("Eden_", ""), token.type, token.value);
    }

    static void printWarn(String warnMessage) {
        System.err.printf("WARN: %s%n", warnMessage);
    }

    interface Op {
        void interpret();
        void generate();
        String toString();
    }

    static class OpFunc implements Op {
        private final String name;
        private final String type;
        private final int argsCount;
        private int localVarCount = 0;

        public OpFunc(String name, String type, int argsCount) {
            this.name = name;
            this.type = type;
            this.argsCount = argsCount;
        }

        @Override
        public void interpret() {
            stackSizeList.push(programStack.size());
            for (int i = 0; i < localVarCount; i++) {
                programStack.add(0);
            }
            irPointer++;
        }

        @Override
        public void generate() {
            if (!validateFuncHasBeenUsed()) {
                return;
            }
            programCode.append("\n;OpFunc ").append(toString());
            programCode.append("\n").append(name).append(":");
            // Saving context
            programCode.append("\n\tPUSH ebp");
            programCode.append("\n\tMOV ebp, esp");
            if (localVarCount != 0) {
                programCode.append("\n\tSUB esp, ").append(4*localVarCount);
                for (int i = 1; i < localVarCount + 1; i++) {
                    programCode.append("\n\tMOV DWORD [ebp-").append(4*i).append("], 0");
                }
            }

        }

        @Override
        public String toString() {
            return String.format("Name: %s, type: %s, localVarCount: %d", name, type, localVarCount);
        }

        private boolean validateFuncHasBeenUsed() {
            if (name.equals(mainFuncName)) {
                return true;
            }
            for (Op op : intermediateRepresentation) {
                if (!(op instanceof OpCall)) {
                    continue;
                }
                OpCall call = (OpCall) op;
                if (!call.getCallName().equals(name)) {
                    continue;
                }
                return true;
            }
            printWarn("Function is defined, but not used: " + name.replaceAll("Eden_", ""));
            return false;
        }

        public String getName() {
            return this.name;
        }

        public int getArgCount() {
            return this.argsCount;
        }

        public String getType() {
            return type;
        }
    }

    static class OpCall implements Op {

        private final String callName;
        private final int nArgs;

        public OpCall(String callName, int nArgs) {
            this.callName = callName;
            this.nArgs = nArgs;
        }

        @Override
        public void interpret() {
            EdenType type = validateDeclaration();
            validateGivenArgs();
            runSubRoutine();
            for (int i = 0; i < nArgs; i++) {
                programStack.pop();
            }
            if (!type.equals(EdenType.VOID)) {
                programStack.push(registerA);
            }
        }

        private void runSubRoutine() {
            int currentPoint = irPointer;
            irPointer = getCallFuncIndex();
            Op currentOp;
            do {
                currentOp = intermediateRepresentation.get(irPointer);
                currentOp.interpret();
            } while (!(currentOp instanceof OpReturn));
            irPointer = currentPoint + 1;
        }

        private int getCallFuncIndex() {
            for (int i = 0; i < intermediateRepresentation.size(); i++) {
                Op op = intermediateRepresentation.get(i);
                if (op instanceof OpFunc && ((OpFunc) op).getName().equals(callName)) {
                    return i;
                }
            }
            throw new RuntimeException("Can't find index for call function");
        }

        @Override
        public void generate() {
            EdenType type = validateDeclaration();
            validateGivenArgs();
            programCode.append("\n;OpCall ").append(callName).append(" - ").append(nArgs);
            programCode.append("\n\tCALL ").append(callName);
            for (int i = 0; i < nArgs; i++) {
                programCode.append("\n\tPOP edx");
            }
            if (!type.equals(EdenType.VOID)) {
                programCode.append("\n\tPUSH eax");
            }
        }

        private EdenType validateDeclaration() {
            for (Op op : intermediateRepresentation) {
                if (op instanceof OpFunc) {
                    OpFunc func = (OpFunc) op;
                    if (func.getName().equals(callName)) {
                        return EdenType.valueOf(func.getType());
                    }
                }
            }
            printErr("There is a call for subroutine, but no declaration: " + callName.replaceAll("Eden_", ""));
            return EdenType.VOID;
        }

        private void validateGivenArgs() {
            for (Op op : intermediateRepresentation) {
                if (!(op instanceof OpFunc)) {
                    continue;
                }
                OpFunc func = (OpFunc) op;
                if (!func.getName().equals(callName)) {
                    continue;
                }
                if (func.getArgCount() == nArgs) {
                    break;
                }
                printErr(String.format("For function '%s' was expected %d arguments, but given %d", callName.replaceAll("Eden_", ""), func.argsCount, nArgs));
            }
        }

        public String getCallName() {
            return callName;
        }
    }

    static class OpReturn implements Op {

        @Override
        public void interpret() {
            if (stackSizeList.isEmpty()) {
                irPointer = -1;
                return;
            }
            boolean isVoid = validateReturnType();
            int stackInitSize = stackSizeList.pop();
            int count = programStack.size() - stackInitSize;
            if (!isVoid) {
                registerA = programStack.peek();
            }
            for (int i = 0; i < count; i++) {
                programStack.pop();
            }
            // TODO: we have to pass result to caller func through the stack,
            //  but at the same time we have to get rid of local variables
        }

        @Override
        public void generate() {
            boolean isVoid = validateReturnType();
            programCode.append("\n;OpReturn");
            if (isVoid) {
                programCode.append("\n\tMOV esp, ebp");
                programCode.append("\n\tPOP ebp");
                programCode.append("\n\tRET");
            } else {
                programCode.append("\n\tPOP eax");
                programCode.append("\n\tMOV esp, ebp");
                programCode.append("\n\tPOP ebp");
                programCode.append("\n\tRET");
            }
        }

        private boolean validateReturnType() {
            int index = intermediateRepresentation.indexOf(this);
            for (int i = index; i >= 0; i--) {
                Op op = intermediateRepresentation.get(i);
                if (!(op instanceof OpFunc)) {
                    continue;
                }
                OpFunc func = (OpFunc) op;
                EdenType funcType = EdenType.valueOf(func.getType());
                Op prevOp = intermediateRepresentation.get(index - 1);
                switch (funcType) {
                    // TODO: Не совсем корректно определяется тип (нужна полноценная система проверки типов)
                    case INT: {
                        if (!(prevOp instanceof OpPushNumber) && !(prevOp instanceof OpPushVar)
                                && !(prevOp instanceof OpPlus) && !(prevOp instanceof OpMinus)
                                && !(prevOp instanceof OpMultiply) && !(prevOp instanceof OpDivide)
                                && !(prevOp instanceof OpNeg)
                        ) {
                            printErr(String.format("Function %s must return integer", func.getName().replaceAll("Eden_", "")));
                        }
                    } break;
                    case BOOL: {
                        if (!(prevOp instanceof OpPushTrue) && !(prevOp instanceof OpPushFalse)) {
                            printErr(String.format("Function %s must return bool", func.getName().replaceAll("Eden_", "")));
                        }
                    } break;
                    case VOID: {
                        if (prevOp instanceof OpPushVar || prevOp instanceof OpPushNumber
                                || prevOp instanceof OpPushTrue || prevOp instanceof OpPushFalse
                                || prevOp instanceof OpPushString) {
                            printErr(String.format("Function %s must return void", func.getName().replaceAll("Eden_", "")));
                        }
                        return true;
                    }
                    default: {
                        printErr("Unexpected function return");
                    }
                }
                break;
            }
            return false;
        }
    }

    static class OpPrint implements Op {

        @Override
        public void interpret() {
            Object value = programStack.pop();
            // TODO: There might be more special characters
            System.out.printf(String.valueOf(value).replace("\\n", "\r\n"));
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPrint");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tcall print");
        }
    }

    static class OpPushString implements Op {
        private final String value;

        public OpPushString(Object value) {
            this.value = String.valueOf(value);
        }

        @Override
        public void interpret() {
            programStack.push(value);
            irPointer++;
        }

        @Override
        public void generate() {
            String cmpName = "str_" + stringConstants.size();
            stringConstants.add(value);
            programCode.append("\n;OpPushString: ").append(value);
            programCode.append("\n\tPUSH ").append(cmpName);
        }
    }

    static class OpPushNumber implements Op {
        private final Object value;

        public OpPushNumber(Object value) {
            this.value = value;
        }

        @Override
        public void interpret() {
            programStack.push(value);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPushNumber: ").append(value);
            programCode.append("\n\tPUSH ").append(value);
        }
    }

    static class OpNeg implements Op {

        @Override
        public void interpret() {
            int value = Integer.parseInt(String.valueOf(programStack.pop()));
            programStack.push(-value);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpNeg: ");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tNEG eax");
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpPlus implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            programStack.add(a + b);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPlus");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCALL strOrInt");
            programCode.append("\n\tCMP ecx, 0");
            programCode.append("\n\tJE retFrom"); // If 0 then String, no support for now
            programCode.append("\n\tADD eax, ebx");
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpMinus implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            programStack.add(a - b);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpMinus");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCALL strOrInt");
            programCode.append("\n\tCMP ecx, 0");
            programCode.append("\n\tJE retFrom"); // If 0 then String, no support for now
            programCode.append("\n\tSUB eax, ebx");
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpMultiply implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            programStack.add(a * b);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpMultiply");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCALL strOrInt");
            programCode.append("\n\tCMP ecx, 0");
            programCode.append("\n\tJE retFrom"); // If 0 then String, no support for now
            programCode.append("\n\tIMUL eax, ebx");
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpDivide implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            programStack.add(a / b);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpDivide");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCALL strOrInt");
            programCode.append("\n\tCMP ecx, 0");
            programCode.append("\n\tJE retFrom"); // If 0 then String, no support for now
            programCode.append("\n\tXOR edx, edx");
            programCode.append("\n\tCDQ");
            programCode.append("\n\tIDIV ebx");
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpLabel implements Op {
        private final String label;

        public OpLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }

        @Override
        public void interpret() {
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpLabel: ").append(label);
            programCode.append("\n").append(label).append(":");
        }
    }

    static class OpGoto implements Op {
        private final String label;

        public OpGoto(String label) {
            this.label = label;
        }

        @Override
        public void interpret() {
            int indexToGo = -1;
            for (int i = 0; i < intermediateRepresentation.size(); i++) {
                Op op = intermediateRepresentation.get(i);
                if (op instanceof OpLabel) {
                    if (((OpLabel) op).getLabel().equals(label)) {
                        indexToGo = i;
                        break;
                    }
                }
            }
            irPointer = indexToGo;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpGoto: ").append(label);
            programCode.append("\n\tJMP ").append(label);
        }
    }

    static class OpIfGoto implements Op {
        private final String label;

        public OpIfGoto(String label) {
            this.label = label;
        }

        @Override
        public void interpret() {
            boolean jumpNotEqualZero = String.valueOf(programStack.pop()).equals("1");
            int index = -1;
            if (jumpNotEqualZero) {
                for (int i = 0; i < intermediateRepresentation.size(); i++) {
                    Op op = intermediateRepresentation.get(i);
                    if (op instanceof OpLabel) {
                        if (((OpLabel) op).getLabel().equals(label)) {
                            index = i;
                            break;
                        }
                    }
                }
                irPointer = index;
                return;
            }
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpIfGoto: ").append(label);
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCMP eax, 0");
            programCode.append("\n\tJNE ").append(label);
        }
    }

    static class OpMore implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            if (a > b) {
                programStack.push(1);
            } else {
                programStack.push(0);
            }
            irPointer++;
        }

        @Override
        public void generate() {
            int uniqueIndex = getUniqueIndex();
            String uniqueLabelStart = String.format("greater_st_%d", uniqueIndex);
            String uniqueLabelEnd = String.format("greater_end_%d", uniqueIndex);
            programCode.append("\n;OpLess: ");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCMP eax, ebx");
            programCode.append("\n\tJG ").append(uniqueLabelStart);
            programCode.append("\n\tPUSH 0");
            programCode.append("\n\tJMP ").append(uniqueLabelEnd);
            programCode.append("\n").append(uniqueLabelStart).append(":");
            programCode.append("\n\tMOV eax, 0");
            programCode.append("\n\tNOT eax");
            programCode.append("\n\tADD eax, 2");
            programCode.append("\n\tPUSH eax");
            programCode.append("\n").append(uniqueLabelEnd).append(":");
        }
    }

    static class OpLess implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            if (a < b) {
                programStack.push(1);
            } else {
                programStack.push(0);
            }
            irPointer++;
        }

        @Override
        public void generate() {
            int uniqueIndex = getUniqueIndex();
            String uniqueLabelStart = String.format("less_st_%d", uniqueIndex);
            String uniqueLabelEnd = String.format("less_end_%d", uniqueIndex);
            programCode.append("\n;OpLess: ");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCMP eax, ebx");
            programCode.append("\n\tJL ").append(uniqueLabelStart);
            programCode.append("\n\tPUSH 0");
            programCode.append("\n\tJMP ").append(uniqueLabelEnd);
            programCode.append("\n").append(uniqueLabelStart).append(":");
            programCode.append("\n\tMOV eax, 0");
            programCode.append("\n\tNOT eax");
            programCode.append("\n\tADD eax, 2");
            programCode.append("\n\tPUSH eax");
            programCode.append("\n").append(uniqueLabelEnd).append(":");
        }
    }

    static class OpEqual implements Op {

        @Override
        public void interpret() {
            int b = Integer.parseInt(String.valueOf(programStack.pop()));
            int a = Integer.parseInt(String.valueOf(programStack.pop()));
            if (a == b) {
                programStack.push(1);
            } else {
                programStack.push(0);
            }
            irPointer++;
        }

        @Override
        public void generate() {
            int uniqueIndex = getUniqueIndex();
            String uniqueLabelStart = String.format("equal_st_%d", uniqueIndex);
            String uniqueLabelEnd = String.format("equal_end_%d", uniqueIndex);
            programCode.append("\n;OpEqual: ");
            programCode.append("\n\tPOP ebx");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tCMP eax, ebx");
            programCode.append("\n\tJZ ").append(uniqueLabelStart);
            programCode.append("\n\tPUSH 0");
            programCode.append("\n\tJMP ").append(uniqueLabelEnd);
            programCode.append("\n").append(uniqueLabelStart).append(":");
            programCode.append("\n\tMOV eax, 0");
            programCode.append("\n\tNOT eax");
            programCode.append("\n\tADD eax, 2");
            programCode.append("\n\tPUSH eax");
            programCode.append("\n").append(uniqueLabelEnd).append(":");
        }
    }

    static class OpLogicalNeg implements Op {

        @Override
        public void interpret() {
            boolean a = String.valueOf(programStack.pop()).equals("1");
            int res = a ? 0 : 1;
            programStack.push(res);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpLogicalNeg: ");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tNOT eax");
            programCode.append("\n\tADD eax, 2"); // FALSE IS 0, TRUE IS 1.
            programCode.append("\n\tPUSH eax");
        }
    }

    static class OpPushTrue implements Op {

        @Override
        public void interpret() {
            programStack.push(1);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPushTrue");
            programCode.append("\n\tPUSH 1");
        }
    }

    static class OpPushFalse implements Op {

        @Override
        public void interpret() {
            programStack.push(0);
            irPointer++;
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPushFalse");
            programCode.append("\n\tPUSH 0");
        }
    }

    static class OpAssign implements Op {
        private final String name;
        private final ElemKind kind;
        private final int shift;
        private final boolean isArray;

        public OpAssign(String name, ElemKind kind, int shift) {
            this(name, kind, shift, false);
        }

        public OpAssign(String name, ElemKind kind, int shift, boolean isArray) {
            this.name = name;
            this.kind = kind;
            this.shift = shift;
            this.isArray = isArray;
        }

        @Override
        public void interpret() {
            if (kind.equals(ElemKind.LOCAL)) {
                if (isArray) {
                    throw new NotImplementedException();
                } else {
                    Object expr = programStack.pop();
                    Object adr = programStack.pop();
                    programStack.set(Integer.parseInt(String.valueOf(adr)), expr);
                    irPointer++;
                }
            } else {
                throw new NotImplementedException();
            }
        }

        @Override
        public void generate() {
            // Local
            if (kind.equals(ElemKind.LOCAL)) {
                if (isArray) {
                    programCode.append("\n;OpAssign Array ").append(name).append(":").append(kind.name()).append(":").append(shift);
                    programCode.append("\n\tPOP ecx");
                    programCode.append("\n\tPOP eax");
                    programCode.append("\n\tMOV esi, 4"); // FOR INT
                    programCode.append("\n\tMUL esi");
                    programCode.append("\n\tMOV ebx, eax");
                    programCode.append("\n\tMOV eax, ecx");
                    programCode.append("\n\tADD ebx, ").append(shift);
                    programCode.append("\n\tMOV edx, ebp");
                    programCode.append("\n\tSUB edx, ebx"); // SUB - LOCAL
                    programCode.append("\n\tMOV dword [edx], eax");
                } else {
                    programCode.append("\n;OpAssign ").append(name).append(":").append(kind.name()).append(":").append(shift);
                    programCode.append("\n\tPOP eax"); // expr
                    programCode.append("\n\tPOP ebx"); // adr
                    programCode.append("\n\tMOV dword [ebx], eax");
                }
            } else {
                throw new NotImplementedException();
            }
        }
    }

    static class OpPushVar implements Op {
        private final String name;
        private final int shift;
        private final ElemKind kind;
        private final boolean isDereference;

        public OpPushVar(String name, ElemKind kind, int shift, boolean isDereference) {
            this.name = name;
            this.kind = kind;
            this.shift = shift;
            this.isDereference = isDereference;
        }

        @Override
        public void interpret() {
            switch (kind) {
                case LOCAL: {
                    int index = stackSizeList.peek() - 1 + shift / 4;
                    if (isDereference) {
                        programStack.push(programStack.get(index));
                    } else {
                        programStack.push(index);
                    }
                    irPointer++;
                } break;
                case ARGUMENT: {
                    int index = stackSizeList.peek() - shift / 4;
                    if (isDereference) {
                        programStack.push(programStack.get(index));
                    } else {
                        programStack.push(index);
                    }
                    irPointer++;
                } break;
                default: throw new NotImplementedException();
            }
        }

        @Override
        public void generate() {
            programCode.append("\n;OpPushVar ").append(name).append(":").append(kind.name()).append(":").append(shift);
            switch (kind) {
                case LOCAL: {
                    if (isDereference) {
                        programCode.append("\n\tPUSH dword [ebp - ").append(shift).append("]");
                    } else {
                        programCode.append("\n\tMOV eax, ebp");
                        programCode.append("\n\tSUB eax, ").append(shift);
                        programCode.append("\n\tPUSH eax");
                    }
                } break;
                case ARGUMENT: {
                    if (isDereference) {
                        programCode.append("\n\tPUSH dword [ebp + ").append(4 + shift).append("]");
                    } else {
                        programCode.append("\n\tMOV eax, ebp");
                        programCode.append("\n\tADD eax, 4");
                        programCode.append("\n\tADD eax, ").append(shift);
                        programCode.append("\n\tPUSH eax");
                    }
                } break;
                default: throw new NotImplementedException();
            }
        }
    }

    static class OpDereference implements Op {

        @Override
        public void interpret() {
            throw new NotImplementedException();
        }

        @Override
        public void generate() {
            programCode.append("\n;OpDereference ");
            programCode.append("\n\tPOP eax");
            programCode.append("\n\tPUSH dword [eax]");
        }
    }

    enum ElemKind {
        FIELD,
        STATIC,
        ARGUMENT,
        LOCAL,
        CONST
    }

    static class SymbolTableElem {
        String name;
        String type;
        ElemKind kind;
        int shift;
        boolean isRoutine;
        Token token;

        public SymbolTableElem(String name, String type, ElemKind kind, int shift, boolean isRoutine) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.shift = shift;
            this.isRoutine = isRoutine;
            this.token = tokenList.get(tokenIndex - 1);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            SymbolTableElem o = (SymbolTableElem) obj;
            return this.name.equals(o.name) && this.type.equals(o.type) && this.kind.equals(o.kind) && this.shift == o.shift && this.isRoutine == o.isRoutine;
        }
    }

    @Deprecated
    static List<String> getLocalVarNamesFromSymbolTable() {
        List<String> varNames = new ArrayList<>();
        for (SymbolTableElem s : symbolTable) {
            if (s.kind.equals(ElemKind.LOCAL)) {
                varNames.add(s.name);
            }
        }
        return varNames;
    }

    static SymbolTableElem getVarByName(String varName) {
        for (SymbolTableElem s : symbolTable) {
            if (s.name.equals(varName)) {
                return s;
            }
        }
        return null;
    }

    static void clearLocalVarsSymbolTable(String classFunName) {
        symbolTable.removeAll(usedVariables);
        List<SymbolTableElem> toRemove = new ArrayList<>();
        for (SymbolTableElem e : symbolTable) {
            switch (e.kind) {
                case LOCAL: {
                    printWarnToken(e.token, "Local variable is defined, but not used: " + e.name + " in " + classFunName);
                    toRemove.add(e);
                } break;
                case ARGUMENT: {
                    printWarnToken(e.token, "Argument variable is defined, but not used: " + e.name + " in " + classFunName);
                    toRemove.add(e);
                } break;
                default: {
                    throw new NotImplementedException();
                }
            }
        }
        symbolTable.removeAll(toRemove);
    }

    enum EdenType {
        VOID,
        BOOL,
        INT
    }

    enum TokenType {
        //CHARACTER,
        SEMICOLON,
        COMMA,
        DOT,
        WIN_CALL_SYMBOL,
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
        NEGATE,
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
        String allowedCharacters = "~+-*/;()=><!{}().,'\"|&[]:";
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
            keywordList.add("use");
            keywordList.add("final");
            keywordList.add("func");
            keywordList.add("new");
            keywordList.add("method");
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
            keywordList.add("win");
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
                if (currentChar == '\0') {
                    continue; // EOF.
                }
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
                case '.': {
                    tokenList.add(new Token(TokenType.DOT, currentChar, new Location(line, column)));
                    break;
                }
                case ':': {
                    tokenList.add(new Token(TokenType.WIN_CALL_SYMBOL, currentChar, new Location(line, column)));
                    break;
                }
                case '!': {
                    tokenList.add(new Token(TokenType.NEGATE, currentChar, new Location(line, column)));
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
