import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/pages/ai_chat/models/search_result.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import 'package:daily_satori/app/pages/diary/utils/diary_utils.dart';

/// 搜索结果卡片组件
///
/// 显示单个搜索结果，支持点击导航到详情页
/// 支持文章、日记、书籍等多种类型的搜索结果
class SearchResultCard extends StatelessWidget {
  // ========================================================================
  // ========================================================================

  /// 搜索结果数据
  final SearchResult result;

  /// 构造函数，确保接收搜索结果数据
  const SearchResultCard({super.key, required this.result});

  // ========================================================================
  // UI构建
  // ========================================================================

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 4),
      elevation: 0,
      color: AppColors.getSurfaceContainerHighest(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        side: BorderSide(
          color: AppColors.getOutline(context).withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: InkWell(
        onTap: () => _navigateToDetail(),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: Dimensions.spacingM,
            vertical: Dimensions.spacingS,
          ),
          child: Row(
            children: [
              // 类型图标
              Text(result.typeIcon, style: const TextStyle(fontSize: 16)),
              const SizedBox(width: Dimensions.spacingS),
              // 标题
              Expanded(
                child: Text(
                  result.title,
                  style: AppTypography.bodyMedium.copyWith(
                    fontWeight: FontWeight.w500,
                    color: AppColors.getOnSurface(context),
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              // 收藏图标
              if (result.isFavorite == true) ...[
                const SizedBox(width: Dimensions.spacingXs),
                Icon(
                  Icons.favorite,
                  size: 14,
                  color: AppColors.getError(context),
                ),
              ],
              // 箭头图标
              const SizedBox(width: Dimensions.spacingXs),
              Icon(
                Icons.chevron_right,
                size: 18,
                color: AppColors.getOnSurfaceVariant(context),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ========================================================================
  // 事件处理
  // ========================================================================

  /// 导航到详情页
  ///
  /// 根据搜索结果类型导航到对应的详情页面
  /// 文章导航到详情页，日记和书籍显示对话框
  void _navigateToDetail() {
    logger.i(
      '[SearchResultCard] 点击搜索结果: ${result.type.name} - ${result.title}',
    );

    switch (result.type) {
      case SearchResultType.article:
        logger.d('[SearchResultCard] 导航到文章详情: ${result.id}');
        AppNavigation.toNamed(Routes.articleDetail, arguments: result.id);
        break;
      case SearchResultType.diary:
        logger.d('[SearchResultCard] 显示日记详情对话框: ${result.id}');
        _showDiaryDialog();
        break;
      case SearchResultType.book:
        logger.d('[SearchResultCard] 显示书籍详情对话框: ${result.id}');
        _showBookDialog();
        break;
    }
  }

  /// 显示日记详情对话框
  void _showDiaryDialog() {
    logger.d('[SearchResultCard] 尝试查找日记, ID: ${result.id}');
    final diary = DiaryRepository.i.find(result.id);
    if (diary == null) {
      logger.w('[SearchResultCard] 找不到日记, ID: ${result.id}');
      final context = AppNavigation.navigatorKey.currentContext;
      if (context != null) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('找不到该日记')));
      }
      return;
    }

    logger.d(
      '[SearchResultCard] 找到日记, 标题: ${result.title}, 内容长度: ${diary.content.length}',
    );
    final context = AppNavigation.navigatorKey.currentContext;
    if (context == null) return;

    showDialog(
      context: context,
      builder: (ctx) => _ContentDialog(
        title: result.title,
        content: diary.content.isNotEmpty ? diary.content : '（暂无内容）',
        createdAt: diary.createdAt,
        tags: diary.tags,
        icon: '📔',
      ),
    );
  }

  /// 显示书籍详情对话框
  void _showBookDialog() {
    final book = BookRepository.i.find(result.id);
    if (book == null) {
      final context = AppNavigation.navigatorKey.currentContext;
      if (context != null) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('找不到该书籍')));
      }
      return;
    }

    final context = AppNavigation.navigatorKey.currentContext;
    if (context == null) return;

    showDialog(
      context: context,
      builder: (ctx) => _ContentDialog(
        title: book.title,
        content: '**作者**: ${book.author}\n\n${book.introduction}',
        createdAt: book.createdAt,
        icon: '📖',
      ),
    );
  }
}

/// 内容详情对话框
///
/// 用于显示日记或书籍的详细内容（全屏显示）
class _ContentDialog extends StatelessWidget {
  final String title;
  final String content;
  final DateTime? createdAt;
  final String? tags;
  final String icon;

  const _ContentDialog({
    required this.title,
    required this.content,
    this.createdAt,
    this.tags,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Dialog.fullscreen(
      backgroundColor: DiaryStyles.getBackgroundColor(context),
      child: Scaffold(
        backgroundColor: DiaryStyles.getBackgroundColor(context),
        appBar: AppBar(
          backgroundColor: DiaryStyles.getCardBackgroundColor(context),
          elevation: 0,
          leading: IconButton(
            icon: Icon(
              Icons.close,
              color: DiaryStyles.getPrimaryTextColor(context),
            ),
            onPressed: () => AppNavigation.back(),
          ),
          title: Row(
            children: [
              Text(icon, style: const TextStyle(fontSize: 24)),
              const SizedBox(width: Dimensions.spacingS),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: DiaryStyles.getPrimaryTextColor(context),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (createdAt != null)
                      Text(
                        _formatDateTime(createdAt!),
                        style: TextStyle(
                          fontSize: 12,
                          color: DiaryStyles.getSecondaryTextColor(context),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        body: _buildContent(context),
      ),
    );
  }

  /// 构建对话框内容
  Widget _buildContent(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(Dimensions.spacingM),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标签列表
          if (tags != null && tags!.isNotEmpty) ...[
            _buildTags(context),
            const SizedBox(height: Dimensions.spacingM),
          ],
          // 使用与日记页面相同的 Markdown 渲染
          MarkdownBody(
            data: content,
            selectable: true,
            styleSheet: DiaryUtils.getMarkdownStyleSheet(context),
            softLineBreak: true,
            fitContent: true,
            shrinkWrap: true,
          ),
        ],
      ),
    );
  }

  /// 构建标签列表（与日记页面风格一致）
  Widget _buildTags(BuildContext context) {
    return Wrap(
      spacing: Dimensions.spacingS,
      runSpacing: Dimensions.spacingS,
      children: tags!.split(',').map((tag) {
        final trimmedTag = tag.trim();
        if (trimmedTag.isEmpty) return const SizedBox.shrink();
        return Container(
          padding: const EdgeInsets.symmetric(
            horizontal: Dimensions.spacingS + 2,
            vertical: Dimensions.spacingXs,
          ),
          decoration: BoxDecoration(
            color: DiaryStyles.getAccentColor(context).withAlpha(20),
            borderRadius: Dimensions.borderRadiusM,
            border: Border.all(
              color: DiaryStyles.getAccentColor(context).withAlpha(50),
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.tag,
                size: 14,
                color: DiaryStyles.getAccentColor(context),
              ),
              const SizedBox(width: Dimensions.spacingXs),
              Text(
                trimmedTag,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: DiaryStyles.getAccentColor(context),
                ),
              ),
            ],
          ),
        );
      }).toList(),
    );
  }

  /// 格式化日期时间
  String _formatDateTime(DateTime dateTime) {
    return '${dateTime.year}年${dateTime.month}月${dateTime.day}日 '
        '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
  }
}
