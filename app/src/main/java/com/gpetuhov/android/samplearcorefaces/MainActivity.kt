package com.gpetuhov.android.samplearcorefaces

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.AugmentedFaceNode
import com.pawegio.kandroid.toast

class MainActivity : AppCompatActivity() {

    companion object {
        const val MIN_OPENGL_VERSION = 3.0
    }

    private var arFragment: FaceArFragment? = null
    private var modelRenderable: ModelRenderable? = null
    private val faceNodeMap = mutableMapOf<AugmentedFace, AugmentedFaceNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }

        setContentView(R.layout.activity_main)
        arFragment = supportFragmentManager.findFragmentById(R.id.faceFragment) as FaceArFragment

        loadModel()

        // This is important to make sure that the camera stream renders first so that
        // the face mesh occlusion works correctly.
        arFragment?.arSceneView?.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST

        arFragment?.arSceneView?.scene?.addOnUpdateListener(::onUpdateFrame)
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * Finishes the activity if Sceneform can not run
     */
    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toast("Sceneform requires Android N or later")
            activity.finish()
            return false
        }

        val openGlVersionString = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion

        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            toast("Sceneform requires OpenGL ES 3.0 or later")
            activity.finish()
            return false
        }

        return true
    }

    private fun loadModel() {
        // Load the face regions renderable.
        // This is a skinned model that renders 3D objects mapped to the regions of the augmented face.
        ModelRenderable.builder()
            .setSource(this, Uri.parse("file:///android_asset/model.sfb"))
            .build()
            .thenAccept { renderable ->
                modelRenderable = renderable
                modelRenderable?.isShadowCaster = false
                modelRenderable?.isShadowReceiver = false
            }
            .exceptionally { throwable ->
                toast("Unable to load renderable")
                null
            }

        // In this example we have no texture, only model
    }

    private fun onUpdateFrame(frameTime: FrameTime) {
        if (modelRenderable == null) {
            return
        }

        val faceList = arFragment?.arSceneView?.session?.getAllTrackables(AugmentedFace::class.java) ?: mutableListOf()

        // Make new AugmentedFaceNodes for any new faces.
        for (face in faceList) {
            if (!faceNodeMap.containsKey(face)) {
                val faceNode = AugmentedFaceNode(face)
                faceNode.setParent(arFragment?.arSceneView?.scene)
                faceNode.faceRegionsRenderable = modelRenderable
                faceNodeMap[face] = faceNode
            }
        }

        // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
        val iter = faceNodeMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val face = entry.key
            if (face.trackingState == TrackingState.STOPPED) {
                val faceNode = entry.value
                faceNode.setParent(null)
                iter.remove()
            }
        }
    }
}
