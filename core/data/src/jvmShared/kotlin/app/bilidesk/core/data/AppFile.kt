package app.bilidesk.core.data

expect class AppFile {
    constructor(path: String)
    constructor(parent: AppFile, child: String)

    val name: String
    val absolutePath: String
    val parentFile: AppFile?

    fun exists(): Boolean
    fun isFile(): Boolean
    fun mkdirs()
    fun readText(): String
    fun writeText(text: String)
    fun writeBytes(bytes: ByteArray)
    fun listFiles(): List<AppFile>
    fun walkFiles(): List<AppFile>
    fun deleteRecursively()
    fun lastModified(): Long
    fun length(): Long
}
