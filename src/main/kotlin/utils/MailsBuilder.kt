package utils

import settings.Settings
import java.io.File

/**
 * Builds html for emails
 */
object MailsBuilder {

    private const val EMAIL_CONFIRMATION_FILE = "confirm_email.html"
    private const val LOGIN_LINK_FILE = "login_link.html"
    private const val BACKUP_MAIL_FILE = "backup_file.html"

    private const val EMAIL_CONFIRMATION_LINK_PLACEHOLDER = "INSERT_EMAIL_CONFIRMATION_LINK_HERE"

    private const val PNG_SUFFIX = ".png"

    private val dir = Settings["emailTemplatesDir"]!!
    private val rawEmailConfirmationMail
        get() = File(dir + EMAIL_CONFIRMATION_FILE).readText()

    private val rawLoginLinkMail
        get() = File(dir + LOGIN_LINK_FILE).readText()

    private val rawBackupFileMail
        get() = File(dir + BACKUP_MAIL_FILE).readText()

    fun buildEmailConfirmationMailHtml(confirmationLink: String) = rawEmailConfirmationMail.replace(EMAIL_CONFIRMATION_LINK_PLACEHOLDER, confirmationLink)

    fun buildLoginLinkMailHtml(loginLink: String) = rawLoginLinkMail.replace(EMAIL_CONFIRMATION_LINK_PLACEHOLDER, loginLink)

    fun buildBackupMailHtml() = rawBackupFileMail

    /**
     * Get image Files by name
     */
    fun getImages(vararg names: String): List<File> = names.map { File(dir + it + PNG_SUFFIX)}

    fun getImages(names: List<String>) = getImages(*names.toTypedArray())
}