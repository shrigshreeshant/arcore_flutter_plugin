package com.difrancescogianmarco.arcore_flutter_plugin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.platform.PlatformViewFactory


class ArCoreViewFactory(val activity: Activity, val messenger: BinaryMessenger) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {



//    override fun create(context: Context, id: Int, args: Any?): PlatformView {
//        val params = args as HashMap<*, *>
//        val debug = params["debug"] as Boolean
//        val type = params["type"] as String
//
//        if (debug) {
//            Log.i("ArCoreViewFactory", id.toString())
//            Log.i("ArCoreViewFactory", args.toString())
//        }
//
//        if (type == "faces") {
//            return ArCoreFaceView(activity, context, messenger, id, debug)
//        } else if (type == "augmented") {
//            val useSingleImage = params["useSingleImage"] as? Boolean ?: true
//            return ArCoreAugmentedImagesView(activity, context, messenger, id, useSingleImage, debug)
//        }
//   return ArCoreView(activity, context, messenger, id, isAugmentedFaces = false, debug)
//    }
override fun create(context: Context, id: Int, args: Any?): PlatformView {
    val params = args as Map<*, *>
    val debug = params["debug"] as? Boolean ?: false
    val type = params["type"] as? String ?: "standard"
val view= ArCoreView(activity, context, messenger, id, false, debug)
//    val view = when (type) {
//        "faces" -> ArCoreFaceView(activity, context, messenger, id, debug)
//        "augmented" -> {
//            val useSingleImage = params["useSingleImage"] as? Boolean ?: true
//            ArCoreAugmentedImagesView(activity, context, messenger, id, useSingleImage, debug)
//        }
//        else -> ArCoreView(activity, context, messenger, id, false, debug)
//    }

    // Create a dedicated EventChannel for this view instance
    val eventChannel = EventChannel(messenger, "ar_image")
    eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            Log.d("ArCoreViewFactory", "[$id] onListen")
            view.startBackgroundThread()
            view.startStreaming(events)
        }

        override fun onCancel(arguments: Any?) {
            Log.d("ArCoreViewFactory", "[$id] onCancel")
            view.stopBackgroundThread()
        }
    })

    return view
}
}



//@SuppressLint("StaticFieldLeak")
//object ArCoreViewManager {
//    private var arCoreView: ArCoreView? = null
//    private var onViewReadyCallback: ((ArCoreView) -> Unit)? = null
//
//    fun createOrGetView(
//        activity: Activity,
//        context: Context,
//        messenger: BinaryMessenger,
//        id: Int,
//        isAugmentedFaces: Boolean,
//        debug: Boolean
//    ): ArCoreView {
//        if (arCoreView == null) {
//            arCoreView = ArCoreView(activity, context, messenger, id, isAugmentedFaces, debug)
//        } else {
//            Log.w("ArCoreViewManager", "Reusing existing ArCoreView")
//        }
//        return arCoreView!!
//    }
//
//    fun getView(): ArCoreView? = arCoreView
//
//    fun release() {
//        arCoreView?.dispose()
//        arCoreView = null
//        Log.d("ArCoreViewManager", "ArCoreView released")
//    }
//
//    fun setOnViewReadyCallback(callback: (ArCoreView) -> Unit) {
//        onViewReadyCallback = callback
//        arCoreView?.let {
//            callback(it)
//            onViewReadyCallback = null
//        }
//    }
//}