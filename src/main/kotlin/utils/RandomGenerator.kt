package utils

import java.security.SecureRandom

class RandomGenerator(private val allowedCharacters: String) {
    fun generateCode(size: Int): String{
        val result = mutableListOf<Char>()
        SecureRandom().let{ random ->
            val bbb = ByteArray(16)
            while (result.size != size){
                random.nextBytes(bbb)
                bbb.firstOrNull { it.toInt() in allowedCharacters.indices }?.let{ index ->
                    result.add(allowedCharacters[index.toInt()])
                }
            }
            return result.joinToString("")
        }
    }
}