package com.breakyuna.esjzone.offline

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A document the user picked as the export destination (an SAF `CreateDocument`
 * result on Android). Kept free of Android types so the controller is JVM-testable.
 */
interface NovelExportTarget {
    /** Opens the document for writing with [mode]; `null` when the provider refuses. */
    fun openOutputStream(mode: String): OutputStream?

    /** Removes the document. Returns `true` only when it is gone. Must not throw. */
    fun delete(): Boolean
}

sealed interface NovelExportOutcome {
    data object Success : NovelExportOutcome

    /** The export threw; [removed] tells whether the partial document was deleted. */
    data class Failed(val removed: Boolean, val error: Throwable?) : NovelExportOutcome

    /** The owning scope went away mid-export (page left / ViewModel cleared). */
    data class Cancelled(val removed: Boolean) : NovelExportOutcome

    /** Another export was still running; the new [removed] target was discarded. */
    data class Busy(val removed: Boolean) : NovelExportOutcome
}

/**
 * Runs one export at a time in [scope] (the novel page's ViewModel scope), so a
 * configuration change no longer cancels it. Every non-success path removes the
 * document that the picker already created, so no truncated TXT or broken EPUB
 * is left behind.
 */
class NovelExportController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val onOutcome: (NovelExportOutcome) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val exporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = exporting.asStateFlow()

    /**
     * Writes into [target]. [write] runs on [ioDispatcher] and receives a
     * `checkActive` callback it should invoke between chapters so cancellation
     * stops the work promptly instead of after the whole book is written.
     */
    fun start(
        target: NovelExportTarget,
        write: (output: OutputStream, checkActive: () -> Unit) -> Unit
    ): Job? {
        if (!running.compareAndSet(false, true)) {
            scope.launch {
                withContext(NonCancellable) {
                    val removed = withContext(ioDispatcher) { target.delete() }
                    onOutcome(NovelExportOutcome.Busy(removed))
                }
            }
            return null
        }
        exporting.value = true
        return scope.launch {
            var written = false
            try {
                try {
                    withContext(ioDispatcher) {
                        val job = coroutineContext.job
                        val output = target.openOutputStream(TRUNCATE_WRITE_MODE)
                            ?: error("Unable to open the selected file")
                        output.use { stream -> write(stream) { job.ensureActive() } }
                    }
                    written = true
                } catch (error: CancellationException) {
                    // Cleanup and report inside one NonCancellable block: a
                    // withContext(IO) called straight from the cancelled coroutine
                    // would run, but its result is discarded on return (prompt
                    // cancellation), which would skip the report.
                    withContext(NonCancellable) {
                        val removed = withContext(ioDispatcher) { target.delete() }
                        onOutcome(NovelExportOutcome.Cancelled(removed))
                    }
                    throw error
                } catch (error: Exception) {
                    withContext(NonCancellable) {
                        val removed = withContext(ioDispatcher) { target.delete() }
                        onOutcome(NovelExportOutcome.Failed(removed, error))
                    }
                }
            } finally {
                exporting.value = false
                running.set(false)
            }
            // Outside the try: a throwing reporter must never delete a finished file.
            if (written) onOutcome(NovelExportOutcome.Success)
        }
    }

    companion object {
        /** "w" may leave trailing bytes on some providers; "wt" always truncates. */
        const val TRUNCATE_WRITE_MODE = "wt"
    }
}
