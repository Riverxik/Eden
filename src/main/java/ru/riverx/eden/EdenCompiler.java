package ru.riverx.eden;

import ru.riverx.eden.tokenizer.Tokenizer;

import java.io.IOException;
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
        tokenizer.printTokens();
//        CompilationEngine engine = new CompilationEngine(tokenizer.getTokenList(), filename);
//        writeToFile(filename, engine.getStatements());
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
            String correctName = filename.split("[.]")[0] + ".vm";
            Files.write(Paths.get(correctName), codeLines, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.printf("ERROR: can't write file %s to disk.%n%s", filename, e.getMessage());
        }
    }
}
