package app.bilidesk.core.network

class JSONObject {
    private val map = linkedMapOf<String, Any?>()

    constructor()

    constructor(json: String) {
        val parsed = JsonParser(json).parseValue()
        require(parsed is JSONObject) { "不是 JSON 对象" }
        map.putAll(parsed.map)
    }

    fun length(): Int = map.size

    fun has(key: String): Boolean = map.containsKey(key)

    fun isNull(key: String): Boolean = !map.containsKey(key) || map[key] == null

    fun keys(): Iterator<String> = map.keys.iterator()

    fun remove(key: String): Any? = map.remove(key)

    fun put(key: String, value: Any?): JSONObject {
        map[key] = wrap(value)
        return this
    }

    fun opt(key: String): Any? = map[key]

    fun optString(key: String, fallback: String = ""): String {
        val v = map[key] ?: return fallback
        return v.toString()
    }

    fun optInt(key: String, fallback: Int = 0): Int = numberOf(map[key])?.toInt() ?: fallback

    fun optLong(key: String, fallback: Long = 0L): Long = numberOf(map[key])?.toLong() ?: fallback

    fun optDouble(key: String, fallback: Double = Double.NaN): Double = numberOf(map[key])?.toDouble() ?: fallback

    fun optBoolean(key: String, fallback: Boolean = false): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        else -> fallback
    }

    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject

    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray

    override fun toString(): String = stringify(0, 0)

    fun toString(indent: Int): String = stringify(indent, 0)

    internal fun stringify(indent: Int, level: Int): String = buildString {
        append('{')
        val entries = map.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            if (indent > 0) {
                append('\n')
                repeat((level + 1) * indent) { append(' ') }
            }
            append(quote(key))
            append(':')
            if (indent > 0) append(' ')
            append(stringifyValue(value, indent, level + 1))
            if (index != entries.lastIndex) append(',')
        }
        if (indent > 0 && entries.isNotEmpty()) {
            append('\n')
            repeat(level * indent) { append(' ') }
        }
        append('}')
    }
}

class JSONArray {
    private val items = mutableListOf<Any?>()

    constructor()

    constructor(json: String) {
        val parsed = JsonParser(json).parseValue()
        require(parsed is JSONArray) { "不是 JSON 数组" }
        items.addAll(parsed.items)
    }

    fun length(): Int = items.size

    fun put(value: Any?): JSONArray {
        items += wrap(value)
        return this
    }

    fun opt(index: Int): Any? = items.getOrNull(index)

    fun optString(index: Int, fallback: String = ""): String {
        val v = items.getOrNull(index) ?: return fallback
        return v.toString()
    }

    fun optInt(index: Int, fallback: Int = 0): Int = numberOf(items.getOrNull(index))?.toInt() ?: fallback

    fun optDouble(index: Int, fallback: Double = Double.NaN): Double =
        numberOf(items.getOrNull(index))?.toDouble() ?: fallback

    fun optJSONObject(index: Int): JSONObject? = items.getOrNull(index) as? JSONObject

    fun optJSONArray(index: Int): JSONArray? = items.getOrNull(index) as? JSONArray

    override fun toString(): String = stringify(0, 0)

    internal fun stringify(indent: Int, level: Int): String = buildString {
        append('[')
        items.forEachIndexed { index, value ->
            if (indent > 0) {
                append('\n')
                repeat((level + 1) * indent) { append(' ') }
            }
            append(stringifyValue(value, indent, level + 1))
            if (index != items.lastIndex) append(',')
        }
        if (indent > 0 && items.isNotEmpty()) {
            append('\n')
            repeat(level * indent) { append(' ') }
        }
        append(']')
    }
}

private fun wrap(value: Any?): Any? = when (value) {
    null, is JSONObject, is JSONArray, is Boolean, is Number, is String -> value
    else -> value.toString()
}

private fun numberOf(value: Any?): Number? = when (value) {
    is Number -> value
    is String -> value.toLongOrNull() ?: value.toDoubleOrNull()
    else -> null
}

private fun stringifyValue(value: Any?, indent: Int, level: Int): String = when (value) {
    null -> "null"
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is Double, is Float -> {
        val d = (value as Number).toDouble()
        when {
            d.isNaN() || d.isInfinite() -> "null"
            d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() -> d.toLong().toString()
            else -> d.toString()
        }
    }
    is Number -> value.toString()
    is String -> quote(value)
    is JSONObject -> value.stringify(indent, level)
    is JSONArray -> value.stringify(indent, level)
    else -> quote(value.toString())
}

private fun quote(text: String): String = buildString(text.length + 2) {
    append('"')
    for (ch in text) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
        }
    }
    append('"')
}

private class JsonParser(private val source: String) {
    private var index = 0

    fun parseValue(): Any? {
        skip()
        if (index >= source.length) error("空 JSON")
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            '-', in '0'..'9' -> parseNumber()
            else -> error("JSON 位置 $index 非法")
        }
    }

    private fun parseObject(): JSONObject {
        expect('{')
        val obj = JSONObject()
        skip()
        if (peek() == '}') {
            index++
            return obj
        }
        while (true) {
            skip()
            val key = parseString()
            skip()
            expect(':')
            obj.put(key, parseValue())
            skip()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return obj
                }
                else -> error("JSON 对象在 $index 处缺少逗号")
            }
        }
    }

    private fun parseArray(): JSONArray {
        expect('[')
        val arr = JSONArray()
        skip()
        if (peek() == ']') {
            index++
            return arr
        }
        while (true) {
            arr.put(parseValue())
            skip()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return arr
                }
                else -> error("JSON 数组在 $index 处缺少逗号")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= source.length) error("字符串未结束")
                    when (val esc = source[index++]) {
                        '"', '\\', '/' -> out.append(esc)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > source.length) error("非法 unicode")
                            val hex = source.substring(index, index + 4)
                            out.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> error("非法转义")
                    }
                }
                else -> out.append(ch)
            }
        }
        error("字符串未结束")
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index++
        while (index < source.length && source[index] in '0'..'9') index++
        var decimal = false
        if (index < source.length && source[index] == '.') {
            decimal = true
            index++
            while (index < source.length && source[index] in '0'..'9') index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            decimal = true
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            while (index < source.length && source[index] in '0'..'9') index++
        }
        val raw = source.substring(start, index)
        if (!decimal) {
            raw.toLongOrNull()?.let { return if (it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) it.toInt() else it }
        }
        return raw.toDouble()
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        if (!source.startsWith(literal, index)) error("期望 $literal")
        index += literal.length
        return value
    }

    private fun expect(ch: Char) {
        skip()
        if (peek() != ch) error("期望 $ch")
        index++
    }

    private fun peek(): Char = if (index < source.length) source[index] else '\u0000'

    private fun skip() {
        while (index < source.length) {
            val ch = source[index]
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') index++ else break
        }
    }
}
