import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:intl/intl.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/modules/articles/controllers/article_calendar_controller.dart';

/// 文章日历对话框
///
/// 无状态组件,使用GetX控制器管理状态
/// 通过回调函数与外部交互
class ArticleCalendarDialog extends StatelessWidget {
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
    final controller = Get.put(ArticleCalendarController());

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Obx(() {
        final colorScheme = AppTheme.getColorScheme(context);

        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(context),
            Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
            _buildCalendarHeader(context, controller),
            Expanded(child: SingleChildScrollView(child: _buildCalendar(context, controller))),
            Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
            _buildAllArticlesButton(context),
            SizedBox(height: MediaQuery.of(context).padding.bottom),
          ],
        );
      }),
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
            '文章日历',
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

  Widget _buildCalendarHeader(BuildContext context, ArticleCalendarController controller) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTypography.getTextTheme();
    final monthFormat = DateFormat('yyyy年MM月');

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            monthFormat.format(controller.displayedMonth.value),
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500, color: colorScheme.primary),
          ),
          Row(
            children: [
              _buildIconButton(context, FeatherIcons.chevronLeft, controller.previousMonth),
              const SizedBox(width: 8),
              _buildIconButton(context, FeatherIcons.chevronRight, controller.nextMonth),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildIconButton(BuildContext context, IconData icon, VoidCallback onPressed) {
    final colorScheme = AppTheme.getColorScheme(context);

    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(8),
        child: Icon(icon, size: 18, color: colorScheme.onSurfaceVariant),
      ),
    );
  }

  Widget _buildCalendar(BuildContext context, ArticleCalendarController controller) {
    // 计算当月第一天是星期几
    final firstDayOfMonth = controller.displayedMonth.value;
    final firstWeekday = firstDayOfMonth.weekday;

    // 计算当月有多少天
    final daysInMonth = DateTime(firstDayOfMonth.year, firstDayOfMonth.month + 1, 0).day;

    // 构建日历网格
    return Column(
      children: [
        // 星期头部
        _buildWeekdayHeader(context),

        // 日期网格
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 7,
            childAspectRatio: 1,
            mainAxisSpacing: 2,
            crossAxisSpacing: 2,
          ),
          itemCount: 42, // 6周 x 7天
          itemBuilder: (context, index) {
            // 计算实际日期
            int day = index - (firstWeekday - 1) + 1;

            // 检查是否在当月范围内
            if (day < 1 || day > daysInMonth) {
              return const SizedBox.shrink();
            }

            // 创建日期对象
            final date = DateTime(firstDayOfMonth.year, firstDayOfMonth.month, day);

            // 检查该日期是否有文章
            final articleCount = articleCountMap[DateTime(date.year, date.month, date.day)] ?? 0;

            // 当前日期、选中日期的判断
            final isToday = controller.isToday(date);
            final isSelected = controller.isSameDay(date, controller.selectedDate.value);

            return GestureDetector(
              onTap: () {
                controller.selectDate(date);
                onDateSelected(date);
                Navigator.pop(context);
              },
              child: _buildDayCell(context, day, articleCount, isToday, isSelected, controller),
            );
          },
        ),
      ],
    );
  }

  Widget _buildWeekdayHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    const weekdays = ['一', '二', '三', '四', '五', '六', '日'];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      margin: const EdgeInsets.only(bottom: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: weekdays.map((day) {
          final bool isWeekend = day == '六' || day == '日';
          return Expanded(
            child: Text(
              day,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: isWeekend ? colorScheme.primary.withValues(alpha: 0.7) : colorScheme.onSurfaceVariant,
                fontWeight: isWeekend ? FontWeight.w500 : FontWeight.normal,
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildDayCell(
    BuildContext context,
    int day,
    int articleCount,
    bool isToday,
    bool isSelected,
    ArticleCalendarController controller,
  ) {
    final colorScheme = AppTheme.getColorScheme(context);

    // 选择适当的背景颜色
    Color bgColor;
    if (isSelected) {
      bgColor = colorScheme.primary;
    } else if (isToday) {
      bgColor = colorScheme.primary.withValues(alpha: 0.12);
    } else if (articleCount > 0) {
      bgColor = colorScheme.primary.withValues(alpha: 0.04);
    } else {
      bgColor = Colors.transparent;
    }

    return Container(
      margin: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: bgColor,
        border: articleCount > 0 && !isSelected
            ? Border.all(color: colorScheme.primary.withValues(alpha: 0.3), width: 1)
            : null,
      ),
      child: Stack(
        fit: StackFit.expand,
        alignment: Alignment.center,
        children: [
          Center(
            child: Text(
              day.toString(),
              style: TextStyle(
                fontSize: 15,
                fontWeight: (isToday || isSelected || articleCount > 0) ? FontWeight.bold : FontWeight.normal,
                color: isSelected ? colorScheme.onPrimary : colorScheme.onSurface,
              ),
            ),
          ),
          if (articleCount > 0)
            Positioned(
              right: 1,
              bottom: 1,
              child: Container(
                width: 13,
                height: 13,
                decoration: BoxDecoration(
                  color: isSelected
                      ? colorScheme.onPrimary.withValues(alpha: 0.92)
                      : colorScheme.primary.withValues(alpha: 0.92),
                  shape: BoxShape.circle,
                  border: isSelected ? Border.all(color: colorScheme.primary, width: 0.5) : null,
                  boxShadow: [
                    BoxShadow(color: Colors.black.withValues(alpha: 0.06), blurRadius: 1, offset: const Offset(0, 0.5)),
                  ],
                ),
                child: Center(
                  child: Text(
                    articleCount > 99 ? '99+' : articleCount.toString(),
                    style: TextStyle(
                      color: isSelected ? colorScheme.primary : colorScheme.onPrimary,
                      fontSize: 8,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
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
          '查看全部文章',
          style: textTheme.labelLarge?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.w500),
        ),
      ),
    );
  }
}
