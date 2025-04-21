package ru.riverx.eden.parser;

import ru.riverx.eden.exceptions.NoMainFuncException;
import ru.riverx.eden.parser.high.SymbolKind;
import ru.riverx.eden.parser.high.SymbolTable;
import ru.riverx.eden.parser.high.Variable;
import ru.riverx.eden.parser.middle.OpCall;
import ru.riverx.eden.parser.middle.OpFunctionInfo;
import ru.riverx.eden.parser.middle.VM2Asm;
import ru.riverx.eden.parser.middle.VMCommand;
import ru.riverx.eden.tokenizer.Token;

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

    private final List<String> primTypes = Arrays.asList("int", "char", "boolean");
    private final List<String> primFuncTypes = Arrays.asList("int", "char", "boolean", "void");
    private final List<Token> tokens;
    private final String className;
    private String mainFuncName;
    private int index;
    private Token currentToken;
    private boolean wasReturn = false;
    private boolean isNeedByteShift = false;
    private int errorCount = 0;
    private final long compilationTime;
    private final SymbolTable symbolTable;
    private final VMWriter writer;
    private int uniqueNumber = 0;

    public ParserEngine(List<Token> tokens, String filename) {
        this.tokens = tokens;
        this.className = calculateClassName(filename);
        this.index = 0;
        this.currentToken = tokens.get(index);
        this.symbolTable = new SymbolTable();
        this.writer = new VMWriter();
        long start = System.currentTimeMillis();
        parseClass();
        this.compilationTime = System.currentTimeMillis() - start;
        validateMain();
        printTranslateInfo();
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
        asmCode.add("extern ExitProcess\n");
        asmCode.add("extern GetProcessHeap\n");
        asmCode.add("extern HeapAlloc\n");
        asmCode.add("extern HeapFree\n");
        asmCode.add("global Start\n");
        asmCode.add("section .data");
        asmCode.add("section .bss");
        asmCode.add("\teden_sp resd 1");
        asmCode.add("\teden_lcl resd 1");
        asmCode.add("\teden_arg resd 1");
        asmCode.add("\teden_this resd 1");
        asmCode.add("\teden_that resd 1");
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
        asmCode.add("; THINK DOWN HERE");
        asmCode.add("\tmov dword eax, [eden_arg]");
        asmCode.add("\tpush    dword [eax]");        // ; size
        asmCode.add("\tpush    0");                  // ; flags (0 = default)
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

    private String calculateClassName(String filename) {
        if (filename.contains("/")) {
            filename = filename.split("/")[1];
        }
        return filename.split("[.]")[0];
    }

    private void printTranslateInfo() {
        if (errorCount > 0) {
            System.err.printf("[%s] Translating failed with %d errors!%n", className, errorCount);
            System.exit(1);
        } else {
            System.out.printf("[%s] Translation done in %d ms%n", className, compilationTime);
        }
    }

    private void parseClass() {
        expectTokenValue("class"); acceptToken();
        expectTokenValue(className);
        validateClassName();
        parseClassName();
        expectTokenValue("{"); acceptToken();
        parseClassVarDec();
        parseSubroutineDec();
        expectTokenValue("}");
        symbolTable.resetKindCountClass();
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
            String type = parseType(); // int | char | boolean | className
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
            writer.writePush(CONSTANT, symbolTable.varCount(SymbolKind.FIELD));
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
            isNeedByteShift = true;
            parseExpression();
            isNeedByteShift = false;
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
        parseTerm();
        if (expectTokenValue(false, "+", "-", "*", "/", "&", "|", "<", ">", "=")) {
            expectTokenValue("+", "-", "*", "/", "&", "|", "<", ">", "=");
            char op = currentToken.getValue().charAt(0); acceptToken();
            parseExpression();
            writeOp(op, false);
        }
    }

    private void parseTerm() {
        switch (currentToken.getType()) {
            case INTEGER_CONSTANT: {
                String numConst = currentToken.getValue();
                acceptToken();
                if (isNeedByteShift) {
                    writer.writeConstant(numConst + "*4");
                } else {
                    writer.writeConstant(numConst);
                }
                break;
            }
            case STRING_CONSTANT: {
                String strConst = currentToken.getValue();
                acceptToken();
                parseString(strConst, strConst.length());
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
                    isNeedByteShift = true;
                    parseExpression();
                    isNeedByteShift = false;
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

    private void parseString(String inputStr, int length) {
        writer.writePush(CONSTANT, length);
        writer.writeCall("String.new", 1);
        for (char c : inputStr.toCharArray()) {
            writer.writePush(CONSTANT, c);
            writer.writeCall("String.appendChar", 2); // 0 is String base address, 1 is char
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
            case '=': writer.writeArithmetic(VMCommand.EQ); break;
            case '<': writer.writeArithmetic(VMCommand.LT); break;
            case '>': writer.writeArithmetic(VMCommand.GT); break;
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
