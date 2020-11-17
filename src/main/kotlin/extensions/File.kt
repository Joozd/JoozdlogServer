package extensions

import java.io.File

fun File.readNBytes(n: Int): ByteArray{
    val buffer = ByteArray(n)
    this.inputStream().use{ it.read(buffer)}
    return buffer
}