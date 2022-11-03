package server.serverFunctions

import data.email.EmailDataRecord
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import settings.Settings
import data.email.EmailRepository
import utils.Logger
import utils.MailsBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import jakarta.activation.FileDataSource
import nl.joozd.joozdlogcommon.EmailData

object EmailFunctions {
    private val log = Logger.singleton

    // true on success, false on bad data
    fun forwardBackupEmail(backupEmailData: EmailData): Boolean{
        if (!EmailRepository.checkIfEmailConfirmed(backupEmailData.emailID, backupEmailData.emailAddress)) return false
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        val date = LocalDateTime.now().format(formatter)
        sendMail(
            content = "Your Joozdlog Backup CSV file.\n\nJust open this on a device with Joozdlog installed and you can import or replace all flights into your app.",
            subject = "Joozdlog Backup CSV",
            to = backupEmailData.emailAddress,
            htmlContent = MailsBuilder.buildBackupMailHtml(),
            images = MailsBuilder.getImages("joozdlog_logo_email"),
            attachment = backupEmailData.attachment,
            attachmentName = "JoozdlogBackup - $date.csv",
            attachmentType = "text/csv"
        )
        return true
    }

    /**
     * Send an email address confirmation mail
     */
    fun sendEmailConfirmationMail(emailDataRecord: EmailDataRecord, address: String): FunctionResult{
        log.d("sendEmailConfirmationMail: confirmation requested for id ${emailDataRecord.id}")
        val confirmationLink = "https://joozdlog.joozd.nl/verify-email/${emailDataRecord.confirmationString()}".also { log.d("link: $it")}
        return sendMail(
            content = "Please open this link with the JoozdLog app because I didn't make a webserver to accept this yet.\n\n" +
                    "link:\n$confirmationLink\n\nEnjoy,\nJoozd",
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
        content: String,
        subject: String,
        to: String,
        htmlContent: String? = null,
        images: List<File> = emptyList(),
        attachment: ByteArray? = null,
        attachmentName: String? = null,
        attachmentType: String? = null,
        fromName: String = "JoozdLog Airline Pilots\' Logbook"
    ): FunctionResult{
        if (!seemsToBeAnEmail(to)) return FunctionResult.BAD_EMAIL_ADDRESS.also {log.d("Bad email address")}
        log.d("Sending a mail with subject $subject to $to")

        val email = EmailBuilder.startingBlank()
            .from(fromName, Settings["emailFrom"]!!)
            .to(to)
            .withSubject(subject)
            .withPlainText(content)

        // add HTML content if any
        if(htmlContent != null)
            log.v("Adding HTML to email","sendMail()")
            email.withHTMLText(htmlContent)

        // add attachment if any
        if (attachment != null && attachmentType != null && attachmentName != null)
            email.withAttachment(attachmentName, attachment, attachmentType)
        else if (attachment != null || attachmentType != null || attachmentName != null)
            log.w("Attachment, attachment type or attachment name not null even though one or more of the others is", "sendMail()")

        /**
         * Images will only work if correct placeholders in html file
         * ie. cid:image_1, cid:image_2 etc
         */
        if (images.isNotEmpty()) {
            images.forEachIndexed { index, file ->
                log.v("Adding image to email", "sendMail()")
                val id = "${CID_PREFIX}${index+1}"
                email.withEmbeddedImage(id, FileDataSource(file) )
            }
        }


        MailerBuilder
            .withSMTPServer("smtp03.hostnet.nl", 587, Settings["emailFrom"]!!, Settings["noReply"])
            .withTransportStrategy(TransportStrategy.SMTP_TLS)
            .buildMailer()
            .sendMail(email.buildEmail())
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