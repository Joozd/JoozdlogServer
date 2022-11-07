package server.serverFunctions.mail


import settings.Settings
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource


class Mailer(
    private val from: Address,
    private val to: String,
    private val subject: String,
    private val htmlContent: String,
    private val attachmentData: ByteArray? = null,
    private val attachmentMimeType: String? = null,
    private val attachmentName: String? = null,
){
    fun send(){
        val msg = MimeMessage(getMailerSession()).apply{
            setFrom(this@Mailer.from)
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(this@Mailer.to))
            subject = this@Mailer.subject
            setContent(makeMimeContent(htmlContent, attachmentData, attachmentMimeType, attachmentName))
        }
        Transport.send(msg)
    }

    private fun makeMimeContent(htmlContent: String, attachmentData: ByteArray?, attachmentMimeType: String?, attachmenName: String?) = MimeMultipart().apply{
        addBodyPart(MimeBodyPart().apply{ setContent(htmlContent, "text/html; charset=utf-8")})
        attachmentData?.let{
            addBodyPart(MimeBodyPart().apply {
                val bds = ByteArrayDataSource(it, attachmentMimeType!!)
                dataHandler = DataHandler(bds)
                fileName = attachmenName ?: bds.name
            })
        }
    }


    private fun getMailerSession(): Session =
        Session.getInstance(buildMailerProperties(), getAuthenticator())


    private fun getAuthenticator(): Authenticator = object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication =
            PasswordAuthentication(Settings["emailFrom"]!!, Settings["noReply"])
    }

    private fun buildMailerProperties(): Properties = Properties().apply {
        put("mail.smtp.auth", true)
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.host", "smtp03.hostnet.nl")
        put("mail.smtp.port", "587")
        put("mail.smtp.ssl.trust", "smtp03.hostnet.nl")
    }
}