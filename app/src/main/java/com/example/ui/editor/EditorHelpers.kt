package com.example.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class AutocompleteItem(
    val trigger: String,
    val insertion: String,
    val cursorOffset: Int = 0, // offset from end of insertion
    val description: String
)

object AutocompleteHelper {
    val COMMON_SUGGESTIONS = listOf(
        AutocompleteItem("pinMode", "pinMode(pin, OUTPUT);", -10, "Configure digital pin direction"),
        AutocompleteItem("digitalWrite", "digitalWrite(pin, HIGH);", -8, "Set pin output HIGH or LOW"),
        AutocompleteItem("digitalRead", "digitalRead(pin)", -1, "Read digital state from pin"),
        AutocompleteItem("analogRead", "analogRead(A0)", -1, "Read 10-bit analog voltage value"),
        AutocompleteItem("analogWrite", "analogWrite(pin, val);", -7, "Write analog PWM output value"),
        AutocompleteItem("Serial.begin", "Serial.begin(115200);", 0, "Initialize hardware UART baud rate"),
        AutocompleteItem("Serial.println", "Serial.println(\"\");", -3, "Print line to serial console"),
        AutocompleteItem("Serial.print", "Serial.print(\"\");", -3, "Print string without newline"),
        AutocompleteItem("Serial.available", "Serial.available()", 0, "Check incoming serial buffer bytes"),
        AutocompleteItem("delay", "delay(1000);", -2, "Pause execution for milliseconds"),
        AutocompleteItem("millis", "millis()", 0, "Current runtime in milliseconds"),
        AutocompleteItem("void setup", "void setup() {\n  \n}", -2, "Arduino setup initialization loop"),
        AutocompleteItem("void loop", "void loop() {\n  \n}", -2, "Arduino main execution loop"),
        AutocompleteItem("for", "for (int i = 0; i < count; i++) {\n  \n}", -2, "For counting loop"),
        AutocompleteItem("if", "if (condition) {\n  \n}", -2, "Conditional branch"),
        AutocompleteItem("#include", "#include <.h>", -3, "Include external library header"),
        AutocompleteItem("#define", "#define NAME value", 0, "Macro definition constant"),
        AutocompleteItem("OUTPUT", "OUTPUT", 0, "Pin output mode constant"),
        AutocompleteItem("INPUT", "INPUT", 0, "Pin input mode constant"),
        AutocompleteItem("INPUT_PULLUP", "INPUT_PULLUP", 0, "Internal pullup resistor mode"),
        AutocompleteItem("HIGH", "HIGH", 0, "Digital logic 1"),
        AutocompleteItem("LOW", "LOW", 0, "Digital logic 0"),
        AutocompleteItem("LED_BUILTIN", "LED_BUILTIN", 0, "On-board default LED pin")
    )

    fun getSuggestions(text: String, selectionEnd: Int): List<AutocompleteItem> {
        if (selectionEnd <= 0 || selectionEnd > text.length) return emptyList()
        // Find current word before cursor
        var start = selectionEnd - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '.' || text[start] == '#')) {
            start--
        }
        val prefix = text.substring(start + 1, selectionEnd)
        if (prefix.length < 2) return emptyList()

        return COMMON_SUGGESTIONS.filter {
            it.trigger.startsWith(prefix, ignoreCase = true) || it.insertion.startsWith(prefix, ignoreCase = true)
        }.take(5)
    }

    fun applySuggestion(currentValue: TextFieldValue, suggestion: AutocompleteItem): TextFieldValue {
        val text = currentValue.text
        val selectionEnd = currentValue.selection.end
        var start = selectionEnd - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '.' || text[start] == '#')) {
            start--
        }
        val prefixStart = start + 1
        val newText = text.substring(0, prefixStart) + suggestion.insertion + text.substring(selectionEnd)
        val newCursor = prefixStart + suggestion.insertion.length + suggestion.cursorOffset
        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursor.coerceIn(0, newText.length))
        )
    }
}

class UndoRedoManager(private val maxHistory: Int = 50) {
    private val undoStack = mutableListOf<TextFieldValue>()
    private val redoStack = mutableListOf<TextFieldValue>()

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun recordState(current: TextFieldValue) {
        if (undoStack.isNotEmpty() && undoStack.last().text == current.text) {
            return
        }
        undoStack.add(current)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(current: TextFieldValue): TextFieldValue? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(current: TextFieldValue): TextFieldValue? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        return redoStack.removeAt(redoStack.lastIndex)
    }
}
