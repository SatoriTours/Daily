import 'package:daily_satori/app/pages/weekly_summary/providers/weekly_summary_controller_provider.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:flutter_markdown/flutter_markdown.dart';

import 'package:daily_satori/app/pages/diary/views/widgets/diary_image_gallery.dart';

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

class _WeeklySummaryAppBar extends ConsumerWidget
    implements PreferredSizeWidget {
  final WeeklySummaryControllerState state;
  const _WeeklySummaryAppBar({required this.state});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) => SAppBar(
    title: GestureDetector(
      onTap: () => _showHistorySheet(context, ref, state),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            'weekly_summary.title'.t,
            style: TextStyle(color: AppColors.getOnPrimary(context)),
          ),
          const SizedBox(width: 4),
          Icon(Icons.arrow_drop_down, color: AppColors.getOnPrimary(context)),
        ],
      ),
    ),
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
      IconButton(
        icon: Icon(Icons.history, color: AppColors.getOnPrimary(context)),
        tooltip: 'weekly_summary.history'.t,
        onPressed: () => _showHistorySheet(context, ref, state),
      ),
      state.isGenerating
          ? Padding(
              padding: Dimensions.paddingCard,
              child: SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    AppColors.getOnPrimary(context),
                  ),
                ),
              ),
            )
          : IconButton(
              icon: Icon(Icons.refresh, color: AppColors.getOnPrimary(context)),
              tooltip: 'weekly_summary.regenerate'.t,
              onPressed: () => ref
                  .read(weeklySummaryControllerProvider.notifier)
                  .regenerateCurrentSummary(),
            ),
    ],
  );
}

class _WeeklySummaryBody extends ConsumerWidget {
  final WeeklySummaryControllerState state;
  const _WeeklySummaryBody({required this.state});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (state.isGenerating) return const _GeneratingState();
    if (state.isLoading) return const _LoadingState();
    if (state.summaries.isEmpty) return const _EmptyState();

    return _SummaryContent(summary: state.currentSummary);
  }
}

// 加载状态

class _LoadingState extends StatelessWidget {
  const _LoadingState();

  @override
  Widget build(BuildContext context) => Center(
    child: CircularProgressIndicator(
      valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
    ),
  );
}

// 生成中状态

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
              valueColor: AlwaysStoppedAnimation<Color>(
                AppColors.getPrimary(context),
              ),
            ),
          ),
          Dimensions.verticalSpacerL,
          Text(
            'weekly_summary.generating'.t,
            style: AppTypography.titleMedium.copyWith(
              color: AppColors.getOnSurface(context),
            ),
          ),
          Dimensions.verticalSpacerS,
          Text(
            'weekly_summary.generating_hint'.t,
            style: AppTypography.bodySmall.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    ),
  );
}

// 空状态

class _EmptyState extends ConsumerWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context, WidgetRef ref) => Center(
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
          style: AppTypography.titleMedium.copyWith(
            color: AppColors.getOnSurfaceVariant(context),
          ),
        ),
        Dimensions.verticalSpacerS,
        Text(
          'weekly_summary.no_summary_hint'.t,
          style: AppTypography.bodySmall.copyWith(
            color: AppColors.getOnSurfaceVariant(context),
          ),
          textAlign: TextAlign.center,
        ),
        Dimensions.verticalSpacerL,
        ElevatedButton.icon(
          onPressed: () => ref
              .read(weeklySummaryControllerProvider.notifier)
              .checkAndGenerate(),
          icon: const Icon(Icons.auto_awesome),
          label: Text('weekly_summary.generate_now'.t),
          style: ButtonStyles.getPrimaryStyle(context),
        ),
      ],
    ),
  );
}

// 周报内容

class _SummaryContent extends StatelessWidget {
  final WeeklySummaryModel? summary;
  const _SummaryContent({required this.summary});

  @override
  Widget build(BuildContext context) {
    if (summary == null) return const SizedBox.shrink();
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(
        horizontal: Dimensions.spacingM,
        vertical: Dimensions.spacingM,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SummaryHeader(summary: summary!),
          const SizedBox(height: 16),
          _MarkdownContent(content: summary!.content),
          SizedBox(height: MediaQuery.of(context).padding.bottom + 20),
        ],
      ),
    );
  }
}

class _SummaryHeader extends StatelessWidget {
  final WeeklySummaryModel summary;
  const _SummaryHeader({required this.summary});

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Icon(Icons.auto_awesome, color: AppColors.getPrimary(context), size: 18),
      const SizedBox(width: 8),
      Expanded(
        child: Text(
          summary.weekTitle,
          style: AppTypography.titleMedium.copyWith(
            color: AppColors.getOnSurface(context),
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
      _StatBadge(
        icon: Icons.article_outlined,
        count: summary.articleCount,
        color: AppColors.getPrimary(context),
      ),
      const SizedBox(width: 12),
      _StatBadge(
        icon: Icons.book_outlined,
        count: summary.diaryCount,
        color: AppColors.getSuccess(context),
      ),
      if (summary.isFailed) ...[
        const SizedBox(width: 12),
        Icon(Icons.error_outline, color: AppColors.getError(context), size: 18),
      ],
    ],
  );
}

class _StatBadge extends StatelessWidget {
  final IconData icon;
  final int count;
  final Color color;

  const _StatBadge({
    required this.icon,
    required this.count,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: color, size: 16),
        const SizedBox(width: 4),
        Text(
          '$count',
          style: AppTypography.labelMedium.copyWith(
            color: AppColors.getOnSurfaceVariant(context),
            fontWeight: FontWeight.w500,
          ),
        ),
      ],
    );
  }
}

// Markdown 内容

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
        h1: AppTypography.headingMedium.copyWith(
          color: AppColors.getOnSurface(context),
        ),
        h2: AppTypography.titleLarge.copyWith(
          color: AppColors.getOnSurface(context),
        ),
        h3: AppTypography.titleMedium.copyWith(
          color: AppColors.getOnSurface(context),
        ),
        p: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurface(context),
        ),
        listBullet: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurface(context),
        ),
        blockquote: AppTypography.bodyMedium.copyWith(
          color: AppColors.getOnSurfaceVariant(context),
          fontStyle: FontStyle.italic,
        ),
        blockquoteDecoration: BoxDecoration(
          border: Border(
            left: BorderSide(color: AppColors.getPrimary(context), width: 4),
          ),
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
      (match) =>
          '[📄 ${match.group(2) ?? '文章${match.group(1)}'}](article:${match.group(1)})',
    );
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[diary:(\d+)(?::([^\]]+))?\]\]'),
      (match) =>
          '[📝 ${match.group(2) ?? '日记${match.group(1)}'}](diary:${match.group(1)})',
    );
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[viewpoint:(\d+)(?::([^\]]+))?\]\]'),
      (match) =>
          '[📖 ${match.group(2) ?? '书摘${match.group(1)}'}](viewpoint:${match.group(1)})',
    );
    // 兼容 bookmark 格式（映射到 viewpoint）
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[bookmark:(\d+)(?::([^\]]+))?\]\]'),
      (match) =>
          '[📖 ${match.group(2) ?? '书摘${match.group(1)}'}](viewpoint:${match.group(1)})',
    );
    return processed;
  }

  void _handleLinkTap(BuildContext context, String? href) {
    if (href == null) return;

    if (href.startsWith('article:')) {
      final id = int.tryParse(href.substring(8));
      if (id != null) {
        AppNavigation.toNamed(Routes.articleDetail, arguments: id);
      }
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

  void _showViewpointDetailSheet(
    BuildContext context,
    BookViewpointModel viewpoint,
  ) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.getSurface(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(Dimensions.radiusL),
        ),
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
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(Dimensions.radiusL),
        ),
      ),
      builder: (context) => _DiaryDetailSheet(diary: diary),
    );
  }
}

// 日记详情底部弹出框

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
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(Dimensions.radiusL),
        ),
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
    decoration: BoxDecoration(
      color: AppColors.getOutlineVariant(context),
      borderRadius: BorderRadius.circular(2),
    ),
  );

  Widget _buildHeader(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
    child: Row(
      children: [
        Icon(
          Icons.book_outlined,
          color: AppColors.getPrimary(context),
          size: 24,
        ),
        Dimensions.horizontalSpacerS,
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'weekly_summary.diary_detail'.t,
                style: AppTypography.titleMedium.copyWith(
                  color: AppColors.getOnSurface(context),
                ),
              ),
              Text(
                _formatDate(diary.createdAt),
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.getOnSurfaceVariant(context),
                ),
              ),
            ],
          ),
        ),
        IconButton(
          icon: Icon(
            Icons.close,
            color: AppColors.getOnSurfaceVariant(context),
          ),
          onPressed: () => AppNavigation.back(),
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

  Widget _buildContent(
    BuildContext context,
    ScrollController scrollController,
  ) {
    final tagList =
        diary.tags?.split(',').where((t) => t.trim().isNotEmpty).toList() ?? [];

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
              h1: AppTypography.headingMedium.copyWith(
                color: AppColors.getOnSurface(context),
              ),
              h2: AppTypography.titleLarge.copyWith(
                color: AppColors.getOnSurface(context),
              ),
              h3: AppTypography.titleMedium.copyWith(
                color: AppColors.getOnSurface(context),
              ),
              p: AppTypography.bodyMedium.copyWith(
                color: AppColors.getOnSurface(context),
                height: 1.6,
              ),
              listBullet: AppTypography.bodyMedium.copyWith(
                color: AppColors.getOnSurface(context),
              ),
              blockquote: AppTypography.bodyMedium.copyWith(
                color: AppColors.getOnSurfaceVariant(context),
                fontStyle: FontStyle.italic,
              ),
              blockquoteDecoration: BoxDecoration(
                border: Border(
                  left: BorderSide(
                    color: AppColors.getPrimary(context),
                    width: 4,
                  ),
                ),
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
          if (diary.images != null && diary.images!.isNotEmpty) ...[
            Dimensions.verticalSpacerL,
            DiaryImageGallery(imagesString: diary.images!),
          ],
          if (tagList.isNotEmpty) ...[
            Dimensions.verticalSpacerL,
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: tagList.map((tag) => _buildTag(context, tag)).toList(),
            ),
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
    child: Text(
      '#$tag',
      style: AppTypography.labelSmall.copyWith(
        color: AppColors.getPrimary(context),
      ),
    ),
  );
}

// 书摘详情底部弹出框

class _ViewpointDetailSheet extends ConsumerWidget {
  final BookViewpointModel viewpoint;
  const _ViewpointDetailSheet({required this.viewpoint});

  @override
  Widget build(BuildContext context, WidgetRef ref) => DraggableScrollableSheet(
    initialChildSize: 0.7,
    minChildSize: 0.5,
    maxChildSize: 0.95,
    expand: false,
    builder: (context, scrollController) => Container(
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(Dimensions.radiusL),
        ),
      ),
      child: Column(
        children: [
          _buildDragHandle(context),
          _buildHeader(context, ref),
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
    decoration: BoxDecoration(
      color: AppColors.getOutlineVariant(context),
      borderRadius: BorderRadius.circular(2),
    ),
  );

  Widget _buildHeader(BuildContext context, WidgetRef ref) {
    final booksNotifier = ref.read(booksStateProvider.notifier);
    final book = booksNotifier.findBookById(viewpoint.bookId);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Icon(
            Icons.bookmark_outlined,
            color: AppColors.getPrimary(context),
            size: 24,
          ),
          Dimensions.horizontalSpacerS,
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'weekly_summary.viewpoint_detail'.t,
                  style: AppTypography.titleMedium.copyWith(
                    color: AppColors.getOnSurface(context),
                  ),
                ),
                if (book != null)
                  Text(
                    book.title,
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.getOnSurfaceVariant(context),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          IconButton(
            icon: Icon(
              Icons.close,
              color: AppColors.getOnSurfaceVariant(context),
            ),
            onPressed: () => AppNavigation.back(),
          ),
        ],
      ),
    );
  }

  Widget _buildContent(
    BuildContext context,
    ScrollController scrollController,
  ) => SingleChildScrollView(
    controller: scrollController,
    padding: Dimensions.paddingCard,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText(
          viewpoint.content,
          style: AppTypography.bodyMedium.copyWith(
            color: AppColors.getOnSurface(context),
            height: 1.6,
          ),
        ),
        SizedBox(height: MediaQuery.of(context).padding.bottom + 16),
      ],
    ),
  );
}

// 历史周报选择器

void _showHistorySheet(
  BuildContext context,
  WidgetRef ref,
  WeeklySummaryControllerState state,
) {
  showModalBottomSheet(
    context: context,
    backgroundColor: AppColors.getSurface(context),
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(
        top: Radius.circular(Dimensions.radiusL),
      ),
    ),
    builder: (context) => DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.4,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) =>
          _HistorySheet(state: state, scrollController: scrollController),
    ),
  );
}

class _HistorySheet extends ConsumerWidget {
  final WeeklySummaryControllerState state;
  final ScrollController scrollController;

  const _HistorySheet({required this.state, required this.scrollController});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.only(top: 4),
          alignment: Alignment.center,
          child: Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.getOutline(context).withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(left: 20, right: 20, bottom: 4),
          child: Row(
            children: [
              Text(
                'weekly_summary.history'.t,
                style: AppTypography.titleMedium.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => AppNavigation.back(),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
                iconSize: 20,
              ),
            ],
          ),
        ),
        const Divider(height: 1, thickness: 0.5),
        Expanded(
          child: ListView.separated(
            controller: scrollController,
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: state.summaries.length,
            separatorBuilder: (context, index) => const Divider(
              height: 1,
              indent: 20,
              endIndent: 20,
              thickness: 0.5,
            ),
            itemBuilder: (context, index) {
              final summary = state.summaries[index];
              final isSelected = state.currentSummary?.id == summary.id;
              return InkWell(
                onTap: () {
                  ref
                      .read(weeklySummaryControllerProvider.notifier)
                      .selectSummary(summary);
                  AppNavigation.back();
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 12,
                  ),
                  color: isSelected
                      ? AppColors.getPrimary(context).withValues(alpha: 0.05)
                      : null,
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              summary.weekLabel,
                              style: AppTypography.bodyLarge.copyWith(
                                color: isSelected
                                    ? AppColors.getPrimary(context)
                                    : AppColors.getOnSurface(context),
                                fontWeight: isSelected
                                    ? FontWeight.w600
                                    : FontWeight.normal,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              summary.weekTitle,
                              style: AppTypography.bodySmall.copyWith(
                                color: AppColors.getOnSurfaceVariant(context),
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (isSelected)
                        Icon(
                          Icons.check,
                          color: AppColors.getPrimary(context),
                          size: 20,
                        ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
