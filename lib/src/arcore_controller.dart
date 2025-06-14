import 'dart:io';

import 'package:arcore_flutter_plugin/src/arcore_augmented_image.dart';
import 'package:arcore_flutter_plugin/src/arcore_rotating_node.dart';
import 'package:arcore_flutter_plugin/src/utils/vector_utils.dart';
import 'package:flutter/services.dart';
import 'package:meta/meta.dart';
import 'package:path_provider/path_provider.dart';

import 'arcore_hit_test_result.dart';
import 'arcore_node.dart';
import 'arcore_plane.dart';

typedef StringResultHandler = void Function(String text);
typedef UnsupportedHandler = void Function(String text);
typedef ArCoreHitResultHandler = void Function(List<ArCoreHitTestResult> hits);
typedef ArCorePlaneHandler = void Function(ArCorePlane plane);
typedef ArCoreAugmentedImageTrackingHandler = void Function(
    ArCoreAugmentedImage);

const UTILS_CHANNEL_NAME = 'arcore_flutter_plugin/utils';

class ArCoreController {
  static checkArCoreAvailability() async {
    final bool arcoreAvailable = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkArCoreApkAvailability');
    return arcoreAvailable;
  }

  static checkIsArCoreInstalled() async {
    final bool arcoreInstalled = await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkIfARCoreServicesInstalled');
    return arcoreInstalled;
  }

  ArCoreController(
      {required this.id,
      this.enableTapRecognizer,
      this.enablePlaneRenderer,
      this.enableUpdateListener,
      this.debug = false
//    @required this.onUnsupported,
      }) {
    print("IDD:::  $id");
    init();
    _channel = MethodChannel('arcore_view_$id');
    _channel.setMethodCallHandler(_handleMethodCalls);
    print("IDDD:::  $id");
  }

  final int id;
  final bool? enableUpdateListener;
  final bool? enableTapRecognizer;
  final bool? enablePlaneRenderer;
  final bool? debug;
  late MethodChannel _channel;

  StringResultHandler? onError;
  StringResultHandler? onNodeTap;

//  UnsupportedHandler onUnsupported;
  ArCoreHitResultHandler? onPlaneTap;
  ArCorePlaneHandler? onPlaneDetected;
  String trackingState = '';
  ArCoreAugmentedImageTrackingHandler? onTrackingImage;

  init() async {
    print('ArCoreController init() called');
    try {
      await _channel.invokeMethod<void>('init', {
        'enableTapRecognizer': enableTapRecognizer,
        'enablePlaneRenderer': enablePlaneRenderer,
        'enableUpdateListener': enableUpdateListener,
      });
      print('ArCoreController init() completed');
    } on PlatformException catch (ex) {
      print('ArCoreController init() error: ${ex.message}');
    }
  }

  Future<dynamic> _handleMethodCalls(MethodCall call) async {
    if (debug ?? true) {
      print('_platformCallHandler call ${call.method} ${call.arguments}');
    }

    switch (call.method) {
      case 'onError':
        if (onError != null) {
          onError!(call.arguments);
        }
        break;
      case 'onNodeTap':
        if (onNodeTap != null) {
          onNodeTap!(call.arguments);
        }
        break;
      case 'onPlaneTap':
        if (onPlaneTap != null) {
          final List<dynamic> input = call.arguments;
          final objects = input
              .cast<Map<dynamic, dynamic>>()
              .map<ArCoreHitTestResult>(
                  (Map<dynamic, dynamic> h) => ArCoreHitTestResult.fromMap(h))
              .toList();
          onPlaneTap!(objects);
        }
        break;
      case 'onPlaneDetected':
        if (enableUpdateListener ?? true && onPlaneDetected != null) {
          final plane = ArCorePlane.fromMap(call.arguments);
          onPlaneDetected!(plane);
        }
        break;
      case 'getTrackingState':
        // TRACKING, PAUSED or STOPPED
        trackingState = call.arguments;
        if (debug ?? true) {
          print('Latest tracking state received is: $trackingState');
        }
        break;
      case 'onTrackingImage':
        if (debug ?? true) {
          print('flutter onTrackingImage');
        }
        final arCoreAugmentedImage =
            ArCoreAugmentedImage.fromMap(call.arguments);
        onTrackingImage!(arCoreAugmentedImage);
        break;
      case 'togglePlaneRenderer':
        if (debug ?? true) {
          print('Toggling Plane Renderer Visibility');
        }
        togglePlaneRenderer();
        break;

      default:
        if (debug ?? true) {
          print('Unknown method ${call.method}');
        }
    }
    return Future.value();
  }

  Future<void> addArCoreNode(ArCoreNode node, {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    if (debug ?? true) {
      print(params.toString());
    }
    _addListeners(node);
    return _channel.invokeMethod('addArCoreNode', params);
  }

  Future<dynamic> togglePlaneRenderer() async {
    return _channel.invokeMethod('togglePlaneRenderer');
  }

  Future<dynamic> getTrackingState() async {
    return _channel.invokeMethod('getTrackingState');
  }

  Future<void> sendModelPathToNative(String path) async {
    final byteData = await rootBundle.load('assets/model.glb');

    // Copy to temp folder
    final tempDir = await getTemporaryDirectory();
    final file = File('${tempDir.path}/$path');
    await file.writeAsBytes(byteData.buffer.asUint8List(), flush: true);

    // Send path to Android via MethodChannel
    const channel = MethodChannel('com.example.yourplugin/arcore');
    await channel.invokeMethod('loadGlbFromPath', {
      'path': file.path,
      'position': {'x': 0.0, 'y': 0.0, 'z': -1.0}, // optional world position
    });
  }

  Future<Map> hitTest(
      double x, double y, double screenWidth, double screenHeight) async {
    final result = await _channel.invokeMethod('hitTest', {
      "x": x,
      "y": y,
      "screenWidth": screenWidth,
      "screenHeight": screenHeight
    });
    return result;
  }

  Future<Map> toWorldCordinate(double x, double y, double depth) async {
    final result = await _channel
        .invokeMethod('toWorldCordinate', {"x": x, "y": y, "depth": depth});
    return result;
  }

  addArCoreNodeToAugmentedImage(ArCoreNode node, int index,
      {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    return _channel.invokeMethod(
        'attachObjectToAugmentedImage', {'index': index, 'node': params});
  }

  Future<void> addArCoreNodeWithAnchor(ArCoreNode node,
      {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    if (debug ?? true) {
      print(params.toString());
    }
    _addListeners(node);
    if (debug ?? true) {
      print('---------_CALLING addArCoreNodeWithAnchor : $params');
    }
    return _channel.invokeMethod('addArCoreNodeWithAnchor', params);
  }

  Future<void> removeNode({@required String? nodeName}) {
    assert(nodeName != null);
    return _channel.invokeMethod('removeARCoreNode', {'nodeName': nodeName});
  }

  Map<String, dynamic>? _addParentNodeNameToParams(
      Map<String, dynamic> geometryMap, String? parentNodeName) {
    if (parentNodeName != null && parentNodeName.isNotEmpty)
      geometryMap['parentNodeName'] = parentNodeName;
    return geometryMap;
  }

  void _addListeners(ArCoreNode node) {
    node.position?.addListener(() => _handlePositionChanged(node));
    node.shape?.materials.addListener(() => _updateMaterials(node));

    if (node is ArCoreRotatingNode) {
      node.degreesPerSecond.addListener(() => _handleRotationChanged(node));
    }
  }

  void _handlePositionChanged(ArCoreNode node) {
    _channel.invokeMethod<void>('positionChanged',
        _getHandlerParams(node, convertVector3ToMap(node.position?.value)));
  }

  void _handleRotationChanged(ArCoreRotatingNode node) {
    _channel.invokeMethod<void>('rotationChanged',
        {'name': node.name, 'degreesPerSecond': node.degreesPerSecond.value});
  }

  void _updateMaterials(ArCoreNode node) {
    _channel.invokeMethod<void>(
        'updateMaterials', _getHandlerParams(node, node.shape!.toMap()));
  }

  Map<String, dynamic> _getHandlerParams(
      ArCoreNode node, Map<String, dynamic>? params) {
    final Map<String, dynamic> values = <String, dynamic>{'name': node.name}
      ..addAll(params!);
    return values;
  }

  Future<void> loadSingleAugmentedImage({required Uint8List bytes}) {
    return _channel.invokeMethod('load_single_image_on_db', {
      'bytes': bytes,
    });
  }

  Future<void> loadMultipleAugmentedImage(
      {@required Map<String, Uint8List>? bytesMap}) {
    assert(bytesMap != null);
    return _channel.invokeMethod('load_multiple_images_on_db', {
      'bytesMap': bytesMap,
    });
  }

  Future<void> loadAugmentedImagesDatabase({@required Uint8List? bytes}) {
    assert(bytes != null);
    return _channel.invokeMethod('load_augmented_images_database', {
      'bytes': bytes,
    });
  }

  void dispose() {
    _channel.invokeMethod<void>('dispose');
  }

  void resume() {
    _channel.invokeMethod<void>('resume');
  }

  Future<void> removeNodeWithIndex(int index) async {
    try {
      return await _channel.invokeMethod('removeARCoreNodeWithIndex', {
        'index': index,
      });
    } catch (ex) {
      print(ex);
    }
  }
}
