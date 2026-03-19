package org.example.compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {

    // Helpers to never overlap argument names
    private var argCounter = 0
    private fun newArg() = "arg${argCounter++}"

    // Helper field to count opened curly brackets
    private var openedBrackets = 0

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

        val body = convertBody(function.block())

        return """public static $returnType $name($arguments){
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

    fun convertBody(body: MiniKotlinParser.BlockContext): String {
        var result = body.statement().joinToString("\n"){
            convertStatement(it)
        }

        while (openedBrackets > 0) {
            openedBrackets--
            result += "\n});"
        }

        return result
    }

    fun convertStatement(statement: MiniKotlinParser.StatementContext) : String
    {
        if (statement.variableDeclaration() != null)
        {
            return convertVariableDeclaration(statement.variableDeclaration())
        }
        if (statement.returnStatement() != null)
        {
            return convertReturn(statement.returnStatement())
        }
        if (statement.expression() != null)
        {
            return convertExpressionStatement(statement.expression())
        }

        return ";"
    }

    fun convertVariableDeclaration(variableDeclaration: MiniKotlinParser.VariableDeclarationContext): String {
        val type = convertType(variableDeclaration.type())
        val name = variableDeclaration.IDENTIFIER().text

        return "$type $name = ${convertExpressionStatement(variableDeclaration.expression())};"
    }

    fun convertReturn(returnStatement: MiniKotlinParser.ReturnStatementContext): String {
        if (returnStatement.expression() == null)
        {
            return """__continuation.accept(null);
                return;
            """.trimMargin()
        }

        return """__continuation.accept(${convertExpressionStatement(returnStatement.expression())});
            return;
        """.trimMargin()
    }

    fun convertExpressionStatement(expression: MiniKotlinParser.ExpressionContext): String {
        if (expression is MiniKotlinParser.FunctionCallExprContext)
        {
            return convertComplexExpression(expression)
        }
        else
        {
            return convertSimpleExpression(expression)
        }
    }

//    fun containsFunctionCall(expression: MiniKotlinParser.ExpressionContext) : Boolean {
//        if (expression is MiniKotlinParser.FunctionCallExprContext) return true
//        if (expression is MiniKotlinParser.PrimaryExprContext) return false
//        if (expression is MiniKotlinParser.NotExprContext) return containsFunctionCall(expression.expression())
//        if (expression is MiniKotlinParser.AddSubExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//        if (expression is MiniKotlinParser.MulDivExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//        if (expression is MiniKotlinParser.ComparisonExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//        if (expression is MiniKotlinParser.EqualityExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//        if (expression is MiniKotlinParser.AndExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//        if (expression is MiniKotlinParser.OrExprContext) return (containsFunctionCall(expression.expression(0)) || containsFunctionCall(expression.expression(1)))
//
//        return false
//    }

    fun convertComplexExpression(expression: MiniKotlinParser.FunctionCallExprContext) : String {
        val name = mapFunction(expression.IDENTIFIER().text)
        val arguments = expression.argumentList().expression().joinToString(", ") {
            convertExpressionStatement(it)
        }
        openedBrackets++

        return "$name($arguments, (${newArg()}) ->{"
    }

    fun convertSimpleExpression(expression: MiniKotlinParser.ExpressionContext) : String {
        if (expression is MiniKotlinParser.PrimaryExprContext)
        {
            return convertPrimaryExpression(expression.primary())
        }
        if (expression is MiniKotlinParser.NotExprContext)
        {
            return "!(${convertExpressionStatement(expression.expression())})"
        }
        if (expression is MiniKotlinParser.MulDivExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0))} $operator ${convertExpressionStatement(expression.expression(1))})"
        }
        if (expression is MiniKotlinParser.AddSubExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0))} $operator ${convertExpressionStatement(expression.expression(1))})"
        }
        if (expression is MiniKotlinParser.ComparisonExprContext)
        {
            val operator = expression.getChild(1).text
            return "(${convertExpressionStatement(expression.expression(0))} $operator ${convertExpressionStatement(expression.expression(1))})"
        }
        if (expression is MiniKotlinParser.OrExprContext)
        {
            return "(${convertExpressionStatement(expression.expression(0))} || ${convertExpressionStatement(expression.expression(1))})"
        }
        if (expression is MiniKotlinParser.AndExprContext)
        {
            return "(${convertExpressionStatement(expression.expression(0))} && ${convertExpressionStatement(expression.expression(1))})"
        }
        if (expression is MiniKotlinParser.EqualityExprContext)
        {
            val operator = expression.getChild(1).text
            if (operator == "==") {
                return "(${convertExpressionStatement(expression.expression(0))}.equals(${convertExpressionStatement(expression.expression(1))}))"
            }
            else if (operator == "!=") {
                return "!(${convertExpressionStatement(expression.expression(0))}.equals(${convertExpressionStatement(expression.expression(1))}))"
            }
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

    fun convertPrimaryExpression(primary: MiniKotlinParser.PrimaryContext) : String {
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
            return convertExpressionStatement(primary.expression())
        }

        error("unrecognized primary expression: $primary")
    }
}
