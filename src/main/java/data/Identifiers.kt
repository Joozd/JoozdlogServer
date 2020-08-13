package data

object Identifiers {
    /**
     * A header consists of:
     * "JOOZDLOG" in UTF_8 encoded bytes
     * + 8 bytes forming a LONG stating the remaining length of the message
     * so "JOOZDLOG00000003Hoi" is a correct message
     */
    val HEADER = "JOOZDLOG".toByteArray(Charsets.UTF_8)
}