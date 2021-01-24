package no.realitylab.arface

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
import android.os.Environment
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.HashMap


@Suppress("DEPRECATION")
class GlassesActivity : AppCompatActivity() {

    companion object {
        const val MIN_OPENGL_VERSION = 3.0
    }

    lateinit var arFragment: FaceArFragment
    private var faceMeshTexture: Texture? = null
    private var glasses: ArrayList<ModelRenderable> = ArrayList()
    private var faceRegionsRenderable: ModelRenderable? = null

    var faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var index: Int = 0
    private var changeModel: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish()) {
            return
        }

        setContentView(R.layout.activity_glasses)
        button_next.setOnClickListener {
            changeModel = !changeModel
            index++
            if (index > glasses.size - 1) {
                index = 0
            }
            faceRegionsRenderable = glasses.get(index)
        }

        button_snap.setOnClickListener{
            takeScreenshot()
        }

        arFragment = face_fragment as FaceArFragment
        Texture.builder()
            .setSource(this, R.drawable.makeup)
            .build()
            .thenAccept { texture -> faceMeshTexture = texture }

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
    private fun takeScreenshot(){
        val now = Date()
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now)

        try {
            // image naming and path  to include sd card  appending name you choose for file
            val mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpg"

            // create bitmap screen capture
            val v1 = image_view
            v1.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(v1.drawingCache)
            v1.isDrawingCacheEnabled = false

            val imageFile = File(mPath)

            val outputStream = FileOutputStream(imageFile)
            val quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            //openScreenshot(imageFile)
        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }

    }

    private fun openScreenshot(imageFile: File) {
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        val uri = Uri.fromFile(imageFile)
        intent.setDataAndType(uri, "image/*")
        startActivity(intent)
    }
}