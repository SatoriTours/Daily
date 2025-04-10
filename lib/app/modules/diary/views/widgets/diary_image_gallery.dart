import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';
import 'package:get/get.dart';
import 'dart:io';

/// 日记图片画廊组件
class DiaryImageGallery extends StatelessWidget {
  final String imagesString;

  const DiaryImageGallery({super.key, required this.imagesString});

  @override
  Widget build(BuildContext context) {
    final List<String> images = imagesString.split(',');

    return SizedBox(
      height: 100,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: images.length,
        itemBuilder: (context, index) {
          final String imagePath = images[index];
          final file = File(imagePath);

          // 检查文件是否存在
          if (!file.existsSync()) {
            return _buildPlaceholder(context);
          }

          return _buildImageItem(context, imagePath, file, index, images);
        },
      ),
    );
  }

  /// 构建图片占位符
  Widget _buildPlaceholder(BuildContext context) {
    return Center(
      child: Container(
        width: 100,
        height: 100,
        margin: const EdgeInsets.only(right: 8),
        decoration: BoxDecoration(
          color: DiaryStyle.tagBackgroundColor(context),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(FeatherIcons.image, color: DiaryStyle.primaryTextColor(context)),
      ),
    );
  }

  /// 构建单个图片项
  Widget _buildImageItem(BuildContext context, String imagePath, File file, int index, List<String> allImages) {
    return GestureDetector(
      onTap: () => _showFullScreenGallery(context, allImages, index),
      child: Container(
        width: 100,
        height: 100,
        margin: const EdgeInsets.only(right: 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          image: DecorationImage(image: FileImage(file), fit: BoxFit.cover),
        ),
      ),
    );
  }

  /// 显示全屏图片画廊
  void _showFullScreenGallery(BuildContext context, List<String> images, int initialIndex) {
    Get.dialog(
      Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: EdgeInsets.zero,
        child: GestureDetector(
          onTap: () => Navigator.of(context).pop(), // 点击背景关闭
          child: Scaffold(
            backgroundColor: Colors.black,
            appBar: AppBar(
              backgroundColor: Colors.black.withAlpha(179),
              elevation: 0,
              automaticallyImplyLeading: false,
              // 使用Navigator.of(context).pop()直接关闭当前对话框
              actions: [
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
            body: PhotoViewGallery.builder(
              scrollDirection: Axis.horizontal,
              pageController: PageController(initialPage: initialIndex),
              itemCount: images.length,
              builder: (BuildContext context, int index) {
                final imagePath = images[index];
                final file = File(imagePath);

                if (!file.existsSync()) {
                  return PhotoViewGalleryPageOptions.customChild(
                    child: Center(child: Icon(FeatherIcons.image, color: Colors.white70, size: 48)),
                  );
                }

                return PhotoViewGalleryPageOptions(
                  imageProvider: FileImage(file),
                  initialScale: PhotoViewComputedScale.contained,
                  minScale: PhotoViewComputedScale.contained,
                  maxScale: PhotoViewComputedScale.covered * 5.0,
                  heroAttributes: PhotoViewHeroAttributes(tag: imagePath),
                  errorBuilder: (context, error, stackTrace) {
                    return Center(child: Icon(FeatherIcons.alertCircle, color: Colors.white70, size: 48));
                  },
                );
              },
              loadingBuilder:
                  (context, event) => Center(
                    child: CircularProgressIndicator(
                      value: event == null ? 0 : event.cumulativeBytesLoaded / (event.expectedTotalBytes ?? 1),
                      valueColor: const AlwaysStoppedAnimation<Color>(Colors.white70),
                    ),
                  ),
              backgroundDecoration: const BoxDecoration(color: Colors.black),
            ),
          ),
        ),
      ),
      barrierDismissible: true,
    );
  }
}
