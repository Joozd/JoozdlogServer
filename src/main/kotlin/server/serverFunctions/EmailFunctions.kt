package server.serverFunctions

import data.email.EmailHashData
import nl.joozd.joozdlogcommon.FeedbackData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import settings.Settings
import storage.EmailRepository
import storage.FlightsStorage
import utils.CsvExporter
import utils.Logger
import utils.MailsBuilder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.activation.FileDataSource

object EmailFunctions {
    private val log = Logger.singleton



    fun sendTestEmail() =
        sendMail("HALLON TEST AMIL AUB GRGR", "Joozdlog Test email", "joozd@joozd.nl")



    /**
     * Send a backup mail to user
     * @param flightsStorage: a FlightsStorage object with flights to send a backup about
     * @param email: Address to send email to. Needs to be confirmed for user in [flightsStorage]
     * //TODO get mail from a file (html) and own address and subject from [Settings]
     */
    fun sendBackupMail(flightsStorage: FlightsStorage, email: String): FunctionResult =
        if (!EmailRepository.checkIfEmailConfirmed(flightsStorage.loginData.userName, email)) FunctionResult.BAD_EMAIL_ADDRESS
    else
        flightsStorage.flightsFile?.flights?.let {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
            val date = LocalDateTime.now().format(formatter)
            return sendMail(
                content = "Your Joozdlog Backup CSV file.\n\nJust open this on a device with Joozdlog installed and you can import or replace all flights into your app.",
                subject = "Joozdlog Backup CSV",
                to = email,
                htmlContent = MailsBuilder.buildBackupMailHtml(),
                images = MailsBuilder.getImages("joozdlog_logo_email"),
                attachment = CsvExporter(it).toByteArray(),
                attachmentName = "JoozdlogBackup - $date.csv",
                attachmentType = "text/csv"
            )
        } ?: FunctionResult.BAD_LOGIN_DATA

    /**
     * Send a login link email.
     * //TODO get mail from a file (html) and own address and subject from [Settings]
     */
    fun sendLoginLinkEmail(loginData: LoginDataWithEmail): FunctionResult{
        if (!EmailRepository.checkIfEmailConfirmed(loginData)) return FunctionResult.BAD_EMAIL_ADDRESS.also{
            log.v("Bad email address: $loginData")
        }
        val passwordString = Base64.getEncoder().encodeToString(loginData.password).replace("/", "-") // slashes will cause problems
        val loginLink = "https://joozdlog.joozd.nl/inject-key/${loginData.userName}:$passwordString"

        return sendMail(
            content = "This is your Joozdlog Login Link\n\n$loginLink\n\nPlease store this somewhere safe as it will give full control to your logbook, and you won't be able to sync your logbook on a new install without it.",
            subject = Settings["emailSubject"]?: "Joozdlog Login Link",
            htmlContent = MailsBuilder.buildLoginLinkMailHtml(loginLink),
            images = MailsBuilder.getImages("joozdlog_logo_email"),
            to = loginData.email)
        }

    /**
     * Send an email address confirmation mail
     */

    fun sendEmailConfirmationMail(loginData: LoginDataWithEmail, hashData: EmailHashData): FunctionResult{
        log.d("sendEmailConfirmationMail: confirmation requested")
        if (!EmailRepository.checkIfValidEmailAddress(loginData.email)) return FunctionResult.BAD_EMAIL_ADDRESS
        val hashedEmailBase64: String = Base64.getEncoder().encodeToString(hashData.hash).replace("/", "-")
        val confirmationLink = "https://joozdlog.joozd.nl/verify-email/${loginData.userName}:$hashedEmailBase64"
        log.d(confirmationLink)
        return sendMail(
            content = "Please open this link with the JoozdLog app because I didn't make a webserver to accept this yet.\n\n" +
            "link:\n$confirmationLink\n\nEnjoy,\nJoozd",
            subject = "Joozdlog email confirmation mail",
            htmlContent = MailsBuilder.buildEmailConfirmationMailHtml(confirmationLink),
            images = MailsBuilder.getImages("joozdlog_logo_email"),
            to = loginData.email)
    }

    /**
     * Send me (Joozd) an email with feedback from a Feedbackdata object
     */
    fun sendMeFeedbackMail(feedbackData: FeedbackData): FunctionResult{
        log.d("sendMeFeedbackMail: Feedback: ${feedbackData.toString().take(50)}")
        log.v("Feedback: ${feedbackData.toString().take(500)}", "Feedback")
        return sendMail(
            content = "***********************************\n" +
                    "Feedback received from:\n" +
                    "${feedbackData.contactInfo}\n" +
                    "***********************************\n\n\n" +
                    "***********************************\n" +
                    "Contents:\n" +
                    "${feedbackData.feedback}\n" +
                    "***********************************\n" +
                    "END OF FEEDBACK EMAIL",
            subject = "Joozdlog Automated Feedback Mail",
            to = "joozdlog@joozd.nl"
        )
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


    const val CID_PREFIX = "image_"
}