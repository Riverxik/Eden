package ru.riverx.eden.parser;

import ru.riverx.eden.exceptions.NoMainFuncException;
import ru.riverx.eden.parser.high.SymbolKind;
import ru.riverx.eden.parser.high.SymbolTable;
import ru.riverx.eden.parser.high.Variable;
import ru.riverx.eden.parser.middle.*;
import ru.riverx.eden.tokenizer.Token;
import ru.riverx.eden.tokenizer.Tokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParserEngine {

    public static final String CONSTANT = "constant";
    public static final String RETURN = "return";
    public static final String WIN = "win";
    public static final String ALLOC = "alloc";
    public static final String FREE = "free";
    public static final String DO = "do";
    public static final String WHILE = "while";
    public static final String IF = "if";
    public static final String LET = "let";
    public static final String POINTER = "pointer";
    public static final String ARGUMENT = "argument";
    public static final String TEMP = "temp";
    public static final String THAT = "that";

    private final List<String> primTypes = Arrays.asList("int", "char", "bool");
    private final List<String> primFuncTypes = Arrays.asList("int", "char", "bool", "void");
    private final List<Token> tokens;
    private final List<String> externWinCallList = new ArrayList<>();
    private final List<String> usedClasses = new ArrayList<>();
    private String className;
    private String mainFuncName;
    private int index;
    private Token currentToken;
    private boolean wasReturn = false;
    private int errorCount = 0;
    private final long compilationTime;
    private final SymbolTable symbolTable;
    private final VMWriter writer;
    private int uniqueNumber = 0;

    public ParserEngine(List<Token> tokens, String filename) {
        this.tokens = tokens;
        this.className = filename;
        this.index = 0;
        this.currentToken = tokens.get(index);
        this.symbolTable = new SymbolTable();
        this.writer = new VMWriter();
        long start = System.currentTimeMillis();
        parseProgram();
        this.compilationTime = System.currentTimeMillis() - start;
        validateMain();
    }

    private void parseProgram() {
        parseClass();
        if (index < tokens.size() - 1) {
            acceptToken(); // '}' from end of the class
            parseProgram();
        }
    }

    private void validateMain() {
        for (VM2Asm op : writer.getCode()) {
            if (op instanceof OpFunctionInfo) {
                String funcName = ((OpFunctionInfo)op).getFuncName();
                if (funcName.endsWith(".main")) {
                    mainFuncName = funcName;
                }
            }
        }
        if (mainFuncName == null || mainFuncName.isEmpty()) {
            throw new NoMainFuncException("ERROR: Your program must have main function");
        }
    }

    public List<String> getStatements() {
        List<String> list = new ArrayList<>();
        List<VM2Asm> code = writer.getCode();

        writeHeader(list);
        for (VM2Asm cmd : code) {
            list.add(cmd.getAsmCode());
        }
        writeFooter(list);

        return list;
    }

    private void writeHeader(List<String> asmCode) {
        asmCode.add("extern ExitProcess");
        asmCode.add("extern GetProcessHeap");
        asmCode.add("extern HeapAlloc");
        asmCode.add("extern HeapFree");
        // Custom user win-calls
        for (String wc : externWinCallList) {
            asmCode.add("extern " + wc);
        }
        asmCode.add("global Start\n");
        asmCode.add("section .data");
        asmCode.add("section .bss");
        asmCode.add("\teden_sp resd 1");
        asmCode.add("\teden_lcl resd 1");
        asmCode.add("\teden_arg resd 1");
        asmCode.add("\teden_this resd 1");
        asmCode.add("\teden_that resd 1");
        asmCode.add("\teden_temp resd 1");
        asmCode.add("\teden_r13 resd 1");
        asmCode.add("section .text");
        // Eden Alloc
        asmCode.add(";Expects a number on the stack - how much bytes allocate");
        asmCode.add("eden_alloc:");
        asmCode.add("\tpush eax");
        asmCode.add("\tpush dword [eden_lcl]\n\tpush dword [eden_arg]");
        asmCode.add("\tpush dword [eden_this]\n\tpush dword [eden_that]");
        asmCode.add("\tmov dword eax, [eden_r13]");
        asmCode.add("\tadd eax, 20");
        asmCode.add("\tmov ebx, esp");
        asmCode.add("\tadd ebx, eax");
        asmCode.add("\tmov dword [eden_arg], ebx");
        asmCode.add("\tsub dword [eden_arg], 4");
        asmCode.add("\tmov dword [eden_lcl], esp");
        asmCode.add("; LOGIC HERE");
        asmCode.add("\tmov dword eax, [eden_arg]");
        asmCode.add("\tpush    dword [eax]");        // ; size
        asmCode.add("\tpush    8");                  // ; flags (0 = default, 8 = zeroing memory)
        asmCode.add("\tcall    GetProcessHeap");
        asmCode.add("\tpush    eax");                // ; heap handle
        asmCode.add("\tcall    HeapAlloc");
        asmCode.add("\tjmp eden_return");
        // Eden Free
        asmCode.add(";Expects a pointer on the stack - to de-allocate memory");
        asmCode.add("eden_free:");
        asmCode.add("\tpush eax");
        asmCode.add("\tpush dword [eden_lcl]\n\tpush dword [eden_arg]");
        asmCode.add("\tpush dword [eden_this]\n\tpush dword [eden_that]");
        asmCode.add("\tmov dword eax, [eden_r13]");
        asmCode.add("\tadd eax, 20");
        asmCode.add("\tmov ebx, esp");
        asmCode.add("\tadd ebx, eax");
        asmCode.add("\tmov dword [eden_arg], ebx");
        asmCode.add("\tsub dword [eden_arg], 4");
        asmCode.add("\tmov dword [eden_lcl], esp");
        asmCode.add("\tmov dword eax, [eden_arg]");
        asmCode.add("\tpush    dword [eax]");        //   ; ptr
        asmCode.add("\tpush    0");                  //   ; flags
        asmCode.add("\tcall    GetProcessHeap");
        asmCode.add("\tpush    eax");                // ; heap handle
        asmCode.add("\tcall    HeapFree");
        asmCode.add("\tjmp eden_return");
        // Eden Append Char
        asmCode.add("eden_append_char:");
        asmCode.add(";; base adr, char, shift");
        asmCode.add("\tmov dword [eden_r13], eax"); // ; save return address
        asmCode.add("\tpop ecx");                   // ; shift
        asmCode.add("\tpop ebx");                   // ; char
        asmCode.add("\tpop eax");                   // ; base adr
        asmCode.add("\tpush eax");
        asmCode.add("\tadd eax, ecx");
        asmCode.add("\tmov [eax], ebx");
        asmCode.add("\tjmp [eden_r13]");
        // Eden inner Comparison EQ
        asmCode.add("eden_comp_eq:");
        asmCode.add("\tmov dword [eden_r13], eax");
        asmCode.add("\tpop eax");
        asmCode.add("\tpop ebx");
        asmCode.add("\tcmp ebx, eax");
        asmCode.add("\tje eden_comp_success");
        asmCode.add("\tjmp eden_comp_failure");
        // Eden inner Comparison LT
        asmCode.add("eden_comp_lt:");
        asmCode.add("\tmov dword [eden_r13], eax");
        asmCode.add("\tpop eax");
        asmCode.add("\tpop ebx");
        asmCode.add("\tcmp ebx, eax");
        asmCode.add("\tjl eden_comp_success");
        asmCode.add("\tjmp eden_comp_failure");
        // Eden inner Comparison GT
        asmCode.add("eden_comp_gt:");
        asmCode.add("\tmov dword [eden_r13], eax");
        asmCode.add("\tpop eax");
        asmCode.add("\tpop ebx");
        asmCode.add("\tcmp ebx, eax");
        asmCode.add("\tjg eden_comp_success");
        asmCode.add("\tjmp eden_comp_failure");
        // Comparison failure
        asmCode.add("eden_comp_failure:");
        asmCode.add("\tpush 0");
        asmCode.add("\tjmp eden_comp_end");
        // Comparison success
        asmCode.add("eden_comp_success:");
        asmCode.add("\tpush -1");
        asmCode.add("eden_comp_end:");
        // Jump back from comparison
        asmCode.add("\tjmp [eden_r13]");
        // call return label
        asmCode.add("eden_return:");
        // put return value that is on eax in [arg]
        asmCode.add("\tmov dword ebx, [eden_arg]");
        asmCode.add("\tmov dword [ebx], eax");
        // save new sp for calling function in ebx
        asmCode.add("\tmov dword ebx, [eden_arg]");
        // set sp as local
        asmCode.add("\tmov dword esp, [eden_lcl]");
        // pop that, this, arg, lcl
        asmCode.add("\tpop dword [eden_that]");
        asmCode.add("\tpop dword [eden_this]");
        asmCode.add("\tpop dword [eden_arg]");
        asmCode.add("\tpop dword [eden_lcl]");
        // pop return address into eax
        asmCode.add("\tpop eax");
        // set correct esp now
        asmCode.add("\tmov esp, ebx");
        // jump to return address
        asmCode.add("\tjmp eax");
        asmCode.add("; CODE GENERATION ");
    }

    private void writeFooter(List<String> asmCode) {
        asmCode.add("; CODE GENERATION END ");
        asmCode.add("; User program entry point ");
        asmCode.add("Start:");
        asmCode.add("\tmov [eden_lcl], esp");
        asmCode.add("\tmov [eden_arg], esp");
        asmCode.add("\tsub dword [eden_arg], 4");
        asmCode.add(new OpCall(mainFuncName, 0).getAsmCode());
        asmCode.add("; End of program");
        asmCode.add("eden_exit:");
        asmCode.add("\tpush 0");
        asmCode.add("\tcall ExitProcess");
    }

    private void printTranslateInfo() {
        if (errorCount > 0) {
            System.err.printf("[%s] Translating failed with %d errors!%n", className, errorCount);
            System.exit(1);
        } else {
            System.out.printf("[%s] Translation done in %d ms%n", className, compilationTime);
        }
    }

    private void parseLibs() {
        if (!expectTokenValue(false, "use")) {
            return;
        }
        expectTokenValue("use"); acceptToken();
        String libNameWithPath = expectAndGetStringConstant();
        if (libNameWithPath.isEmpty()) {
            printError(currentToken, "Filename for include is empty: " + libNameWithPath);
            return;
        }

        if (usedClasses.contains(libNameWithPath)) {
            printError(currentToken, "Class already included: " + libNameWithPath);
        } else {
            usedClasses.add(libNameWithPath);

            Path sourcePath = Paths.get(libNameWithPath);
            if (!Files.exists(sourcePath)) {
                printError(currentToken, "File does not exists: " + sourcePath);
                return;
            }

            try {
                String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
                Tokenizer tokenizer = new Tokenizer(libNameWithPath, source);
                List<Token> libTokens = tokenizer.getTokenList();
                tokens.addAll(libTokens);
            } catch (IOException e) {
                printError(currentToken, "Unable to import file: " + sourcePath + ", cause: " + e.getMessage());
                return;
            }
        }
        expectTokenValue(";"); acceptToken();

        parseLibs();
    }

    private String extractNameFromPath(String path) {
        int end = 0;
        int start = 0;
        int i = path.length() - 1;
        for (; i > 0; i--) {
            char c = path.charAt(i);
            if (c == '.') {
                end = i;
                break;
            }
        }
        for (; i > 0; i--) {
            char c = path.charAt(i);
            if (c == '/' || c == '\\') {
                start = i;
                break;
            }
        }
        return path.substring(start + 1, end);
    }

    private void parseClass() {
        parseLibs();
        className = extractNameFromPath(currentToken.getFilename());
        expectTokenValue("class"); acceptToken();
        expectTokenValue(className);
        validateClassName();
        parseClassName();
        expectTokenValue("{"); acceptToken();
        parseClassVarDec();
        parseSubroutineDec();
        expectTokenValue("}");
        symbolTable.resetKindCountClass();
        printTranslateInfo();
    }

    private void validateClassName() {
        if (!currentToken.getValue().equals(className)) {
            printError(currentToken, "Name of the class must be equal to the filename");
            System.exit(1);
        }
    }

    private void parseClassName() {
        if (!Character.isUpperCase(currentToken.getValue().charAt(0))) {
            printError(currentToken, "Class name must start with the capital letter");
        }
        acceptToken();
    }

    private void parseClassVarDec() {
        while (expectTokenValue(false,"static", "field")) {
            String kind = parseKind(); // static | field
            String type = parseType(); // int | char | bool | className
            parseIdentifiers(kind, type);
            expectTokenValue(";"); acceptToken();
        }
    }

    private String parseKind() {
        String kind = currentToken.getValue();
        acceptToken();
        return kind;
    }

    private String parseType() {
        expectTypeValue(false);
        return currentToken.getValue();
    }

    private void parseSubroutineDec() {
        while (expectTokenValue(false, "constructor", "func", "method")) {
            int subIndex = parseSubroutineKind();
            if (subIndex == 2) { symbolTable.defineSubroutine("this", className, "arg"); }
            parseSubroutineType(true);
            String subName = parseIdentifier();
            expectTokenValue("("); acceptToken();
            parseParameterList();
            expectTokenValue(")"); acceptToken();
            parseSubroutineBody(subIndex, subName);
            symbolTable.resetKindCountSubroutine();
        }
    }

    private int parseSubroutineKind() {
        int idx = -1;
        switch (currentToken.getValue()) {
            case "constructor": idx = 0; break;
            case "func": idx = 1; break;
            case "method": idx = 2; break;
            default: printError(currentToken, "Unexpected kind of subroutine");
        }
        acceptToken();
        return idx;
    }

    private String parseSubroutineType(boolean isRoutine) {
        expectTypeValue(isRoutine);
        String resultType = currentToken.getValue();
        acceptToken();
        return resultType;
    }

    private String parseIdentifier() {
        expectIdentifier();
        String name = currentToken.getValue();
        acceptToken();
        return name;
    }

    private void parseParameterList() {
        boolean isEmpty = expectTokenValue(false, ")");
        if (isEmpty) {
            return;
        }
        String paramType = parseSubroutineType(false);
        String paramName = parseIdentifier();
        symbolTable.defineSubroutine(paramName, paramType, "arg");
        while (!expectTokenValue(false, ")")) {
            if (expectTokenValue( ",")) {
                acceptToken();
                String type = parseType(); acceptToken();
                String name = parseIdentifier();
                symbolTable.defineSubroutine(name, type, "arg");
            } else {
                printError(currentToken, "Expected ',' or ')' but found");
                System.exit(1);
            }
        }
    }

    private void parseSubroutineBody(int subIndex, String subName) {
        expectTokenValue("{"); acceptToken();
        parseVarDec();
        // subIndex -> [ 0 - constructor, 1 - function, 2 - method ]
        writeSubroutineDeclaration(subIndex, subName);
        wasReturn = false;
        parseStatements(); // Maybe should check for return type also
        if (!wasReturn) {
            printError(currentToken, "Every method must return!");
        }
        expectTokenValue("}"); acceptToken();
    }

    private void parseVarDec() {
        while (expectTokenValue(false,"var")) {
            acceptToken();
            String type = parseType();
            parseIdentifiers("var", type);
            expectTokenValue(";"); acceptToken();
        }
    }

    private void writeSubroutineDeclaration(int subIndex, String subName) {
        writer.writeFunction(className + "." + subName, symbolTable.varCount(SymbolKind.VAR));
        if (subIndex == 0) { // constructor
            writer.writePush(CONSTANT, symbolTable.varCount(SymbolKind.FIELD) * 4);
            writer.writeCall("eden_alloc", 1);
            writer.writePop(POINTER, 0);
        } else if (subIndex == 2) { // method
            writer.writePush(ARGUMENT, 0);
            writer.writePop(POINTER, 0);
        }
    }

    private void parseStatements() {
        while (expectTokenValue(false, LET, IF, WHILE, DO, RETURN)) {
            switch (currentToken.getValue()) {
                case LET: { parseLet(); break; }
                case IF: { parseIf(); break; }
                case WHILE: { parseWhile(); break; }
                case DO: { parseDo(); break; }
                case RETURN: { parseReturn(); wasReturn = true; break; }
                default: break;
            }
        }
    }

    private void parseLet() {
        boolean isArray = false;
        expectTokenValue(LET); acceptToken();
        String variableName = parseIdentifier();
        Variable variable = symbolTable.findVariable(variableName);
        if (expectTokenValue(false, "[")) {
            acceptToken();
            writer.writePush(variable.getKind(), variable.getIndex());
            parseExpression();
            if ("int".contentEquals(variable.getType())) {
                writer.writeConstant("4");
                writer.writeArithmetic(VMCommand.MULTIPLY);
            }
            expectTokenValue("]"); acceptToken();
            writer.writeArithmetic(VMCommand.ADD);
            isArray = true;
        }
        expectTokenValue("="); acceptToken();
        parseExpression();
        if (isArray) {
            writer.writePop(TEMP, 0);
            writer.writePop(POINTER, 1);
            writer.writePush(TEMP, 0);
            writer.writePop(THAT, 0);
        } else {
            writer.writePop(variable.getKind(), variable.getIndex());
        }
        expectTokenValue(";"); acceptToken();
    }

    private void parseIf() {
        expectTokenValue(IF); acceptToken();
        int unique = getUniqueNumber();
        String l1 = className+".IF_L1_" + unique;
        String l2 = className+".IF_L2_" + unique;
        parseBracketExpression();
        writer.writeArithmetic(VMCommand.NOT);
        writer.writeIfGoto(l1);
        parseBlockStatement();
        writer.writeGoto(l2);
        writer.writeLabel(l1);
        boolean isIfReturn = wasReturn;
        wasReturn = false;
        if (expectTokenValue(false, "else")) {
            acceptToken();
            parseBlockStatement();
            if (isIfReturn ^ wasReturn) {
                printError(currentToken, "All `if` branches must return if `return` presents at least in one of them");
            }
        }
        writer.writeLabel(l2);
    }

    private void parseBracketExpression() {
        expectTokenValue("("); acceptToken();
        parseExpression();
        expectTokenValue(")"); acceptToken();
    }

    private void parseBlockStatement() {
        expectTokenValue("{"); acceptToken();
        parseStatements();
        expectTokenValue("}"); acceptToken();
    }

    private void parseWhile() {
        expectTokenValue(WHILE); acceptToken();
        int unique = getUniqueNumber();
        String l1 = className + ".WHILE_L1_" + unique;
        String l2 = className + ".WHILE_L2_" + unique;
        writer.writeLabel(l1);
        parseBracketExpression();
        writer.writeArithmetic(VMCommand.NOT);
        writer.writeIfGoto(l2);
        parseBlockStatement();
        writer.writeGoto(l1);
        writer.writeLabel(l2);
    }

    private void parseDo() {
        expectTokenValue(DO); acceptToken();
        subroutineCall();
        expectTokenValue(";"); acceptToken();
        writer.writePop(TEMP, 0); // We don't need to store result in 'do' statement.
    }

    private void parseReturn() {
        expectTokenValue(RETURN); acceptToken();
        if (!expectTokenValue(false, ";")) {
            parseExpression();
        } else {
            writer.writePush(CONSTANT, 0);
        }
        writer.writeReturn();
        expectTokenValue(";"); acceptToken();
    }

    private void parseExpression() {
        part();
        sum();
        shift();
        logical();
        bitwise();
    }

    private void part() {
        unary();
    }

    private void unary() {
        Token t = tokens.get(index);
        if (expectTokenValue(false, "+", "-")) {
            acceptToken();
        }
        parseTerm();
        if (t.getValue().equals("-")) {
            writeOp('-', true);
        }
        starSlash();
    }

    private void starSlash() {
        if (expectTokenValue(false, "*")) {
            acceptToken();
            unary();
            writeOp('*', false);
            starSlash();
        }
        if (expectTokenValue(false, "/")) {
            acceptToken();
            unary();
            writeOp('/', false);
            starSlash();
        }
    }

    private void sum() {
        if (expectTokenValue(false, "+")) {
            acceptToken();
            part();
            writeOp('+', false);
            sum();
        }
        if (expectTokenValue(false, "-")) {
            acceptToken();
            part();
            writeOp('-', false);
            sum();
        }
    }

    private void shift() {
        if (expectTokenValue(false, "<<")) {
            acceptToken();
            part();
            sum();
            writer.writeArithmetic(VMCommand.L_SHIFT);
        }
        if (expectTokenValue(false, ">>")) {
            acceptToken();
            part();
            sum();
            writer.writeArithmetic(VMCommand.R_SHIFT);
        }
    }

    private void bitwise() {
        if (expectTokenValue(false, "&")) {
            acceptToken();
            part();
            sum();
            shift();
            logical();
            writeOp('&', false);
            bitwise();
        }
        if (expectTokenValue(false, "|")) {
            acceptToken();
            part();
            sum();
            shift();
            logical();
            writeOp('|', false);
            bitwise();
        }
    }

    private void logical() {
        if (expectTokenValue(false, ">")) {
            acceptToken();
            part();
            sum();
            shift();
            writeOp('>', false);
        }
        if (expectTokenValue(false, "<")) {
            acceptToken();
            part();
            sum();
            shift();
            writeOp('<', false);
        }
        if (expectTokenValue(false, "=")) {
            acceptToken();
            part();
            sum();
            shift();
            writeOp('=', false);
        }
        if (expectTokenValue(false, "~")) {
            acceptToken();
            part();
            sum();
            shift();
            writeOp('~', false);
        }
    }

    private void parseTerm() {
        switch (currentToken.getType()) {
            case INTEGER_CONSTANT: {
                String numConst = currentToken.getValue();
                acceptToken();
                writer.writeConstant(numConst);
                break;
            }
            case STRING_CONSTANT: {
                String strConst = currentToken.getValue();
                acceptToken();
                parseString(strConst);
                break;
            }
            case KEYWORD: {
                parseKeywordConstant();
                acceptToken();
                break;
            }
            case IDENTIFIER: {
                // Variable, array or subroutine call
                Token next = getNextToken();
                String value = next.getValue();
                if (value.equals("[")) {
                    // array
                    String name = parseIdentifier();
                    Variable variable = symbolTable.findVariable(name);
                    writer.writePush(variable.getKind(), variable.getIndex());
                    acceptToken(); // [
                    parseExpression();
                    if ("int".contentEquals(variable.getType())) {
                        writer.writeConstant("4");
                        writer.writeArithmetic(VMCommand.MULTIPLY);
                    }
                    expectTokenValue("]"); acceptToken();
                    writer.writeArithmetic(VMCommand.ADD);
                    next = getNextToken();
                    value = next.getValue();
                    if (value.equals("=")) {
                        acceptToken();
                        parseExpression();
                        writer.writePop(TEMP, 0);
                        writer.writePop(POINTER, 1);
                        writer.writePush(TEMP, 0);
                        writer.writePop(THAT, 0);
                    } else {
                        writer.writePop(POINTER, 1);
                        writer.writePush(THAT, 0);
                    }
                } else if (value.equals(".") || value.equals("(")) {
                    subroutineCall();
                } else {
                    // Identifier
                    String name = parseIdentifier();
                    Variable variable = symbolTable.findVariable(name);
                    writer.writePush(variable.getKind(), variable.getIndex());
                }
                break;
            }
            case SYMBOL: {
                if (expectTokenValue(false, "-", "~")) {
                    String symbol = currentToken.getValue();
                    acceptToken();
                    parseTerm();
                    final String symbols = "-~";
                    writeOp(symbols.charAt(symbols.indexOf(symbol)), true);
                    break;
                }
                expectTokenValue("("); acceptToken();
                parseExpression();
                expectTokenValue(")"); acceptToken();
                break;
            }
            default: printError(currentToken, "Can't be right");
        }
    }

    private void parseString(String inputStr) {
        writer.writePush(CONSTANT, inputStr.length());
        writer.writeCall("eden_alloc", 1);
        for (int i = 0; i < inputStr.length(); i++) {
            writer.writePush(CONSTANT, inputStr.charAt(i));
            writer.writePush(CONSTANT, i);
            writer.writeCall("eden_append_char", 3); // 0 is String base address, 1 is char, 2 shift
        }
    }

    private void parseKeywordConstant() {
        switch (currentToken.getValue()) {
            case "true": { // True is -1
                writer.writePush(CONSTANT, 1);
                writer.writeArithmetic(VMCommand.NEG);
                break;
            }
            case "false":
            case "null": {
                writer.writePush(CONSTANT, 0);
                break;
            }
            case "this": {
                writer.writePush(POINTER, 0);
                break;
            }
            case ALLOC: {
                expectTokenValue(ALLOC); acceptToken();
                expectTokenValue("("); acceptToken();
                int nArgs = parseExpressionList();
                expectTokenValue(")"); // Does not accept here, cause after keyword constant acceptToken will call.
                writer.writeCall("eden_alloc", nArgs);
                break;
            }
            case FREE: {
                expectTokenValue(FREE); acceptToken();
                expectTokenValue("("); acceptToken();
                int nArgs = parseExpressionList();
                expectTokenValue(")"); // Does not accept here, cause after keyword constant acceptToken will call.
                writer.writeCall("eden_free", nArgs);
                break;
            }
            case "win": {
                expectTokenValue(WIN); acceptToken();
                expectTokenValue("("); acceptToken();
                String winCallName = expectAndGetStringConstant();
                if (expectTokenValue(false, ",")) { acceptToken(); }
                int nArgs = parseExpressionList();
                if (nArgs != 0) {
                    List<VM2Asm> currentCode = writer.getCode();
                    List<VM2Asm> tmpList = new ArrayList<>();
                    for (int i = currentCode.size() - 1, c = 0; c < nArgs; c++, i--) {
                        tmpList.add(currentCode.get(i));
                        currentCode.remove(i);
                    }
                    currentCode.addAll(tmpList);
                }
                writer.writeRawCall(winCallName);
                if (!externWinCallList.contains(winCallName)) { externWinCallList.add(winCallName); }
                expectTokenValue(")"); // Does not accept here, cause after keyword constant acceptToken will call.
            } break;
            default:
                printError(currentToken, "Unknown keyword");
        }
    }

    private void writeOp(char op, boolean isUnary) {
        switch (op) {
            case '+': writer.writeArithmetic(VMCommand.ADD); break;
            case '-': {
                if (isUnary) {
                    writer.writeArithmetic(VMCommand.NEG);
                } else {
                    writer.writeArithmetic(VMCommand.SUB);
                }
            } break;
            case '*': writer.writeArithmetic(VMCommand.MULTIPLY); break;
            case '/': writer.writeArithmetic(VMCommand.DIVIDE); break;
            case '=': writer.writeArithmetic(VMCommand.EQ, className+"_RT_" + getUniqueNumber()); break;
            case '<': writer.writeArithmetic(VMCommand.LT, className+"_RT_" + getUniqueNumber()); break;
            case '>': writer.writeArithmetic(VMCommand.GT, className+"_RT_" + getUniqueNumber()); break;
            case '&': writer.writeArithmetic(VMCommand.AND); break;
            case '|': writer.writeArithmetic(VMCommand.OR); break;
            case '~': writer.writeArithmetic(VMCommand.NOT); break;
            default: throw new IllegalArgumentException("Not implemented op: " + op);
        }
    }

    private void subroutineCall() {
        // Examples: Math.sqrt() | sqrt() | a.sqrt()
        String callName;
        int nArgs = 0;
        String identifierName = parseIdentifier();
        if (expectTokenValue(false, ".")) {
            acceptToken();
            String subName = parseIdentifier();
            try {
                // a.calculate()
                Variable variable = symbolTable.findVariable(identifierName);
                writer.writePush(variable.getKind(), variable.getIndex());
                nArgs++;
                callName = variable.getType() + "." + subName;
            } catch (RuntimeException ignored) {
                // Math.sqrt()
                callName = identifierName + "." + subName;
            }
        } else {
            // sqrt(); // We assume that in this case function sqrt belongs to the current class.
            callName = className + "." + identifierName;
        }
        expectTokenValue("("); acceptToken();
        nArgs += parseExpressionList();
        expectTokenValue(")"); acceptToken();
        writer.writeCall(callName, nArgs);
    }

    private int parseExpressionList() {
        if (expectTokenValue(false, ")")) {
            return 0;
        }
        int count = 1;
        parseExpression();
        while (!expectTokenValue(false, ")")) {
            if (expectTokenValue( ",")) {
                acceptToken();
                parseExpression();
                count++;
            } else {
                System.exit(1);
            }
        }
        return count;
    }

    private void parseIdentifiers(String kind, String type) {
        do {
            acceptToken();
            expectIdentifier(); //varName: identifier
            if ("var".equals(kind)) {
                symbolTable.defineSubroutine(currentToken.getValue(), type, kind);
            } else {
                symbolTable.defineClass(currentToken.getValue(), type, kind);
            }
            acceptToken();
        } while (expectTokenValue(false, ","));
    }

    private String expectAndGetStringConstant() {
        Token.TokenType type = currentToken.getType();
        assert type == Token.TokenType.STRING_CONSTANT;
        String strConstWinCallName = currentToken.getValue();
        acceptToken();
        return strConstWinCallName;
    }

    private void expectTypeValue(boolean isFunc) {
        String tokenValue = currentToken.getValue();
        boolean isPrimitiveType = isFunc ? primFuncTypes.contains(tokenValue) : primTypes.contains(tokenValue);
        boolean isClassName = Character.isUpperCase(tokenValue.charAt(0));
        if (!isPrimitiveType && !isClassName) {
            printError(currentToken, String.format("expected primitive type or Class, but found [%s]", tokenValue));
            errorCount++;
        }
    }

    private void expectIdentifier() {
        Token.TokenType type = currentToken.getType();
        if (!type.equals(Token.TokenType.IDENTIFIER)) {
            printError(currentToken, String.format("expected [%s], but found [%s `%s`]", Token.TokenType.IDENTIFIER, type, currentToken.getValue()));
            errorCount++;
        }
    }

    private boolean expectTokenValue(String... expected) {
        return expectTokenValue(true, expected);
    }

    private boolean expectTokenValue(boolean isRequired, String... expected) {
        String tokenValue = currentToken.getValue();
        boolean isContain = Arrays.asList(expected).contains(tokenValue);
        if (!isContain && isRequired) {
            printError(currentToken, String.format("expected %s, but found [%s]", Arrays.toString(expected), tokenValue));
            errorCount++;
        }
        return isContain;
    }

    private Token getNextToken() {
        if (index + 1 < tokens.size()) {
            return tokens.get(index + 1);
        }
        return tokens.get(index); // Might be a bug
    }

    private void acceptToken() {
        currentToken = tokens.get(++index);
    }

    private void printError(Token token, String message) {
        System.err.printf("ERROR: [%s:%d]: %s%n", token.getFilename(), token.getLine(), message);
    }

    private int getUniqueNumber() {
        return uniqueNumber++;
    }
}
