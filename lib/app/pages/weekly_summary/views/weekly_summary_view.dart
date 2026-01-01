import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/weekly_summary/providers/weekly_summary_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/data/index.dart';

/// 周报页面视图
class WeeklySummaryView extends ConsumerWidget {
  const WeeklySummaryView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(weeklySummaryControllerProvider);

    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: _WeeklySummaryAppBar(state: state),
      body: _WeeklySummaryBody(state: state),
    );
  }
}

// ============================================================================
// AppBar
// ============================================================================

class _WeeklySummaryAppBar extends ConsumerWidget implements PreferredSizeWidget {
  final WeeklySummaryControllerState state;
  const _WeeklySummaryAppBar({required this.state});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) => SAppBar(
    title: Text('weekly_summary.title'.t, style: TextStyle(color: AppColors.getOnPrimary(context))),
    centerTitle: true,
    elevation: 0,
    backgroundColorLight: AppColors.primary,
    backgroundColorDark: AppColors.backgroundDark,
    foregroundColor: AppColors.getOnPrimary(context),
    leading: IconButton(
      icon: Icon(Icons.settings, color: AppColors.getOnPrimary(context)),
      tooltip: 'title.settings'.t,
      onPressed: () => AppNavigation.toNamed(Routes.settings),
    ),
    actions: [
      state.isGenerating
          ? Padding(
              padding: Dimensions.paddingCard,
              child: SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(AppColors.getOnPrimary(context)),
                ),
              ),
            )
          : IconButton(
              icon: Icon(Icons.refresh, color: AppColors.getOnPrimary(context)),
              tooltip: 'weekly_summary.regenerate'.t,
              onPressed: () => ref.read(weeklySummaryControllerProvider.notifier).regenerateCurrentSummary(),
            ),
    ],
  );
}

// ============================================================================
// Body
// ============================================================================

class _WeeklySummaryBody extends ConsumerWidget {
  final WeeklySummaryControllerState state;
  const _WeeklySummaryBody({required this.state});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (state.isGenerating) return const _GeneratingState();
    if (state.summaries.isEmpty) return const _EmptyState();

    return Column(
      children: [
        _WeekSelector(state: state),
        Expanded(child: _SummaryContent(summary: state.currentSummary)),
      ],
    );
  }
}

// ============================================================================
// 生成中状态
// ============================================================================

class _GeneratingState extends StatelessWidget {
  const _GeneratingState();

  @override
  Widget build(BuildContext context) => Center(
    child: Container(
      padding: Dimensions.paddingCard,
      decoration: CardStyles.getStandardDecoration(context),
      child: Column(
        mainAxisSize: MainAxisSize.min,
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
  );
}

// ============================================================================
// 空状态
// ============================================================================

class _EmptyState extends ConsumerWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context, WidgetRef ref) => Center(
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.summarize_outlined, size: 80, color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.3)),
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

// ============================================================================
// 周选择器
// ============================================================================

class _WeekSelector extends ConsumerWidget {
  final WeeklySummaryControllerState state;
  const _WeekSelector({required this.state});

  @override
  Widget build(BuildContext context, WidgetRef ref) => Container(
    height: 44,
    decoration: BoxDecoration(
      color: AppColors.getSurfaceContainer(context),
      border: Border(bottom: BorderSide(color: AppColors.getOutlineVariant(context), width: 0.5)),
    ),
    child: ListView.builder(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      itemCount: state.summaries.length,
      itemBuilder: (context, index) =>
          _WeekChip(summary: state.summaries[index], isSelected: state.currentSummary?.id == state.summaries[index].id),
    ),
  );
}

class _WeekChip extends ConsumerWidget {
  final WeeklySummaryModel summary;
  final bool isSelected;
  const _WeekChip({required this.summary, required this.isSelected});

  @override
  Widget build(BuildContext context, WidgetRef ref) => Padding(
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

// ============================================================================
// 周报内容
// ============================================================================

class _SummaryContent extends StatelessWidget {
  final WeeklySummaryModel? summary;
  const _SummaryContent({required this.summary});

  @override
  Widget build(BuildContext context) {
    if (summary == null) return const SizedBox.shrink();
    return SingleChildScrollView(
      padding: Dimensions.paddingCard,
      child: _ContentCard(summary: summary!),
    );
  }
}

class _ContentCard extends StatelessWidget {
  final WeeklySummaryModel summary;
  const _ContentCard({required this.summary});

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: Dimensions.paddingCard,
    decoration: CardStyles.getStandardDecoration(context),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _CardHeader(summary: summary),
        const SizedBox(height: 12),
        _MarkdownContent(content: summary.content),
      ],
    ),
  );
}

class _CardHeader extends StatelessWidget {
  final WeeklySummaryModel summary;
  const _CardHeader({required this.summary});

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Icon(Icons.auto_awesome, color: AppColors.getPrimary(context), size: 18),
      const SizedBox(width: 6),
      Expanded(
        child: Text(
          summary.weekTitle,
          style: AppTypography.titleSmall.copyWith(color: AppColors.getOnSurface(context)),
        ),
      ),
      Icon(Icons.article_outlined, color: AppColors.getPrimary(context), size: 14),
      const SizedBox(width: 2),
      Text(
        '${summary.articleCount}',
        style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
      ),
      const SizedBox(width: 8),
      Icon(Icons.book_outlined, color: AppColors.getSuccess(context), size: 14),
      const SizedBox(width: 2),
      Text(
        '${summary.diaryCount}',
        style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
      ),
      if (summary.isFailed) ...[
        const SizedBox(width: 8),
        Icon(Icons.error_outline, color: AppColors.getError(context), size: 16),
      ],
    ],
  );
}

// ============================================================================
// Markdown 内容
// ============================================================================

class _MarkdownContent extends StatelessWidget {
  final String content;
  const _MarkdownContent({required this.content});

  @override
  Widget build(BuildContext context) {
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
      onTapLink: (text, href, title) => _handleLinkTap(context, href),
    );
  }

  String _processContentLinks(String content) {
    var processed = content.replaceAllMapped(
      RegExp(r'\[\[article:(\d+)(?::([^\]]+))?\]\]'),
      (match) => '[📄 ${match.group(2) ?? '文章${match.group(1)}'}](article:${match.group(1)})',
    );
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[diary:(\d+)(?::([^\]]+))?\]\]'),
      (match) => '[📝 ${match.group(2) ?? '日记${match.group(1)}'}](diary:${match.group(1)})',
    );
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[viewpoint:(\d+)(?::([^\]]+))?\]\]'),
      (match) => '[📖 ${match.group(2) ?? '书摘${match.group(1)}'}](viewpoint:${match.group(1)})',
    );
    // 兼容 bookmark 格式（映射到 viewpoint）
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[bookmark:(\d+)(?::([^\]]+))?\]\]'),
      (match) => '[📖 ${match.group(2) ?? '书摘${match.group(1)}'}](viewpoint:${match.group(1)})',
    );
    return processed;
  }

  void _handleLinkTap(BuildContext context, String? href) {
    if (href == null) return;

    if (href.startsWith('article:')) {
      final id = int.tryParse(href.substring(8));
      if (id != null) AppNavigation.toNamed(Routes.articleDetail, arguments: id);
    } else if (href.startsWith('diary:')) {
      final id = int.tryParse(href.substring(6));
      if (id != null) {
        final diary = DiaryRepository.i.find(id);
        if (diary != null) {
          _showDiaryDetailSheet(context, diary);
        } else {
          UIUtils.showError('weekly_summary.diary_not_found'.t);
        }
      }
    } else if (href.startsWith('viewpoint:')) {
      final id = int.tryParse(href.substring(10));
      if (id != null) {
        final viewpoint = BookViewpointRepository.i.find(id);
        if (viewpoint != null) {
          _showViewpointDetailSheet(context, viewpoint);
        } else {
          UIUtils.showError('weekly_summary.viewpoint_not_found'.t);
        }
      }
    }
  }

  void _showViewpointDetailSheet(BuildContext context, BookViewpointModel viewpoint) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.getSurface(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      builder: (context) => _ViewpointDetailSheet(viewpoint: viewpoint),
    );
  }

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

// ============================================================================
// 日记详情底部弹出框
// ============================================================================

class _DiaryDetailSheet extends StatelessWidget {
  final DiaryModel diary;
  const _DiaryDetailSheet({required this.diary});

  @override
  Widget build(BuildContext context) => DraggableScrollableSheet(
    initialChildSize: 0.7,
    minChildSize: 0.5,
    maxChildSize: 0.95,
    expand: false,
    builder: (context, scrollController) => Container(
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      child: Column(
        children: [
          _buildDragHandle(context),
          _buildHeader(context),
          Divider(height: 1, color: AppColors.getOutlineVariant(context)),
          Expanded(child: _buildContent(context, scrollController)),
        ],
      ),
    ),
  );

  Widget _buildDragHandle(BuildContext context) => Container(
    margin: const EdgeInsets.only(top: 12, bottom: 8),
    width: 40,
    height: 4,
    decoration: BoxDecoration(color: AppColors.getOutlineVariant(context), borderRadius: BorderRadius.circular(2)),
  );

  Widget _buildHeader(BuildContext context) => Padding(
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

  String _formatDate(DateTime? date) {
    if (date == null) return '';
    final local = date.toLocal();
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')} '
        '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  Widget _buildContent(BuildContext context, ScrollController scrollController) {
    final tagList = diary.tags?.split(',').where((t) => t.trim().isNotEmpty).toList() ?? [];

    return SingleChildScrollView(
      controller: scrollController,
      padding: Dimensions.paddingCard,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          MarkdownBody(
            data: diary.content,
            selectable: true,
            styleSheet: MarkdownStyleSheet(
              h1: AppTypography.headingMedium.copyWith(color: AppColors.getOnSurface(context)),
              h2: AppTypography.titleLarge.copyWith(color: AppColors.getOnSurface(context)),
              h3: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
              p: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context), height: 1.6),
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
          ),
          if (tagList.isNotEmpty) ...[
            Dimensions.verticalSpacerL,
            Wrap(spacing: 8, runSpacing: 8, children: tagList.map((tag) => _buildTag(context, tag)).toList()),
          ],
          SizedBox(height: MediaQuery.of(context).padding.bottom + 16),
        ],
      ),
    );
  }

  Widget _buildTag(BuildContext context, String tag) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
    decoration: BoxDecoration(
      color: AppColors.getPrimary(context).withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
    ),
    child: Text('#$tag', style: AppTypography.labelSmall.copyWith(color: AppColors.getPrimary(context))),
  );
}

// ============================================================================
// 书摘详情底部弹出框
// ============================================================================

class _ViewpointDetailSheet extends StatelessWidget {
  final BookViewpointModel viewpoint;
  const _ViewpointDetailSheet({required this.viewpoint});

  @override
  Widget build(BuildContext context) => DraggableScrollableSheet(
    initialChildSize: 0.7,
    minChildSize: 0.5,
    maxChildSize: 0.95,
    expand: false,
    builder: (context, scrollController) => Container(
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      child: Column(
        children: [
          _buildDragHandle(context),
          _buildHeader(context),
          Divider(height: 1, color: AppColors.getOutlineVariant(context)),
          Expanded(child: _buildContent(context, scrollController)),
        ],
      ),
    ),
  );

  Widget _buildDragHandle(BuildContext context) => Container(
    margin: const EdgeInsets.only(top: 12, bottom: 8),
    width: 40,
    height: 4,
    decoration: BoxDecoration(color: AppColors.getOutlineVariant(context), borderRadius: BorderRadius.circular(2)),
  );

  Widget _buildHeader(BuildContext context) {
    final book = BookRepository.i.find(viewpoint.bookId);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Icon(Icons.bookmark_outlined, color: AppColors.getPrimary(context), size: 24),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'weekly_summary.viewpoint_detail'.t,
                  style: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
                ),
                if (book != null)
                  Text(
                    book.title,
                    style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
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

  Widget _buildContent(BuildContext context, ScrollController scrollController) => SingleChildScrollView(
    controller: scrollController,
    padding: Dimensions.paddingCard,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText(
          viewpoint.content,
          style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context), height: 1.6),
        ),
        SizedBox(height: MediaQuery.of(context).padding.bottom + 16),
      ],
    ),
  );
}
