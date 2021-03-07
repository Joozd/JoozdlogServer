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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
                content = "Hallon dit moet nog beter worden. Met HTML enzo. Maar voor nu: je backup csv dinges.\n\nVeel plezier!\nXOXO Joozd!",
                subject = "Joozdlog Backup CSV",
                to = email,
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
            content = "Hallon dit moet nog beter worden. Met HTML enzo. Maar voor nu: Je login link!\n\n$loginLink\n\nVeel plezier!\nXOXO Joozd!",
            subject = Settings["emailSubject"]?: "Joozdlog Login Link",
            to = loginData.email)
        }

    /**
     * Send an email address confirmation mail
     * //TODO get mail from a file (html) and own address and subject from [Settings]
     */

    fun sendEmailConfirmationMail(loginData: LoginDataWithEmail, hashData: EmailHashData): FunctionResult{
        log.d("sendEmailConfirmationMail: confirmation requested")
        if (!EmailRepository.checkIfValidEmailAddress(loginData.email)) return FunctionResult.BAD_EMAIL_ADDRESS
        val hashedEmailBase64: String = Base64.getEncoder().encodeToString(hashData.hash).replace("/", "-")
        val confirmationLink = "https://joozdlog.joozd.nl/verify-email/${loginData.userName}:$hashedEmailBase64"
        log.d(confirmationLink)
        return sendMail(
            content = "Hallon dit is een email van Joozdlog\n\nVriendelijk verzoek om deze link te openen met JoozdLog, want ik ben te lui om een webserver op te zetten.\n\n" +
            "link:\n$confirmationLink\n\nVeel logplezier,\nJoozd",
            subject = "Joozdlog email confirmation mail",
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

    private fun sendMail(content: String, subject: String, to: String, attachment: ByteArray? = null, attachmentName: String? = null, attachmentType: String? = null, fromName: String = "JoozdLog Airline Pilots\' Logbook"): FunctionResult{
        if (!seemsToBeAnEmail(to)) return FunctionResult.BAD_EMAIL_ADDRESS.also {log.d("Bad email address")}
        log.d("Sending a mail with subject $subject to $to")

        val email = attachment?.let{
            EmailBuilder.startingBlank()
            .from(fromName, Settings["emailFrom"]!!)
            .to(to)
            .withSubject(subject)
            .withPlainText(content)
            .withAttachment(attachmentName!!, attachment, attachmentType!!)
            .buildEmail()
        } ?: EmailBuilder.startingBlank()
            .from(fromName, Settings["emailFrom"]!!)
            .to(to)
            .withSubject(subject)
            .withPlainText(content)
            .buildEmail()

        MailerBuilder
            .withSMTPServer("smtp03.hostnet.nl", 587, Settings["emailFrom"]!!, Settings["noReply"])
            .withTransportStrategy(TransportStrategy.SMTP_TLS)
            .buildMailer()
            .sendMail(email)
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

}