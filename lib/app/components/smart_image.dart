import 'dart:io';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:cached_network_image/cached_network_image.dart';

/// 智能图片组件 - 优先显示本地文件图片，本地图片为空则显示网络图片
class SmartImage extends StatelessWidget {
  /// 本地图片路径
  final String? localPath;

  /// 网络图片URL
  final String? networkUrl;

  /// 图片适应模式
  final BoxFit fit;

  /// 图片宽度
  final double? width;

  /// 图片高度
  final double? height;

  /// 边框圆角
  final double borderRadius;

  /// 错误图标大小
  final double errorIconSize;

  /// 点击图片回调
  final VoidCallback? onTap;

  const SmartImage({
    Key? key,
    this.localPath,
    this.networkUrl,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.borderRadius = 8.0,
    this.errorIconSize = 24.0,
    this.onTap,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: GestureDetector(onTap: onTap, child: _buildImage(context)),
    );
  }

  Widget _buildImage(BuildContext context) {
    // 如果本地路径有效，优先使用本地图片
    if (localPath != null && localPath!.isNotEmpty) {
      return Image.file(
        File(localPath!),
        fit: fit,
        width: width,
        height: height,
        errorBuilder: (_, __, ___) {
          // 本地图片加载失败，尝试使用网络图片
          if (networkUrl != null && networkUrl!.isNotEmpty) {
            return _buildNetworkImage(context);
          }
          return _buildErrorWidget(context);
        },
      );
    }

    // 如果本地路径无效但网络URL有效，使用网络图片
    if (networkUrl != null && networkUrl!.isNotEmpty) {
      return _buildNetworkImage(context);
    }

    // 两者都无效，显示错误占位符
    return _buildErrorWidget(context);
  }

  Widget _buildNetworkImage(BuildContext context) {
    return CachedNetworkImage(
      imageUrl: networkUrl!,
      fit: fit,
      width: width,
      height: height,
      placeholder: (context, url) => _buildLoadingWidget(context),
      errorWidget: (context, url, error) => _buildErrorWidget(context),
    );
  }

  Widget _buildLoadingWidget(BuildContext context) {
    return Container(
      width: width,
      height: height,
      color: Theme.of(context).colorScheme.surfaceVariant,
      child: Center(child: CircularProgressIndicator(strokeWidth: 2.0, color: Theme.of(context).colorScheme.primary)),
    );
  }

  Widget _buildErrorWidget(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: ComponentStyle.imageContainerDecoration(context),
      child: Icon(
        Icons.image_not_supported,
        color: Theme.of(context).colorScheme.onSurfaceVariant,
        size: errorIconSize,
      ),
    );
  }
}
