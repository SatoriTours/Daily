import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/smart_image.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/global.dart';

import 'article_info_item.dart';
import 'article_action_bar.dart';

/// 文章卡片组件
/// 负责展示单个文章的信息，包括标题、图片、状态等
class ArticleCard extends GetView<ArticlesController> {
  final ArticleModel articleModel;

  const ArticleCard({super.key, required this.articleModel});

  // 日志记录器
  static final _logger = Logger(printer: PrettyPrinter(methodCount: 0));

  @override
  Widget build(BuildContext context) {
    _logger.d('构建文章卡片: ${articleModel.id}');

    return Card(
      margin: EdgeInsets.zero,
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: _buildCardContent(context),
    );
  }

  /// 构建卡片内容
  Widget _buildCardContent(BuildContext context) {
    final isProcessing =
        articleModel.status == ArticleStatus.pending || articleModel.status == ArticleStatus.webContentFetched;

    return Stack(
      children: [
        // 主要内容
        _buildMainContent(context, isProcessing),
        // 错误状态标签
        if (articleModel.status == ArticleStatus.error) _buildErrorBadge(context),
      ],
    );
  }

  /// 构建主要内容
  Widget _buildMainContent(BuildContext context, bool isProcessing) {
    return InkWell(
      onTap: () {
        _logger.d('点击文章卡片: ${articleModel.id}');
        Get.toNamed(Routes.ARTICLE_DETAIL, arguments: articleModel);
      },
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [_buildArticleInfo(context), const SizedBox(height: 6), _buildActionBar(context, isProcessing)],
        ),
      ),
    );
  }

  /// 构建文章信息
  Widget _buildArticleInfo(BuildContext context) {
    final hasImage =
        articleModel.hasHeaderImage() || (articleModel.coverImageUrl != null && articleModel.coverImageUrl!.isNotEmpty);
    final hasTitle =
        (articleModel.aiTitle != null && articleModel.aiTitle!.isNotEmpty) ||
        (articleModel.title != null && articleModel.title!.isNotEmpty);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (hasImage) ...[_buildImage(context), const SizedBox(width: 12)],
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              // 标题或URL
              hasTitle ? _buildTitle(context) : _buildUrlAsTitle(context),
              // 错误信息
              if (articleModel.status == ArticleStatus.error) _buildErrorMessage(context),
            ],
          ),
        ),
      ],
    );
  }

  /// 构建标题
  Widget _buildTitle(BuildContext context) {
    return Text(
      articleModel.showTitle(),
      style: AppTheme.getTextTheme(context).titleMedium,
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// 构建URL作为标题
  Widget _buildUrlAsTitle(BuildContext context) {
    return Text(
      articleModel.url ?? '',
      style: AppTheme.getTextTheme(
        context,
      ).titleMedium?.copyWith(color: AppTheme.getColorScheme(context).onSurfaceVariant),
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// 构建错误信息
  Widget _buildErrorMessage(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Text(
        articleModel.aiContent ?? '内容处理失败',
        style: AppTheme.getTextTheme(context).bodySmall?.copyWith(color: AppTheme.getColorScheme(context).error),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  /// 构建图片
  Widget _buildImage(BuildContext context) {
    final imagePath = articleModel.getHeaderImagePath();
    return SmartImage(
      localPath: imagePath.isNotEmpty ? imagePath : null,
      networkUrl: articleModel.coverImageUrl,
      width: 90,
      height: 70,
      fit: BoxFit.cover,
      borderRadius: 8,
    );
  }

  /// 构建操作栏
  Widget _buildActionBar(BuildContext context, bool isProcessing) {
    final url = Uri.parse(articleModel.url ?? '');
    return SizedBox(
      height: 24,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          ArticleInfoItem(icon: Icons.public, text: getTopLevelDomain(url.host)),
          const SizedBox(width: 12),
          ArticleInfoItem(
            icon: Icons.access_time,
            text: articleModel.createdAt != null ? GetTimeAgo.parse(articleModel.createdAt!, pattern: 'MM-dd') : '未知时间',
          ),
          const Spacer(),
          ArticleActionBar(articleModel: articleModel, isProcessing: isProcessing),
        ],
      ),
    );
  }

  /// 构建错误状态标签
  Widget _buildErrorBadge(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Positioned(
      top: 0,
      right: 0,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: colorScheme.error,
          borderRadius: const BorderRadius.only(topRight: Radius.circular(10), bottomLeft: Radius.circular(10)),
        ),
        child: Text('加载失败', style: TextStyle(color: colorScheme.onError, fontSize: 12)),
      ),
    );
  }
}
