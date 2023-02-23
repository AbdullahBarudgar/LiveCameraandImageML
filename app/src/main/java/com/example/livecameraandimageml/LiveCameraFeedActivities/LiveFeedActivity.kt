package com.example.livecameraandimageml.LiveCameraFeedActivities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Fragment
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.imageclassificationlivefeed.CameraConnectionFragment
import com.example.imageclassificationlivefeed.ImageUtils.convertYUV420ToARGB8888
import com.example.imageclassificationlivefeed.ImageUtils.getTransformationMatrix
import com.example.livecameraandimageml.R
import com.example.objectdetectionlivefeed.Classifier

import com.example.objectdetectionlivefeed.Drawing.BorderedText
import com.example.objectdetectionlivefeed.Drawing.MultiBoxTracker
import com.example.objectdetectionlivefeed.Drawing.OverlayView
import java.io.IOException
import java.util.*

class LiveFeedActivity : AppCompatActivity(),ImageReader.OnImageAvailableListener {
    private var classifier: Classifier? = null
    var resultTV: TextView? = null

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    // Configuration values for the prepackaged SSD model.
    private val MAINTAIN_ASPECT = false
    private val TEXT_SIZE_DIP = 10f

    var trackingOverlay: OverlayView? = null
    private var borderedText: BorderedText? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_livefeed)

        //TODO show live camera footage
        //TODO ask for permission of camera upon first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
            ) {
                val permission = arrayOf(
                        Manifest.permission.CAMERA
                )
                requestPermissions(permission, 1122)
            } else {
                //TODO show live camera footage
                setFragment()
            }
        }else{
            //TODO show live camera footage
            setFragment()
        }
        resultTV = findViewById(R.id.textViewLiveFeed)


        //TODO inialize classifier class
        try {
            classifier = Classifier(assets, "detect.tflite", "labelmap.txt", 300, true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //TODO show live camera footage
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            setFragment()
        } else {
            finish()
        }
    }

    //TODO fragment which show llive footage from camera
    var previewHeight = 0
    var previewWidth = 0
    private var sensorOrientation = 0
    protected fun setFragment() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val fragment: Fragment
        val camera2Fragment = CameraConnectionFragment.newInstance(
            object :
                CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    val textSizePx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        TEXT_SIZE_DIP,
                        resources.displayMetrics
                    )
                    borderedText = BorderedText(textSizePx)
                    borderedText!!.setTypeface(Typeface.MONOSPACE)
                    tracker = MultiBoxTracker(this@LiveFeedActivity)

                    val cropSize = 300
                    previewWidth = size.width
                    previewHeight = size.height
                    sensorOrientation = rotation - getScreenOrientation()
                    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)


                    frameToCropTransform = getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT
                    )
                    cropToFrameTransform = Matrix()
                    frameToCropTransform!!.invert(cropToFrameTransform)

                    trackingOverlay =
                        findViewById<View>(R.id.tracking_overlay) as OverlayView
                    trackingOverlay!!.addCallback(
                        object : OverlayView.DrawCallback {
                            override fun drawCallback(canvas: Canvas?) {
                                tracker!!.draw(canvas!!)
                            }
                        })
                    tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation)
                }
            },
            this,
            R.layout.camera_fragment,
            Size(640, 480)
        )
        camera2Fragment.setCamera(cameraId)
        fragment = camera2Fragment
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }


    //TODO getting frames of live camera footage and passing them to model
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null

    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            Log.d("tryError", e.message + "abc ")
            return
        }
    }


    var croppedBitmap: Bitmap? = null
    private var tracker: MultiBoxTracker? = null
    fun processImage() {
        imageConverter!!.run()
        rgbFrameBitmap =                Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap!!.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        val results: List<Classifier.Recognition> = classifier!!.recognizeImage(croppedBitmap!!)

        for (result in results) {
            val location: RectF = result.getLocation()
            cropToFrameTransform!!.mapRect(location)
            result.setLocation(location)
        }
        tracker?.trackResults(results, 10)
        trackingOverlay?.postInvalidate()
        postInferenceCallback!!.run()

    }

    protected fun fillBytes(
        planes: Array<Plane>,
        yuvBytes: Array<ByteArray?>
    ) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}