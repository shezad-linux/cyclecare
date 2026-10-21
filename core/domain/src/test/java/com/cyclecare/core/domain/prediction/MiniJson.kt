package com.cyclecare.core.domain.prediction

/**
 * A minimal JSON reader and writer.
 *
 * The golden fixtures are JSON files, because spec `12` §7 names them
 * that way. `core:domain` is a pure JVM module with no JSON library on
 * its classpath, and adding one to the shared version catalogue for a
 * single test would be a wider change than the fixtures warrant, so
 * this reads and writes the subset of JSON the fixtures use: objects,
 * arrays, strings, numbers, booleans and null.
 *
 * Numbers are read as [Double] and written back with enough precision
 * to round-trip exactly, so a fixture comparison never fails on
 * formatting.
 */
internal object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        require(parser.atEnd()) { "trailing content at ${parser.position}" }
        return value
    }

    /**
     * Writes JSON, indented so that a committed fixture can be read by
     * a reviewer.
     *
     * Spec `12` §7 rule 1 requires the fixtures to be "generated, then
     * reviewed and committed", and a reviewer cannot review one line.
     */
    fun write(value: Any?): String = buildString { writeValue(value, this, 0) } + "\n"

    private fun writeValue(value: Any?, out: StringBuilder, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(value, out)
            is Boolean -> out.append(value.toString())
            is Int -> out.append(value.toString())
            is Long -> out.append(value.toString())
            is Double -> out.append(writeNumber(value))
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                var first = true
                value.forEach { (key, entry) ->
                    if (!first) out.append(",\n")
                    first = false
                    indent(out, depth + 1)
                    writeString(key.toString(), out)
                    out.append(": ")
                    writeValue(entry, out, depth + 1)
                }
                out.append('\n')
                indent(out, depth)
                out.append('}')
            }
            is Iterable<*> -> {
                if (!value.iterator().hasNext()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                var first = true
                value.forEach { entry ->
                    if (!first) out.append(",\n")
                    first = false
                    indent(out, depth + 1)
                    writeValue(entry, out, depth + 1)
                }
                out.append('\n')
                indent(out, depth)
                out.append(']')
            }
            else -> throw IllegalArgumentException("cannot write ${value::class.simpleName} as JSON")
        }
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append("  ") }
    }

    /**
     * Numbers are written so that they read back as the same value.
     *
     * A whole number is written without a decimal point, so a fixture
     * reads as a human would write it, and anything else uses the
     * shortest representation that round-trips.
     */
    private fun writeNumber(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> throw IllegalArgumentException("cannot write $value as JSON")
        value == Math.floor(value) && Math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (character < ' ') {
                    out.append("\\u").append(String.format("%04x", character.code))
                } else {
                    out.append(character)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var position = 0

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): Any? {
            skipWhitespace()
            require(!atEnd()) { "unexpected end of input" }
            return when (val character = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> {
                    require(character == '-' || character.isDigit()) {
                        "unexpected character '$character' at $position"
                    }
                    readNumber()
                }
            }
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            require(text.startsWith(literal, position)) { "expected $literal at $position" }
            position += literal.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            position++
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && text[position] == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                require(!atEnd() && text[position] == ':') { "expected ':' at $position" }
                position++
                result[key] = readValue()
                skipWhitespace()
                require(!atEnd()) { "unexpected end of object" }
                when (text[position]) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> throw IllegalArgumentException("unexpected character '${text[position]}' at $position")
                }
            }
        }

        private fun readArray(): List<Any?> {
            position++
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (!atEnd() && text[position] == ']') {
                position++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                require(!atEnd()) { "unexpected end of array" }
                when (text[position]) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> throw IllegalArgumentException("unexpected character '${text[position]}' at $position")
                }
            }
        }

        private fun readString(): String {
            require(!atEnd() && text[position] == '"') { "expected a string at $position" }
            position++
            val result = StringBuilder()
            while (true) {
                require(!atEnd()) { "unterminated string" }
                when (val character = text[position]) {
                    '"' -> {
                        position++
                        return result.toString()
                    }
                    '\\' -> {
                        position++
                        require(!atEnd()) { "unterminated escape" }
                        when (val escaped = text[position]) {
                            '"' -> result.append('"')
                            '\\' -> result.append('\\')
                            '/' -> result.append('/')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'b' -> result.append('\b')
                            'f' -> result.append('')
                            'u' -> {
                                val hex = text.substring(position + 1, position + 5)
                                result.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> throw IllegalArgumentException("unknown escape \\$escaped at $position")
                        }
                        position++
                    }
                    else -> {
                        result.append(character)
                        position++
                    }
                }
            }
        }

        private fun readNumber(): Double {
            val start = position
            if (!atEnd() && text[position] == '-') position++
            while (!atEnd() && text[position].isDigit()) position++
            if (!atEnd() && text[position] == '.') {
                position++
                while (!atEnd() && text[position].isDigit()) position++
            }
            if (!atEnd() && (text[position] == 'e' || text[position] == 'E')) {
                position++
                if (!atEnd() && (text[position] == '+' || text[position] == '-')) position++
                while (!atEnd() && text[position].isDigit()) position++
            }
            return text.substring(start, position).toDouble()
        }
    }
}
