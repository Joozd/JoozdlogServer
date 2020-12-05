package storage

import settings.Settings

/**
 * Object to use for retrieving and storing verification data for email addresses
 * No email adresses will be stored, but a combination of username, salt and hash of salt+emailaddress will be stored.
 */
object EmailData {
    private val userFilesDirectory
        get() = Settings["emailDir"]
}