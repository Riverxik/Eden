package ru.riverx.eden;

import ru.riverx.eden.tokenizer.Tokenizer;
import ru.riverx.eden.parser.ParserEngine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class EdenCompiler {

    public static void main(String[] args) {
        if (args.length < 1) {
            printHelpAndExit();
        }

        String filename = args[0];
        if (!filename.endsWith(".eden")) {
            printHelpAndExit();
        }

        compileFile(filename);
    }

    private static void printHelpAndExit() {
        System.out.println("Please provide the valid .eden file to compile");
        System.out.println("Example: eden main.eden");
        System.exit(-1);
    }

    private static void compileFile(String filename) {
        String fileContent = readFile(filename);
        Tokenizer tokenizer = new Tokenizer(filename, fileContent);
        //tokenizer.printTokens();
        ParserEngine engine = new ParserEngine(tokenizer.getTokenList(), filename);
        writeToFile(filename, engine.getStatements());
        compileAsmFile(filename);
    }

    private static String readFile(String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            List<String> allLines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
            for (String line : allLines) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.printf("ERROR: can't read file %s.%n%s", filename, e.getMessage());
        }

        return sb.toString();
    }

    private static void writeToFile(String filename, List<String> codeLines) {
        try {
            String correctName = filename.split("[.]")[0] + ".asm";
            Files.write(Paths.get(correctName), codeLines, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.printf("ERROR: can't write file %s to disk.%n%s", filename, e.getMessage());
        }
    }

    private static void compileAsmFile(String sourceName) {
        try {
            String name = sourceName.split("[.]")[0];
            System.out.printf("[INFO] Compiling %s...%n", sourceName);
            String cmdNasm = String.format("nasm -f win32 %s.asm", name);
            String cmdGoLink = String.format("golink /entry:Start /console kernel32.dll user32.dll %s.obj", name);
            run(cmdNasm);
            run(cmdGoLink);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String run(String execCmd) throws IOException, InterruptedException {
        System.out.println("[CMD] " + execCmd);
        Process process = Runtime.getRuntime().exec("cmd.exe /c " + execCmd);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Error while execute command: " + execCmd + "\n" +
                    readInputStream(process.getErrorStream().available() != 0 ? process.getErrorStream() : process.getInputStream()));
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
}
