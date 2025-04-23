# Eden Programming Language

> [!WARNING]
> Compiler is not fully implemented yet.

**WARNING! Eden language is still in development stage.**

Eden is object-oriented programming language written in Java. 
Main goal for this project is make a simple oop self-hosted language.
Just because it's fun, and I can (can I?).

Eden is planned to be:
- [x] Compiled
- [x] Turing-complete
- [ ] Self-hosted (Java is used only as an initial bootstrap, once the language is mature enough I'm going to rewrite it in itself)

The stuff required for be self-hosted (In my opinion):
- [ ] Support reading from file
- [ ] Support writing to file  
- [ ] Support executing external programs
- [x] Class declarations
- [x] Function declarations
- [x] Method declarations
- [x] Constructors
- [x] Memory management (alloc, free)
- [x] Variable declarations
- [x] Type: Integer
- [x] Type: Boolean
- [x] Type: Char
- [x] Notion of Array
- [ ] List structure
- [ ] Stack structure
- [x] Conditional statements (if-else)
- [x] Loop statement (while)
---

## Quick start
You have to have installed [nasm](https://www.nasm.us/) and [golink](https://www.godevtool.com/) on your machine.

You can compile your program to native executable:
```shell
$ java -jar Eden.jar -s Hello.eden
```

### How exactly compilation works?
Compilation process made though [nasm](https://www.nasm.us/) and [golink](https://www.godevtool.com/) in three steps:
1. Lexing, parsing `.eden` and translating to `.asm` file
2. Compile `.asm` file to `.obj`
3. Linking `.obj` file to native executable `.exe`
```shell
$ nasm -f win32 Test.asm
$ golink /entry:Start /console kernel32.dll user32.dll Test.obj 
$ file Test
Test: PE32 executable (console) Intel 80386, for MS Windows
```

I use 32 bit for now, for no reason, just because I can. Maybe I will change it to 64 bit later. Will see.

## Language specification

### Syntax elements

#### White space / comments:
```
// Comment to end of line
/* Comment until closing */
/** API documentation comment */
```

#### Keywords:
* Program components (use, class, constructor, method, func)
* Primitive types (int, bool, char, void)
* Variable declarations (var, static, field)
* Statements (let, do, if, else, while, return)
* Constant values (true, false, null)
* Object reference (this)

#### Symbols:
* `()` - used for grouping arithmetic expressions and for enclosing parameter-lists and argument-lists
* `[]` - used for array indexing
* `{}` - used for grouping program units and statements
* `,` - variable list separator
* `;` - statement terminator
* `=` - assignment and comparison operator
* `.` - class membership
* `+`, `-`, `*`, `/`, `&`, `|`, `~`, `<`, `>` - operators

#### Constants:
* Integer constants must be positive and in standard decimal notation, e.g. 1954
* String constants are enclosed within two quote `"` characters and may contain any character except `newline` or `"`
* Boolean constants can be `true` or `false`
* The `null` constant signifies a null reference

#### Identifiers:
* Identifiers are composed of arbitrarily long sequences of letters `(A-Z,a-z)`, digits `(0-9)` and `_`
* The first character must be a letter or `_`
* Eden language is case-sensitive

### Classes

---

* Class is basic compilation unit
* Each class `Foo` is stored in a separate `Foo.eden` file
* The class name's first character must be an uppercase letter

General class structure
```shell
/** Foo.eden file */
use import declarations
class Foo {
  field variable declarations
  static variable declarations
  subroutine declarations
}
```

### Subroutines

---

```shell
constructor | method | func type subroutineName (parameter-list) {
  local variable declarations
  statements
}
```
* Method and function type can be either `void`, a primitive data type, or a class name
* Each subroutine must return a value (implicitly for now)

#### Constructors:
* 0, 1, or more in a class
* A common name: `new`
* The constructor's type must be the name of the constructor's class
* The constructor must return a reference to an object of the class type


### Variables

---

* static variables - class level variables
* field variables - object properties
* local variables - used by subroutines, for local computations
* parameter variables - used to pass values to subroutines

### Statements

---

* let - `let varName = expression;` or `let varName[expr1] = expr2;`
* if - `if (expr) { statementsTrue } else { statementsFalse }`
* while - `while (exprTrue) { statements }`
* do - `do functionOrMethodCall();`
* return - `return expr;` or `return;`

### Expressions

---

* `integer number`
* `variable`
* `<exp> <op> <exp>`
* `<op> <exp>`
* `win-call`

### WindowsAPI Calls (win-call)

---

Performs a Windows api call

Syntax:
```
result = win("NameOfWinCall", params...);
```

Examples:

Get a console handler and print `Hello world!` through `WriteFile` win-call (kernel32.dll). [More info](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-writefile)

It's a little bit ugly for now, but it works, so who cares :> 
```eden
func void printHelloWorld() {
    var Array data;
    var int stdOutCode, stdOutHandle, writtenBuff, sizeToWrite, resultCode;
    let data = alloc(52); // 4 * 13
    let data[0] = 72;   // 'H'
    let data[1] = 101;  // 'e'
    let data[2] = 108;  // 'l'
    let data[3] = 108;  // 'l'
    let data[4] = 111;  // 'o'
    let data[5] = 32;   // ' '
    let data[6] = 87;   // 'W'
    let data[7] = 111;  // 'o'
    let data[8] = 114;  // 'r'
    let data[9] = 108;  // 'l'
    let data[10] = 100; // 'd'
    let data[11] = 33;  // '!'
    let data[12] = 10;  // '\n'
    let writtenBuff = alloc(1);
    let sizeToWrite = 52;
    let stdOutCode = -11;
    let stdOutHandle = win("GetStdHandle", stdOutCode);
    let resultCode = win("WriteFile", stdOutHandle, data, sizeToWrite, writtenBuff, 0);
    return;
}
```

### Importing external sources

---

You can import already written Eden class to your program.

Syntax:
```
use "pathToEdenfile";
```

Examples:
```shell
use "std\\Math.eden";
use "Point.eden";
use "/home/eden/std/Math.eden";

class YourClass { ... }
```

### Variable definition and assignment

---

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