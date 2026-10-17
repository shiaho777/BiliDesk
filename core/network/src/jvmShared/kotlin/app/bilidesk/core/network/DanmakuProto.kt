package app.bilidesk.core.network

import app.bilidesk.core.model.Danmaku

internal fun parseDanmakuSeg(data: ByteArray): List<Danmaku> {
    if (data.isEmpty()) return emptyList()
    val out = mutableListOf<Danmaku>()
    val reader = ProtoReader(data)
    try {
        while (reader.tryTag()) {
            if (reader.field == 1 && reader.wire == 2) {
                val elem = reader.readBytes()
                val item = parseElem(elem)
                if (item.text.isNotEmpty()) out += item
            } else {
                reader.skip()
            }
        }
    } catch (_: Exception) {
        // 返回已经解析出来的部分
    }
    return out
}

private fun parseElem(bytes: ByteArray): Danmaku {
    var progress = 0
    var mode = 1
    var text = ""
    var color = 0xFFFFFF
    val reader = ProtoReader(bytes)
    while (reader.tryTag()) {
        when (reader.field) {
            2 -> progress = reader.readVarint().toInt()
            3 -> mode = reader.readVarint().toInt()
            5 -> color = reader.readVarint().toInt()
            7 -> text = reader.readString()
            else -> reader.skip()
        }
    }
    if (mode == 6) mode = 1
    if (color < 0 || color > 0xFFFFFF) color = 0xFFFFFF
    return Danmaku(progress / 1000.0, text, mode, color)
}

private class ProtoReader(private val buf: ByteArray) {
    private var pos = 0
    var field = 0
        private set
    var wire = 0
        private set

    fun tryTag(): Boolean {
        if (pos >= buf.size) return false
        val tag = readVarint()
        field = (tag ushr 3).toInt()
        wire = (tag and 7).toInt()
        return true
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= buf.size) error("protobuf 越界")
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 63) error("protobuf varint 过长")
        }
        return result
    }

    fun readBytes(): ByteArray {
        if (wire != 2) {
            skip()
            return ByteArray(0)
        }
        val len = readVarint().toInt()
        if (len < 0 || pos + len > buf.size) error("protobuf 长度越界")
        val slice = buf.copyOfRange(pos, pos + len)
        pos += len
        return slice
    }

    fun readString(): String = readBytes().decodeToString()

    fun skip() {
        when (wire) {
            0 -> readVarint()
            1 -> advance(8)
            2 -> advance(readVarint().toInt())
            5 -> advance(4)
            else -> error("不支持的线格式 $wire")
        }
    }

    private fun advance(n: Int) {
        if (n < 0 || pos + n > buf.size) error("protobuf 越界")
        pos += n
    }
}
