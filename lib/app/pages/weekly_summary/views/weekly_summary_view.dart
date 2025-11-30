import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import '../controllers/weekly_summary_controller.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/data/index.dart';

/// 周报页面视图
///
/// 展示每周的文章和日记总结
class WeeklySummaryView extends GetView<WeeklySummaryController> {
  const WeeklySummaryView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: _buildAppBar(context),
      body: Obx(() => _buildBody(context)),
    );
  }

  // ========================================================================
  // AppBar
  // ========================================================================

  PreferredSizeWidget _buildAppBar(BuildContext context) {
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
        onPressed: () => Get.toNamed(Routes.settings),
      ),
      actions: [
        Obx(
          () => controller.isGenerating.value
              ? Padding(
                  padding: const EdgeInsets.all(12),
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
                  tooltip: 'weekly_summary.refresh'.t,
                  onPressed: controller.checkAndGenerate,
                ),
        ),
      ],
    );
  }

  // ========================================================================
  // Body
  // ========================================================================

  Widget _buildBody(BuildContext context) {
    if (controller.isLoading.value) {
      return StyleGuide.getLoadingState(context);
    }

    if (controller.isGenerating.value) {
      return _buildGeneratingState(context);
    }

    if (controller.summaries.isEmpty) {
      return _buildEmptyState(context);
    }

    return Column(
      children: [
        // 周选择器
        _buildWeekSelector(context),
        // 周报内容
        Expanded(child: _buildSummaryContent(context)),
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
  Widget _buildEmptyState(BuildContext context) {
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
            onPressed: controller.checkAndGenerate,
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

  Widget _buildWeekSelector(BuildContext context) {
    return Container(
      height: 56,
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        border: Border(bottom: BorderSide(color: AppColors.getOutlineVariant(context), width: 1)),
      ),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: controller.summaries.length,
        itemBuilder: (context, index) {
          final summary = controller.summaries[index];
          return _buildWeekChip(context, summary);
        },
      ),
    );
  }

  Widget _buildWeekChip(BuildContext context, WeeklySummaryModel summary) {
    final isSelected = controller.currentSummary.value?.id == summary.id;

    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(summary.weekLabel),
        selected: isSelected,
        onSelected: (_) => controller.selectSummary(summary),
        backgroundColor: AppColors.getSurface(context),
        selectedColor: AppColors.getPrimary(context).withValues(alpha: 0.2),
        labelStyle: AppTypography.labelMedium.copyWith(
          color: isSelected ? AppColors.getPrimary(context) : AppColors.getOnSurfaceVariant(context),
          fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
        ),
        side: BorderSide(color: isSelected ? AppColors.getPrimary(context) : AppColors.getOutlineVariant(context)),
      ),
    );
  }

  // ========================================================================
  // 周报内容
  // ========================================================================

  Widget _buildSummaryContent(BuildContext context) {
    final summary = controller.currentSummary.value;
    if (summary == null) {
      return const SizedBox.shrink();
    }

    return SingleChildScrollView(
      padding: Dimensions.paddingPage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 统计卡片
          _buildStatsCard(context, summary),
          Dimensions.verticalSpacerM,
          // 内容卡片
          _buildContentCard(context, summary),
        ],
      ),
    );
  }

  /// 构建统计卡片
  Widget _buildStatsCard(BuildContext context, WeeklySummaryModel summary) {
    return Container(
      padding: Dimensions.paddingCard,
      decoration: CardStyles.getStandardDecoration(context),
      child: Row(
        children: [
          Expanded(
            child: _buildStatItem(
              context,
              icon: Icons.article_outlined,
              label: 'weekly_summary.articles'.t,
              value: summary.articleCount.toString(),
              color: Colors.blue,
            ),
          ),
          Container(width: 1, height: 40, color: AppColors.getOutlineVariant(context)),
          Expanded(
            child: _buildStatItem(
              context,
              icon: Icons.book_outlined,
              label: 'weekly_summary.diaries'.t,
              value: summary.diaryCount.toString(),
              color: Colors.green,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatItem(
    BuildContext context, {
    required IconData icon,
    required String label,
    required String value,
    required Color color,
  }) {
    return Column(
      children: [
        Icon(icon, color: color, size: 28),
        Dimensions.verticalSpacerXs,
        Text(value, style: AppTypography.headingMedium.copyWith(color: AppColors.getOnSurface(context))),
        Text(label, style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context))),
      ],
    );
  }

  /// 构建内容卡片
  Widget _buildContentCard(BuildContext context, WeeklySummaryModel summary) {
    return Container(
      width: double.infinity,
      padding: Dimensions.paddingCard,
      decoration: CardStyles.getStandardDecoration(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题行
          Row(
            children: [
              Icon(Icons.auto_awesome, color: AppColors.getPrimary(context), size: 20),
              Dimensions.horizontalSpacerS,
              Text(
                summary.weekTitle,
                style: AppTypography.titleMedium.copyWith(color: AppColors.getOnSurface(context)),
              ),
              const Spacer(),
              if (summary.isFailed)
                TextButton.icon(
                  onPressed: controller.regenerateCurrentSummary,
                  icon: const Icon(Icons.refresh, size: 16),
                  label: Text('weekly_summary.retry'.t),
                  style: ButtonStyles.getTextStyle(context),
                ),
            ],
          ),
          Dimensions.verticalSpacerM,
          // Markdown 内容
          _buildMarkdownContent(context, summary.content),
        ],
      ),
    );
  }

  /// 构建 Markdown 内容
  Widget _buildMarkdownContent(BuildContext context, String content) {
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
        _handleLinkTap(href);
      },
    );
  }

  /// 处理内容中的特殊链接格式
  String _processContentLinks(String content) {
    // 将 [[article:ID:标题]] 转换为 Markdown 链接
    var processed = content.replaceAllMapped(
      RegExp(r'\[\[article:(\d+):([^\]]+)\]\]'),
      (match) => '[📄 ${match.group(2)}](article:${match.group(1)})',
    );

    // 将 [[diary:ID:日期]] 转换为 Markdown 链接
    processed = processed.replaceAllMapped(
      RegExp(r'\[\[diary:(\d+):([^\]]+)\]\]'),
      (match) => '[📝 ${match.group(2)}](diary:${match.group(1)})',
    );

    return processed;
  }

  /// 处理链接点击
  void _handleLinkTap(String? href) {
    if (href == null) return;

    if (href.startsWith('article:')) {
      final id = int.tryParse(href.substring(8));
      if (id != null) {
        controller.openArticle(id);
      }
    } else if (href.startsWith('diary:')) {
      final id = int.tryParse(href.substring(6));
      if (id != null) {
        controller.openDiary(id);
      }
    }
  }
}
