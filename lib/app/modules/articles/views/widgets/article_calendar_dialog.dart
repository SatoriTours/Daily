import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import '../../controllers/articles_controller.dart';

/// 文章日历对话框
class ArticleCalendarDialog extends StatefulWidget {
  const ArticleCalendarDialog({super.key});

  @override
  State<ArticleCalendarDialog> createState() => _ArticleCalendarDialogState();
}

class _ArticleCalendarDialogState extends State<ArticleCalendarDialog> {
  late final ArticlesController controller;
  late DateTime _selectedDate;
  late DateTime _displayedMonth;
  late Map<DateTime, int> _articleCountMap;

  @override
  void initState() {
    super.initState();
    controller = Get.find<ArticlesController>();
    _selectedDate = DateTime.now();
    _displayedMonth = DateTime(_selectedDate.year, _selectedDate.month, 1);
    _articleCountMap = controller.getDailyArticleCounts();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildHeader(context),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withOpacity(0.5)),
          _buildCalendarHeader(context),
          Expanded(child: SingleChildScrollView(child: _buildCalendar(context))),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withOpacity(0.5)),
          _buildAllArticlesButton(context),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

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

  Widget _buildCalendarHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final monthFormat = DateFormat('yyyy年MM月');

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            monthFormat.format(_displayedMonth),
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500, color: colorScheme.primary),
          ),
          Row(
            children: [
              _buildIconButton(context, FeatherIcons.chevronLeft, () {
                setState(() {
                  _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month - 1, 1);
                });
              }),
              const SizedBox(width: 8),
              _buildIconButton(context, FeatherIcons.chevronRight, () {
                setState(() {
                  _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month + 1, 1);
                });
              }),
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

  Widget _buildCalendar(BuildContext context) {
    // 计算当月第一天是星期几
    final firstDayOfMonth = _displayedMonth;
    final firstWeekday = firstDayOfMonth.weekday;

    // 计算当月有多少天
    final daysInMonth = DateTime(_displayedMonth.year, _displayedMonth.month + 1, 0).day;

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
            final date = DateTime(_displayedMonth.year, _displayedMonth.month, day);

            // 检查该日期是否有文章
            final articleCount = _articleCountMap[DateTime(date.year, date.month, date.day)] ?? 0;

            // 当前日期、选中日期的判断
            final isToday = _isToday(date);
            final isSelected = _isSameDay(date, _selectedDate);

            return GestureDetector(
              onTap: () {
                setState(() {
                  _selectedDate = date;
                });

                // 直接应用过滤并关闭对话框
                controller.filterByDate(date);
                Navigator.pop(context);
              },
              child: _buildDayCell(context, day, articleCount, isToday, isSelected),
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
        children:
            weekdays.map((day) {
              final bool isWeekend = day == '六' || day == '日';
              return Expanded(
                child: Text(
                  day,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    color: isWeekend ? colorScheme.primary.withAlpha(180) : colorScheme.onSurfaceVariant,
                    fontWeight: isWeekend ? FontWeight.w500 : FontWeight.normal,
                  ),
                ),
              );
            }).toList(),
      ),
    );
  }

  Widget _buildDayCell(BuildContext context, int day, int articleCount, bool isToday, bool isSelected) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    // 选择适当的背景颜色
    Color bgColor;
    if (isSelected) {
      bgColor = colorScheme.primary;
    } else if (isToday) {
      bgColor = colorScheme.primary.withAlpha(30);
    } else if (articleCount > 0) {
      bgColor = colorScheme.primary.withAlpha(10);
    } else {
      bgColor = Colors.transparent;
    }

    return Container(
      margin: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: bgColor,
        border: articleCount > 0 && !isSelected ? Border.all(color: colorScheme.primary.withAlpha(80), width: 1) : null,
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
                  color: isSelected ? colorScheme.onPrimary.withAlpha(235) : colorScheme.primary.withAlpha(235),
                  shape: BoxShape.circle,
                  border: isSelected ? Border.all(color: colorScheme.primary, width: 0.5) : null,
                  boxShadow: [
                    BoxShadow(color: Colors.black.withAlpha(15), blurRadius: 1, offset: const Offset(0, 0.5)),
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
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: () {
        controller.clearAllFilters();
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

  // 辅助方法：判断日期是否是今天
  bool _isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  // 辅助方法：判断两个日期是否是同一天
  bool _isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }
}
