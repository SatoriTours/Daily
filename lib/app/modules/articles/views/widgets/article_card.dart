import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/smart_image.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/global.dart';

import 'article_info_item.dart';
import 'article_action_bar.dart';

/// 文章卡片组件
class ArticleCard extends GetView<ArticlesController> {
  final ArticleModel articleModel;

  const ArticleCard({super.key, required this.articleModel});

  @override
  Widget build(BuildContext context) {
    final isProcessing =
        articleModel.status == WebpageParserService.statusPending ||
        articleModel.status == WebpageParserService.statusWebContentFetched;
    final isError = articleModel.status == WebpageParserService.statusError;
    final colorScheme = AppTheme.getColorScheme(context);

    return Stack(
      children: [
        Card(
          margin: EdgeInsets.zero,
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          child: InkWell(
            onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: articleModel),
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [_buildArticleContent(context), _buildActionBar(context, isProcessing)],
              ),
            ),
          ),
        ),
        if (isError)
          Positioned(
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
          ),
      ],
    );
  }

  Widget _buildArticleContent(BuildContext context) {
    final hasImage =
        articleModel.hasHeaderImage() || (articleModel.coverImageUrl != null && articleModel.coverImageUrl!.isNotEmpty);
    final imagePath = articleModel.getHeaderImagePath();
    final hasTitle =
        (articleModel.aiTitle != null && articleModel.aiTitle!.isNotEmpty) ||
        (articleModel.title != null && articleModel.title!.isNotEmpty);
    final isError = articleModel.status == WebpageParserService.statusError;
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Stack(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (hasImage) _buildImage(context, imagePath),
                if (hasImage) const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [if (hasTitle) _buildTitle(context) else _buildUrlAsTitle(context)],
                  ),
                ),
              ],
            ),
            if (isError)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  articleModel.aiContent ?? '内容处理失败',
                  style: textTheme.bodySmall?.copyWith(color: colorScheme.error),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
        ),
      ],
    );
  }

  Widget _buildUrlAsTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Text(
      articleModel.url ?? '',
      style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurfaceVariant),
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Text(articleModel.showTitle(), style: textTheme.titleMedium, maxLines: 3, overflow: TextOverflow.ellipsis);
  }

  Widget _buildImage(BuildContext context, String localPath) {
    return SmartImage(
      localPath: localPath.isNotEmpty ? localPath : null,
      networkUrl: articleModel.coverImageUrl,
      width: 90,
      height: 70,
      fit: BoxFit.cover,
      borderRadius: 8,
    );
  }

  Widget _buildActionBar(BuildContext context, bool isProcessing) {
    final url = Uri.parse(articleModel.url ?? '');
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      margin: const EdgeInsets.only(top: 6),
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
}
