import 'dart:ui' as ui;
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'dart:io';

Future<String> captureFullPageScreenshot(
    InAppWebViewController controller) async {
  try {
    // 获取网页的高度
    final height = await controller.evaluateJavascript(
        source: "document.documentElement.scrollHeight");
    final screenHeight =
        await controller.evaluateJavascript(source: "window.innerHeight");

    logger.d("网页高度信息: $height, $screenHeight");

    final int totalHeight = height;
    final int screenHeightInt = screenHeight;

    // 计算总页数
    final int totalPages = (totalHeight / screenHeightInt).ceil();
    logger.d("总页数: $totalPages");

    List<ui.Image> screenshots = [];
    for (var i = 0; i < totalPages; i++) {
      logger
          .i("页面高度 => $screenHeightInt, totalHeight => $totalHeight, 页数 => $i");

      int position = i * screenHeightInt;
      // 滚动到当前位置
      await controller.evaluateJavascript(
          source: "window.scrollTo(0, $position)");
      await Future.delayed(Duration(milliseconds: 100)); // 等待页面滚动

      // 截取当前屏幕的截图
      final Uint8List? screenshot = await controller.takeScreenshot();
      if (screenshot != null) {
        final codec = await ui.instantiateImageCodec(screenshot);
        final frame = await codec.getNextFrame();
        if ((i + 1) * screenHeightInt > totalHeight) {
          screenshots.add(await cropImageFromHeight(frame.image,
              ((i + 1) * screenHeightInt - totalHeight) / screenHeightInt));
        } else {
          screenshots.add(frame.image);
        }
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

Future<ui.Image> cropImageFromHeight(
    ui.Image image, double startHeightRatio) async {
  int startHeight = (image.height * startHeightRatio).round();
  logger.i(
      "开始高度比例是 $startHeightRatio, 实际开始高度是 $startHeight, 图片高度 => ${image.height}");

  // 确保开始高度不超过图片高度
  if (startHeight >= image.height) {
    throw ArgumentError('开始高度不能大于或等于图片高度');
  }

  // 计算裁剪后的高度
  final int croppedHeight = image.height - startHeight;

  // 创建一个新的画布
  final ui.PictureRecorder recorder = ui.PictureRecorder();
  final ui.Canvas canvas = ui.Canvas(recorder);

  // 在新画布上绘制裁剪后的图像
  canvas.drawImageRect(
    image,
    Rect.fromLTWH(0, startHeight.toDouble(), image.width.toDouble(),
        croppedHeight.toDouble()),
    Rect.fromLTWH(0, 0, image.width.toDouble(), croppedHeight.toDouble()),
    ui.Paint(),
  );

  // 生成新的图像
  final ui.Picture picture = recorder.endRecording();
  final ui.Image croppedImage =
      await picture.toImage(image.width, croppedHeight);

  return croppedImage;
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
  int maxWidth = screenshots.fold(
      0, (max, image) => max > image.width ? max : image.width);

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
