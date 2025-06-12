//package com.difrancescogianmarco.arcore_flutter_plugin
//
//import android.util.Log
//import androidx.annotation.NonNull
//import androidx.annotation.Nullable
//import io.flutter.embedding.engine.plugins.FlutterPlugin
//import io.flutter.embedding.engine.plugins.activity.ActivityAware
//import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
//
//class ArcoreFlutterPlugin : FlutterPlugin, ActivityAware {
//
//    private lateinit var eventChannel: EventChannel
//    private var eventSink: EventChannel.EventSink? = null
//
//    @Nullable
//    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
//
//    private var methodCallHandler: MethodCallHandlerImpl? = null
//
//    companion object {
//        const val TAG = "ArCoreFlutterPlugin"
//        private const val CHANNEL_NAME = "arcore_flutter_plugin"
//    }
//
//    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
//        this.flutterPluginBinding = flutterPluginBinding
//
//        eventChannel = EventChannel(messenger, "ar_image")
//        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
//            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
//                Log.d("ArCorePlugin", "EventChannel onListen")
//                eventSink = events
//                ArCoreViewManager.getView()?.startStreaming(eventSink)
//            }
//
//            override fun onCancel(arguments: Any?) {
//                Log.d("ArCorePlugin", "EventChannel onCancel")
//                eventSink = null
//            }
//        })
//    }
//
//
//    override fun onDetachedFromEngine(p0: FlutterPlugin.FlutterPluginBinding) {
//        this.flutterPluginBinding = null
//    }
//
//
//    override fun onDetachedFromActivity() {
//        //TODO remove othen channel
//        methodCallHandler?.stopListening()
//        methodCallHandler = null
//    }
//
//    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
//        onAttachedToActivity(binding)
//    }
//
//    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
//        flutterPluginBinding?.platformViewRegistry?.registerViewFactory(CHANNEL_NAME, ArCoreViewFactory(binding.activity, flutterPluginBinding?.binaryMessenger!!))
//        methodCallHandler = MethodCallHandlerImpl(
//                binding.activity, flutterPluginBinding?.binaryMessenger!!)
//
//    }
//
//    override fun onDetachedFromActivityForConfigChanges() {
//        onDetachedFromActivity()
//    }
//
//
//}
//

package com.difrancescogianmarco.arcore_flutter_plugin

import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

class ArcoreFlutterPlugin : FlutterPlugin, ActivityAware {

    private var isAttached = false

    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null

    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var methodCallHandler: MethodCallHandlerImpl? = null

    companion object {
        const val TAG = "ArCoreFlutterPlugin"
        private const val CHANNEL_NAME = "arcore_flutter_plugin"
        private const val IMAGE_CHANNEL = "ar_image"
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "✅ onAttachedToEngine")
        if (isAttached) return
        isAttached = true
        this.flutterPluginBinding = flutterPluginBinding
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, IMAGE_CHANNEL)
        // Setup the EventChannel early so it's ready before Flutter listens

    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "🧹 onDetachedFromEngine")
        eventSink = null
        flutterPluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "🎯 onAttachedToActivity")

        val bindingNonNull = flutterPluginBinding
        if (bindingNonNull == null) {
            Log.e(TAG, "FlutterPluginBinding is null in onAttachedToActivity")
            return
        }

        bindingNonNull.platformViewRegistry.registerViewFactory(
            CHANNEL_NAME,
            ArCoreViewFactory(binding.activity, bindingNonNull.binaryMessenger)
        )


        // Register the platform view with per-instance stream handler support
//        pluginBinding.platformViewRegistry.registerViewFactory(
//            CHANNEL_NAME,
//            ArCoreViewFactory(binding.activity, pluginBinding.binaryMessenger)
//        )


//        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
//            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
//                Log.d(TAG, "Start eventChanel")
//                    eventSink = events
//                    val view = ArCoreViewManager.getView()
//                    if (view == null) {
//                        ArCoreViewManager.setOnViewReadyCallback {
//                            it.startBackgroundThread()
//                            it.startStreaming(eventSink)
//                        }
//                    } else {
//                        view.startBackgroundThread()
//                        view.startStreaming(eventSink)
//                    }
//                }
//
//            override fun onCancel(arguments: Any?) {
//                Log.d(TAG, "🛑 EventChannel onCancel")
//                val view = ArCoreViewManager.getView()
//                view?.stopBackgroundThread()
//                eventSink = null
//            }
//        })

        methodCallHandler = MethodCallHandlerImpl(binding.activity, bindingNonNull.binaryMessenger)
    }


    override fun onDetachedFromActivity() {
        Log.d(TAG, "🔌 onDetachedFromActivity")
        methodCallHandler?.stopListening()
        methodCallHandler = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }
}
