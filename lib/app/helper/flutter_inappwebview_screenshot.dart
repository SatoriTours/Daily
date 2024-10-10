import 'dart:ui' as ui;
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'dart:io';

Future<String> captureFullPageScreenshot(InAppWebViewController controller) async {
  try {
    // 获取网页的高度
    final height = await controller.evaluateJavascript(source: "document.documentElement.scrollHeight");
    final screenHeight = await controller.evaluateJavascript(source: "window.innerHeight");

    logger.d("网页高度信息: $height, $screenHeight");

    final int totalHeight = height;
    final int screenHeightInt = screenHeight;

    List<ui.Image> screenshots = [];
    bool hasHeader = true;
    for (int i = 0; i < totalHeight; i += screenHeightInt) {
      if(i != 0 && hasHeader) { //第二屏开始,删除顶部导航栏, 避免截图重复
        logger.i("删除网页头部 和 广告节点");
        controller.evaluateJavascript(source: "removeHeaderNode()");
        hasHeader = false;
      }
      // 滚动到当前位置
      await controller.evaluateJavascript(source: "window.scrollTo(0, $i)");
      await Future.delayed(Duration(milliseconds: 500)); // 等待页面滚动

      // 截取当前屏幕的截图
      final Uint8List? screenshot = await controller.takeScreenshot();
      if (screenshot != null) {
        final codec = await ui.instantiateImageCodec(screenshot);
        final frame = await codec.getNextFrame();
        screenshots.add(frame.image);
      } else {
        logger.i("Failed to capture screenshot at position $i");
      }
    }

    // 将截图拼接在一起
    return await _saveFullPageScreenshot(screenshots);
  } catch (e, stackTrace) {
    logger.i("Error capturing full page screenshot: $e");
    logger.i(stackTrace);
  }
  return "";
}

Future<String> _saveFullPageScreenshot(List<ui.Image> screenshots) async {
  final filePath = FileService.instance.getScreenshotPath();
  final file = File(filePath);

  // 创建一个空白的画布
  final image = await _combineImages(screenshots);
  final pngBytes = await image.toByteData(format: ui.ImageByteFormat.png);
  await file.writeAsBytes(pngBytes!.buffer.asUint8List());
  print("Full page screenshot saved to: $filePath");
  return filePath;
}

Future<ui.Image> _combineImages(List<ui.Image> screenshots) async {
  // 计算总高度
  int totalHeight = screenshots.fold(0, (sum, image) => sum + image.height);
  int maxWidth = screenshots.fold(0, (max, image) => max > image.width ? max : image.width);

  // 创建一个新的空白画布
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);

  // 将每个截图绘制到画布上
  int offset = 0;
  for (var image in screenshots) {
    canvas.drawImage(image, Offset(0, offset.toDouble()), ui.Paint());
    offset += image.height;
  }

  final picture = recorder.endRecording();
  final combinedImage = await picture.toImage(maxWidth, totalHeight);
  return combinedImage;
}