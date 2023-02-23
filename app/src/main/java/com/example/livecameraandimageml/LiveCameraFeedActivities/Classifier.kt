package com.example.objectdetectionlivefeed

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*


class Classifier(
    assetManager: AssetManager,
    modelPath: String,
    labelPath: String,
    private val INPUT_SIZE: Int,
    quantized: Boolean
) {


    private val interpreter: Interpreter
    private val labelList: List<String>
    private val PIXEL_SIZE = 3
    private val IMAGE_MEAN = 0
    private val IMAGE_STD = 127.0f
    private val MAX_RESULTS = 3f
    private val THRESHOLD = 0.7f
    private var quantized = false

    init {
        val options: Interpreter.Options = Interpreter.Options()
        options.setNumThreads(5)
        options.setUseNNAPI(true)
        this.quantized = quantized
        interpreter = Interpreter(loadModelFile(assetManager, modelPath), options)
        Log.d("tryLog","initalized")
        labelList = loadLabelList(assetManager, labelPath)

    }


    inner class Recognition(
        val id: String?,
        val title: String?,
        val confidence: Float?,
        private var location: RectF?
    ) {

        fun getLocation(): RectF {
            return RectF(location)
        }

        fun setLocation(location: RectF?) {
            this.location = location
        }

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }
            if (title != null) {
                resultString += "$title "
            }
            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f)
            }
            if (location != null) {
                resultString += location.toString() + " "
            }
            return resultString.trim { it <= ' ' }
        }

    }

    @Throws(IOException::class)
    private fun loadModelFile(
        assetManager: AssetManager,
        MODEL_FILE: String
    ): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_FILE)
        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }



    private fun loadLabelList(assetManager: AssetManager, labelPath: String): List<String> {
        return assetManager.open(labelPath).bufferedReader().useLines { it.toList() }

    }

    private lateinit var outputLocations: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>
    private lateinit var numDetections: FloatArray
    fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

        outputLocations = Array(
            1
        ) { Array(10) { FloatArray(4) } }
        outputClasses = Array(1) { FloatArray(10) }
        outputScores = Array(1) { FloatArray(10) }
        numDetections = FloatArray(1)
        val outputMap: MutableMap<Int, Any> =
            HashMap()
        outputMap[0] = outputLocations
        outputMap[1] = outputClasses
        outputMap[2] = outputScores
        outputMap[3] = numDetections

        val inputArray = arrayOf<Any>(byteBuffer)
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)

        val numDetectionsOutput = Math.min(10, numDetections[0].toInt())
        val recognitions =
            ArrayList<Recognition>(numDetectionsOutput)
        for (i in 0 until numDetectionsOutput) {
            Log.d(
                "tryRes",
                outputClasses[0][i].toString() + "    " + outputScores[0][i]
            )
            val detection = RectF(
                outputLocations[0][i][1] * bitmap.width,
                    outputLocations[0][i][0] * bitmap.height,
                    outputLocations[0][i][3] * bitmap.width,
                    outputLocations[0][i][2] * bitmap.height
            )
            if (outputScores[0][i] > THRESHOLD) {
                recognitions.add(
                    Recognition(
                        "" + i,
                        labelList[outputClasses[0][i].toInt()],
                        outputScores[0][i],
                        detection
                    )
                )
            }
        }
        return recognitions
    }


    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer:ByteBuffer = if (quantized) {
            ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        } else {
            ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        }
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val `val` = intValues[pixel++]
                if (quantized) {
                    byteBuffer.put((`val` shr 16 and 0xFF).toByte())
                    byteBuffer.put((`val` shr 8 and 0xFF).toByte())
                    byteBuffer.put((`val` and 0xFF).toByte())
                } else {
                    byteBuffer.putFloat(((`val` shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    byteBuffer.putFloat(((`val` shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    byteBuffer.putFloat(((`val` and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        return byteBuffer
    }

    fun close(){
        interpreter.close()
    }

}
