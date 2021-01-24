package no.realitylab.arface

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import kotlinx.android.synthetic.main.activity_glasses.*
import android.graphics.Bitmap
import android.os.Environment.getExternalStorageDirectory
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.annotation.NonNull
import com.google.ar.sceneform.ArSceneView
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.HashMap


@Suppress("DEPRECATION")
class GlassesActivity : AppCompatActivity() {

    companion object {
        const val MIN_OPENGL_VERSION = 3.0
    }

    private val requestWritePermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    lateinit var arFragment: FaceArFragment
    private var glasses: ArrayList<ModelRenderable> = ArrayList()
    private var faceRegionsRenderable: ModelRenderable? = null
    private lateinit var bitmap: Bitmap
    var faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var index: Int = 0
    private var changeModel: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish()) {
            return
        }
        val hasWritePermission = RuntimePermissionUtil.checkPermissonGranted(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        setContentView(R.layout.activity_glasses)
        button_next.setOnClickListener {
            changeModel = !changeModel
            index++
            if (index > glasses.size - 1) {
                index = 0
            }
            faceRegionsRenderable = glasses.get(index)
        }

        arFragment = face_fragment as FaceArFragment

        ModelRenderable.builder()
            .setSource(this, Uri.parse("yellow_sunglasses.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                faceRegionsRenderable = modelRenderable
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }

        ModelRenderable.builder()
            .setSource(this, Uri.parse("sunglasses.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }

        ModelRenderable.builder()
            .setSource(this, Uri.parse("redGlasses.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }

        ModelRenderable.builder()
            .setSource(this, Uri.parse("modernGlasses.sfb"))
            .build()
            .thenAccept { modelRenderable ->
                glasses.add(modelRenderable)
                modelRenderable.isShadowCaster = false
                modelRenderable.isShadowReceiver = false
            }

        val sceneView = arFragment.arSceneView
        sceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        val scene = sceneView.scene

        scene.addOnUpdateListener {
            if (faceRegionsRenderable != null) {
                sceneView.session
                    ?.getAllTrackables(AugmentedFace::class.java)?.let {
                        for (f in it) {
                            if (!faceNodeMap.containsKey(f)) {
                                val faceNode = AugmentedFaceNode(f)
                                faceNode.setParent(scene)
                                faceNode.faceRegionsRenderable = faceRegionsRenderable
                                faceNodeMap.put(f, faceNode)
                            } else if (changeModel) {
                                faceNodeMap.getValue(f).faceRegionsRenderable = faceRegionsRenderable
                            }
                        }
                        changeModel = false
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
        }


        button_snap.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                val view1 =  arFragment.arSceneView
                bitmap = Bitmap.createBitmap(view1.width, view1.height,
                    Bitmap.Config.ARGB_8888)
                val handlerThread =  HandlerThread("PixelCopier");
                handlerThread.start();
                PixelCopy.request(view1, bitmap,{copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        try {
                            if (bitmap != null) {
                                if (hasWritePermission) {
                                    saveScreenshot(bitmap)
                                } else {
                                    RuntimePermissionUtil.requestPermission(
                                        this@GlassesActivity,
                                        requestWritePermission,
                                        100
                                    )
                                }
                            }
                        } catch (e: java.lang.Exception) {
                            var toast = Toast.makeText(this@GlassesActivity, e.toString(),
                            Toast.LENGTH_LONG)
                            toast.show()
                        }

                    } }, Handler(handlerThread.getLooper()));
            }
        })
    }

    fun checkIsSupportedDeviceOrFinish() : Boolean {
        if (ArCoreApk.getInstance().checkAvailability(this) == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            Toast.makeText(this, "Augmented Faces requires ARCore", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        val openGlVersionString =  (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo
            ?.glEsVersion

        openGlVersionString?.let { s ->
            if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
                Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show()
                finish()
                return false
            }
        }
        return true
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        // Save the screenshot

        try {
            val file = ScreenShott.getInstance()
                .saveScreenshotToPicturesFolder(this@GlassesActivity, bitmap, "my_screenshot")
            // Display a toast
            Toast.makeText(
                this@GlassesActivity, "Bitmap Saved at " + file.absolutePath,
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }
}