# Eden Programming Language

**WARNING! Eden language is still in development stage.**

Eden is object-oriented programming language written in Java. 
Main goal for this project is make a simple oop self-hosted language.
Just because it's fun, and I can (can I?).

Eden is planned to be:
- [ ] Compiled
- [ ] Turing-complete
- [ ] Self-hosted (Java is used only as an initial bootstrap, once the language is mature enough I'm going to rewrite it in itself)

The stuff required for be self-hosted (In my opinion):
- [ ] Support reading from file
- [ ] Support writing to file  
- [ ] Support executing external programs
- [ ] Class declarations
- [ ] Method declarations
- [ ] Variable declarations
- [ ] Constructors
- [x] Type: Integer
- [x] Type: Boolean
- [ ] Type: Char
- [ ] Type: String
- [ ] List structure
- [ ] Stack structure
- [ ] Conditional statements (if-else)
- [ ] Loop statement (probably while)

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

#### Variable definition and assignment

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