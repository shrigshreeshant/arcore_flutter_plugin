// import 'package:arcore_flutter_plugin/src/arcore_view.dart';
// import 'package:flutter/material.dart';
// import 'package:flutter/services.dart';

// typedef PlatformViewCreatedCallback = void Function(int id);

// class ArCoreAndroidView extends AndroidView {
//   final String viewType;
//   final PlatformViewCreatedCallback? onPlatformViewCreated;
//   final ArCoreViewType arCoreViewType;
//   final bool debug;

//   ArCoreAndroidView(
//       {Key? key,
//       required this.viewType,
//       this.onPlatformViewCreated,
//       this.arCoreViewType = ArCoreViewType.STANDARDVIEW,
//       this.debug = false})
//       : super(
//           viewType: viewType,
//           onPlatformViewCreated: onPlatformViewCreated,
//           creationParams: <String, dynamic>{
//             "type": arCoreViewType == ArCoreViewType.AUGMENTEDFACE
//                 ? "faces"
//                 : arCoreViewType == ArCoreViewType.AUGMENTEDIMAGES
//                     ? "augmented"
//                     : "standard",
//             "debug": debug
//           },
//           creationParamsCodec: const StandardMessageCodec(),
//         );
// }

import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

typedef PlatformViewCreatedCallback = void Function(int id);

class ArCoreAndroidView extends StatelessWidget {
  final String viewType;
  final PlatformViewCreatedCallback? onPlatformViewCreated;
  final ArCoreViewType arCoreViewType;
  final bool debug;

  const ArCoreAndroidView({
    Key? key,
    required this.viewType,
    this.onPlatformViewCreated,
    this.arCoreViewType = ArCoreViewType.STANDARDVIEW,
    this.debug = false,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return PlatformViewLink(
      viewType: viewType,
      surfaceFactory: (context, controller) {
        return AndroidViewSurface(
          controller: controller as AndroidViewController,
          gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
          hitTestBehavior: PlatformViewHitTestBehavior.opaque,
        );
      },
      onCreatePlatformView: (params) {
        final controller = PlatformViewsService.initSurfaceAndroidView(
          id: params.id,
          viewType: viewType,
          layoutDirection: TextDirection.ltr,
          creationParams: {
            "type": arCoreViewType == ArCoreViewType.AUGMENTEDFACE
                ? "faces"
                : arCoreViewType == ArCoreViewType.AUGMENTEDIMAGES
                    ? "augmented"
                    : "standard",
            "debug": debug,
          },
          creationParamsCodec: const StandardMessageCodec(),
        );
        if (onPlatformViewCreated != null) {
          controller.addOnPlatformViewCreatedListener(onPlatformViewCreated!);
        }

        controller
            .addOnPlatformViewCreatedListener(params.onPlatformViewCreated);
        controller.create();
        return controller;
      },
    );
  }
}
