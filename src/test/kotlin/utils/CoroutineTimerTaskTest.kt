package utils

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoroutineTimerTaskTest {
    @Test
    fun testCoroutineTimerTask(){
        runBlocking {
            oneAndTwo("1")
            oneAndTwoPlusAHalf("1")
            delay(3.seconds)
        }
        println("***")
        runBlocking {
            oneAndTwo("2")
            oneAndTwoPlusAHalf("2")
            delay(2100.milliseconds)
            println("2100millis")
        }
        println("***")
        runBlocking {
            val one = oneAndTwo("3")
            @Suppress("UNUSED_VARIABLE") val two = oneAndTwoPlusAHalf("3")
            delay(1700.milliseconds)
            one.cancel()
            println("Expecting one to do only one second, two to keep going.")
        }
        runBlocking {
            println("Waiting 2 seconds")
            delay(2.seconds)
        }
        println("***")
        runBlocking {
            oneAndTwo("999")
            oneAndTwoPlusAHalf("999")
            println("this should do nothing because test ends here")
        }
    }

    private fun oneAndTwo(id: String) =
        CoroutineTimerTask.start("one", 1.seconds) {
            println("ONE: One second passed / $id")
            launch {
                delay(1.seconds)
                println("ONE: Two seconds passed / $id/ isActive: $isActive")
            }
        }


    private fun oneAndTwoPlusAHalf(id: String) =
        CoroutineTimerTask.start("two", 1500.milliseconds) {
            println("TWO: 1.5 seconds / $id")
            launch {
                delay(1.seconds)
                println("TWO: 2.5 seconds / $id / isActive: $isActive")
            }
        }
}