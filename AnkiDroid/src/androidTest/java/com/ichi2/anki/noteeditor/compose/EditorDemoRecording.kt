// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Emulator-only recording; restores the exact touch settings even after a failed take. */
internal class EditorDemoRecording(
    private val device: UiDevice,
) : AutoCloseable {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.getExternalFilesDir(null), "editor-demo").apply { mkdirs() }
    private val touches = shell("settings get system show_touches").trim()
    private val pointer = shell("settings get system pointer_location").trim()
    private val events = JSONArray()
    private val frames = JSONObject()
    private val inputs = JSONArray()
    private var started = 0L
    private var pid: Int? = null
    private var recorderOutput: ParcelFileDescriptor? = null
    private var recorderReader: Thread? = null
    private val recorderError = AtomicReference<Throwable>()
    private var successful = false

    init {
        check(InstrumentedDemoEnvironment.isAllowed()) { "Demo settings and recording require the isolated emulator probe" }
    }

    fun start() {
        shell("settings put system show_touches 1")
        shell("settings put system pointer_location 0")
        check(shell("settings get system show_touches").trim() == "1")
        check(shell("pidof screenrecord").isBlank()) { "Another recording is already running" }
        directory.listFiles()?.forEach { check(it.delete()) { "Could not clear previous artifact: $it" } }
        recorderOutput =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screenrecord --bit-rate 10000000 --time-limit 0 ${directory.absolutePath}/take.mp4",
            )
        val output = checkNotNull(recorderOutput)
        recorderReader =
            thread(name = "editor-demo-recorder") {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(output).use { input ->
                        File(directory, "screenrecord.log").outputStream().use { input.copyTo(it) }
                    }
                }.exceptionOrNull()?.let(recorderError::set)
            }
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (pid == null && SystemClock.elapsedRealtime() < deadline) {
            pid = shell("pidof screenrecord").trim().toIntOrNull()
            SystemClock.sleep(50)
        }
        checkNotNull(pid) { "screenrecord did not start" }
        SystemClock.sleep(400)
        started = SystemClock.elapsedRealtime()
    }

    fun scene(
        name: String,
        minimumMillis: Long = 0,
        action: () -> Unit,
    ) {
        val from = SystemClock.elapsedRealtime()
        val event = JSONObject().put("scene", name).put("startMs", from - started)
        events.put(event)
        writeEvents()
        action()
        val remaining = minimumMillis - (SystemClock.elapsedRealtime() - from)
        if (remaining > 0) SystemClock.sleep(remaining)
        event.put("endMs", SystemClock.elapsedRealtime() - started)
        writeEvents()
    }

    fun finish() {
        successful = true
    }

    /** Android's native touch sprite does not display UiAutomation-injected input. */
    fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        if (started == 0L) return
        inputs.put(
            JSONObject()
                .put("elapsedNanos", SystemClock.elapsedRealtimeNanos())
                .put("action", action)
                .put("x", x)
                .put("y", y),
        )
    }

    /** Extract these frames from the video afterward; screenshots must not stall on-camera input. */
    fun markFrame(name: String) {
        frames.put(name, SystemClock.elapsedRealtime() - started)
        File(directory, "frames.json").writeText(frames.toString(2))
    }

    private fun capture(name: String) {
        device.takeScreenshot(File(directory, "$name.png"))
    }

    override fun close() {
        val duration = SystemClock.elapsedRealtime() - started
        try {
            if (!successful) {
                runCatching {
                    capture("failure")
                    device.dumpWindowHierarchy(File(directory, "failure.xml"))
                }
            }
            pid?.let { recordingPid ->
                shell("kill -2 $recordingPid")
                recorderReader?.join(15_000)
                check(recorderReader?.isAlive != true) { "screenrecord output did not close" }
                recorderError.get()?.let { throw IllegalStateException("Could not read screenrecord output", it) }
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (shell("pidof screenrecord").trim() == recordingPid.toString() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(100)
                }
                check(shell("pidof screenrecord").trim() != recordingPid.toString()) { "screenrecord did not stop" }
            }
            if (successful) check(File(directory, "take.mp4").length() > 0) { "Recording is empty" }
            File(directory, "touches.json").writeText(inputs.toString(2))
            File(directory, "result.json").writeText(
                JSONObject()
                    .put("passed", successful)
                    .put("durationMs", duration)
                    .put("touches", "timestamped input; render with tools/render-editor-demo.py")
                    .put("startedElapsedNanos", started * 1_000_000)
                    .put("scenes", events)
                    .toString(2),
            )
        } finally {
            recorderOutput?.close()
            restore("show_touches", touches)
            restore("pointer_location", pointer)
        }
    }

    private fun writeEvents() = File(directory, "timeline.json").writeText(events.toString(2))

    private fun restore(
        key: String,
        value: String,
    ) {
        if (value == "null") shell("settings delete system $key") else shell("settings put system $key $value")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor
            .AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
            ).use {
                InputStreamReader(it).readText()
            }
}

internal object InstrumentedDemoEnvironment {
    fun isAllowed(): Boolean =
        com.ichi2.anki.tests.InstrumentedTest
            .isEmulator() &&
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.packageName
                .endsWith(".composefieldsprobe")
}
