# Eden Programming Language

**WARNING! Eden language is still in development stage.**

Eden is object-oriented programming language written in Java. 
Main goal for this project is make a simple oop self-hosted language.
Just because it's fun, and I can (can I?).

Eden is planned to be:
- [x] Compiled
- [ ] Turing-complete
- [ ] Self-hosted (Java is used only as an initial bootstrap, once the language is mature enough I'm going to rewrite it in itself)

The stuff required for be self-hosted (In my opinion):
- [ ] Support reading from file
- [ ] Support writing to file  
- [ ] Support executing external programs
- [ ] Class declarations
- [ ] Method declarations
- [x] Variable declarations
- [ ] Constructors
- [x] Type: Integer
- [x] Type: Boolean
- [x] Type: Char
- [x] Type: String
- [ ] List structure
- [ ] Stack structure
- [x] Conditional statements (if-else)
- [x] Loop statement (probably while)
---

## Quick start
Compiler use interpreter mode by default for now. To specify source file use `-s` flag:
```shell
$ java -jar Eden.jar -s hello.eden
```

Also, you can compile your program to native executable. Use `-c` to compile.
```shell
$ java -jar Eden.jar -c -s hello.eden
```

You can use `-r` to execute automatically after successful compilation.
```shell
$ java -jar Eden.jar -c -r -s hello.eden
```

### How exactly compilation works:
Compilation process made though [nasm](https://www.nasm.us/) and [golink](https://www.godevtool.com/) in three steps:
1. Lexing, parsing `.eden` and making `.asm` file
2. Compile `.asm` file to `.obj`
3. Linking `.obj` file to native executable `.exe`
```shell
$ nasm -f win32 Test.asm
$ golink /entry:Start /console kernel32.dll user32.dll Test.obj 
$ file Test
Test: PE32 executable (console) Intel 80386, for MS Windows
```

I use 32 bit for now, for no reason, just because I can. Maybe I will change it to 64 bit later. Will see.

## Statements

#### Print statement (~)

Prints some expression on the screen.

Syntax:
```
~ expr ;
```

Examples:
```
~ 2 + 2 * 2;
~ 1 + 2;
~ -1;
```
---

#### If-else statements
Conditional statements are used to perform different actions based on different conditions.

Syntax:
```
if <expr> { 
    // statements when expression returns 1; 
} else {
    // statements when expression returns 0;
}
```

Examples:
```
// Prints 456
bool a = false;
if (a) {
    ~ 123;
} else {
    ~ 456;
}

// Not equal to (like != in java)
int a = expr;
if (expr = 0) {
    // statements
    ...
}
```
---

### WindowsAPI Calls (wincall)

Performs a windows api call

Syntax:
```
result = wincall("NameOfWinCall", params...);
```

Examples:

Get a console handler and print `Hello world!` through `WriteFile` wincall (kernel32.dll). [More info](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-writefile)
```
int stdOutHandle;
int writtnBuf;
int resultCode;
stdOutHandle = wincall("GetStdHandle", -11); // Getting the stdOut handler
resultCode = wincall("WriteFile", 0, writtnBuf, 14, "Hello world!\n", stdOutHandle); // Performs a wincall
~ resultCode; // Check that wincall was successful. Returns non zero value if it was.
```

### Variable definition and assignment

Defines a variable with different types and assign value to it.

Syntax:
```
<type> <variableName>[[= <expression>], <anotherVariableName>];
```

Examples:
```
int a;
int a, b;
int a = 1;
int a = 1, b = 2;
int a = 1, b = 2, c = a + b; // c = 3
bool isX = true;
bool isY = 2 = 2; // isY = true
```
---

## Supported Operations

#### Addition
```
~ 1 + 2;
```

#### Subtraction
```
~ 4 - 2;
```

#### Multiplication
```
~ 3 * 2;
```

#### Division
```
~ 4 / 2;
```

#### Nested expressions
```
~ 2 * (3 + 4);
~ 2 * (3 * (4 + 6 / 6);
```

#### Logical equal
```
~ 4 = 2;            // 0
~ 3 + 6 = 3 * 3;    // 1
```

#### Logical more
```
~ 4 > 2;            // 1
~ 3 > 5;            // 0
```

#### Logical less
```
~ 4 < 2;            // 0
~ 3 < 5;            // 1
```