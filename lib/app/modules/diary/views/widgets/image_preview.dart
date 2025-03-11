import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'dart:io';

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
      height: 100,
      margin: EdgeInsets.symmetric(vertical: 8),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: images.length,
        itemBuilder: (context, index) {
          return Stack(
            children: [
              Container(
                width: 100,
                height: 100,
                margin: EdgeInsets.only(right: 8),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  image: DecorationImage(image: FileImage(File(images[index])), fit: BoxFit.cover),
                ),
              ),
              Positioned(
                right: 10,
                top: 5,
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
        },
      ),
    );
  }
}
