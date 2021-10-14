package data.aircraft

import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.serializing.JoozdSerializable
import nl.joozd.serializing.unwrap
import nl.joozd.serializing.wrap


data class TypeCounter(val type: AircraftType, val count: Int): JoozdSerializable {
    override fun equals(other: Any?): Boolean {
        if (other !is TypeCounter) return false
        return other.type == type
    }
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + count
        return result
    }



    override fun serialize(): ByteArray {
        var serialized = ByteArray(0)
        serialized += wrap(component1().serialize())
        serialized += wrap(component2())
        return serialized
    }


    companion object: JoozdSerializable.Deserializer<TypeCounter> {

        override fun deserialize(source: ByteArray): TypeCounter {
            val wraps = TypeCounter.serializedToWraps(source)
            return TypeCounter(
                AircraftType.deserialize(unwrap(wraps[0])),
                unwrap(wraps[1])
            )
        }
    }

}