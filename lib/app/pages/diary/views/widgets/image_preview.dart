import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'dart:io';
import 'package:daily_satori/app/services/file_service.dart';

/// 图片预览组件 - 支持删除操作
class ImagePreview extends StatelessWidget {
  /// 图片路径列表
  final List<String> images;

  /// 图片删除回调
  final Function(int index) onDelete;

  /// 是否使用 XFile（true）或 File（false）
  final bool isXFile;

  const ImagePreview({super.key, required this.images, required this.onDelete, this.isXFile = false});

  @override
  Widget build(BuildContext context) {
    if (images.isEmpty) return const SizedBox.shrink();

    return Container(
      margin: EdgeInsets.symmetric(vertical: 8),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: List.generate(images.length, (index) {
          // 解析图片路径，处理相对路径
          final imagePath = images[index];
          final resolvedPath = FileService.i.resolveLocalMediaPath(imagePath);
          final file = File(resolvedPath);

          // 检查文件是否存在
          if (!file.existsSync()) {
            return _buildPlaceholder(context, index);
          }

          return Stack(
            children: [
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  image: DecorationImage(image: FileImage(file), fit: BoxFit.cover),
                ),
              ),
              Positioned(
                right: 2,
                top: 2,
                child: GestureDetector(
                  onTap: () => onDelete(index),
                  child: Container(
                    padding: EdgeInsets.all(4),
                    decoration: BoxDecoration(color: Colors.black.withAlpha(128), shape: BoxShape.circle),
                    child: Icon(FeatherIcons.x, size: 14, color: Colors.white),
                  ),
                ),
              ),
            ],
          );
        }),
      ),
    );
  }

  /// 构建图片占位符（文件不存在时）
  Widget _buildPlaceholder(BuildContext context, int index) {
    return Container(
      width: 100,
      height: 100,
      decoration: BoxDecoration(color: Colors.grey.withAlpha(77), borderRadius: BorderRadius.circular(8)),
      child: Stack(
        children: [
          Center(child: Icon(FeatherIcons.image, color: Colors.grey)),
          Positioned(
            right: 2,
            top: 2,
            child: GestureDetector(
              onTap: () => onDelete(index),
              child: Container(
                padding: EdgeInsets.all(4),
                decoration: BoxDecoration(color: Colors.black.withAlpha(128), shape: BoxShape.circle),
                child: Icon(FeatherIcons.x, size: 14, color: Colors.white),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
