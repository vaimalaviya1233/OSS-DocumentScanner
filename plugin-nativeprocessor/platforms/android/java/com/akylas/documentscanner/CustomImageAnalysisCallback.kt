package com.akylas.documentscanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import com.akylas.documentscanner.models.Quad
import com.nativescript.cameraview.BitmapUtils
import com.nativescript.cameraview.ImageAnalysisCallback
import com.nativescript.cameraview.ImageAsyncProcessor
import java.util.*
import kotlin.concurrent.thread
import org.json.JSONArray

class CustomImageAnalysisCallback
@JvmOverloads
constructor(private val context: Context, private val cropView: CropView? = null) :
    ImageAnalysisCallback {

    var previewResizeThreshold = 300.0

    interface OCRDocumentCallback {
        fun onResult(e: Exception?, result: Any?)
    }
    interface OCRDocumentProgress {
        fun onProgress(progress: Int)
    }

    companion object {
        private external fun nativeScanJSON(
            srcBitmap: Bitmap,
            shrunkImageHeight: Int,
            imageRotation: Int
        ): String

        private external fun nativeScan(
            srcBitmap: Bitmap,
            shrunkImageHeight: Int,
            imageRotation: Int
        ): Vector<Vector<Point>>

        private external fun nativeCrop(
            srcBitmap: Bitmap,
            points: String,
            transforms: String,
            outBitmap: Bitmap
        )
        private external fun nativeOCR(
            srcBitmap: Bitmap,
            options: String,
            progress: OCRDocumentProgress?
        ): String

        /**
         * @property cropperOffsetWhenCornersNotFound if we can't find document corners, we
         * set corners to image size with a slight margin
         */
        private const val cropperOffsetWhenCornersNotFound = 100.0

        init {
            try {
                System.loadLibrary("document_detector")
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }

        /**
         * take a photo with a document, and find the document's corners
         *
         * @param image a photo with a document
         * @return a list with document corners (top left, top right, bottom right, bottom
         * left)
         */
        @JvmOverloads
        fun ocrDocument(
            image: Bitmap,
            callback: OCRDocumentCallback,
            options: String = "",
            progress: OCRDocumentProgress? = null,
        ) {
            thread(start = true) {
                try {
                    callback.onResult(null, nativeOCR(image, options, progress))
                } catch (e: Exception) {
                    callback.onResult(e, null)
                }
            }
        }
        /**
         * take a photo with a document, and find the document's corners
         *
         * @param image a photo with a document
         * @return a list with document corners (top left, top right, bottom right, bottom
         * left)
         */
        @JvmOverloads
        fun getJSONDocumentCorners(
            image: Bitmap,
            callback: OCRDocumentCallback,
            shrunkImageHeight: Double = 500.0,
            imageRotation: Int = 0
        ) {
            thread(start = true) {
                try {
                    callback.onResult(null, nativeScanJSON(image, shrunkImageHeight.toInt(), imageRotation))
                } catch (e: Exception) {
                    callback.onResult(e, null)
                }
            }
        }

        fun quadsFromJSONString(str: String): List<Quad> {
            val jsonArray = JSONArray(str)
            val listdata = ArrayList<Quad>()
            for (i in 0 until jsonArray.length()) {
                val subJsonArray = jsonArray.getJSONArray(i)
                listdata.add(
                    Quad(
                        PointF(
                            subJsonArray.getJSONArray(
                                0
                            )
                                .getDouble(
                                    0
                                )
                                .toFloat(),
                            subJsonArray.getJSONArray(
                                0
                            )
                                .getDouble(
                                    1
                                )
                                .toFloat()
                        ),
                        PointF(
                            subJsonArray.getJSONArray(
                                1
                            )
                                .getDouble(
                                    0
                                )
                                .toFloat(),
                            subJsonArray.getJSONArray(
                                1
                            )
                                .getDouble(
                                    1
                                )
                                .toFloat()
                        ),
                        PointF(
                            subJsonArray.getJSONArray(
                                2
                            )
                                .getDouble(
                                    0
                                )
                                .toFloat(),
                            subJsonArray.getJSONArray(
                                2
                            )
                                .getDouble(
                                    1
                                )
                                .toFloat()
                        ),
                        PointF(
                            subJsonArray.getJSONArray(
                                3
                            )
                                .getDouble(
                                    0
                                )
                                .toFloat(),
                            subJsonArray.getJSONArray(
                                3
                            )
                                .getDouble(
                                    1
                                )
                                .toFloat()
                        )
                    )
                )
            }
            return listdata
        }

        /**
         * take a photo with a document, and find the document's corners
         *
         * @param image a photo with a document
         * @return a list with document corners (top left, top right, bottom right, bottom
         * left)
         */
        fun findDocumentCorners(
            image: Bitmap,
            shrunkImageHeight: Double = 500.0,
            imageRotation: Int = 0
        ): List<List<Point>>? {
            val outPoints = nativeScan(image, shrunkImageHeight.toInt(), imageRotation)
            if (outPoints.size > 0) {
                if (outPoints[0].size == 0) {
                    return null
                }
                return (outPoints)
            }
            return null
        }

        /**
         * Pass in a photo of a document, and get back 4 corner points (top left, top right,
         * bottom right, bottom left). This tries to detect document corners, but falls back
         * to photo corners with slight margin in case we can't detect document corners.
         *
         * @param photo the original photo with a rectangular document
         * @return a List of 4 OpenCV points (document corners)
         */
        @JvmOverloads
        fun getDocumentCorners(
            photo: Bitmap,
            shrunkImageHeight: Double = 500.0,
            imageRotation: Int = 0,
            returnDefault: Boolean = true
        ): List<List<Point>>? {
            val cornerPoints: List<List<Point>>? =
                findDocumentCorners(photo, shrunkImageHeight, imageRotation)
            // if cornerPoints is null then default the corners to the photo bounds with
            // a margin
            val default =
                if (returnDefault)
                    listOf(
                        Point(
                            cropperOffsetWhenCornersNotFound
                                .toInt(),
                            cropperOffsetWhenCornersNotFound
                                .toInt()
                        ),
                        Point(
                            (photo.width
                                .toDouble() -
                                    cropperOffsetWhenCornersNotFound)
                                .toInt(),
                            cropperOffsetWhenCornersNotFound
                                .toInt()
                        ),
                        Point(
                            cropperOffsetWhenCornersNotFound
                                .toInt(),
                            (photo.height
                                .toDouble() -
                                    cropperOffsetWhenCornersNotFound
                                        .toInt())
                                .toInt()
                        ),
                        Point(
                            (photo.width
                                .toDouble() -
                                    cropperOffsetWhenCornersNotFound)
                                .toInt(),
                            (photo.height
                                .toDouble() -
                                    cropperOffsetWhenCornersNotFound)
                                .toInt()
                        )
                    )
                else null
            return if (cornerPoints != null) cornerPoints
            else (if (default != null) listOf(default) else null)
        }

        @JvmOverloads
        fun cropDocument(
            bitmap: Bitmap,
            quads: String,
            callback: OCRDocumentCallback,
            transforms: String = ""
        ) {
            thread(start = true) {
                try {
                    val bitmaps = arrayListOf<Bitmap>()
                    quadsFromJSONString(quads).forEachIndexed { index, quad ->
                        // convert corners from image preview coordinates to original photo
                        // coordinates
                        // (original image is probably bigger than the preview image)
                        // convert output image matrix to bitmap
                        val cropWidth =
                            ((quad.topLeftCorner.distance(
                                quad.topRightCorner
                            ) +
                                    quad.bottomLeftCorner
                                        .distance(
                                            quad.bottomRightCorner
                                        )) /
                                    2)
                                .toInt()
                        val cropHeight =
                            ((quad.bottomLeftCorner.distance(
                                quad.topLeftCorner
                            ) +
                                    quad.bottomRightCorner
                                        .distance(
                                            quad.topRightCorner
                                        )) /
                                    2)
                                .toInt()

                        val cropBitmap =
                            Bitmap.createBitmap(
                                cropWidth,
                                cropHeight,
                                Bitmap.Config.ARGB_8888
                            )
                        val jsonArray = JSONArray(quads)
                        nativeCrop(
                            bitmap,
                            jsonArray.getJSONArray(index).toString(),
                            transforms,
                            cropBitmap
                        )
                        bitmaps.add(cropBitmap)
                    }
                    callback.onResult(null, bitmaps.toArray())
                } catch (e: Exception) {
                    callback.onResult(e, null)
                }
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun process(
        imageProxy: ImageProxy,
        info: androidx.camera.core.ImageInfo,
        processor: ImageAsyncProcessor
    ) {
        try {
            if (cropView == null) {
                processor.finished()
                return
            }
            val image = imageProxy.image!!
            val previewBitmap = BitmapUtils.getBitmap(context, imageProxy)
            val pointsList: List<List<Point>>? =
                getDocumentCorners(
                    previewBitmap,
                    previewResizeThreshold,
                    info.rotationDegrees,
                    false
                )
            if (pointsList != null) {
                val photoHeight: Int =
                    if (info.rotationDegrees == 180 ||
                        info.rotationDegrees ==
                        0
                    ) {
                        image.height
                    } else {
                        image.width
                    }
                val ratio = cropView.height.toFloat() / photoHeight.toFloat()
                val quads =
                    pointsList.map { points ->
                        points
                            .sortedBy { it.y }
                            .chunked(2)
                            .map {
                                it.sortedBy { point
                                    ->
                                    point.x
                                }
                            }
                            .flatten()
                    }
                cropView.quads =
                    quads.map { points -> Quad(points) }.map { quad ->
                        quad.applyRatio(ratio)
                    }
            } else {
                cropView.quads = null
            }
            cropView.invalidate()
            previewBitmap.recycle()
            processor.finished()
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
}
