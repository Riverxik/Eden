import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class EdenTest {
    private static boolean isCaptureMode = false;
    private static boolean isRebuild = false;
    private static boolean isFullTesting = false;
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) { printHelp(); return; }
        String sourceName = parseArgs(args);
        if (isCaptureMode) {
            captureTest(sourceName);
        } else if (isFullTesting) {
            File folder = new File("tests");
            int errorCount = 0;
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                String filePath = file.getPath();
                if (filePath.endsWith(".eden")) {
                    String expectedOutput = readTestOutput(file.getPath() + ".output");
                    errorCount += checkTest(file.getPath(), expectedOutput);
                }
            }
            if (errorCount == 0) {
                System.out.println("[INFO] All tests successfully passed :>");
            } else {
                System.out.println("[WARN] Some tests failed: " + errorCount);
            }
        } else {
            String expectedOutput = readTestOutput(sourceName + ".output");
            checkTest(sourceName, expectedOutput);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: test [PARAMS] [inputFile]");
        System.out.println("Params:");
        System.out.println("\t-s <testFile>\t\t- check particular test file");
        System.out.println("\t-c -s <testFile>\t- capture output for particular test file");
        System.out.println("\t-r\t\t\t\t\t- rebuild Eden compiler before capture output");
        System.out.println("\t-f\t\t\t\t\t- test all test files in the `tests` folder");
        System.out.println("Examples:");
        System.out.println("> test -s tests/srcTestName.eden");
        System.out.println("> test -c -s tests/srcTestName.eden -> will produce tests/srcTestName.eden.output");
        System.out.println("> test -r -c -s tests/srcTestName.eden -> rebuild and produce tests/srcTestName.eden.output");
        System.out.println("> test -f");
    }

    private static String parseArgs(String[] args) {
        int i = 0;
        String sourceName = "";
        while (i < args.length) {
            if (args[i].equalsIgnoreCase("-s")) {
                if (i+1 < args.length) {
                    sourceName = args[i+1];
                } else {
                    System.out.println("You must provide path to the source test file");
                    System.exit(1);
                }
            }
            if (args[i].equalsIgnoreCase("-c")) { isCaptureMode = true; }
            if (args[i].equals("-r")) { isRebuild = true; }
            if (args[i].equals("-f")) { isFullTesting = true; }
            i++;
        }
        if (!Files.exists(Paths.get(sourceName))) {
            System.err.println("File does not exists: " + sourceName);
            System.exit(1);
        }
        return sourceName;
    }

    private static void captureTest(String sourceName) throws IOException, InterruptedException {
        if (isRebuild) {
            System.out.println("[INFO] Compiling latest version of Eden compiler...");
            executeCommandAndReturnStringOutput("mvn clean compile package");
            System.out.println("[INFO] Copying jar to project folder...");
            executeCommandAndReturnStringOutput("copy /Y /B target\\Eden-*.jar /B Eden.jar");
        }
        String cmdInterpreter = String.format("java -jar Eden.jar -s %s", sourceName);
        System.out.println("[INFO] Executing interpreter with: " + sourceName);
        System.out.println("[CMD] " + cmdInterpreter);
        String output = executeCommandAndReturnStringOutput(cmdInterpreter);
        File outputFile = new File(sourceName + ".output");
        FileWriter fw = new FileWriter(outputFile);
        fw.write(output);
        fw.flush();
        fw.close();
        System.out.println(output);
        System.out.println("[INFO] Test successfully captured: " + sourceName + ".output");
    }

    private static String readTestOutput(String sourceName) throws IOException {
        File testFile = new File(sourceName);
        if (!testFile.exists()) {
            System.err.println("[ERROR] Can't read supposed test output, cause it doesn't exist: " + sourceName);
            System.err.println("Please run test -c -s " + sourceName);
        }
        FileReader fr = new FileReader(testFile);
        StringBuilder sb = new StringBuilder();
        while (fr.ready()) {
            sb.append(((char)fr.read()));
        }
        return sb.toString();
    }

    private static int checkTest(String sourceName, String expectedOutput) throws IOException, InterruptedException {
        String interpreterOutput = executeCommandAndReturnStringOutput("java -jar Eden.jar -s " + sourceName);
        executeCommandAndReturnStringOutput("java -jar Eden.jar -c -s " + sourceName);
        String compilerOutput = executeCommandAndReturnStringOutput(sourceName.replaceAll("/", "\\\\").split("[.]")[0]+".exe");
        if (!interpreterOutput.equals(expectedOutput)) {
            System.out.println("[ERROR] Interpreter output is different from expected for: " + sourceName);
            System.out.println("--Interpreter--");
            System.out.println(interpreterOutput);
            System.out.println("--Expected--");
            System.out.println(expectedOutput);
            return 1;
        }
        if (!compilerOutput.equals(expectedOutput)) {
            System.out.println("[ERROR] Compiler output is different from expected for: " + sourceName);
            System.out.println("--Compiler--");
            System.out.println(compilerOutput);
            System.out.println("--Expected--");
            System.out.println(expectedOutput);
            return 1;
        }
        System.out.println("[INFO] Test successfully passed: " + sourceName);
        return 0;
    }

    private static String executeCommandAndReturnStringOutput(String execCmd) throws IOException, InterruptedException {
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
}
