package p2p

/**
 * A P2P session is a place where data is stored from one client and for another to retrieve. It contains raw bytes.
 */
class P2PSession {
    private var savedBlob: ByteArray? = null

    fun put(data: ByteArray){
        savedBlob = data
    }

    fun get() = savedBlob
}