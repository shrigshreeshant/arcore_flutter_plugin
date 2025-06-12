package com.difrancescogianmarco.arcore_flutter_plugin

import YuvToRgbRenderer
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.core.graphics.createBitmap
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreHitTestResult
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCorePose
import com.difrancescogianmarco.arcore_flutter_plugin.models.RotatingNode
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.DeadlineExceededException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class ArCoreView(val activity: Activity, private val context: Context, messenger: BinaryMessenger, id: Int, private val isAugmentedFaces: Boolean, private val debug: Boolean) : PlatformView, MethodChannel.MethodCallHandler {
    private val methodChannel: MethodChannel = MethodChannel(messenger, "arcore_view_$id")

    private var eventSink: EventChannel.EventSink? = null

    //       private val activity: Activity = (context.applicationContext as FlutterApplication).currentActivity
    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks
    private var installRequested: Boolean = false
    private var mUserRequestedInstall = true
    private val TAG: String = ArCoreView::class.java.name
    private val arSceneView = ArSceneView(context)
    private val gestureDetector: GestureDetector
    private val RC_PERMISSIONS = 0x123
    private var sceneUpdateListener: Scene.OnUpdateListener
    private var faceSceneUpdateListener: Scene.OnUpdateListener
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private var renderer=YuvToRgbRenderer()



    //AUGMENTEDFACE
    private var faceRegionsRenderable: ModelRenderable? = null
    private var faceMeshTexture: Texture? = null
    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private fun resumeSession() {
        Log.d("ArCoreView", "▶️ Resuming session")
        arSceneView?.resume()
    }

    private var lastFrameTime = 0L
    private val frameIntervalMillis = 200L

     fun startBackgroundThread() {
        backgroundThread = HandlerThread("ImageProcessingThread")
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)
    }

     fun stopBackgroundThread() {
         if(::backgroundThread.isInitialized){
             backgroundThread.quitSafely()
             backgroundThread.join()
         }
    }
    private val LOG_TAG = "YUV2RGB"

//    private fun setupSceneUpdateGPu() {
//        Log.d(LOG_TAG, "🔧 Initializing scene update listener")
//
//        val scene = arSceneView?.scene
//        if (scene == null) {
//            Log.e(LOG_TAG, "❌ ARSceneView or scene is null, cannot setup update listener")
//            return
//        }
//
//        scene.addOnUpdateListener { _ ->
//            val frame = arSceneView?.arFrame ?: return@addOnUpdateListener
//            if (frame.camera.trackingState != TrackingState.TRACKING) return@addOnUpdateListener
//
//            val now = System.currentTimeMillis()
//            if (now - lastFrameTime < frameIntervalMillis) {
//                Log.d(LOG_TAG, "⏩ Frame skipped for throttling")
//                return@addOnUpdateListener
//            }
//            lastFrameTime = now
//
//            val image = frame.acquireCameraImage()
//
//            try {
//                Log.d(LOG_TAG, "📸 Acquired camera image")
//
//                val width = image.width
//                val height = image.height
//                Log.d(LOG_TAG, "📏 Image dimensions: $width x $height")
//
//                if (width != lastWidth || height != lastHeight) {
//
//                    lastWidth = width
//                    lastHeight = height
//                    Log.d(LOG_TAG, "🆕 Framebuffer reinitialized for new dimensions")
//                }
//
//                val startTime = System.nanoTime()
//                val rgbBuffer = renderer.renderToBuffer(image, width, height)
//                val endTime = System.nanoTime()
//                Log.d(LOG_TAG, "⏱️ YUV to RGB render took ${(endTime - startTime) / 1_000_000} ms")
//                Log.d(LOG_TAG, "🎨 YUV to RGB conversion completed")
//
//              backgroundHandler.post{
//                  if (rgbBuffer != null) {
//                      rgbBuffer.rewind()  // Reset buffer position for reading
//
//                      // Convert ByteBuffer to Bitmap, then compress to PNG byte array
//                      val pngByteArray = convertBufferToJpegByteArray(rgbBuffer, width, height)
//
//                      // Post to main thread to send via eventSink
//                      Handler(Looper.getMainLooper()).post {
//                          if (eventSink != null) {
//                              eventSink?.success(pngByteArray)
//                              Log.d(LOG_TAG, "📤 PNG byte array sent to Flutter (${pngByteArray.size} bytes)")
//                          } else {
//                              Log.w(LOG_TAG, "⚠️ EventSink is null, skipping send")
//                          }
//                      }
//                  } else {
//                      Log.w(LOG_TAG, "⚠️ rgbBuffer is null, skipping send")
//                  }
//              }
//
//            } catch (e: DeadlineExceededException) {
//                Log.w(LOG_TAG, "⚠️ DeadlineExceededException - skipped this frame")
//            } catch (e: NotYetAvailableException) {
//                Log.d(LOG_TAG, "⏳ Camera image not yet available")
//            } catch (e: Exception) {
//                Log.e(LOG_TAG, "❌ Unexpected error: ${e.message}", e)
//            } finally {
//                image?.close()
//                Log.d(LOG_TAG, "🔒 Camera image closed")
//            }
//        }
//    }

    // Helper function to convert ByteBuffer RGB data to PNG byte array
    private fun convertBufferToJpegByteArray(buffer: ByteBuffer, width: Int, height: Int): ByteArray {
        val bitmap = createBitmap(width, height)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream) // 80 = quality level
        return stream.toByteArray()
    }

// 10 FPS max

    var lastWidth = -1
    var lastHeight = -1
private fun setupSceneUpdate() {
    Log.d("ArCoreView", "🔄 Setting up scene update")


    arSceneView?.scene?.addOnUpdateListener { _ ->
        val frame = arSceneView?.arFrame ?: return@addOnUpdateListener
        if (frame.camera.trackingState != TrackingState.TRACKING) return@addOnUpdateListener

        val now = System.currentTimeMillis()
        if (now - lastFrameTime < frameIntervalMillis) {
            // Skip frame to throttle FPS
            return@addOnUpdateListener
        }
        lastFrameTime = now

        backgroundHandler.post {
            var image: Image? = null
            try {
                image=frame.acquireCameraImage()
                val width = image.width
                val height = image.height
//
                val startTime = System.nanoTime()
           val nv21=yuv420ToNV21(image)

                val rotatednv21=rotateNV21(nv21,width,height,90)

                val jpeg=nv21ToJpeg(rotatednv21,height,width,16,9,50)
                val endTime = System.nanoTime()
                Log.d(LOG_TAG, "⏱️ YUV to RGB render took ${(endTime - startTime) / 1_000_000} ms")


                Handler(Looper.getMainLooper()).post {


                    eventSink?.success(jpeg)
                }

            } catch (e: DeadlineExceededException) {
                Log.w("ArCoreView", "⚠️ DeadlineExceededException, skipping frame")
            } catch (e: NotYetAvailableException) {
                Log.d("ArCoreView", "⏳ Camera image not yet available")
            } catch (e: Exception) {
                Log.e("ArCoreView", "❌ Exception: ${e.message}")
            } finally {
                image?.close()  // safe to call even if image == null
            }
        }
    }
}


    // Helper: convert YUV_420_888 Image to NV21 byte array (same as before)\
    fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Copy Y plane directly (Y plane usually has pixelStride=1)
        yBuffer.get(nv21, 0, ySize)

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStrideU = image.planes[1].pixelStride
        val chromaRowStrideV = image.planes[2].rowStride
        val chromaPixelStrideV = image.planes[2].pixelStride

        var position = ySize

        // Copy UV planes interleaved as VU for NV21
        for (row in 0 until height / 2) {
            val uRowStart = row * chromaRowStride
            val vRowStart = row * chromaRowStrideV
            for (col in 0 until width / 2) {
                val uIndex = uRowStart + col * chromaPixelStrideU
                val vIndex = vRowStart + col * chromaPixelStrideV

                // NV21 requires V first, then U
                nv21[position++] = vBuffer.get(vIndex)
                nv21[position++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }
//    fun yuv420ToNV21(image: Image): ByteArray {
//        val width = image.width
//        val height = image.height
//        val ySize = width * height
//        val uvSize = width * height / 4
//
//        val nv21 = ByteArray(ySize + uvSize * 2)
//
//        val yBuffer = image.planes[0].buffer // Y
//        val uBuffer = image.planes[1].buffer // U
//        val vBuffer = image.planes[2].buffer // V
//
//        var position = 0
//
//        // Copy Y plane
//        yBuffer.get(nv21, 0, ySize)
//        position += ySize
//
//        val chromaRowStride = image.planes[1].rowStride
//        val chromaPixelStride = image.planes[1].pixelStride
//
//        val u = ByteArray(uvSize)
//        val v = ByteArray(uvSize)
//
//        // Copy UV planes interleaved as VU for NV21 format
//        for (row in 0 until height / 2) {
//            for (col in 0 until width / 2) {
//                val uIndex = row * chromaRowStride + col * chromaPixelStride
//                val vIndex = row * chromaRowStride + col * chromaPixelStride
//                nv21[position++] = vBuffer.get(vIndex)
//                nv21[position++] = uBuffer.get(uIndex)
//            }
//        }
//        return nv21
//    }

    // Helper: compress NV21 byte array to JPEG bytes
    private fun nv21ToJpeg(
        nv21: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        targetAspectWidth: Int,
        targetAspectHeight: Int,
        quality: Int
    ): ByteArray? {
        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageWidth, imageHeight, null)

            val imageAspect = imageWidth.toFloat() / imageHeight
            val targetAspect = targetAspectWidth.toFloat() / targetAspectHeight

            // Determine cropped width & height to match target aspect ratio
            val cropWidth: Int
            val cropHeight: Int

            if (imageAspect > targetAspect) {
                // Image is too wide → crop width
                cropHeight = imageHeight
                cropWidth = (cropHeight * targetAspect).toInt()
            } else {
                // Image is too tall → crop height
                cropWidth = imageWidth
                cropHeight = (cropWidth / targetAspect).toInt()
            }

            // Center crop
            val cropLeft = (imageWidth - cropWidth) / 2
            val cropTop = (imageHeight - cropHeight) / 2

            val cropRect = Rect(
                cropLeft,
                cropTop,
                cropLeft + cropWidth,
                cropTop + cropHeight
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(cropRect, quality, out)
            out.toByteArray()

        } catch (e: Exception) {
            Log.e("ArCoreView", "JPEG compression error: ${e.message}")
            null
        }
    }




    // Helper: convert YUV_420_888 Image to NV21 byte array (same as before)

    fun rotateNV21(yuv: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        require(rotation in listOf(0, 90, 180, 270)) { "Rotation must be 0, 90, 180, or 270" }
        if (rotation == 0) return yuv

        val output = ByteArray(yuv.size)
        val frameSize = width * height
        val swap = rotation % 180 != 0
        val xflip = rotation % 270 != 0
        val yflip = rotation >= 180

        val wOut = if (swap) height else width
        val hOut = if (swap) width else height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val yIn = j * width + i
                val uIn = frameSize + (j shr 1) * width + (i and -2)
                val vIn = uIn + 1

                val iSwapped = if (swap) j else i
                val jSwapped = if (swap) i else j
                val iOut = if (xflip) wOut - iSwapped - 1 else iSwapped
                val jOut = if (yflip) hOut - jSwapped - 1 else jSwapped

                val yOut = jOut * wOut + iOut
                val uOut = frameSize + (jOut shr 1) * wOut + (iOut and -2)
                val vOut = uOut + 1

                output[yOut] = yuv[yIn]
                output[uOut] = yuv[uIn]
                output[vOut] = yuv[vIn]
            }
        }
        return output
    }

    fun startStreaming(eventSink: EventChannel.EventSink?) {
        this.eventSink = eventSink
        setupSceneUpdate()
        resumeSession()
    }


            init {



        Log.d("ArCoreView", "Initializing Event Sink")


                methodChannel.setMethodCallHandler(this)








        // Set up a tap gesture detector.
        gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        onSingleTap(e)
                        return true
                    }

                    override fun onDown(e: MotionEvent): Boolean {
                        return true
                    }
                })

        sceneUpdateListener = Scene.OnUpdateListener { frameTime ->

            val frame = arSceneView.arFrame ?: return@OnUpdateListener

            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@OnUpdateListener
            }

            for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING) {

                    val pose = plane.centerPose
                    val map: HashMap<String, Any> = HashMap<String, Any>()
                    map["type"] = plane.type.ordinal
                    map["centerPose"] = FlutterArCorePose(pose.translation, pose.rotationQuaternion).toHashMap()
                    map["extentX"] = plane.extentX
                    map["extentZ"] = plane.extentZ

                    methodChannel.invokeMethod("onPlaneDetected", map)
                }
            }
        }

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
                //                if (faceRegionsRenderable == null || faceMeshTexture == null) {
                if (faceMeshTexture == null) {
                    return@OnUpdateListener
                }

                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    // Make new AugmentedFaceNodes for any new faces.
                    for (face in faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(arSceneView?.scene)
                            faceNode.faceRegionsRenderable = faceRegionsRenderable
                            faceNode.faceMeshTexture = faceMeshTexture
                            faceNodeMap[face] = faceNode
                        }
                    }

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    val iter = faceNodeMap.iterator()
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

        // Lastly request CAMERA permission which is required by ARCore.
        ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
        setupLifeCycle(context)
    }

    fun debugLog(message: String) {
        if (debug) {
            Log.i(TAG, message)
        }
    }


    fun loadMesh(textureBytes: ByteArray?) {
        // Load the face regions renderable.
        // This is a skinned model that renders 3D objects mapped to the regions of the augmented face.
        /*ModelRenderable.builder()
                .setSource(activity, Uri.parse("fox_face.sfb"))
                .build()
                .thenAccept { modelRenderable ->
                    faceRegionsRenderable = modelRenderable;
                    modelRenderable.isShadowCaster = false;
                    modelRenderable.isShadowReceiver = false;
                }*/

        // Load the face mesh texture.
        //                .setSource(activity, Uri.parse("fox_face_mesh_texture.png"))
        Texture.builder()
                .setSource(BitmapFactory.decodeByteArray(textureBytes, 0, textureBytes!!.size))
                .build()
                .thenAccept { texture -> faceMeshTexture = texture }
    }


    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "init" -> {

                Log.d("Initilization of arcore", "this is initailizaitohn")
                arScenViewInit(call, result, activity)
            }

            "addArCoreNode" -> {
                debugLog(" addArCoreNode")
                val map = call.arguments as HashMap<String, Any>
                val flutterNode = FlutterArCoreNode(map);
                onAddNode(flutterNode, result)
            }

            "addArCoreNodeWithAnchor" -> {
                debugLog(" addArCoreNode")
                val map = call.arguments as HashMap<String, Any>
                val flutterNode = FlutterArCoreNode(map)
                addNodeWithAnchor(flutterNode, result)
            }

            "removeARCoreNode" -> {
                debugLog(" removeARCoreNode")
                val map = call.arguments as HashMap<String, Any>
                removeNode(map["nodeName"] as String, result)
            }

            "positionChanged" -> {
                debugLog(" positionChanged")

            }

            "rotationChanged" -> {
                debugLog(" rotationChanged")
                updateRotation(call, result)

            }

            "updateMaterials" -> {
                debugLog(" updateMaterials")
                updateMaterials(call, result)

            }

            "takeScreenshot" -> {
                debugLog(" takeScreenshot")
                takeScreenshot(call, result)

            }

            "loadMesh" -> {
                val map = call.arguments as HashMap<String, Any>
                val textureBytes = map["textureBytes"] as ByteArray
                loadMesh(textureBytes)
            }

            "dispose" -> {
                debugLog("Disposing ARCore now")
                dispose()
            }

            "resume" -> {
                debugLog("Resuming ARCore now")
                onResume()
            }

            "getTrackingState" -> {
                debugLog("1/3: Requested tracking state, returning that back to Flutter now")

                val trState = arSceneView?.arFrame?.camera?.trackingState
                debugLog("2/3: Tracking state is " + trState.toString())
                methodChannel.invokeMethod("getTrackingState", trState.toString())
            }

            "togglePlaneRenderer" -> {
                debugLog(" Toggle planeRenderer visibility")
                arSceneView?.planeRenderer?.isVisible = !arSceneView?.planeRenderer?.isVisible!!
            }

            "hitTest" -> {
                val x = call.argument<Double>("x") ?: 0.5
                val y = call.argument<Double>("y") ?: 0.5
                val scnreenWidth = call.argument<Double>("screenWidth") ?: 0.0
                val screenHeight = call.argument<Double>("screenHeight") ?: 0.0

                val coords = performFullDepthHitTest(
                    arSceneView = arSceneView,
                    xNorm = x.toFloat(),
                    yNorm = y.toFloat(),
                    scnreenWidth.toFloat(),
                    screenHeight.toFloat()
                )
                if (coords != null) {
                    result.success(coords)
                } else {
                    result.error("NO_HIT", "No surface found", null)
                }
            }

            "toWorldCordinate" -> {
                val x = call.argument<Double>("x") ?: 0.5
                val y = call.argument<Double>("y") ?: 0.5
                val depth = call.argument<Double>("depth") ?: 0
                if (arSceneView.arFrame != null) {
                    val coords = convertDepthToWorld(
                        x.toFloat(),
                        y.toFloat(),
                        depth.toFloat(),
                        arSceneView.arFrame!!
                    )

                    result.success(coords)
                } else {
                    result.error("Error", "Cannot convet ow orld cordinate", null)
                }


            }

            "addGlbNode" -> {
                val path = call.argument<String>("path")
                if (path == null) {
                    result.error("NO_PATH", "No file path provided", null)
                    return@onMethodCall  // ← Return from this lambda only
                }

                val pos = call.argument<Map<String, Double>>("position") ?: mapOf(
                    "x" to 0.0,
                    "y" to 0.0,
                    "z" to -1.0
                )

                val x = pos["x"]?.toFloat() ?: 0f
                val y = pos["y"]?.toFloat() ?: 0f
                val z = pos["z"]?.toFloat() ?: -1f

             //   loadModelAtPosition(path, x, y, z)
                result.success(null)
            }

            else -> {
            }
        }
    }

    fun convertCoordinates(
        xNorm: Float,
        yNorm: Float,
        fromSize: Size = Size(224, 224),
        toSize: Size = Size(640, 480)
    ): PointF {
        // Convert normalized coordinates to pixel coordinates in original space
        val xPixel = xNorm * fromSize.width
        val yPixel = yNorm * fromSize.height

        // Scale to new resolution
        val xNewPixel = xPixel * (toSize.width.toFloat() / fromSize.width.toFloat())
        val yNewPixel = yPixel * (toSize.height.toFloat() / fromSize.height.toFloat())

        // Convert back to normalized coordinates
        val xNewNorm = xNewPixel / toSize.width
        val yNewNorm = yNewPixel / toSize.height

        return PointF(xNewNorm, yNewNorm)
    }

    fun convertNormalizedKeypointFrom3x4To16x9(keyPoint: NormalizedPoint): NormalizedPoint {
        val modelAspectRatio = 3f / 4f
        val canvasAspectRatio = 2.5f / 4f

        val targetHeight = 1f
        val targetWidth = targetHeight * modelAspectRatio

        val fullCanvasWidth = targetHeight * canvasAspectRatio
        val horizontalPadding = (fullCanvasWidth - targetWidth) / 2f

        val xInCanvas = keyPoint.x * targetWidth + horizontalPadding
        val yInCanvas = keyPoint.y * targetHeight

        val normalizedX = xInCanvas / fullCanvasWidth
        val normalizedY = yInCanvas

        return NormalizedPoint(normalizedX, normalizedY)
    }
    fun performFullDepthHitTest(
        arSceneView: ArSceneView,
        xNorm: Float,
        yNorm: Float,
        screenWidth: Float,
        screenHeigth: Float,
    ): Map<String, Any>? {
        val frame = arSceneView.arFrame ?: return null
        val session = arSceneView.session ?: return null
        val keypoints =NormalizedPoint(xNorm,yNorm)
        val resizedKeyPoint=convertNormalizedKeypointFrom3x4To16x9(keypoints)



        Log.w("ARCore", "❌ Screen width and height($screenWidth, $screenHeigth)")
        if (xNorm !in 0.0f..1.0f || yNorm !in 0.0f..1.0f) {
            Log.w("ARCore", "❌ Normalized coordinates out of bounds: ($xNorm, $yNorm)")

            return null
        }
        logCameraCpuImageSize(frame)
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            Log.d("ARCore", "⚠️ Camera not tracking")
            return null
        }

        val px = resizedKeyPoint.x * screenWidth
        val py = resizedKeyPoint.y * screenHeigth

        // 1. Try hitTest against Plane or Point
        val hits = frame.hitTest(px, py)
        for (hit in hits) {

            val trackable = hit.trackable
            if ((trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) ||
                (trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
            ) {
                val pose = hit.hitPose
              //  val cameraPose = frame.camera.displayOrientedPose
                val distance = hit.distance

                Log.d("ARCoreHit", "✅ Plane/Point hit at (${pose.tx()}, ${pose.ty()}, ${pose.tz()})")

                return mapOf(
                    "x" to pose.tx(),
                    "y" to pose.ty(),
                    "z" to pose.tz(),
                    "depth" to distance,
                    "source" to "hitTest"
                )
            }
        }

        // 2. Fallback: Use raw depth
        val depth = getDepthAtNormalizedPoint(frame, keypoints.x, keypoints.y)
        if (depth != null) {
            val intrinsics = frame.camera.imageIntrinsics
            val worldPoint = convertDepthToWorld(
                keypoints.x,
                keypoints.y,
                depthMeters = depth,
      frame
            )

            val cameraPose = frame.camera.displayOrientedPose
            val distance = calculateDistanceToCamera(
                cameraPose,
                worldPoint["x"]!!,
                worldPoint["y"]!!,
                worldPoint["z"]!!,
            )

            Log.d("ARCoreHit", "✅ Raw depth fallback world point: (80,45) at $depth meters")
            return mapOf(
                "x" to      worldPoint["x"]!!,
                "y" to    worldPoint["y"]!!,
                "z" to    worldPoint["z"]!!,
                "depth" to depth,
                "source" to "rawDepth"
            )

        }

        Log.d("ARCore", "❌ No hit or depth at point ($xNorm, $yNorm)")
        return null
    }



    fun calculateDistanceToCamera(cameraPose: Pose, worldX: Float, worldY: Float, worldZ: Float): Float {
        val camX = cameraPose.tx()
        val camY = cameraPose.ty()
        val camZ = cameraPose.tz()

        val dx = worldX - camX
        val dy = worldY - camY
        val dz = worldZ - camZ


        return sqrt(dx * dx + dy * dy + dz * dz)

    }

    fun getDepthAtNormalizedPoint(frame: Frame, xNorm: Float, yNorm: Float): Float? {
        val depthImage: Image = try {
            frame.acquireDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            return null
        }
        val newX = xNorm * (160f / 224f)
        val newY = yNorm * (90f / 224f)

        logCameraCpuImageSize(frame)

        val depthWidth = depthImage.width
        val depthHeight = depthImage.height
//


        val depthMm =getMillimetersDepth(depthImage,(newX*depthWidth).toInt(),(newY*depthHeight).toInt())
        depthImage.close()
        return if (depthMm.toInt() == 0) null else (depthMm.toFloat()) / 1000.0f // Convert mm to meters
    }

    /** Obtain the depth in millimeters for [depthImage] at coordinates ([x], [y]). */
    fun getMillimetersDepth(depthImage: Image, x: Int, y: Int): UInt {
        // The depth image has a single plane, which stores depth for each
        // pixel as 16-bit unsigned integers.
        val plane = depthImage.planes[0]
        val byteIndex = x* plane.pixelStride + y * plane.rowStride
        val buffer = plane.buffer.order(ByteOrder.nativeOrder())
        val depthSample = buffer.getShort(byteIndex)
        return depthSample.toUInt()
    }


    fun convertDepthToWorld(
        xNorm224: Float, // normalized in [0, 1]
        yNorm224: Float,
        depthMeters: Float,
        frame: Frame
    ): Map<String, Float> {
        // Get current frame


        // Get camera intrinsics
        val intrinsics = frame.camera.imageIntrinsics
        val imageWidth = intrinsics.imageDimensions[0]  // typically 640
        val imageHeight = intrinsics.imageDimensions[1] // typically 480

        // Scale normalized 224x224 coordinates to full image resolution
        val u = xNorm224 * imageHeight
        val v = yNorm224 * imageWidth

        val fx = intrinsics.focalLength[0]
        val fy = intrinsics.focalLength[1]
        val cx = intrinsics.principalPoint[0]
        val cy = intrinsics.principalPoint[1]

        // Unproject depth pixel to camera space
        val xCam = (u - cx) * depthMeters / fx
        val yCam = (v - cy) * depthMeters / fy
        val zCam = depthMeters

        // Transform from camera to world space
        val worldCoords = frame.camera.displayOrientedPose.transformPoint(floatArrayOf(xCam, yCam, zCam))

        // Return in a Map<String, Float> format for Flutter
        return mapOf(
            "x" to worldCoords[0],
            "y" to worldCoords[1],
            "z" to worldCoords[2]
        )
    }



    /*    fun maybeEnableArButton() {
            Log.i(TAG,"maybeEnableArButton" )
            try{
                val availability = ArCoreApk.getInstance().checkAvailability(activity.applicationContext)
                if (availability.isTransient) {
                    // Re-query at 5Hz while compatibility is checked in the background.
                    Handler().postDelayed({ maybeEnableArButton() }, 200)
                }
                if (availability.isSupported) {
                    debugLog("AR SUPPORTED")
                } else { // Unsupported or unknown.
                    debugLog("AR NOT SUPPORTED")
                }
            }catch (ex:Exception){
                Log.i(TAG,"maybeEnableArButton ${ex.localizedMessage}" )
            }

        }*/
    fun logCameraCpuImageSize(frame: Frame) {
        try {
            val cameraImage = frame.acquireCameraImage()
            val width = cameraImage.width
            val height = cameraImage.height
            Log.d("ARCore", "📷 Camera CPU image size: $width x $height")
            cameraImage.close()

            val depthImage = try {
                frame.acquireDepthImage16Bits()
            } catch (e: NotYetAvailableException) {
                Log.w("ARCore", "❌ Depth image not available yet.")
                return
            }

            // Log depth image resolution
            Log.d("ARCore", "📐 Depth image size: ${depthImage.width} x ${depthImage.height}")

            // Get camera intrinsics
            val intrinsics = frame.camera.imageIntrinsics

            val focalLength = FloatArray(2)
            val principalPoint = FloatArray(2)
            val imageDimensions = IntArray(2)

            intrinsics.getFocalLength(focalLength, 0)
            intrinsics.getPrincipalPoint(principalPoint, 0)
            intrinsics.getImageDimensions(imageDimensions, 0)

            Log.d("ARCore", "🔍 Camera Intrinsics:")
            Log.d("ARCore", "   • Focal length: fx = ${focalLength[0]}, fy = ${focalLength[1]}")
            Log.d("ARCore", "   • Principal point: cx = ${principalPoint[0]}, cy = ${principalPoint[1]}")
            Log.d("ARCore", "   • Image dimensions: width = ${imageDimensions[0]}, height = ${imageDimensions[1]}")

            depthImage.close()
        } catch (e: NotYetAvailableException) {
            Log.w("ARCore", "⚠️ Camera image not yet available.")
        } catch (e: Exception) {
            Log.e("ARCore", "❌ Error acquiring camera image: ${e.message}")
        }
    }
    private fun setupLifeCycle(context: Context) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                debugLog("onActivityCreated")
//                maybeEnableArButton()
            }

            override fun onActivityStarted(activity: Activity) {
                debugLog("onActivityStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                debugLog("onActivityResumed")

                    onResume()

            }

            override fun onActivityPaused(activity: Activity) {
                debugLog("onActivityPaussed")


                    arSceneView.pause()

            }

            override fun onActivityStopped(activity: Activity) {
                debugLog("onActivityStopped (Just so you know)")
//                onPause()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                debugLog("onActivityDestroyed (Just so you know)")
//                onDestroy()
//                dispose()
            }
        }

        activity.application.registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

    private fun onSingleTap(tap: MotionEvent?) {
        debugLog(" onSingleTap")
        val frame = arSceneView?.arFrame
        if (frame != null) {
            if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
                val hitList = frame.hitTest(tap)
                val list = ArrayList<HashMap<String, Any>>()
                for (hit in hitList) {
                    val trackable = hit.trackable
                    if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                        hit.hitPose
                        val distance: Float = hit.distance
                        val translation = hit.hitPose.translation
                        val rotation = hit.hitPose.rotationQuaternion
                        val flutterArCoreHitTestResult = FlutterArCoreHitTestResult(distance, translation, rotation)
                        val arguments = flutterArCoreHitTestResult.toHashMap()
                        list.add(arguments)
                    }
                }
                methodChannel.invokeMethod("onPlaneTap", list)
            }
        }
    }

    private fun takeScreenshot(call: MethodCall, result: MethodChannel.Result) {
        try {
            // create bitmap screen capture

            // Create a bitmap the size of the scene view.
            val bitmap: Bitmap = Bitmap.createBitmap(arSceneView!!.getWidth(), arSceneView!!.getHeight(),
                    Bitmap.Config.ARGB_8888)

            // Create a handler thread to offload the processing of the image.
            val handlerThread = HandlerThread("PixelCopier")
            handlerThread.start()
            // Make the request to copy.
            // Make the request to copy.
            PixelCopy.request(arSceneView!!, bitmap, { copyResult ->
                if (copyResult === PixelCopy.SUCCESS) {
                    try {
                        saveBitmapToDisk(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace();
                    }
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.getLooper()))

        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }
        result.success(null)
    }

    @Throws(IOException::class)
    fun saveBitmapToDisk(bitmap: Bitmap):String {

//        val now = LocalDateTime.now()
//        now.format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
        val now = "rawScreenshot"
        // android/data/com.hswo.mvc_2021.hswo_mvc_2021_flutter_ar/files/
        // activity.applicationContext.getFilesDir().toString() //doesnt work!!
        // Environment.getExternalStorageDirectory()
        val mPath: String =  Environment.getExternalStorageDirectory().toString() + "/DCIM/" + now + ".jpg"
        val mediaFile = File(mPath)
        debugLog(mediaFile.toString())
        //Log.i("path","fileoutputstream opened")
        //Log.i("path",mPath)
        val fileOutputStream = FileOutputStream(mediaFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
//        Log.i("path","fileoutputstream closed")
        return mPath as String
    }

    private fun arScenViewInit(call: MethodCall, result: MethodChannel.Result, context: Context) {


        val enableTapRecognizer: Boolean? = call.argument("enableTapRecognizer")
        if (enableTapRecognizer != null && enableTapRecognizer) {
            arSceneView
                    ?.scene
                    ?.setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent ->

                        if (hitTestResult.node != null) {
                            debugLog(" onNodeTap " + hitTestResult.node?.name)
                            debugLog(hitTestResult.node?.localPosition.toString())
                            debugLog(hitTestResult.node?.worldPosition.toString())
                            methodChannel.invokeMethod("onNodeTap", hitTestResult.node?.name)
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener gestureDetector.onTouchEvent(event)
                    }
        }
        val enableUpdateListener: Boolean? = call.argument("enableUpdateListener")
        if (enableUpdateListener != null && enableUpdateListener) {
            // Set an update listener on the Scene that will hide the loading message once a Plane is
            // detected.
            arSceneView?.scene?.addOnUpdateListener(sceneUpdateListener)
        }

        val enablePlaneRenderer: Boolean? = call.argument("enablePlaneRendere")
        if (enablePlaneRenderer != null && !enablePlaneRenderer) {
            Log.d("here","This si the test")
            print(" The plane renderer (enablePlaneRenderer) is set to " + enablePlaneRenderer.toString())
            arSceneView?.planeRenderer?.isVisible = false
        }

        result.success(null)
    }

    fun addNodeWithAnchor(flutterArCoreNode: FlutterArCoreNode, result: MethodChannel.Result) {

        if (arSceneView == null) {
            return
        }

        RenderableCustomFactory.makeRenderable(activity.applicationContext, flutterArCoreNode) { renderable, t ->
            if (t != null) {
                result.error("Make Renderable Error", t.localizedMessage, null)
                return@makeRenderable
            }
            val myAnchor = arSceneView?.session?.createAnchor(Pose(flutterArCoreNode.getPosition(), flutterArCoreNode.getRotation()))
            if (myAnchor != null) {
                val anchorNode = AnchorNode(myAnchor)
                anchorNode.name = flutterArCoreNode.name
                anchorNode.renderable = renderable

                debugLog("addNodeWithAnchor inserted ${anchorNode.name}")
                attachNodeToParent(anchorNode, flutterArCoreNode.parentNodeName)

                for (node in flutterArCoreNode.children) {
                    node.parentNodeName = flutterArCoreNode.name
                    onAddNode(node, null)
                }
            }
            result.success(null)
        }
    }

    fun onAddNode(flutterArCoreNode: FlutterArCoreNode, result: MethodChannel.Result?) {

        debugLog(flutterArCoreNode.toString())
        NodeFactory.makeNode(activity.applicationContext, flutterArCoreNode, debug) { node, throwable ->

            debugLog("onAddNode inserted ${node?.name}")

/*            if (flutterArCoreNode.parentNodeName != null) {
                debugLog(flutterArCoreNode.parentNodeName);
                val parentNode: Node? = arSceneView?.scene?.findByName(flutterArCoreNode.parentNodeName)
                parentNode?.addChild(node)
            } else {
                debugLog("addNodeToSceneWithGeometry: NOT PARENT_NODE_NAME")
                arSceneView?.scene?.addChild(node)
            }*/
            if (node != null) {
                attachNodeToParent(node, flutterArCoreNode.parentNodeName)
                for (n in flutterArCoreNode.children) {
                    n.parentNodeName = flutterArCoreNode.name
                    onAddNode(n, null)
                }
            }

        }
        result?.success(null)
    }

    fun attachNodeToParent(node: Node?, parentNodeName: String?) {
        if (parentNodeName != null) {
            debugLog(parentNodeName);
            val parentNode: Node? = arSceneView?.scene?.findByName(parentNodeName)
            parentNode?.addChild(node)
        } else {
            debugLog("addNodeToSceneWithGeometry: NOT PARENT_NODE_NAME")
            arSceneView?.scene?.addChild(node)
        }
    }

    fun removeNode(name: String, result: MethodChannel.Result) {
        val node = arSceneView?.scene?.findByName(name)
        if (node != null) {
            arSceneView?.scene?.removeChild(node);
            debugLog("removed ${node.name}")
        }

        result.success(null)
    }

    fun updateRotation(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val node = arSceneView?.scene?.findByName(name) as RotatingNode
        debugLog("rotating node:  $node")
        val degreesPerSecond = call.argument<Double?>("degreesPerSecond")
        debugLog("rotating value:  $degreesPerSecond")
        if (degreesPerSecond != null) {
            debugLog("rotating value:  ${node.degreesPerSecond}")
            node.degreesPerSecond = degreesPerSecond.toFloat()
        }
        result.success(null)
    }

    fun updateMaterials(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val materials = call.argument<ArrayList<HashMap<String, *>>>("materials")!!
        val node = arSceneView?.scene?.findByName(name)
        val oldMaterial = node?.renderable?.material?.makeCopy()
        if (oldMaterial != null) {
            val material = MaterialCustomFactory.updateMaterial(oldMaterial, materials[0])
            node.renderable?.material = material
        }
        result.success(null)
    }

    override fun getView(): View {

        return arSceneView as View

    }



    override fun dispose() {
        if (arSceneView != null) {
            Log.d("ArcoreView","Destroying arView")
//            onPause()
            onDestroy()
        }
    }

    fun sendEvent(data: Any) {
        eventSink?.success(data)
    }


    fun onResume() {
        Log.d(TAG, "🔄 onResume()")

        if (arSceneView == null) return

        if (!ArCoreUtils.hasCameraPermission(activity)) {
            ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            return
        }

        var session = arSceneView.session

        if (session == null) {
            Log.d(TAG, "📷 ARCore session is null, creating new session")

            try {
                session = ArCoreUtils.createArSession(activity, mUserRequestedInstall, isAugmentedFaces)
                if (session == null) {
                    Log.d(TAG, "Session creation returned null. Aborting resume.")
                    mUserRequestedInstall = false
                    return
                }

                val config = Config(session).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode=Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                        Log.d(TAG, "✅ Depth mode enabled")
                    } else {
                        Log.d(TAG, "❌ Depth mode not supported")
                    }
                    setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY)
                }
                session.configure(config)
                arSceneView.setupSession(session)

            } catch (e: UnavailableUserDeclinedInstallationException) {
                Toast.makeText(activity, "User declined ARCore installation", Toast.LENGTH_LONG).show()
                return
            } catch (e: UnavailableException) {
                ArCoreUtils.handleSessionException(activity, e)
                return
            } catch (e: Exception) {
                Log.d(TAG, "Exception creating AR session: ${e.message}")
                return
            }
        }

        try {
            Log.d(TAG, "Resuming ArSceneView session")
            arSceneView.resume()
            Log.d("ArCoreView", "here is the width ${arSceneView.width} x ${arSceneView.height}")
        } catch (e: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", e)
            activity.finish()
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "IllegalArgumentException on arSceneView.resume(): ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "Exception on arSceneView.resume(): ${e.message}")
        }
    }


    fun onPause() {
        stopBackgroundThread()
        if (arSceneView != null) {

            arSceneView?.pause()
        }
    }

    fun onDestroy() {
        debugLog("ARCoreView onDestroy called")

        try {
            // Pause AR session first to stop frame updates
            arSceneView?.pause()

            // Remove listeners BEFORE destroying the view
            arSceneView?.scene?.removeOnUpdateListener(sceneUpdateListener)
            arSceneView?.scene?.removeOnUpdateListener(faceSceneUpdateListener)

            // Close the AR session cleanly
            arSceneView?.session?.close()

            // Destroy the ArSceneView to release all resources
            arSceneView?.destroy()

            debugLog("Destroyed ArSceneView.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during ArCoreView cleanup", e)
        }

        stopBackgroundThread()

        // Cleanup event channel
        eventSink?.endOfStream()
        eventSink = null

        debugLog("✅ ArCoreView cleanup complete.")
    }


    data class NormalizedPoint(val x: Float, val y: Float)
    /* private fun tryPlaceNode(tap: MotionEvent?, frame: Frame) {
        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    // Create the Anchor.
                    val anchor = hit.createAnchor()
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arSceneView?.scene)

                    ModelRenderable.builder()
                            .setSource(activity.applicationContext, Uri.parse("TocoToucan.sfb"))
                            .build()
                            .thenAccept { renderable ->
                                val node = Node()
                                node.renderable = renderable
                                anchorNode.addChild(node)
                            }.exceptionally { throwable ->
                                Log.e(TAG, "Unable to load Renderable.", throwable);
                                return@exceptionally null
                            }
                }
            }
        }

    }*/

    /*    fun updatePosition(call: MethodCall, result: MethodChannel.Result) {
        val name = call.argument<String>("name")
        val node = arSceneView?.scene?.findByName(name)
        node?.localPosition = parseVector3(call.arguments as HashMap<String, Any>)
        result.success(null)
    }*/
}


