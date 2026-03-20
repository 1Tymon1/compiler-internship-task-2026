package org.example.compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {

    // Helpers to never overlap argument names
    private var argCounter = 0
    private fun newArg() = "arg${argCounter++}"

    // Helper field to count opened curly brackets
    private var openedBrackets: Array<Int> = emptyArray()

    fun compile(program: MiniKotlinParser.ProgramContext, className: String = "MiniProgram"): String {

        val functions = program.functionDeclaration().joinToString("\n\n")
        {
            convertFunction(it)
        }

        val result = """
            public class $className {
                $functions
            }
        """.trimIndent()

        return result
    }

    fun convertFunction(function: MiniKotlinParser.FunctionDeclarationContext): String {
        val name = function.IDENTIFIER().text
        val returnType = convertType(function.type())
        var arguments = ""

        if (function.parameterList() != null) {
            arguments += convertParameters(function.parameterList()) + ", Continuation<$returnType> __continuation"
        }

        if (name == "main")
        {
            arguments += "String[] args"
        }

        val body = convertBody(function.block(), 0)

        return """public static void $name($arguments){
                $body
            }
        """.trimMargin()
    }

    fun convertType(type: MiniKotlinParser.TypeContext): String {
        if (type.UNIT_TYPE() != null)
        {
            return "void"
        }
        else if (type.INT_TYPE() != null)
        {
            return "Integer"
        }
        else if (type.BOOLEAN_TYPE() != null)
        {
            return "Boolean"
        }
        else if (type.STRING_TYPE() != null)
        {
            return "String"
        }

        else error("unrecognized type: $type")
    }

    fun convertParameters(parameters: MiniKotlinParser.ParameterListContext): String {
        val result = parameters.parameter().joinToString(", "){
            "${convertType(it.type())} ${it.IDENTIFIER().text}"
        }

        return result
    }

    fun convertBody(body: MiniKotlinParser.BlockContext, depth: Int): String {
        while (openedBrackets.size <= depth)
        {
            openedBrackets += arrayOf(0)
        }
        var result = body.statement().joinToString("\n"){
            convertStatement(it, depth)
        }

        while (openedBrackets[depth] > 0) {
            openedBrackets[depth] = openedBrackets[depth] - 1
            result += "\n});"
        }

        return result
    }

    fun convertStatement(statement: MiniKotlinParser.StatementContext, depth: Int) : String
    {
        if (statement.variableDeclaration() != null)
        {
            return convertVariableDeclaration(statement.variableDeclaration(), depth)
        }
        if (statement.variableAssignment() != null)
        {
            return convertVariableAssigment(statement.variableAssignment(), depth)
        }
        if (statement.returnStatement() != null)
        {
            return convertReturn(statement.returnStatement(), depth)
        }
        if (statement.expression() != null)
        {
            return convertExpressionStatement(statement.expression(), depth)
        }
        if (statement.ifStatement() != null)
        {
            return convertIfStatement(statement.ifStatement(), depth)
        }
        if (statement.whileStatement() != null)
        {
            return convertWhileStatement(statement.whileStatement(), depth)
        }

        return ";"
    }

    fun convertWhileStatement(condition: MiniKotlinParser.WhileStatementContext, depth: Int) : String {
        val body = convertBody(condition.block(), depth + 1)
        return convertComplexExpression(condition.expression(), depth){ conditionExpression ->
            "while($conditionExpression){\n$body\n}"
        }

    }

    fun convertIfStatement(condition: MiniKotlinParser.IfStatementContext, depth: Int) : String {
        val ifBody = convertBody(condition.block(0), depth + 1)
        var elseBody: String = ""

        if (condition.ELSE() != null)
            elseBody = """ else {
                ${convertBody(condition.block(1), depth + 1)}
            }""".trimIndent()

        return convertComplexExpression(condition.expression(), depth){ conditionExpression ->
            "if($conditionExpression){\n$ifBody\n}\n$elseBody"
        }
    }

    fun convertVariableDeclaration(variableDeclaration: MiniKotlinParser.VariableDeclarationContext, depth: Int): String {
        val type = convertType(variableDeclaration.type())
        val name = variableDeclaration.IDENTIFIER().text
        val expression = variableDeclaration.expression()

        return convertComplexExpression(expression, depth) { finalExpression ->
            "$type $name = $finalExpression;"
        }
    }

    fun convertVariableAssigment(variableAssignment: MiniKotlinParser.VariableAssignmentContext, depth: Int): String {
        val name = variableAssignment.IDENTIFIER().text
        val expression = variableAssignment.expression()

        return convertComplexExpression(expression, depth) { finalExpression ->
            "$name = $finalExpression;"
        }
    }

    fun convertReturn(returnStatement: MiniKotlinParser.ReturnStatementContext, depth: Int): String {
        if (returnStatement.expression() == null)
        {
            return """__continuation.accept(null);
                return;
            """.trimMargin()
        }

        if (containsFunctionCall(returnStatement.expression()))
        {
            return convertComplexExpression(returnStatement.expression(), depth) { finalExpression ->
                "__continuation.accept($finalExpression);\nreturn;"
            }
        }

        return """__continuation.accept(${convertExpressionStatement(returnStatement.expression(), depth)});
            return;
        """.trimMargin()
    }


    fun convertExpressionStatement(expression: MiniKotlinParser.ExpressionContext, depth: Int): String {
        if (containsFunctionCall(expression))
        {
            return convertComplexExpression(expression, depth) {""}

        }
        else
        {
            return convertSimpleExpression(expression, depth)
        }
    }

    fun containsFunctionCall(expression: MiniKotlinParser.ExpressionContext) : Boolean {
        if (expression is MiniKotlinParser.FunctionCallExprContext) return true
        if (expression is MiniKotlinParser.PrimaryExprContext) return false
        if (expression is MiniKotlinParser.NotExprContext) return containsFunctionCall(expression.expression())
        if (expression is MiniKotlinParser.AddSubExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
        if (expression is MiniKotlinParser.MulDivExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
        if (expression is MiniKotlinParser.ComparisonExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
        if (expression is MiniKotlinParser.EqualityExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
        if (expression is MiniKotlinParser.AndExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
        if (expression is MiniKotlinParser.OrExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))

        return false
    }

    fun convertComplexExpression(expression: MiniKotlinParser.ExpressionContext, depth: Int, continuation: (String) -> String) : String {
        val functionCalls = collectFunctionCalls(expression)
        if (functionCalls.isEmpty()){
            return continuation(convertSimpleExpression(expression, depth))
        }

        val argumentHashMap: HashMap<Int, String> = HashMap()
        for (functionCall in functionCalls)
        {
            argumentHashMap[System.identityHashCode(functionCall)] = newArg()
        }

        val finalExpression = substituteArguments(expression, argumentHashMap, depth)

        var result = continuation(finalExpression)
        for (i in functionCalls.size - 1 downTo 0)
        {
            val arg = argumentHashMap[System.identityHashCode(functionCalls[i])]
            val functionName = mapFunction(functionCalls[i].IDENTIFIER().text)
            val functionArguments = functionCalls[i].argumentList().expression().joinToString(", ") { substituteArguments(it, argumentHashMap, depth) }

            if (functionName != "")
            {
                result = "$functionName($functionArguments, ($arg) -> {\n$result"
            }
            openedBrackets[depth]++
        }

        return result
    }

    fun collectFunctionCalls(expression: MiniKotlinParser.ExpressionContext) : List<MiniKotlinParser.FunctionCallExprContext> {
        if (expression is MiniKotlinParser.FunctionCallExprContext)
        {
            val argumentCalls: MutableList<MiniKotlinParser.FunctionCallExprContext> = mutableListOf()
            for (expression in expression.argumentList().expression())
            {
                argumentCalls += collectFunctionCalls(expression)
            }
            return argumentCalls + listOf(expression)
        }
        if (expression is MiniKotlinParser.NotExprContext)
        {
            return collectFunctionCalls(expression.expression())
        }
        if (expression is MiniKotlinParser.MulDivExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.AddSubExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.ComparisonExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.EqualityExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.AndExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.OrExprContext)
        {
            return collectFunctionCalls(expression.expression(0)) + collectFunctionCalls(expression.expression(1))
        }
        if (expression is MiniKotlinParser.PrimaryExprContext)
        {
            val primary = expression.primary()
            if (primary is MiniKotlinParser.ParenExprContext)
                return collectFunctionCalls(primary.expression())
        }

        return emptyList()
    }

    fun substituteArguments(expression: MiniKotlinParser.ExpressionContext, argumentMap: Map<Int, String>, depth: Int) : String {
        if (expression is MiniKotlinParser.FunctionCallExprContext)
        {
            return argumentMap[System.identityHashCode(expression)] ?: error("Call from outside of the map")
        }
        if (expression is MiniKotlinParser.PrimaryExprContext)
        {
            return convertSimpleExpression(expression, depth)
        }
        if (expression is MiniKotlinParser.MulDivExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${substituteArguments(expression.expression(0), argumentMap, depth)} $operator ${substituteArguments(expression.expression(1), argumentMap, depth)})"
        }
        if (expression is MiniKotlinParser.AddSubExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${substituteArguments(expression.expression(0), argumentMap, depth)} $operator ${substituteArguments(expression.expression(1), argumentMap, depth)})"
        }
        if (expression is MiniKotlinParser.ComparisonExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${substituteArguments(expression.expression(0), argumentMap, depth)} $operator ${substituteArguments(expression.expression(1), argumentMap, depth)})"
        }
        if (expression is MiniKotlinParser.EqualityExprContext)
        {
            val operator = expression.getChild(1).text
            val left = substituteArguments(expression.expression(0), argumentMap, depth)
            val right = substituteArguments(expression.expression(1), argumentMap, depth)
            return "($left) $operator ($right)"
        }
        if (expression is MiniKotlinParser.AndExprContext)
        {
            return "(${substituteArguments(expression.expression(0), argumentMap, depth)} && ${substituteArguments(expression.expression(1), argumentMap, depth)
            })"
        }
        if (expression is MiniKotlinParser.OrExprContext)
        {
            return "(${substituteArguments(expression.expression(0), argumentMap, depth)} || ${substituteArguments(expression.expression(1), argumentMap, depth)})"
        }
        if (expression is MiniKotlinParser.NotExprContext)
        {
            return "!(${substituteArguments(expression.expression(), argumentMap, depth)})"
        }

        error("Unknown expression in substituteArguments: $expression")
    }

    fun convertSimpleExpression(expression: MiniKotlinParser.ExpressionContext, depth: Int) : String {
        if (expression is MiniKotlinParser.PrimaryExprContext)
        {
            return convertPrimaryExpression(expression.primary(), depth)
        }
        if (expression is MiniKotlinParser.NotExprContext)
        {
            return "!(${convertExpressionStatement(expression.expression(), depth)})"
        }
        if (expression is MiniKotlinParser.MulDivExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0), depth)} $operator ${convertExpressionStatement(expression.expression(1), depth)})"
        }
        if (expression is MiniKotlinParser.AddSubExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0), depth)} $operator ${convertExpressionStatement(expression.expression(1), depth)})"
        }
        if (expression is MiniKotlinParser.ComparisonExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0), depth)} $operator ${convertExpressionStatement(expression.expression(1), depth)})"
        }
        if (expression is MiniKotlinParser.OrExprContext)
        {
            return "(${convertExpressionStatement(expression.expression(0), depth)} || ${convertExpressionStatement(expression.expression(1), depth)})"
        }
        if (expression is MiniKotlinParser.AndExprContext)
        {
            return "(${convertExpressionStatement(expression.expression(0), depth)} && ${convertExpressionStatement(expression.expression(1), depth)})"
        }
        if (expression is MiniKotlinParser.EqualityExprContext)
        {
            val operator = expression.getChild(1).text
            return "((${convertExpressionStatement(expression.expression(0), depth)}) $operator (${convertExpressionStatement(expression.expression(1), depth)}))"

        }


        error("unrecognized expression: $expression")
    }

    fun mapFunction(function: String) : String {
        if (function == "println")
        {
            return "Prelude.println"
        }
        return function
    }

    fun convertPrimaryExpression(primary: MiniKotlinParser.PrimaryContext, depth: Int) : String {
        if (primary is MiniKotlinParser.IntLiteralContext)
        {
            return primary.INTEGER_LITERAL().text
        }
        if (primary is MiniKotlinParser.StringLiteralContext)
        {
            return primary.STRING_LITERAL().text
        }
        if (primary is MiniKotlinParser.BoolLiteralContext)
        {
            return primary.BOOLEAN_LITERAL().text
        }
        if (primary is MiniKotlinParser.IdentifierExprContext)
        {
            return primary.IDENTIFIER().text
        }
        if (primary is MiniKotlinParser.ParenExprContext)
        {
            return convertExpressionStatement(primary.expression(), depth)
        }

        error("unrecognized primary expression: $primary")
    }
}
