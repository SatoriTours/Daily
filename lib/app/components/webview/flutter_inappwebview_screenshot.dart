import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/services.dart';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

Future<List<String>> captureFullPageScreenshot(InAppWebViewController controller) async {
  logger.d("[captureFullPageScreenshot] - 开始获取网页截图");

  try {
    await _initializeWebPage(controller);
    final pageInfo = await _getPageDimensions(controller);
    final screenshots = await _captureScreenshots(controller, pageInfo);
    return await _saveScreenshotsAsFiles(screenshots);
  } catch (e, stackTrace) {
    logger.e("截取网页截图时出错: $e");
    logger.e(stackTrace);
    return [];
  } finally {
    await _cleanupWebPage(controller);
  }
}

Future<void> _initializeWebPage(InAppWebViewController controller) async {
  await controller.evaluateJavascript(source: "initPage()");
}

Future<({int totalHeight, int screenHeight, int totalPages})> _getPageDimensions(
  InAppWebViewController controller,
) async {
  final height = (await controller.evaluateJavascript(source: "document.documentElement.scrollHeight")) as int;
  final screenHeight = (await controller.evaluateJavascript(source: "window.innerHeight")) as int;

  final totalPages = (height / screenHeight).ceil();
  logger.d("总页数: $totalPages");

  return (totalHeight: height, screenHeight: screenHeight, totalPages: totalPages);
}

Future<List<ui.Image>> _captureScreenshots(
  InAppWebViewController controller,
  ({int totalHeight, int screenHeight, int totalPages}) pageInfo,
) async {
  List<ui.Image> screenshots = [];

  for (var i = 0; i < pageInfo.totalPages; i++) {
    final position = i * pageInfo.screenHeight;
    // logger.i("正在截取第${i + 1}页: 位置=$position");

    await _scrollToPosition(controller, position);
    final screenshot = await _captureScreenshot(controller);

    if (screenshot != null) {
      final processedImage = await _processScreenshot(screenshot, i, pageInfo.screenHeight, pageInfo.totalHeight);
      screenshots.add(processedImage);
    }
  }

  return screenshots;
}

Future<void> _scrollToPosition(InAppWebViewController controller, int position) async {
  await controller.evaluateJavascript(source: "window.scrollTo(0, $position)");
  await Future.delayed(WebViewConfig.screenshotDelay);
}

Future<Uint8List?> _captureScreenshot(InAppWebViewController controller) async {
  return await controller.takeScreenshot();
}

Future<ui.Image> _processScreenshot(Uint8List screenshot, int pageIndex, int screenHeight, int totalHeight) async {
  final codec = await ui.instantiateImageCodec(screenshot);
  final frame = await codec.getNextFrame();

  final isLastPage = (pageIndex + 1) * screenHeight > totalHeight;
  if (isLastPage) {
    final heightRatio = ((pageIndex + 1) * screenHeight - totalHeight) / screenHeight;
    return await cropImageFromHeight(frame.image, heightRatio);
  }

  return frame.image;
}

Future<ui.Image> cropImageFromHeight(ui.Image image, double startHeightRatio) async {
  final startHeight = (image.height * startHeightRatio).round();
  logger.i("裁剪图片: 开始高度比例=$startHeightRatio, 实际开始高度=$startHeight, 原图高度=${image.height}");

  if (startHeight >= image.height) {
    throw ArgumentError('开始高度不能大于或等于图片高度');
  }

  final croppedHeight = image.height - startHeight;
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);

  canvas.drawImageRect(
    image,
    Rect.fromLTWH(0, startHeight.toDouble(), image.width.toDouble(), croppedHeight.toDouble()),
    Rect.fromLTWH(0, 0, image.width.toDouble(), croppedHeight.toDouble()),
    ui.Paint(),
  );

  return await recorder.endRecording().toImage(image.width, croppedHeight);
}

Future<List<String>> _saveScreenshotsAsFiles(List<ui.Image> screenshots) async {
  List<String> filePaths = [];

  for (int i = 0; i < screenshots.length; i++) {
    final image = screenshots[i];
    final filePath = FileService.i.getScreenshotPath();
    final file = File(filePath);

    final pngBytes = await image.toByteData(format: ui.ImageByteFormat.png);
    await file.writeAsBytes(pngBytes!.buffer.asUint8List());

    filePaths.add(filePath);
    logger.i("保存截图 ${i + 1}/${screenshots.length}: $filePath");
  }

  return filePaths;
}

Future<void> _cleanupWebPage(InAppWebViewController controller) async {
  await controller.evaluateJavascript(source: "showObstructiveNodes()");
  await controller.evaluateJavascript(source: "window.scrollTo(0, 0)");
}
