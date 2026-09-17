package com.example.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxBuiltin
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxConstant
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxPreprocessor
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxType
import java.util.regex.Pattern

object ArduinoSyntaxHighlighter {

    private val KEYWORDS = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "break",
        "continue", "return", "default", "new", "delete", "class", "struct",
        "public", "private", "protected", "virtual", "const", "static", "volatile"
    )

    private val TYPES = setOf(
        "void", "int", "long", "float", "double", "char", "bool", "boolean",
        "byte", "word", "short", "unsigned", "signed", "uint8_t", "uint16_t",
        "uint32_t", "int8_t", "int16_t", "int32_t", "size_t", "String", "auto"
    )

    private val BUILTINS = setOf(
        "setup", "loop", "pinMode", "digitalWrite", "digitalRead", "analogRead",
        "analogWrite", "analogReference", "delay", "delayMicroseconds", "millis",
        "micros", "pulseIn", "shiftOut", "shiftIn", "tone", "noTone", "random",
        "randomSeed", "map", "constrain", "min", "max", "abs", "Serial", "Serial1",
        "Serial2", "begin", "print", "println", "available", "read", "write",
        "flush", "Wire", "SPI", "attachInterrupt", "detachInterrupt"
    )

    private val CONSTANTS = setOf(
        "HIGH", "LOW", "INPUT", "OUTPUT", "INPUT_PULLUP", "LED_BUILTIN",
        "true", "false", "NULL", "null", "HEX", "DEC", "BIN", "OCT", "PI",
        "HALF_PI", "TWO_PI", "DEG_TO_RAD", "RAD_TO_DEG"
    )

    private val PREPROCESSOR_PATTERN = Pattern.compile("^\\s*#(include|define|ifdef|ifndef|endif|if|elif|else|pragma)\\b.*$", Pattern.MULTILINE)
    private val COMMENT_PATTERN = Pattern.compile("(//[^\n]*|/\\*[\\s\\S]*?\\*/)")
    private val STRING_PATTERN = Pattern.compile("(\"[^\"]*\"|'[^']*')")
    private val NUMBER_PATTERN = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?f?)\\b")
    private val WORD_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")

    fun highlight(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)

        // 1. Highlight Words (Keywords, Types, Builtins, Constants)
        val wordMatcher = WORD_PATTERN.matcher(code)
        while (wordMatcher.find()) {
            val start = wordMatcher.start()
            val end = wordMatcher.end()
            val word = wordMatcher.group()

            when {
                KEYWORDS.contains(word) -> {
                    builder.addStyle(
                        SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold),
                        start, end
                    )
                }
                TYPES.contains(word) -> {
                    builder.addStyle(
                        SpanStyle(color = SyntaxType, fontWeight = FontWeight.Medium),
                        start, end
                    )
                }
                BUILTINS.contains(word) -> {
                    builder.addStyle(
                        SpanStyle(color = SyntaxBuiltin, fontWeight = FontWeight.SemiBold),
                        start, end
                    )
                }
                CONSTANTS.contains(word) -> {
                    builder.addStyle(
                        SpanStyle(color = SyntaxConstant, fontWeight = FontWeight.Bold),
                        start, end
                    )
                }
            }
        }

        // 2. Highlight Numbers
        val numMatcher = NUMBER_PATTERN.matcher(code)
        while (numMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxConstant),
                numMatcher.start(), numMatcher.end()
            )
        }

        // 3. Highlight Preprocessor
        val prepMatcher = PREPROCESSOR_PATTERN.matcher(code)
        while (prepMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxPreprocessor, fontWeight = FontWeight.Medium),
                prepMatcher.start(), prepMatcher.end()
            )
        }

        // 4. Highlight Strings (higher precedence than words)
        val stringMatcher = STRING_PATTERN.matcher(code)
        while (stringMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxString),
                stringMatcher.start(), stringMatcher.end()
            )
        }

        // 5. Highlight Comments (highest precedence)
        val commentMatcher = COMMENT_PATTERN.matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = SyntaxComment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                commentMatcher.start(), commentMatcher.end()
            )
        }

        return builder.toAnnotatedString()
    }

    val visualTransformation = VisualTransformation { text ->
        TransformedText(
            text = highlight(text.text),
            offsetMapping = OffsetMapping.Identity
        )
    }
}
