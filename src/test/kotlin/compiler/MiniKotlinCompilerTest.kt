package org.example.compiler

import MiniKotlinLexer
import MiniKotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MiniKotlinCompilerTest {

    @TempDir
    lateinit var tempDir: Path

    private fun parseString(source: String): MiniKotlinParser.ProgramContext {
        val input = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)
        return parser.program()
    }

    private fun parseFile(path: Path): MiniKotlinParser.ProgramContext {
        val input = CharStreams.fromPath(path)
        val lexer = MiniKotlinLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)
        return parser.program()
    }

    private fun resolveStdlibPath(): Path? {
        val devPath = Paths.get("build", "stdlib")
        if (devPath.toFile().exists()) {
            val stdlibJar = devPath.toFile().listFiles()
                ?.firstOrNull { it.name.startsWith("stdlib") && it.name.endsWith(".jar") }
            if (stdlibJar != null) return stdlibJar.toPath()
        }
        return null
    }

    private fun compileAndRun(sampleName: String): String {
        val path = Paths.get("samples/$sampleName")
        val program = parseFile(path)
        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(program)
        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)
        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)
        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)
        return executionResult.stdout
    }

    @Test
    fun `compile example_mini outputs 120 and 15`() {
        val examplePath = Paths.get("samples/example.mini")
        val program = parseFile(examplePath)

        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(program)

        val javaFile = tempDir.resolve("MiniProgram.java")
        Files.writeString(javaFile, javaCode)

        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()
        val (compilationResult, executionResult) = javaCompiler.compileAndExecute(javaFile, stdlibPath)

        assertIs<CompilationResult.Success>(compilationResult)
        assertIs<ExecutionResult.Success>(executionResult)

        val output = executionResult.stdout
        assertTrue(output.contains("120"), "Expected output to contain factorial result 120, but got: $output")
        assertTrue(output.contains("15"), "Expected output to contain arithmetic result 15, but got: $output")
    }

    // -------------------------------------------------------------------------
    // arithmetic.mini
    // Covers: +, -, *, /, %, operator precedence, calls on both sides of an op
    // Expected output lines: 7, 14, 4, 2, 30
    // -------------------------------------------------------------------------

    @Test
    fun `arithmetic - basic operators and precedence`() {
        val output = compileAndRun("arithmetic.mini")
        assertTrue(output.contains("7"),  "Expected 3+4=7, got: $output")
        assertTrue(output.contains("14"), "Expected 2+3*4=14, got: $output")
        assertTrue(output.contains("4"),  "Expected 20/4-1=4, got: $output")
        assertTrue(output.contains("2"),  "Expected 17%5=2, got: $output")
        assertTrue(output.contains("30"), "Expected multiply(add(2,3),multiply(2,3))=30, got: $output")
    }

    // -------------------------------------------------------------------------
    // booleans.mini
    // Covers: >, ==, !=, !, &&, ||, calls inside boolean expressions
    // Expected output lines: true, true, true, false, true, true, true
    // -------------------------------------------------------------------------

    @Test
    fun `booleans - comparisons, logical operators, calls in conditions`() {
        val output = compileAndRun("booleans.mini")
        // We check specific expected values by counting occurrences carefully
        assertTrue(output.contains("true"),  "Expected at least one true, got: $output")
        assertTrue(output.contains("false"), "Expected at least one false, got: $output")

        val lines = output.trim().lines()
        assertTrue(lines[0] == "true",  "5>3 should be true, got: ${lines[0]}")
        assertTrue(lines[1] == "true",  "2+2==4 should be true, got: ${lines[1]}")
        assertTrue(lines[2] == "true",  "!false should be true, got: ${lines[2]}")
        assertTrue(lines[3] == "false", "5>3 && 2>10 should be false, got: ${lines[3]}")
        assertTrue(lines[4] == "true",  "2>10 || 5>3 should be true, got: ${lines[4]}")
        assertTrue(lines[5] == "true",  "isPositive(5)&&isEven(4) should be true, got: ${lines[5]}")
        assertTrue(lines[6] == "true",  "5!=3 should be true, got: ${lines[6]}")
    }

    // -------------------------------------------------------------------------
    // while_loop.mini
    // Covers: while statement, variable assignment (reassignment)
    // Expected output lines: 15, 120, 3
    // -------------------------------------------------------------------------

    @Test
    fun `while loop - sum, product, and boolean flag`() {
        val output = compileAndRun("while_loop.mini")
        val lines = output.trim().lines()
        assertTrue(lines[0] == "15",  "Sum 1..5 should be 15, got: ${lines[0]}")
        assertTrue(lines[1] == "120", "5! should be 120, got: ${lines[1]}")
        assertTrue(lines[2] == "3",   "Count should be 3, got: ${lines[2]}")
    }

    // -------------------------------------------------------------------------
    // if_else.mini
    // Covers: if/else, nested if/else, function call inside condition
    // Expected output lines: 7, 8, 9, -1, 0, 1, 1
    // -------------------------------------------------------------------------

    @Test
    fun `if-else - branches, nested ifs, call in condition`() {
        val output = compileAndRun("if_else.mini")
        val lines = output.trim().lines()
        assertTrue(lines[0] == "7",  "abs(-7)=7, got: ${lines[0]}")
        assertTrue(lines[1] == "8",  "max(3,8)=8, got: ${lines[1]}")
        assertTrue(lines[2] == "9",  "max(abs(-3),abs(-9))=9, got: ${lines[2]}")
        assertTrue(lines[3] == "-1", "classify(-5)=-1, got: ${lines[3]}")
        assertTrue(lines[4] == "0",  "classify(0)=0, got: ${lines[4]}")
        assertTrue(lines[5] == "1",  "classify(5)=1, got: ${lines[5]}")
        assertTrue(lines[6] == "1",  "if(max(2,3)==3) should print 1, got: ${lines[6]}")
    }

    // -------------------------------------------------------------------------
    // strings.mini
    // Covers: String type, string literals, println with strings
    // Expected output lines: start, world, hello, 1, goodbye, 0, end
    // -------------------------------------------------------------------------

    @Test
    fun `strings - string variables and println`() {
        val output = compileAndRun("strings.mini")
        val lines = output.trim().lines()
        assertTrue(lines[0] == "start",   "Expected 'start', got: ${lines[0]}")
        assertTrue(lines[1] == "world",   "Expected 'world', got: ${lines[1]}")
        assertTrue(lines[2] == "hello",   "Expected 'hello', got: ${lines[2]}")
        assertTrue(lines[3] == "1",       "greet(true) returns 1, got: ${lines[3]}")
        assertTrue(lines[4] == "goodbye", "Expected 'goodbye', got: ${lines[4]}")
        assertTrue(lines[5] == "0",       "greet(false) returns 0, got: ${lines[5]}")
        assertTrue(lines[6] == "end",     "Expected 'end', got: ${lines[6]}")
    }

    // -------------------------------------------------------------------------
    // mutual_recursion.mini
    // Covers: mutual recursion, fib, nested call as argument
    // Expected output lines: true, true, false, 0, 1, 13, 2
    // -------------------------------------------------------------------------

    @Test
    fun `mutual recursion - isEven, isOdd, fibonacci`() {
        val output = compileAndRun("mutual_recursion.mini")
        val lines = output.trim().lines()
        assertTrue(lines[0] == "true",  "isEven(4)=true, got: ${lines[0]}")
        assertTrue(lines[1] == "true",  "isOdd(7)=true, got: ${lines[1]}")
        assertTrue(lines[2] == "false", "isEven(3)=false, got: ${lines[2]}")
        assertTrue(lines[3] == "0",     "fib(0)=0, got: ${lines[3]}")
        assertTrue(lines[4] == "1",     "fib(1)=1, got: ${lines[4]}")
        assertTrue(lines[5] == "13",    "fib(7)=13, got: ${lines[5]}")
        assertTrue(lines[6] == "2",     "fib(fib(4))=fib(3)=2, got: ${lines[6]}")
    }

    // -------------------------------------------------------------------------
    // complex_expressions.mini
    // Covers: calls on both sides of binary ops, calls as arguments to calls,
    //         deeply nested calls, call result in comparison
    // Expected output lines: 14, 30, 11, 8, true, true, 15
    // -------------------------------------------------------------------------

    @Test
    fun `complex expressions - calls in binary ops and nested args`() {
        val output = compileAndRun("complex_expressions.mini")
        val lines = output.trim().lines()
        assertTrue(lines[0] == "14",   "double(3)+double(4)=14, got: ${lines[0]}")
        assertTrue(lines[1] == "30",   "double(5)*3=30, got: ${lines[1]}")
        assertTrue(lines[2] == "11",   "add(double(3),inc(4))=11, got: ${lines[2]}")
        assertTrue(lines[3] == "8",    "double(double(double(1)))=8, got: ${lines[3]}")
        assertTrue(lines[4] == "true", "double(5)==10 is true, got: ${lines[4]}")
        assertTrue(lines[5] == "true", "double(3)<double(4) is true, got: ${lines[5]}")
        assertTrue(lines[6] == "15",   "inc(double(3))+double(inc(3))=15, got: ${lines[6]}")
    }
}
