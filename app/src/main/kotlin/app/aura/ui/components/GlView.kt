package app.aura.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aura.gl.AuraRenderer

/**
 * Hosts the OpenGL ES 2.0 surface. Rendering runs only while the screen is RESUMED, so the 3D engine costs
 * nothing in the background. Gestures: drag = rotate, pinch = zoom, double-tap = toggle auto-rotate / reset zoom.
 */
@Composable
fun GlView(renderer: AuraRenderer, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(renderer) {
        GLSurfaceView(ctx).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    DisposableEffect(lifecycle, view) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); view.onPause() }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize()
                .pointerInput(renderer) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        renderer.autoRotate = false
                        renderer.yaw += pan.x * 0.35f
                        renderer.pitch = (renderer.pitch + pan.y * 0.35f).coerceIn(-80f, 80f)
                        renderer.zoom = (renderer.zoom / zoom).coerceIn(0.45f, 2.2f)
                    }
                }
                .pointerInput(renderer) {
                    detectTapGestures(onDoubleTap = { renderer.autoRotate = !renderer.autoRotate; renderer.zoom = 1f })
                },
        )
    }
}
