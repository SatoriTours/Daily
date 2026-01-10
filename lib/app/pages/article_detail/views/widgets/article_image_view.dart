import 'package:daily_satori/app/routes/app_navigation.dart';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/components/common/smart_image.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/services/file_service.dart';

class ArticleImageView extends ConsumerWidget {
  final String imagePath;
  final BoxFit fit;
  final int? articleId;
  final ArticleModel? article;
  final String? networkUrl;

  const ArticleImageView({
    super.key,
    required this.imagePath,
    this.fit = BoxFit.cover,
    this.articleId,
    required this.article,
    this.networkUrl,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final images = [imagePath];

    return GestureDetector(
      onTap: () => _showFullScreenImage(context, ref, images),
      child: Container(
        padding: Dimensions.paddingPage.copyWith(bottom: 0),
        width: double.infinity,
        constraints: const BoxConstraints(maxHeight: 200),
        child: SmartImage(
          localPath: imagePath.isNotEmpty ? imagePath : null,
          networkUrl: networkUrl ?? article?.coverImageUrl,
          fit: fit,
          borderRadius: Dimensions.radiusM,
        ),
      ),
    );
  }

  void _showFullScreenImage(
    BuildContext context,
    WidgetRef ref,
    List<String> images, {
    int initialIndex = 0,
  }) {
    showDialog(
      context: context,
      builder: (context) => Scaffold(
        appBar: AppBar(
          backgroundColor: Colors.black.withValues(alpha: Opacities.high),
          elevation: 0,
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.delete, color: Colors.white),
              onPressed: () async {
                await DialogUtils.showConfirm(
                  title: "确认删除",
                  message: "确定要删除这张图片吗?",
                  confirmText: "确认",
                  cancelText: "取消",
                  onConfirm: () async {
                    if (articleId != null) {
                      await ref
                          .read(
                            articleDetailControllerProvider(
                              articleId!,
                            ).notifier,
                          )
                          .deleteImage(images[initialIndex]);
                      UIUtils.showSuccess('删除成功', title: '提示');
                      AppNavigation.back();
                    }
                  },
                );
              },
            ),
            IconButton(
              icon: const Icon(Icons.close, color: Colors.white),
              onPressed: () => AppNavigation.back(),
            ),
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
    final resolved = FileService.i.resolveLocalMediaPath(path);
    if (File(resolved).existsSync()) {
      return FileImage(File(resolved));
    } else if (networkUrl != null && networkUrl!.isNotEmpty) {
      return NetworkImage(networkUrl!);
    } else if (article?.coverImageUrl != null &&
        article!.coverImageUrl!.isNotEmpty) {
      return NetworkImage(article!.coverImageUrl!);
    }
    return FileImage(File(resolved)); // 这里会报错，但会被errorBuilder处理
  }
}
