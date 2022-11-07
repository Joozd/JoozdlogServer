package server.serverFunctions.mail

import data.email.EmailDataRecord

import settings.Settings
import data.email.EmailRepository
import utils.Logger
import utils.MailsBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import nl.joozd.joozdlogcommon.EmailData
import server.serverFunctions.FunctionResult
import javax.mail.internet.InternetAddress

object EmailFunctions {
    private val log = Logger.singleton

    // true on success, false on bad data
    fun forwardBackupEmail(backupEmailData: EmailData): Boolean{
        if (!EmailRepository.checkIfEmailConfirmed(backupEmailData.emailID, backupEmailData.emailAddress)) return false
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        val date = LocalDateTime.now().format(formatter)
        sendMail(
            subject = "Joozdlog Backup CSV",
            to = backupEmailData.emailAddress,
            htmlContent = MailsBuilder.buildBackupMailHtml(),
            images = MailsBuilder.getImages("joozdlog_logo_email"),
            attachmentData = backupEmailData.attachment,
            attachmentName = "JoozdlogBackup - $date.csv",
            attachmentMimeType = "text/csv"
        )
        return true
    }

    /**
     * Send an email address confirmation mail
     */
    fun sendEmailConfirmationMail(emailDataRecord: EmailDataRecord, address: String): FunctionResult {
        log.d("sendEmailConfirmationMail: confirmation requested for id ${emailDataRecord.id}")
        val confirmationLink = "https://joozdlog.joozd.nl/verify-email/${emailDataRecord.confirmationString()}".also { log.d("link: $it")}
        return sendMail(
            subject = "Joozdlog email confirmation mail",
            htmlContent = MailsBuilder.buildEmailConfirmationMailHtml(confirmationLink),
            images = MailsBuilder.getImages("joozdlog_logo_email"),
            to = address)
    }

    /**
     * Placeholder mailer function.
     * TODO: Rewrite this to support HTML etcetera.
     * @return SUCCESS or BAD_EMAIL_ADDRESS
     */
    private fun sendMail(
        subject: String,
        to: String,
        htmlContent: String,
        images: List<File> = emptyList(), // TODO fix this
        attachmentData: ByteArray? = null,
        attachmentMimeType: String? = null,
        attachmentName: String? = null,
        fromName: String = "JoozdLog Airline Pilots\' Logbook"
    ): FunctionResult {
        if (!seemsToBeAnEmail(to)) return FunctionResult.BAD_EMAIL_ADDRESS.also { log.d("Bad email address")}

        val from = InternetAddress(Settings["emailFrom"]!!, fromName)
        Mailer(
            from = from,
            to = to,
            subject = subject,
            htmlContent = htmlContent,
            attachmentData = attachmentData,
            attachmentMimeType = attachmentMimeType,
            attachmentName = attachmentName
        ).send()
        return FunctionResult.SUCCESS
    }

    private fun seemsToBeAnEmail(text: String) = text matches (
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+").toRegex()


    private const val CID_PREFIX = "image_"
}