package com.mutissx.soundframe

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mutissx.soundframe.app.MainActivity
import com.mutissx.soundframe.app.navigation.GenerationRoute
import com.mutissx.soundframe.core.audio.DemoSignal
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.ArtDirection
import com.mutissx.soundframe.domain.usecase.ImportAudioUseCase
import com.mutissx.soundframe.feature.generation.GenerationScreen
import com.mutissx.soundframe.feature.generation.GenerationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import kotlin.math.sin

/**
 * Frame-time harness for the artwork engine. Not a correctness test: each scenario resets the
 * platform frame stats, runs on the real Choreographer clock, then logs `dumpsys gfxinfo`
 * percentiles and a per-stage breakdown under the SF_PERF tag. Skipped unless `sfPerf` is passed;
 * `sfOnly` optionally selects scenarios by name prefix:
 * `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mutissx.soundframe.RenderPerfHarness
 * -Pandroid.testInstrumentationRunnerArguments.sfPerf=true [-Pandroid.testInstrumentationRunnerArguments.sfOnly=steady/,style-switch]`
 * then read the results with `adb logcat -d -s SF_PERF`.
 */
@RunWith(AndroidJUnit4::class)
class RenderPerfHarness {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    @Before
    fun launch() {
        assumeTrue("Opt-in: pass the sfPerf instrumentation argument", InstrumentationRegistry.getArguments().getString("sfPerf") != null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
    }

    /**
     * Posts to the UI thread without waiting for it to go idle: a scene that saturates the main
     * thread would otherwise block `onActivity` forever.
     */
    private fun onUi(block: MainActivity.() -> Unit) {
        val done = java.util.concurrent.CountDownLatch(1)
        activity.runOnUiThread { activity.block(); done.countDown() }
        done.await()
    }

    @After
    fun close() {
        if (!::scenario.isInitialized) return
        onUi { setContent {} } // stop animating so the scenario can reach idle and close
        scenario.close()
    }

    @Test
    fun generationSequence() {
        for (style in ArtStyle.entries) {
            if (!wanted("generation/$style")) continue
            val features = featuresFor(style)
            val sessions = get<CreationSessionRepository>(CreationSessionRepository::class.java)
            val id = runBlocking {
                sessions.create(syntheticClip(), AudioSource.RECORDED).id.also { sessions.attachFeatures(it, features) }
            }
            measure("generation/$style", 7_000) {
                onUi {
                    setContent {
                        // key() so each run gets a fresh ViewModel and sequence instead of reusing the
                        // previous run's remembered state.
                        SoundframeTheme { key(id) {
                            val vm = remember {
                                GenerationViewModel(GenerationRoute(sessionId = id), get(CreationSessionRepository::class.java), get(ImportAudioUseCase::class.java), get(AnalyzeSessionUseCase::class.java), get(TitleVocabularyProvider::class.java))
                            }
                            GenerationScreen(vm, onReady = {}, onBack = {})
                        } }
                    }
                }
            }
            Thread.sleep(2_500) // let the sequence finish; the screen then idles on the artwork
            measure("generation-idle/$style", 3_000) {}
        }
    }

    /** Cycles the style on a live canvas, like tapping styles in the editor: one crossfade per second. */
    @Test
    fun styleSwitch() {
        if (!wanted("style-switch")) return
        val features = featuresFor(ArtStyle.PARTICLES)
        val base = ArtDirection.recommendSettings(features)
        measure("style-switch", 5_500) {
            onUi {
                setContent {
                    SoundframeTheme {
                        AmbientBackground()
                        var index by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(1_000)
                                index = (index + 1) % ArtStyle.entries.size
                            }
                        }
                        ArtworkCanvas(features, base.copy(style = ArtStyle.entries[index], animate = false), Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }
        }
    }

    /** Deterministic stills of every style mid-animation, for before/after visual comparison. */
    @Test
    fun renderStills() {
        if (!wanted("stills")) return
        val dir = activity.getExternalFilesDir("perf-stills")!!.apply { mkdirs() }
        for (style in ArtStyle.entries) {
            val settings = ArtDirection.recommendSettings(featuresFor(style))
            val scene = com.mutissx.soundframe.core.graphics.ArtScene.from(featuresFor(style), settings)
            val art = com.mutissx.soundframe.core.graphics.ArtworkComposer.prepare(style, scene, 1080, 1080)
            val bitmap = android.graphics.Bitmap.createBitmap(1080, 1080, android.graphics.Bitmap.Config.ARGB_8888)
            com.mutissx.soundframe.core.graphics.ArtworkComposer.drawFrame(art, android.graphics.Canvas(bitmap), 3.7f)
            java.io.File(dir, "$style.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        // The app (and its files) are uninstalled after the run, so stage the stills where adb can pull them.
        shell("mkdir -p /data/local/tmp/sf-stills")
        for (style in ArtStyle.entries) shell("cp ${dir.absolutePath}/$style.png /data/local/tmp/sf-stills/")
    }

    @Test
    fun ambientOnly() {
        if (!wanted("steady/ambient-only")) return
        onUi { setContent { SoundframeTheme { AmbientBackground() } } }
        Thread.sleep(1_000)
        measure("steady/ambient-only", 4_000) {}
    }

    @Test
    fun steadyState() {
        for (style in ArtStyle.entries) {
            if (!wanted("steady/$style")) continue
            val features = featuresFor(style)
            val settings = ArtDirection.recommendSettings(features)
            onUi {
                setContent {
                    SoundframeTheme {
                        AmbientBackground()
                        ArtworkCanvas(features, settings, Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
            }
            Thread.sleep(2_000) // let preparation and the first frames settle
            measure("steady/$style", 5_000) {}
        }
    }

    /** Main preview plus the five style thumbnails while a slider is dragged, like the editor. */
    @Test
    fun editorDrag() {
        if (!wanted("editor-drag")) return
        val features = featuresFor(ArtStyle.AURA)
        val base = ArtDirection.recommendSettings(features)
        measure("editor-drag", 5_000) {
            onUi {
                setContent {
                    SoundframeTheme {
                        AmbientBackground()
                        var intensity by remember { mutableFloatStateOf(0.2f) }
                        LaunchedEffect(Unit) {
                            var t = 0f
                            while (true) {
                                delay(16)
                                t += 0.016f
                                intensity = 0.5f + 0.45f * sin(t * 2f)
                            }
                        }
                        val settings = base.copy(intensity = intensity)
                        Column {
                            ArtworkCanvas(features, settings, Modifier.fillMaxWidth().aspectRatio(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (style in ArtStyle.entries) {
                                    ArtworkCanvas(features, settings.copy(style = style, animate = false), Modifier.size(86.dp), animate = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Optional `-e sfOnly prefix1,prefix2` instrumentation argument to run a subset of scenarios. */
    private fun wanted(name: String): Boolean {
        val only = InstrumentationRegistry.getArguments().getString("sfOnly") ?: return true
        return only.split(',').any { name.startsWith(it.trim()) }
    }

    private fun measure(name: String, durationMs: Long, start: () -> Unit) {
        shell("dumpsys gfxinfo $PACKAGE reset")
        start()
        Thread.sleep(durationMs)
        val out = shell("dumpsys gfxinfo $PACKAGE framestats")
        fun field(label: String) = Regex("$label: ([^\\n]+)").find(out)?.groupValues?.get(1)?.trim() ?: "?"
        Log.i(
            "SF_PERF",
            "$name | frames=${field("Total frames rendered")} janky=${field("Janky frames")} " +
                "p50=${field("50th percentile")} p90=${field("90th percentile")} p95=${field("95th percentile")} p99=${field("99th percentile")} " +
                stageBreakdown(out),
        )
    }

    /** Median per-stage milliseconds over the last ~120 frames from `gfxinfo framestats`. */
    private fun stageBreakdown(out: String): String {
        val lines = out.lines()
        val headerAt = lines.indexOfFirst { it.startsWith("Flags,") }
        if (headerAt < 0) return ""
        val cols = lines[headerAt].split(',').withIndex().associate { (i, n) -> n to i }
        val rows = lines.drop(headerAt + 1).takeWhile { it.isNotBlank() && !it.startsWith("---") }
            .map { it.split(',') }.filter { it.size >= cols.size - 1 && it[0] == "0" }
        fun median(from: String, to: String): String {
            val a = cols[from] ?: return "?"
            val b = cols[to] ?: return "?"
            val v = rows.mapNotNull { r -> val x = r[a].toLongOrNull(); val y = r[b].toLongOrNull(); if (x != null && y != null && x > 0 && y > 0) (y - x) / 1e6 else null }.sorted()
            return if (v.isEmpty()) "?" else "%.1f".format(v[v.size / 2])
        }
        fun span(r: List<String>, from: String, to: String): Double? {
            val x = cols[from]?.let { r[it].toLongOrNull() } ?: return null
            val y = cols[to]?.let { r[it].toLongOrNull() } ?: return null
            return if (x > 0 && y > 0) (y - x) / 1e6 else null
        }
        /** Worst frame's total, with where its time went: main-thread work vs RenderThread issue. */
        fun max(from: String, to: String): String {
            val worst = rows.maxByOrNull { span(it, from, to) ?: -1.0 } ?: return "?"
            fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "?"
            return "${f(span(worst, from, to))}(input+anim=${f(span(worst, "IntendedVsync", "PerformTraversalsStart"))} " +
                "layout=${f(span(worst, "PerformTraversalsStart", "DrawStart"))} record=${f(span(worst, "DrawStart", "SyncQueued"))} " +
                "sync=${f(span(worst, "SyncStart", "IssueDrawCommandsStart"))} issue=${f(span(worst, "IssueDrawCommandsStart", "SwapBuffers"))})"
        }
        fun medianDuration(name: String): String {
            val a = cols[name] ?: return "?"
            val v = rows.mapNotNull { it[a].toLongOrNull()?.div(1e6) }.sorted()
            return if (v.isEmpty()) "?" else "%.1f".format(v[v.size / 2])
        }
        return "| record=${median("DrawStart", "SyncQueued")} main=${median("IntendedVsync", "SyncQueued")} " +
            "rt=${median("SyncStart", "SwapBuffers")} issue=${median("IssueDrawCommandsStart", "SwapBuffers")} " +
            "dequeue=${medianDuration("DequeueBufferDuration")} gpu=${median("SwapBuffers", "GpuCompleted")} max=${max("IntendedVsync", "FrameCompleted")}"
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun syntheticClip(): AudioClip {
        val rate = AudioClip.DEFAULT_SAMPLE_RATE
        return AudioClip(ShortArray(rate * 4) { (sin(it * 2.0 * Math.PI * 220 / rate) * 8000).toInt().toShort() }, rate)
    }

    /** Feature profiles that steer [ArtDirection.recommendSettings] to each style. */
    private fun featuresFor(style: ArtStyle): AudioFeatures {
        val base = DemoSignal.features(DemoSignal.Preset.MUSIC)
        val features = when (style) {
            ArtStyle.ECHO -> base.copy(rhythmicRegularity = 0.8f, transientDensity = 2f, spectralFlatness = 0.08f)
            ArtStyle.PARTICLES -> base.copy(rhythmicRegularity = null, transientDensity = 3.5f, spectralFlatness = 0.33f)
            ArtStyle.TOPOGRAPHY -> base.copy(rhythmicRegularity = null, transientDensity = 1f, spectralFlatness = 0.3f)
            ArtStyle.BLOOM -> base.copy(rhythmicRegularity = null, transientDensity = 2f, spectralFlatness = 0.05f)
            ArtStyle.AURA -> base.copy(rhythmicRegularity = null, transientDensity = 0.5f, spectralFlatness = 0.05f)
        }
        assertEquals(style, ArtDirection.recommendSettings(features).style)
        return features
    }

    private companion object {
        const val PACKAGE = "com.mutissx.soundframe"
    }
}
