package utils

import java.util.*

fun base64EncodeLinkSafe(hash: ByteArray) =
    Base64.getEncoder().encodeToString(hash).replace("/", "-")