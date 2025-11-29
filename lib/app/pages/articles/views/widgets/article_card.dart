import 'package:get_time_ago/get_time_ago.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/components/common/smart_image.dart';
import 'package:daily_satori/app/components/common/article_info_item.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'article_action_bar.dart';

/// 文章卡片组件
///
/// 纯展示组件,负责展示单个文章的信息
/// 通过回调函数与外部交互
class ArticleCard extends StatelessWidget {
  final ArticleModel articleModel;
  final VoidCallback? onTap;
  final VoidCallback? onFavoriteToggle;
  final VoidCallback? onShare;

  const ArticleCard({super.key, required this.articleModel, this.onTap, this.onFavoriteToggle, this.onShare});

  bool get _isProcessing =>
      articleModel.status == ArticleStatus.pending || articleModel.status == ArticleStatus.webContentFetched;

  bool get _hasError => articleModel.status == ArticleStatus.error;

  bool get _hasImage =>
      articleModel.hasHeaderImage() || (articleModel.coverImageUrl != null && articleModel.coverImageUrl!.isNotEmpty);

  bool get _hasTitle =>
      (articleModel.aiTitle != null && articleModel.aiTitle!.isNotEmpty) ||
      (articleModel.title != null && articleModel.title!.isNotEmpty);

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Stack(children: [_buildMainContent(context), if (_hasError) _buildErrorBadge(context)]),
    );
  }

  Widget _buildMainContent(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [_buildArticleInfo(context), const SizedBox(height: 6), _buildBottomBar(context)],
        ),
      ),
    );
  }

  Widget _buildArticleInfo(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_hasImage) ...[_buildImage(), const SizedBox(width: 12)],
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [_buildTitleSection(context), if (_hasError) _buildErrorMessage(context)],
          ),
        ),
      ],
    );
  }

  Widget _buildTitleSection(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    if (_hasTitle) {
      return Text(articleModel.showTitle(), style: textTheme.titleMedium, maxLines: 3, overflow: TextOverflow.ellipsis);
    }

    return Text(
      articleModel.url ?? '',
      style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurfaceVariant),
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

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

  Widget _buildImage() {
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

  Widget _buildBottomBar(BuildContext context) {
    final url = Uri.parse(articleModel.url ?? '');
    return SizedBox(
      height: 24,
      child: Row(
        children: [
          ArticleInfoItem(icon: Icons.public, text: StringUtils.getTopLevelDomain(url.host)),
          const SizedBox(width: 12),
          ArticleInfoItem(
            icon: Icons.access_time,
            text: GetTimeAgo.parse(articleModel.createdAt, pattern: 'MM-dd'),
          ),
          const Spacer(),
          ArticleActionBar(
            articleModel: articleModel,
            isProcessing: _isProcessing,
            onFavoriteToggle: onFavoriteToggle,
            onShare: onShare,
          ),
        ],
      ),
    );
  }

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
