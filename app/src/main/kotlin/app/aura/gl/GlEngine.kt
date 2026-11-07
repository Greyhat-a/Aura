package app.aura.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/** Growable interleaved vertex list: x y z r g b a size (8 floats per vertex). */
class FList {
    var a = FloatArray(8 * 2048)
    var n = 0
    fun clear() { n = 0 }
    fun v(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, al: Float, size: Float = 0f) {
        if (n + 8 > a.size) a = a.copyOf(a.size * 2)
        a[n] = x; a[n + 1] = y; a[n + 2] = z; a[n + 3] = r; a[n + 4] = g; a[n + 5] = b; a[n + 6] = al; a[n + 7] = size
        n += 8
    }
}

data class Rgb(val r: Float, val g: Float, val b: Float) {
    fun mix(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
    fun scale(s: Float) = Rgb((r * s).coerceAtMost(1f), (g * s).coerceAtMost(1f), (b * s).coerceAtMost(1f))
}

/** Per-frame geometry collector. Face/edge alpha are scaled by the user-selected animation style. */
class GlBatch {
    val tris = FList()
    val lines = FList()
    val pts = FList()
    var faceScale = 1f
    var edgeScale = 1f
    var pointScale = 1f

    fun clear() { tris.clear(); lines.clear(); pts.clear() }

    fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, c: Rgb, a: Float) {
        val al = a * faceScale
        if (al <= 0.001f) return
        tris.v(ax, ay, az, c.r, c.g, c.b, al); tris.v(bx, by, bz, c.r, c.g, c.b, al); tris.v(cx, cy, cz, c.r, c.g, c.b, al)
    }

    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, c: Rgb, a: Float,
    ) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, c, a)
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, c, a)
    }

    fun line(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, c: Rgb, a: Float) {
        val al = a * edgeScale
        if (al <= 0.001f) return
        lines.v(ax, ay, az, c.r, c.g, c.b, al); lines.v(bx, by, bz, c.r, c.g, c.b, al)
    }

    fun point(x: Float, y: Float, z: Float, size: Float, c: Rgb, a: Float) {
        pts.v(x, y, z, c.r, c.g, c.b, a, size * pointScale)
    }

    fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, c: Rgb, faceA: Float, edgeA: Float) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c, faceA)
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c, faceA)
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c, faceA)
        quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c, faceA)
        quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c, faceA * 1.4f)
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c, faceA * 0.7f)
        if (edgeA > 0f) {
            line(x0, y0, z0, x1, y0, z0, c, edgeA); line(x0, y0, z1, x1, y0, z1, c, edgeA)
            line(x0, y1, z0, x1, y1, z0, c, edgeA); line(x0, y1, z1, x1, y1, z1, c, edgeA)
            line(x0, y0, z0, x0, y1, z0, c, edgeA); line(x1, y0, z0, x1, y1, z0, c, edgeA)
            line(x0, y0, z1, x0, y1, z1, c, edgeA); line(x1, y0, z1, x1, y1, z1, c, edgeA)
            line(x0, y0, z0, x0, y0, z1, c, edgeA); line(x1, y0, z0, x1, y0, z1, c, edgeA)
            line(x0, y1, z0, x0, y1, z1, c, edgeA); line(x1, y1, z0, x1, y1, z1, c, edgeA)
        }
    }

    /** Horizontal circle (XZ plane) outline. */
    fun ringY(cx: Float, cy: Float, cz: Float, r: Float, segs: Int, c: Rgb, a: Float) {
        var px = cx + r; var pz = cz
        for (i in 1..segs) {
            val t = (i.toFloat() / segs) * 6.2831855f
            val x = cx + r * cos(t); val z = cz + r * sin(t)
            line(px, cy, pz, x, cy, z, c, a)
            px = x; pz = z
        }
    }

    fun sphereWire(cx: Float, cy: Float, cz: Float, r: Float, lats: Int, lons: Int, segs: Int, c: Rgb, a: Float) {
        for (i in 1 until lats) {
            val phi = Math.PI.toFloat() * i / lats
            ringY(cx, cy + r * cos(phi), cz, r * sin(phi), segs, c, a)
        }
        for (j in 0 until lons) {
            val th = 6.2831855f * j / lons
            var px = cx; var py = cy + r; var pz = cz
            for (i in 1..segs) {
                val phi = Math.PI.toFloat() * i / segs
                val x = cx + r * sin(phi) * cos(th); val y = cy + r * cos(phi); val z = cz + r * sin(phi) * sin(th)
                line(px, py, pz, x, y, z, c, a)
                px = x; py = y; pz = z
            }
        }
    }
}

/** Deterministic pseudo-random in [0,1) so particle layouts are stable between frames. */
object Hash {
    fun h(i: Int, k: Int): Float {
        var x = i * 374761393 + k * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777216f
    }
}

/** Real readings fed to scenes. null always means "not available"; scenes must never invent a value. */
class SceneData(
    val batteryPct: Float? = null, val charging: Boolean = false, val tempC: Float? = null,
    val powerW: Float? = null, val voltageMv: Float? = null, val currentMa: Float? = null,
    val coreCount: Int = 8, val coreActivity: List<Float?>? = null, val cpuActivity: Float? = null, val cpuHeat: Float? = null,
    val ramUsedFrac: Float? = null, val lowMemory: Boolean = false,
    val downMbps: Float? = null, val upMbps: Float? = null, val latencyMs: Float? = null, val connected: Boolean = false,
    val accent: Rgb = Rgb(0f, 0.7f, 1f),
)

interface GlScene {
    val cameraDistance: Float get() = 5.5f
    fun build(b: GlBatch, t: Float, dt: Float, d: SceneData)
}

class AuraRenderer : GLSurfaceView.Renderer {
    @Volatile var scene: GlScene? = null
    @Volatile var data: SceneData = SceneData()
    @Volatile var style = 0               // 0 hologram, 1 wireframe, 2 solid glow
    @Volatile var yaw = 25f
    @Volatile var pitch = 18f
    @Volatile var zoom = 1f
    @Volatile var autoRotate = true

    private val batch = GlBatch()
    private var program = 0
    private var aPos = 0; private var aColor = 0; private var aSize = 0
    private var uMvp = 0; private var uPoint = 0; private var uScale = 0
    private var buf: FloatBuffer? = null
    private val proj = FloatArray(16); private val view = FloatArray(16); private val model = FloatArray(16)
    private val vp = FloatArray(16); private val mvp = FloatArray(16)
    private var w = 1; private var h = 1
    private var t = 0f
    private var lastNs = 0L
    private var lastScene: GlScene? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)   // additive: glowing hologram look
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        program = link(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos"); aColor = GLES20.glGetAttribLocation(program, "aColor")
        aSize = GLES20.glGetAttribLocation(program, "aSize")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP"); uPoint = GLES20.glGetUniformLocation(program, "uPoint")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width.coerceAtLeast(1); h = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 45f, w.toFloat() / h, 0.1f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStart = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((frameStart - lastNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNs = frameStart
        t += dt
        if (autoRotate) yaw += dt * 12f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val s = scene ?: return
        if (s !== lastScene) { lastScene = s; t = 0f }

        batch.clear()
        when (style) {
            1 -> { batch.faceScale = 0f; batch.edgeScale = 1.3f; batch.pointScale = 0.8f }
            2 -> { batch.faceScale = 3.2f; batch.edgeScale = 1f; batch.pointScale = 1.1f }
            else -> { batch.faceScale = 1f; batch.edgeScale = 1f; batch.pointScale = 1f }
        }
        s.build(batch, t, dt, data)

        Matrix.setLookAtM(view, 0, 0f, 0f, s.cameraDistance * zoom, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uScale, h * 1.2f)
        GLES20.glLineWidth(2f)
        GLES20.glUniform1f(uPoint, 0f)
        draw(batch.tris, GLES20.GL_TRIANGLES)
        draw(batch.lines, GLES20.GL_LINES)
        GLES20.glUniform1f(uPoint, 1f)
        draw(batch.pts, GLES20.GL_POINTS)

        // Frame cap (~45 fps) to save battery; the 3D screen is the only place that renders continuously.
        val spent = (System.nanoTime() - frameStart) / 1_000_000
        if (spent < 22) try { Thread.sleep(22 - spent) } catch (_: InterruptedException) { }
    }

    private fun draw(l: FList, mode: Int) {
        if (l.n == 0) return
        var fb = buf
        if (fb == null || fb.capacity() < l.n) {
            fb = ByteBuffer.allocateDirect(l.a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            buf = fb
        }
        fb!!.clear(); fb.put(l.a, 0, l.n)
        val stride = 32
        fb.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, fb); GLES20.glEnableVertexAttribArray(aPos)
        fb.position(3); GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, fb); GLES20.glEnableVertexAttribArray(aColor)
        fb.position(7); GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, stride, fb); GLES20.glEnableVertexAttribArray(aSize)
        GLES20.glDrawArrays(mode, 0, l.n / 8)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    private companion object {
        const val VERT = """
            uniform mat4 uMVP; uniform float uScale;
            attribute vec3 aPos; attribute vec4 aColor; attribute float aSize;
            varying vec4 vColor;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vColor = aColor;
                gl_PointSize = max(1.0, aSize * uScale / max(gl_Position.w, 0.5));
            }"""
        const val FRAG = """
            precision mediump float;
            uniform float uPoint; varying vec4 vColor;
            void main() {
                if (uPoint > 0.5) {
                    float d = length(gl_PointCoord - vec2(0.5));
                    if (d > 0.5) discard;
                    gl_FragColor = vec4(vColor.rgb, vColor.a * smoothstep(0.5, 0.0, d));
                } else {
                    gl_FragColor = vColor;
                }
            }"""
    }
}
