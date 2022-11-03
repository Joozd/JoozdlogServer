package data.email

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class EmailDataDao(id: EntityID<Long>): LongEntity(id){
    companion object: LongEntityClass<EmailDataDao>(EmailDataTable)
    var hash by EmailDataTable.hash
    var salt by EmailDataTable.salt
    var lastAccessed by EmailDataTable.lastAccessed
    var isVerified by EmailDataTable.isVerified
}