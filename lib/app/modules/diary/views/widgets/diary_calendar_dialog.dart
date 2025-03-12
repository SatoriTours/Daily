import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:intl/intl.dart';

import '../../controllers/diary_controller.dart';

/// 日记日历对话框
class DiaryCalendarDialog extends StatefulWidget {
  final DiaryController controller;

  const DiaryCalendarDialog({Key? key, required this.controller}) : super(key: key);

  @override
  State<DiaryCalendarDialog> createState() => _DiaryCalendarDialogState();
}

class _DiaryCalendarDialogState extends State<DiaryCalendarDialog> {
  late DateTime _selectedDate;
  late DateTime _displayedMonth;
  late Map<DateTime, int> _diaryCountMap;

  @override
  void initState() {
    super.initState();
    _selectedDate = DateTime.now();
    _displayedMonth = DateTime(_selectedDate.year, _selectedDate.month, 1);
    _diaryCountMap = widget.controller.getDailyDiaryCounts();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildHeader(context),
          Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
          _buildCalendarHeader(context),
          Expanded(child: SingleChildScrollView(child: _buildCalendar(context))),
          Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
          _buildAllDiariesButton(context),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            '日记日历',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyle.primaryTextColor(context)),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () => Navigator.pop(context),
            splashRadius: 20,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildCalendarHeader(BuildContext context) {
    final monthFormat = DateFormat('yyyy年MM月');

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            monthFormat.format(_displayedMonth),
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyle.accentColor(context)),
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
    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(8),
        child: Icon(icon, size: 18, color: DiaryStyle.secondaryTextColor(context)),
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

            // 检查该日期是否有日记
            final diaryCount = _diaryCountMap[DateTime(date.year, date.month, date.day)] ?? 0;

            // 当前日期、选中日期的判断
            final isToday = _isToday(date);
            final isSelected = _isSameDay(date, _selectedDate);

            return GestureDetector(
              onTap: () {
                setState(() {
                  _selectedDate = date;
                });

                // 直接应用过滤并关闭对话框
                widget.controller.filterByDate(date);
                Navigator.pop(context);
              },
              child: _buildDayCell(context, day, diaryCount, isToday, isSelected),
            );
          },
        ),
      ],
    );
  }

  Widget _buildWeekdayHeader(BuildContext context) {
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
                    color:
                        isWeekend
                            ? DiaryStyle.accentColor(context).withAlpha(180)
                            : DiaryStyle.secondaryTextColor(context),
                    fontWeight: isWeekend ? FontWeight.w500 : FontWeight.normal,
                  ),
                ),
              );
            }).toList(),
      ),
    );
  }

  Widget _buildDayCell(BuildContext context, int day, int diaryCount, bool isToday, bool isSelected) {
    // 选择适当的背景颜色
    Color bgColor;
    if (isSelected) {
      bgColor = DiaryStyle.accentColor(context);
    } else if (isToday) {
      bgColor = DiaryStyle.accentColor(context).withAlpha(30);
    } else if (diaryCount > 0) {
      bgColor = DiaryStyle.accentColor(context).withAlpha(10);
    } else {
      bgColor = Colors.transparent;
    }

    return Container(
      margin: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: bgColor,
        border:
            diaryCount > 0 && !isSelected
                ? Border.all(color: DiaryStyle.accentColor(context).withAlpha(80), width: 1)
                : null,
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          Text(
            day.toString(),
            style: TextStyle(
              fontSize: 15,
              fontWeight: (isToday || isSelected || diaryCount > 0) ? FontWeight.bold : FontWeight.normal,
              color: isSelected ? Colors.white : DiaryStyle.primaryTextColor(context),
            ),
          ),
          if (diaryCount > 0)
            Positioned(
              right: 6,
              bottom: 6,
              child: Container(
                width: 16,
                height: 16,
                decoration: BoxDecoration(
                  color: isSelected ? Colors.white : DiaryStyle.accentColor(context),
                  shape: BoxShape.circle,
                  border: isSelected ? Border.all(color: DiaryStyle.accentColor(context), width: 0.5) : null,
                ),
                child: Center(
                  child: Text(
                    diaryCount.toString(),
                    style: TextStyle(
                      color: isSelected ? DiaryStyle.accentColor(context) : Colors.white,
                      fontSize: 10,
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

  // 辅助方法：判断日期是否是今天
  bool _isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  // 辅助方法：判断两个日期是否是同一天
  bool _isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  // 添加查看全部日记按钮
  Widget _buildAllDiariesButton(BuildContext context) {
    return InkWell(
      onTap: () {
        widget.controller.clearFilters();
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        alignment: Alignment.center,
        child: Text(
          '查看全部日记',
          style: TextStyle(color: DiaryStyle.accentColor(context), fontWeight: FontWeight.w500, fontSize: 15),
        ),
      ),
    );
  }
}
