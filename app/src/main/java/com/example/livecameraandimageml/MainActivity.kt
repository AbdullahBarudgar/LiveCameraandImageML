package com.example.livecameraandimageml

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.livecameraandimageml.LiveCameraFeedActivities.LiveFeedActivity
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private val RESULT_LOAD_IMAGE = 123
    val IMAGE_CAPTURE_CODE = 654
    private val PERMISSION_CODE = 321

    var resultImage: ImageView? = null
    var resultTv: TextView? = null
    private var image_uri: Uri? = null

    private val mInputSize = 224
    private val mModelPath = "mobilenet_v1_0.5_224_quant.tflite"
    private val mLabelPath = "mobilenet_v1_1.0_224.txt"
    private var classifier: Classifier? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultImage = findViewById(R.id.result_Image)
        resultTv = findViewById(R.id.result_Tv)


        //TODO initialize the Classifier class object. This class will load the model and using its method we will pass input to the model and get the output
        try {
            classifier = Classifier(assets, mModelPath, mLabelPath, mInputSize, true)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //TODO ask for permission of camera upon first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED)
            {
                val permission =
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                requestPermissions(permission, PERMISSION_CODE)
            }
        }


    }

    fun liveCameraFeed(view: View) {
        var intent = Intent(this, LiveFeedActivity::class.java)
        startActivity(intent)
    }
    fun gallery(view: View) {

        val galleryIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent,RESULT_LOAD_IMAGE)

    }
    fun imageCapture(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                == PackageManager.PERMISSION_DENIED
            ) {
                val permission =
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                requestPermissions(permission, PERMISSION_CODE)
            } else {
                openCamera()
            }
        } else {
            openCamera()
        }
    }

    //TODO opens camera so that user can capture image
    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        image_uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri)
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE)
    }

    @Deprecated("")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            image_uri = data.data ///
            //innerImage.setImageURI(image_uri);
            val bitmap = uriToBitmap(image_uri!!)
            resultImage!!.setImageURI(image_uri)
            doInference(bitmap!!)
        }

        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK) {
            //innerImage.setImageURI(image_uri);
            val bitmap = uriToBitmap(image_uri!!)
            resultImage!!.setImageURI(image_uri)
            doInference(bitmap!!)
        }

    }


    //TODO pass image to the model and shows the results on screen
    fun doInference(input: Bitmap) {
        var input = input
        input = rotateBitmap(input)
        val result = classifier!!.recognizeImage(input)
        resultTv!!.text = ""
        Log.d("result:ml",result.toString())
        for (i in result.indices) {
            resultTv!!.append(
                "${result[i].title} - ${String.format("%.2f",result[i].confidence*100)} % sure\n")
        }
    }


    //TODO rotate image if image captured on samsung devices
    //Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    fun rotateBitmap(input: Bitmap): Bitmap {
        val orientationColumn =
            arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cur = contentResolver.query(image_uri!!, orientationColumn, null, null, null)
        var orientation = -1
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]))
        }
        Log.d("tryOrientation", orientation.toString() + "")
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(orientation.toFloat())
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, rotationMatrix, true)
    }


    //TODO takes URI of the image and returns bitmap
    private fun uriToBitmap(selectedFileUri: Uri): Bitmap? {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }


    override fun onDestroy() {
        super.onDestroy()
    }

}