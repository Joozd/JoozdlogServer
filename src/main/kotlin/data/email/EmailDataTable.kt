package data.email

import org.jetbrains.exposed.dao.id.LongIdTable

object EmailDataTable: LongIdTable() {
    val hash = binary("hash", 32)
    val salt = binary("salt", 32)
    val lastAccessed = long("lastAccessed")
    val isVerified = bool("isVerified")
}