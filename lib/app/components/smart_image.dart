import 'dart:io';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:cached_network_image/cached_network_image.dart';

/// 智能图片组件
///
/// 一个灵活的图片显示组件，具有以下特点：
/// - 优先显示本地文件图片
/// - 本地图片加载失败时自动回退到网络图片
/// - 支持网络图片缓存
/// - 提供加载和错误状态的占位符
/// - 支持圆角和点击事件
class SmartImage extends StatelessWidget {
  /// 本地图片路径
  ///
  /// 如果提供了有效路径，组件会优先尝试加载本地图片
  final String? localPath;

  /// 网络图片URL
  ///
  /// 当本地图片不可用或加载失败时，组件会尝试加载网络图片
  final String? networkUrl;

  /// 图片适应模式
  ///
  /// 默认为 [BoxFit.cover]，图片会填充整个容器并保持宽高比
  final BoxFit fit;

  /// 图片宽度
  ///
  /// 可选参数，如果不指定则自适应父容器
  final double? width;

  /// 图片高度
  ///
  /// 可选参数，如果不指定则自适应父容器
  final double? height;

  /// 边框圆角
  ///
  /// 默认为 8.0，设置图片容器的圆角半径
  final double borderRadius;

  /// 错误图标大小
  ///
  /// 默认为 24.0，当图片加载失败时显示的错误图标大小
  final double errorIconSize;

  /// 点击图片回调
  ///
  /// 可选参数，当图片被点击时触发
  final VoidCallback? onTap;

  /// 创建一个智能图片组件
  ///
  /// [localPath] 和 [networkUrl] 至少需要提供一个，否则将显示错误占位符
  const SmartImage({
    super.key,
    this.localPath,
    this.networkUrl,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.borderRadius = 8.0,
    this.errorIconSize = 24.0,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: GestureDetector(onTap: onTap, child: _buildImage(context)),
    );
  }

  /// 构建图片组件
  ///
  /// 按以下顺序尝试显示图片：
  /// 1. 本地文件图片
  /// 2. 网络图片
  /// 3. 错误占位符
  Widget _buildImage(BuildContext context) {
    if (_hasValidLocalPath) {
      return _buildLocalImage(context);
    }

    if (_hasValidNetworkUrl) {
      return _buildNetworkImage(context);
    }

    return _buildErrorWidget(context);
  }

  /// 构建本地图片组件
  Widget _buildLocalImage(BuildContext context) {
    return Image.file(
      File(localPath!),
      fit: fit,
      width: width,
      height: height,
      errorBuilder: (_, __, ___) {
        return _hasValidNetworkUrl ? _buildNetworkImage(context) : _buildErrorWidget(context);
      },
    );
  }

  /// 构建网络图片组件
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

  /// 构建加载中占位符
  Widget _buildLoadingWidget(BuildContext context) {
    return Container(
      width: width,
      height: height,
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Center(child: CircularProgressIndicator(strokeWidth: 2.0, color: Theme.of(context).colorScheme.primary)),
    );
  }

  /// 构建错误占位符
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

  /// 检查是否有有效的本地路径
  bool get _hasValidLocalPath => localPath != null && localPath!.isNotEmpty;

  /// 检查是否有有效的网络URL
  bool get _hasValidNetworkUrl => networkUrl != null && networkUrl!.isNotEmpty;
}
