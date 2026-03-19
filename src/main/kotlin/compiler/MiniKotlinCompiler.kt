package org.example.compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {

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
        val result = body.statement().joinToString("\n"){
            convertStatement(it)
        }

        return result
    }

    fun convertStatement(statement: MiniKotlinParser.StatementContext) : String
    {
        if (statement.variableDeclaration() != null)
        {
            return convertVariableDeclaration(statement.variableDeclaration())
        }

        return ";"
    }

    fun convertVariableDeclaration(variableDeclaration: MiniKotlinParser.VariableDeclarationContext): String {
        var type = convertType(variableDeclaration.type())
        var name = variableDeclaration.IDENTIFIER().text
        var expression = variableDeclaration.expression().text

        return "$type $name = ($expression);"
    }
}
