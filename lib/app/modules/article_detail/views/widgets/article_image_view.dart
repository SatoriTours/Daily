import 'dart:io';

import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';

import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/smart_image.dart';

class ArticleImageView extends StatelessWidget {
  final String imagePath;
  final BoxFit fit;
  final ArticleDetailController controller;
  final String? networkUrl;

  const ArticleImageView({
    super.key,
    required this.imagePath,
    this.fit = BoxFit.cover,
    required this.controller,
    this.networkUrl,
  });

  @override
  Widget build(BuildContext context) {
    final images = [imagePath];

    return GestureDetector(
      onTap: () => _showFullScreenImage(images),
      child: Container(
        padding: Dimensions.paddingPage.copyWith(bottom: 0),
        width: double.infinity,
        constraints: const BoxConstraints(maxHeight: 200),
        child: SmartImage(
          localPath: imagePath.isNotEmpty ? imagePath : null,
          networkUrl: networkUrl ?? controller.articleModel.coverImageUrl,
          fit: fit,
          borderRadius: Dimensions.radiusM,
        ),
      ),
    );
  }

  void _showFullScreenImage(List<String> images, {int initialIndex = 0}) {
    Get.dialog(
      Scaffold(
        appBar: AppBar(
          backgroundColor: Colors.black.withAlpha(179),
          elevation: 0,
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.delete, color: Colors.white),
              onPressed: () {
                Get.defaultDialog(
                  title: "确认删除",
                  middleText: "确定要删除这张图片吗?",
                  confirm: TextButton(
                    onPressed: () async {
                      await controller.deleteImage(images[initialIndex]);
                      Get.back();
                      Get.back();
                      Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
                    },
                    child: Text("确认", style: TextStyle(color: Colors.red)),
                  ),
                  cancel: TextButton(onPressed: () => Get.back(), child: const Text("取消")),
                );
              },
            ),
            IconButton(icon: const Icon(Icons.close, color: Colors.white), onPressed: () => Get.back()),
          ],
        ),
        backgroundColor: Colors.black,
        body: PhotoViewGallery.builder(
          scrollDirection: Axis.horizontal,
          pageController: PageController(initialPage: initialIndex),
          itemCount: images.length,
          builder: (context, index) {
            return PhotoViewGalleryPageOptions(
              imageProvider: _getImageProvider(images[index]),
              initialScale: PhotoViewComputedScale.contained,
              minScale: PhotoViewComputedScale.contained,
              maxScale: PhotoViewComputedScale.covered * 5.0,
              errorBuilder: (context, error, stackTrace) {
                logger.i("加载路径错误 ${images[index]}");
                return const SizedBox.shrink();
              },
            );
          },
        ),
      ),
      barrierDismissible: true,
    );
  }

  ImageProvider _getImageProvider(String path) {
    if (File(path).existsSync()) {
      return FileImage(File(path));
    } else if (networkUrl != null && networkUrl!.isNotEmpty) {
      return NetworkImage(networkUrl!);
    } else if (controller.articleModel.coverImageUrl != null && controller.articleModel.coverImageUrl!.isNotEmpty) {
      return NetworkImage(controller.articleModel.coverImageUrl!);
    }
    return FileImage(File(path)); // 这里会报错，但会被errorBuilder处理
  }
}
