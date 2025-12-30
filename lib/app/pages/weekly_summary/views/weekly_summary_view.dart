import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/data/index.dart';

/// 周报页面视图
///
/// 展示每周的文章和日记总结
class WeeklySummaryView extends ConsumerWidget {
  const WeeklySummaryView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(weeklySummaryControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: _buildAppBar(context, ref, state),
      body: _buildBody(context, ref, state),
    );
  }

  // ========================================================================
  // AppBar
  // ========================================================================

  PreferredSizeWidget _buildAppBar(BuildContext context, WidgetRef ref, WeeklySummaryControllerState state) {
    return SAppBar(
      title: Text('weekly_summary.title'.t, style: const TextStyle(color: Colors.white)),
      centerTitle: true,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
      leading: IconButton(
        icon: const Icon(Icons.settings, color: Colors.white),
        tooltip: 'title.settings'.t,
        onPressed: () => AppNavigation.toNamed(Routes.settings),
      ),
      actions: [
        state.isGenerating
            ? const Padding(
                padding: EdgeInsets.all(12),
                child: SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                ),
              )
            : IconButton(
                icon: const Icon(Icons.refresh, color: Colors.white),
                tooltip: 'weekly_summary.regenerate'.t,
                onPressed: () => ref.read(weeklySummaryControllerProvider.notifier).regenerateCurrentSummary(),
              ),
      ],
    );
  }

  // ========================================================================
  // Body
  // ========================================================================

  Widget _buildBody(BuildContext context, WidgetRef ref, WeeklySummaryControllerState state) {
    if (state.isGenerating) {
      return _buildGeneratingState(context);
    }

    if (state.summaries.isEmpty) {
      return _buildEmptyState(context, ref);
    }

    return Column(
      children: [
        // 周选择器
        _buildWeekSelector(context, ref, state),
        // 周报内容
        Expanded(child: _buildSummaryContent(context, ref, state)),
      ],
    );
  }

  /// 构建生成中状态
  Widget _buildGeneratingState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: Dimensions.paddingCard,
            decoration: CardStyles.getStandardDecoration(context),
            child: Column(
              children: [
                SizedBox(
                  width: 60,
                  height: 60,
                  child: CircularProgressIndicator(
                    strokeWidth: 3,
                    valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
                  ),
                ),
                Dimensions.verticalSpacerL,
                Text(
                  'weekly_summary.generating'.t,
                  style: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
                ),
                Dimensions.verticalSpacerS,
                Text(
                  'weekly_summary.generating_hint'.t,
                  style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 构建空状态
  Widget _buildEmptyState(BuildContext context, WidgetRef ref) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.summarize_outlined,
            size: 80,
            color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3),
          ),
          Dimensions.verticalSpacerL,
          Text(
            'weekly_summary.no_summary'.t,
            style: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
          Dimensions.verticalSpacerS,
          Text(
            'weekly_summary.no_summary_hint'.t,
            style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
            textAlign: TextAlign.center,
          ),
          Dimensions.verticalSpacerL,
          ElevatedButton.icon(
            onPressed: () => ref.read(weeklySummaryControllerProvider.notifier).checkAndGenerate(),
            icon: const Icon(Icons.auto_awesome),
            label: Text('weekly_summary.generate_now'.t),
            style: ButtonStyles.getPrimaryStyle(context),
          ),
        ],
      ),
    );
  }

  // ========================================================================
  // 周选择器
  // ========================================================================

  Widget _buildWeekSelector(BuildContext context, WidgetRef ref, WeeklySummaryControllerState state) {
    return Container(
      height: 44,
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        border: Border(bottom: BorderSide(color: AppColors.getOutlineVariant(context), width: 0.5)),
      ),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        itemCount: state.summaries.length,
        itemBuilder: (context, index) {
          final summary = state.summaries[index];
          return _buildWeekChip(context, ref, state, summary);
        },
      ),
    );
  }

  Widget _buildWeekChip(
    BuildContext context,
    WidgetRef ref,
    WeeklySummaryControllerState state,
    WeeklySummaryModel summary,
  ) {
    final isSelected = state.currentSummary?.id == summary.id;

    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(summary.weekLabel),
        selected: isSelected,
        onSelected: (_) => ref.read(weeklySummaryControllerProvider.notifier).selectSummary(summary),
        showCheckmark: false,
        backgroundColor: AppColors.getSurface(context),
        selectedColor: AppColors.getPrimary(context).withValues(alpha: 0.2),
        visualDensity: VisualDensity.compact,
        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        labelStyle: AppTypography.labelSmall.copyWith(
          color: isSelected ? AppColors.getPrimary(context) : AppColors.getOnSurfaceVariant(context),
          fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
        ),
        side: BorderSide(color: isSelected ? AppColors.getPrimary(context) : AppColors.getOutlineVariant(context)),
        padding: const EdgeInsets.symmetric(horizontal: 8),
      ),
    );
  }

  // ========================================================================
  // 周报内容
  // ========================================================================

  Widget _buildSummaryContent(BuildContext context, WidgetRef ref, WeeklySummaryControllerState state) {
    final summary = state.currentSummary;
    if (summary == null) {
      return const SizedBox.shrink();
    }

    return SingleChildScrollView(padding: const EdgeInsets.all(12), child: _buildContentCard(context, ref, summary));
  }

  /// 构建内容卡片
  Widget _buildContentCard(BuildContext context, WidgetRef ref, WeeklySummaryModel summary) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: CardStyles.getStandardDecoration(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题行 - 包含统计信息
          Row(
            children: [
              Icon(Icons.auto_awesome, color: AppColors.getPrimary(context), size: 18),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  summary.weekTitle,
                  style: AppTypography.titleSmall.copyWith(color: AppColors.getOnSurface(context)),
                ),
              ),
              // 紧凑统计
              const Icon(Icons.article_outlined, color: Colors.blue, size: 14),
              const SizedBox(width: 2),
              Text(
                '${summary.articleCount}',
                style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
              ),
              const SizedBox(width: 8),
              const Icon(Icons.book_outlined, color: Colors.green, size: 14),
              const SizedBox(width: 2),
              Text(
                '${summary.diaryCount}',
                style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
              ),
              if (summary.isFailed) ...[
                const SizedBox(width: 8),
                const Icon(Icons.error_outline, color: Colors.red, size: 16),
              ],
            ],
          ),
          const SizedBox(height: 12),
          // Markdown 内容
          _buildMarkdownContent(context, ref, summary.content),
        ],
      ),
    );
  }

  /// 构建 Markdown 内容
  Widget _buildMarkdownContent(BuildContext context, WidgetRef ref, String content) {
    // 处理特殊链接格式
    final processedContent = _processContentLinks(content);

    return MarkdownBody(
      data: processedContent,
      selectable: true,
      styleSheet: MarkdownStyleSheet(
        h1: AppTypography.headingMedium.copyWith(color: AppColors.getOnSurface(context)),
        h2: AppTypography.titleLarge.copyWith(color: AppColors.getOnSurface(context)),
        h3: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
        p: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
        listBullet: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
        blockquote: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurfaceVariant(context),
          fontStyle: FontStyle.italic,
        ),
        blockquoteDecoration: BoxDecoration(
          border: Border(left: BorderSide(color: AppColors.getPrimary(context), width: 4)),
        ),
        code: AppTypography.bodySmall.copyWith(
          backgroundColor: AppColors.getSurfaceContainer(context),
          color: AppColors.getPrimary(context),
        ),
        codeblockDecoration: BoxDecoration(
          color: AppColors.getSurfaceContainer(context),
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
        ),
      ),
      onTapLink: (text, href, title) {
        _handleLinkTap(context, ref, href);
      },
    );
  }

  /// 处理内容中的特殊链接格式
  String _processContentLinks(String content) {
    // 将 [[article:ID:标题]] 或 [[article:ID]] 转换为 Markdown 链接
    var processed = content.replaceAllMapped(RegExp(r'\[\[article:(\d+)(?::([^\]]+))?\]\]'), (match) {
      final id = match.group(1);
      final title = match.group(2) ?? '文章$id';
      return '[📄 $title](article:$id)';
    });

    // 将 [[diary:ID:日期]] 或 [[diary:ID]] 转换为 Markdown 链接
    processed = processed.replaceAllMapped(RegExp(r'\[\[diary:(\d+)(?::([^\]]+))?\]\]'), (match) {
      final id = match.group(1);
      final date = match.group(2) ?? '日记$id';
      return '[📝 $date](diary:$id)';
    });

    // 将 [[viewpoint:ID:标题]] 或 [[viewpoint:ID]] 转换为 Markdown 链接
    processed = processed.replaceAllMapped(RegExp(r'\[\[viewpoint:(\d+)(?::([^\]]+))?\]\]'), (match) {
      final id = match.group(1);
      final title = match.group(2) ?? '书摘$id';
      return '[📖 $title](viewpoint:$id)';
    });

    return processed;
  }

  /// 处理链接点击
  void _handleLinkTap(BuildContext context, WidgetRef ref, String? href) {
    if (href == null) return;

    if (href.startsWith('article:')) {
      final id = int.tryParse(href.substring(8));
      if (id != null) {
        _openArticle(id);
      }
    } else if (href.startsWith('diary:')) {
      final id = int.tryParse(href.substring(6));
      if (id != null) {
        final diary = _openDiary(id);
        if (diary != null) {
          _showDiaryDetailSheet(context, diary);
        }
      }
    }
  }

  /// 打开文章详情
  void _openArticle(int articleId) {
    AppNavigation.toNamed(Routes.articleDetail, arguments: articleId);
  }

  /// 打开日记详情
  ///
  /// 获取日记数据，触发 View 显示对话框
  DiaryModel? _openDiary(int diaryId) {
    final diary = DiaryRepository.i.find(diaryId);
    if (diary == null) {
      UIUtils.showError('weekly_summary.diary_not_found'.t);
      return null;
    }

    return diary;
  }

  /// 显示日记详情底部弹出框
  void _showDiaryDetailSheet(BuildContext context, DiaryModel diary) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.getSurface(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      builder: (context) => _DiaryDetailSheet(diary: diary),
    );
  }
}

/// 日记详情底部弹出框
class _DiaryDetailSheet extends StatelessWidget {
  final DiaryModel diary;

  const _DiaryDetailSheet({required this.diary});

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: AppColors.getSurface(context),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
          ),
          child: Column(
            children: [
              // 拖动指示器
              _buildDragHandle(context),
              // 标题栏
              _buildHeader(context),
              // 分隔线
              Divider(height: 1, color: AppColors.getOutlineVariant(context)),
              // 内容
              Expanded(child: _buildContent(context, scrollController)),
            ],
          ),
        );
      },
    );
  }

  /// 构建拖动指示器
  Widget _buildDragHandle(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12, bottom: 8),
      width: 40,
      height: 4,
      decoration: BoxDecoration(color: AppColors.getOutlineVariant(context), borderRadius: BorderRadius.circular(2)),
    );
  }

  /// 构建标题栏
  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Icon(Icons.book_outlined, color: AppColors.getPrimary(context), size: 24),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'weekly_summary.diary_detail'.t,
                  style: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
                ),
                Text(
                  _formatDate(diary.createdAt),
                  style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                ),
              ],
            ),
          ),
          IconButton(
            icon: Icon(Icons.close, color: AppColors.getOnSurfaceVariant(context)),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
    );
  }

  /// 格式化日期
  String _formatDate(DateTime? date) {
    if (date == null) return '';
    final local = date.toLocal();
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')} ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  /// 构建内容
  Widget _buildContent(BuildContext context, ScrollController scrollController) {
    final tagList = diary.tags?.split(',').where((t) => t.trim().isNotEmpty).toList() ?? [];

    return SingleChildScrollView(
      controller: scrollController,
      padding: Dimensions.paddingCard,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 日记内容
          SelectableText(
            diary.content,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context), height: 1.6),
          ),
          // 标签（如有）
          if (tagList.isNotEmpty) ...[
            Dimensions.verticalSpacerL,
            Wrap(spacing: 8, runSpacing: 8, children: tagList.map((tag) => _buildTag(context, tag)).toList()),
          ],
          // 底部安全区域
          SizedBox(height: MediaQuery.of(context).padding.bottom + 16),
        ],
      ),
    );
  }

  /// 构建标签
  Widget _buildTag(BuildContext context, String tag) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.getPrimary(context).withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      ),
      child: Text('#$tag', style: AppTypography.labelSmall.copyWith(color: AppColors.getPrimary(context))),
    );
  }
}
