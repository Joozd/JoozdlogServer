package utils

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * This is its own CoroutineContext. This means any actions can launch child jobs which will be inside this scope.
 * The scope will be cancelled after completing task.
 * make sure [action] waits for all child jobs that need to finish!
 */
class CoroutineTimerTask(
    name: String = NO_NAME_SET,
    private val delay: Duration = Duration.ZERO,
    private val repeat: Duration? = null,
    private val action: suspend CoroutineTimerTask.() -> Unit
): CoroutineScope {
    /**
     * The context of this scope.
     * Context is encapsulated by the scope and used for implementation of coroutine builders that are extensions on the scope.
     * Accessing this property in general code is not recommended for any purposes except accessing the [Job] instance for advanced usages.
     *
     * By convention, should contain an instance of a [job][Job] to enforce structured concurrency.
     */
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    // holds the currently running Job.
    // null if no job running.
    private var job: Job? = null

    private val tryAction = suspend {
        try {
            this.action()
        } catch (e: Throwable) {
            Logger.singleton.c("CoroutineTimerTask: \"$name\" timer action threw $e")
            cancel()
        }
    }

    fun start(){
        job = launch(coroutineContext){
            delay(delay)
            if (repeat != null) {
                while (isActive) {
                    tryAction()
                    delay(repeat)
                }
            } else {
                if (isActive) {
                    tryAction()
                }
            }
            cancel() // cancels this CoroutineTimerTask's job
        }
    }

    fun cancel(){
        job?.cancel()
    }

    companion object{
        private const val NO_NAME_SET = "Unnamed CoroutineTimerTask"
        fun start(
            name: String = NO_NAME_SET,
            delay: Duration = Duration.ZERO,
            repeat: Duration? = null,
            action: suspend CoroutineTimerTask.() -> Unit
        ): CoroutineTimerTask =
            CoroutineTimerTask(name, delay, repeat, action).also { it.start() }
    }

}