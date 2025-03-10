import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
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

          return _buildImageItem(context, imagePath, file);
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
  Widget _buildImageItem(BuildContext context, String imagePath, File file) {
    return GestureDetector(
      onTap: () => _showFullImage(context, imagePath),
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

  /// 显示全屏图片
  void _showFullImage(BuildContext context, String imagePath) {
    final file = File(imagePath);
    if (!file.existsSync()) return;

    showDialog(
      context: context,
      builder:
          (context) => Dialog(
            insetPadding: EdgeInsets.zero,
            child: Stack(
              fit: StackFit.expand,
              children: [
                // 图片
                InteractiveViewer(minScale: 0.5, maxScale: 3.0, child: Image.file(file, fit: BoxFit.contain)),
                // 关闭按钮
                Positioned(
                  top: 16,
                  right: 16,
                  child: GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(color: Colors.black.withOpacity(0.5), shape: BoxShape.circle),
                      child: const Icon(Icons.close, color: Colors.white, size: 20),
                    ),
                  ),
                ),
              ],
            ),
          ),
    );
  }
}
