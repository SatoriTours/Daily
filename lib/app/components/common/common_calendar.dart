import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 通用日历组件
///
/// 纯展示组件，所有状态由外部控制
/// 通过回调函数与外部交互
class CommonCalendar extends StatelessWidget {
  /// 当前显示的月份
  final DateTime displayedMonth;

  /// 当前选中的日期
  final DateTime selectedDate;

  /// 日期选择回调
  final Function(DateTime date) onDateSelected;

  /// 上一月回调
  final VoidCallback onPreviousMonth;

  /// 下一月回调
  final VoidCallback onNextMonth;

  /// 日期标记数据 {日期: 标记数量}
  final Map<DateTime, int>? markedDates;

  /// 是否显示头部导航
  final bool showHeader;

  /// 自定义头部标题格式
  final String Function(DateTime)? headerTitleFormatter;

  const CommonCalendar({
    super.key,
    required this.displayedMonth,
    required this.selectedDate,
    required this.onDateSelected,
    required this.onPreviousMonth,
    required this.onNextMonth,
    this.markedDates,
    this.showHeader = true,
    this.headerTitleFormatter,
  });

  /// 判断是否是今天
  bool _isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  /// 判断两个日期是否是同一天
  bool _isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [if (showHeader) _buildHeader(context), _buildWeekdayHeader(context), _buildCalendar(context)],
    );
  }

  Widget _buildHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTypography.getTextTheme();
    final monthFormat = headerTitleFormatter ?? (date) => DateFormat('yyyy年MM月').format(date);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            monthFormat(displayedMonth),
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w500, color: colorScheme.primary),
          ),
          Row(
            children: [
              _buildIconButton(context, Icons.chevron_left, onPreviousMonth),
              const SizedBox(width: 8),
              _buildIconButton(context, Icons.chevron_right, onNextMonth),
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

  Widget _buildCalendar(BuildContext context) {
    final firstDayOfMonth = displayedMonth;
    final firstWeekday = firstDayOfMonth.weekday;
    final daysInMonth = DateTime(firstDayOfMonth.year, firstDayOfMonth.month + 1, 0).day;

    return GridView.builder(
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
        int day = index - (firstWeekday - 1) + 1;

        if (day < 1 || day > daysInMonth) {
          return const SizedBox.shrink();
        }

        final date = DateTime(firstDayOfMonth.year, firstDayOfMonth.month, day);
        final markedCount = markedDates?[DateTime(date.year, date.month, date.day)] ?? 0;
        final isToday = _isToday(date);
        final isSelected = _isSameDay(date, selectedDate);

        return GestureDetector(
          onTap: () => onDateSelected(date),
          child: _buildDayCell(context, day, markedCount, isToday, isSelected),
        );
      },
    );
  }

  Widget _buildDayCell(BuildContext context, int day, int markedCount, bool isToday, bool isSelected) {
    final colorScheme = AppTheme.getColorScheme(context);

    Color bgColor;
    if (isSelected) {
      bgColor = colorScheme.primary;
    } else if (isToday) {
      bgColor = colorScheme.primary.withValues(alpha: 0.12);
    } else if (markedCount > 0) {
      bgColor = colorScheme.primary.withValues(alpha: 0.04);
    } else {
      bgColor = Colors.transparent;
    }

    return Container(
      margin: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: bgColor,
        border: markedCount > 0 && !isSelected
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
                fontWeight: (isToday || isSelected || markedCount > 0) ? FontWeight.bold : FontWeight.normal,
                color: isSelected ? colorScheme.onPrimary : colorScheme.onSurface,
              ),
            ),
          ),
          if (markedCount > 0)
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
                    markedCount > 99 ? '99+' : markedCount.toString(),
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
}
