package data.feedback

import nl.joozd.joozdlogcommon.FeedbackData
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FeedbackHandler private constructor(val feedbackFile: File) {
    fun addFeedback(feedbackData: FeedbackData){
        feedbackFile.appendText(SEPARATOR + "Received: $now\nFrom: ${feedbackData.contactInfo}\nFeedback: ${feedbackData.feedback}")
    }

    private val now get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern(formatterPattern))


    companion object{
        private const val SEPARATOR = "\n\n===========================================================================\n\n"

        private const val formatterPattern = "uuuu/MM/dd HH:mm"

        val instance by lazy { FeedbackHandler(File("../feedback.txt")) }
    }
}