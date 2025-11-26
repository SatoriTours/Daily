import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/pages/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/components/common/common_calendar.dart';

/// 文章日历对话框
///
/// 无状态组件,通过回调函数与外部交互
class ArticleCalendarDialog extends GetView<ArticlesController> {
  final Map<DateTime, int> articleCountMap;
  final void Function(DateTime date) onDateSelected;
  final VoidCallback onShowAllArticles;

  const ArticleCalendarDialog({
    super.key,
    required this.articleCountMap,
    required this.onDateSelected,
    required this.onShowAllArticles,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildHeader(context),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
          Expanded(
            child: SingleChildScrollView(
              child: Obx(
                () => CommonCalendar(
                  displayedMonth: controller.calendarDisplayedMonth.value,
                  selectedDate: controller.calendarSelectedDate.value,
                  markedDates: articleCountMap,
                  onDateSelected: (date) {
                    controller.selectCalendarDate(date);
                    onDateSelected(date);
                    Navigator.pop(context);
                  },
                  onPreviousMonth: controller.calendarPreviousMonth,
                  onNextMonth: controller.calendarNextMonth,
                ),
              ),
            ),
          ),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
          _buildAllArticlesButton(context),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTypography.getTextTheme();

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            'article.calendar_title'.t,
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500, color: colorScheme.onSurface),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: colorScheme.onSurfaceVariant),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  // 添加查看全部文章按钮
  Widget _buildAllArticlesButton(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTypography.getTextTheme();

    return InkWell(
      onTap: () {
        onShowAllArticles();
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        alignment: Alignment.center,
        child: Text(
          'article.view_all_articles'.t,
          style: textTheme.labelLarge?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.w500),
        ),
      ),
    );
  }
}
