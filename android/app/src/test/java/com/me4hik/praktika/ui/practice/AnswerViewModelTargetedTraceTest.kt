// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted answer save trace
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedException
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedReason
import com.me4hik.praktika.data.read.AnswerReadResult
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerViewModelTargetedTraceTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var eventsFile: File
    private lateinit var readRepository: AnswerViewModelTestSupport.FakeAnswerReadRepository
    private lateinit var saveCommand: AnswerViewModelTestSupport.RecordingSaveAnswerCommand
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventsFile = File.createTempFile("targeted-answer-trace", ".jsonl")
        DiagnosticsRecorder.installForTests(eventsFile)
        readRepository = AnswerViewModelTestSupport.FakeAnswerReadRepository()
        saveCommand = AnswerViewModelTestSupport.RecordingSaveAnswerCommand()
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        DiagnosticsRecorder.resetForTests()
        eventsFile.delete()
    }

    private fun createViewModel(): AnswerViewModel {
        return AnswerViewModel(
            occurrenceId = AnswerViewModelTestSupport.OCCURRENCE_ID,
            readRepository = readRepository,
            saveAnswerCommand = saveCommand,
            savedStateHandle = savedStateHandle,
            commandDispatcher = testDispatcher,
        )
    }

    private fun userEvents(): List<Pair<String, Map<String, String>>> {
        return DiagnosticsRecorder.get().getBufferedEvents()
            .filter { it.category == DiagnosticCategory.USER }
            .map { it.name to it.metadata }
    }

    private fun awaitUserEvent(
        name: String,
        predicate: (Map<String, String>) -> Boolean = { true },
    ): Pair<String, Map<String, String>>? {
        repeat(50) {
            val hit = userEvents().lastOrNull { (eventName, meta) ->
                eventName == name && predicate(meta)
            }
            if (hit != null) {
                return hit
            }
            Thread.sleep(50)
        }
        return null
    }

    @Test
    fun saveAnswer_successRecordsAttemptAndResultTrace() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("trace answer")
        viewModel.saveAnswer()
        advanceUntilIdle()

        val successAttempt = awaitUserEvent("answer_save_attempt") { it["draft_blank"] == "false" }
        checkNotNull(successAttempt)
        assertEquals(AnswerViewModelTestSupport.OCCURRENCE_ID.toString(), successAttempt.second["occurrence_id"])

        val successResult = awaitUserEvent("answer_save_result") { it["result"] == "success" }
        checkNotNull(successResult)
        assertEquals(AnswerViewModelTestSupport.OCCURRENCE_ID.toString(), successResult.second["occurrence_id"])
        assertEquals("SUCCESS", successResult.second["reason_enum"])
    }

    @Test
    fun saveAnswer_blankRecordsAttemptAndBlankResult() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.saveAnswer()

        val blankAttempt = awaitUserEvent("answer_save_attempt") { it["draft_blank"] == "true" }
        checkNotNull(blankAttempt)
        val blankResult = awaitUserEvent("answer_save_result") { it["result"] == "blank" }
        checkNotNull(blankResult)
        assertEquals("BLANK", blankResult.second["reason_enum"])
        assertEquals(0, saveCommand.invocations)
    }

    @Test
    fun saveAnswer_blockedRecordsExactReasonEnum() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        saveCommand.exception = CycleAnswerNotAllowedException(
            CycleAnswerNotAllowedReason.ANSWER_ALREADY_EXISTS,
            "test blocked",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("blocked")
        viewModel.saveAnswer()
        advanceUntilIdle()

        val result = awaitUserEvent("answer_save_result") { it["result"] == "blocked" }
        checkNotNull(result)
        assertEquals("blocked", result.second["result"])
        assertEquals(CycleAnswerNotAllowedReason.ANSWER_ALREADY_EXISTS.name, result.second["reason_enum"])
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
