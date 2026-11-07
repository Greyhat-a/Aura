package app.aura.monitor

import app.aura.core.ChargeMath
import app.aura.data.SpeedResultEntity
import app.aura.settings.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

data class SpeedServer(val id: String, val name: String, val downUrl: String, val upUrl: String?, val pingHost: String)

object SpeedServers {
    val cloudflare = SpeedServer(
        "cloudflare", "Cloudflare (speed.cloudflare.com)",
        "https://speed.cloudflare.com/__down?bytes=25000000", "https://speed.cloudflare.com/__up", "speed.cloudflare.com",
    )

    /** User supplied endpoint, e.g. a self-hosted LibreSpeed / nginx. Must be https:// (cleartext is blocked by default). */
    fun custom(s: Settings): SpeedServer? =
        if (s.customDown.startsWith("https://"))
            SpeedServer("custom", "Custom (${s.customDown.substringAfter("://").substringBefore('/')})",
                s.customDown, s.customUp.takeIf { it.startsWith("https://") },
                s.customPing.ifBlank { s.customDown.substringAfter("://").substringBefore('/').substringBefore(':') })
        else null

    fun all(s: Settings): List<SpeedServer> = listOfNotNull(cloudflare, custom(s))
    fun selected(s: Settings): SpeedServer = all(s).firstOrNull { it.id == s.serverId } ?: cloudflare
}

data class SpeedState(
    val running: Boolean = false,
    val phase: String = "Idle",
    val liveMbps: Float = 0f,
    val pingMs: Float? = null, val jitterMs: Float? = null, val lossPct: Float? = null,
    val downMbps: Float? = null, val upMbps: Float? = null,
    val series: List<Float> = emptyList(),
    val error: String? = null,
)

/**
 * Real active speed test: parallel HTTPS streams for a fixed duration, first second discarded as TCP ramp-up.
 * Upload counts bytes handed to the socket (flushed per 32 KB), so it can slightly over-read on very fast links.
 */
class SpeedTester {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()

    private val _state = MutableStateFlow(SpeedState())
    val state: StateFlow<SpeedState> = _state.asStateFlow()

    suspend fun run(server: SpeedServer, networkLabel: String, downSeconds: Int = 8, upSeconds: Int = 8): SpeedResultEntity? {
        _state.value = SpeedState(running = true, phase = "Ping")
        var down: Float? = null
        var up: Float? = null
        var ping: Float? = null
        var jitter: Float? = null
        var loss: Float? = null
        try {
            // ---- ping / jitter / loss ----
            val addr = withContext(Dispatchers.IO) { InetAddress.getByName(server.pingHost) }
            val rtts = ArrayList<Float>()
            var lost = 0
            val n = 10
            val probe = LatencyProbeLite()
            repeat(n) {
                val t = probe.rtt(addr)
                if (t == null) lost++ else rtts += t
                delay(100)
            }
            ping = ChargeMath.median(rtts); jitter = ChargeMath.jitterMs(rtts); loss = lost * 100f / n
            _state.value = _state.value.copy(pingMs = ping, jitterMs = jitter, lossPct = loss, phase = "Download")

            // ---- download ----
            down = measure("Download", downSeconds, 4) { counter, deadline, calls ->
                while (isActive && System.nanoTime() < deadline) {
                    val call = client.newCall(Request.Builder().url(server.downUrl).header("Cache-Control", "no-store").build())
                    calls += call
                    try {
                        call.execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val stream = resp.body!!.byteStream()
                            val buf = ByteArray(65536)
                            while (isActive && System.nanoTime() < deadline) {
                                val r = stream.read(buf)
                                if (r < 0) break
                                counter.addAndGet(r.toLong())
                            }
                        }
                    } catch (e: IOException) {
                        if (isActive && System.nanoTime() < deadline) throw e
                    }
                }
            }
            _state.value = _state.value.copy(downMbps = down, phase = "Upload", liveMbps = 0f, series = emptyList())

            // ---- upload ----
            if (server.upUrl != null && upSeconds > 0) {
                up = measure("Upload", upSeconds, 3) { counter, deadline, calls ->
                    while (isActive && System.nanoTime() < deadline) {
                        val call = client.newCall(Request.Builder().url(server.upUrl).post(CountingBody(4_000_000, counter, deadline)).build())
                        calls += call
                        try {
                            call.execute().use { resp -> if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}") }
                        } catch (e: IOException) {
                            if (isActive && System.nanoTime() < deadline) throw e
                        }
                    }
                }
            }
            _state.value = _state.value.copy(upMbps = up, running = false, phase = "Done", liveMbps = 0f)
        } catch (e: CancellationException) {
            _state.value = _state.value.copy(running = false, phase = "Cancelled")
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, phase = "Failed", error = e.message ?: e.javaClass.simpleName)
        }
        if (down == null && up == null && ping == null) return null
        return SpeedResultEntity(
            ts = System.currentTimeMillis(), server = server.name, network = networkLabel,
            downMbps = down, upMbps = up, pingMs = ping, jitterMs = jitter, lossPct = loss,
        )
    }

    private suspend fun measure(
        phase: String, seconds: Int, streams: Int,
        worker: suspend CoroutineScope.(AtomicLong, Long, ConcurrentLinkedQueue<Call>) -> Unit,
    ): Float = coroutineScope {
        val counter = AtomicLong(0)
        val calls = ConcurrentLinkedQueue<Call>()
        val start = System.nanoTime()
        val deadline = start + seconds * 1_000_000_000L
        var warmBytes = -1L
        var warmNs = 0L
        val sampler = launch {
            var lastB = 0L
            var lastT = start
            val series = ArrayList<Float>()
            while (isActive) {
                delay(250)
                val now = System.nanoTime()
                val b = counter.get()
                val live = ((b - lastB) * 8.0 / ((now - lastT) / 1e9) / 1e6).toFloat()
                lastB = b; lastT = now
                series += live
                if (warmBytes < 0 && now - start >= 1_000_000_000L) { warmBytes = b; warmNs = now }
                _state.value = _state.value.copy(phase = phase, liveMbps = live, series = series.toList())
            }
        }
        val jobs = (1..streams).map { launch(Dispatchers.IO) { worker(counter, deadline, calls) } }
        try {
            jobs.joinAll()
        } finally {
            calls.forEach { it.cancel() }
            sampler.cancel()
        }
        val endNs = minOf(System.nanoTime(), deadline + 500_000_000L)
        val bytes = counter.get()
        if (warmBytes >= 0 && endNs > warmNs && bytes > warmBytes)
            ((bytes - warmBytes) * 8.0 / ((endNs - warmNs) / 1e9) / 1e6).toFloat()
        else
            (bytes * 8.0 / ((endNs - start) / 1e9) / 1e6).toFloat()
    }

    private class CountingBody(val total: Long, val counter: AtomicLong, val deadline: Long) : RequestBody() {
        private val chunk = Random.nextBytes(32768)
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = total
        override fun writeTo(sink: BufferedSink) {
            var left = total
            while (left > 0) {
                if (System.nanoTime() > deadline) throw IOException("deadline reached")
                val n = minOf(left, chunk.size.toLong()).toInt()
                sink.write(chunk, 0, n)
                sink.flush()
                counter.addAndGet(n.toLong())
                left -= n
            }
        }
    }

    private class LatencyProbeLite {
        suspend fun rtt(addr: InetAddress): Float? = withContext(Dispatchers.IO) {
            val s = java.net.Socket()
            try {
                val t0 = System.nanoTime()
                s.connect(java.net.InetSocketAddress(addr, 443), 1500)
                (System.nanoTime() - t0) / 1e6f
            } catch (_: Exception) { null } finally { try { s.close() } catch (_: Exception) { } }
        }
    }
}
