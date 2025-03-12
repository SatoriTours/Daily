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
          _buildButtons(context),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            '选择日期',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyle.primaryTextColor(context)),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () => Navigator.pop(context),
            splashRadius: 20,
          ),
        ],
      ),
    );
  }

  Widget _buildCalendarHeader(BuildContext context) {
    final monthFormat = DateFormat('yyyy年MM月');

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            icon: Icon(FeatherIcons.chevronLeft, size: 20, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () {
              setState(() {
                _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month - 1, 1);
              });
            },
            splashRadius: 20,
          ),
          Text(
            monthFormat.format(_displayedMonth),
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyle.primaryTextColor(context)),
          ),
          IconButton(
            icon: Icon(FeatherIcons.chevronRight, size: 20, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () {
              setState(() {
                _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month + 1, 1);
              });
            },
            splashRadius: 20,
          ),
        ],
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
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 7, childAspectRatio: 1),
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

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children:
            weekdays.map((day) {
              return SizedBox(
                width: 30,
                child: Text(
                  day,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    color: DiaryStyle.secondaryTextColor(context),
                    fontWeight: FontWeight.w500,
                  ),
                ),
              );
            }).toList(),
      ),
    );
  }

  Widget _buildDayCell(BuildContext context, int day, int diaryCount, bool isToday, bool isSelected) {
    return Container(
      margin: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color:
            isSelected
                ? DiaryStyle.accentColor(context)
                : isToday
                ? DiaryStyle.accentColor(context).withAlpha(30)
                : Colors.transparent,
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          Text(
            day.toString(),
            style: TextStyle(
              fontSize: 14,
              fontWeight: isToday || isSelected ? FontWeight.bold : FontWeight.normal,
              color: isSelected ? Colors.white : DiaryStyle.primaryTextColor(context),
            ),
          ),
          if (diaryCount > 0)
            Positioned(
              right: 8,
              bottom: 8,
              child: Container(
                width: 14,
                height: 14,
                decoration: BoxDecoration(
                  color: isSelected ? Colors.white : DiaryStyle.accentColor(context),
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    diaryCount.toString(),
                    style: TextStyle(
                      color: isSelected ? DiaryStyle.accentColor(context) : Colors.white,
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

  Widget _buildButtons(BuildContext context) {
    final dateText = DateFormat('yyyy年MM月dd日').format(_selectedDate);
    final hasEntry =
        _diaryCountMap[DateTime(_selectedDate.year, _selectedDate.month, _selectedDate.day)] != null &&
        _diaryCountMap[DateTime(_selectedDate.year, _selectedDate.month, _selectedDate.day)]! > 0;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Expanded(
            child: ElevatedButton(
              onPressed: () {
                widget.controller.filterByDate(_selectedDate);
                Navigator.pop(context);
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: DiaryStyle.accentColor(context),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              child: Text(hasEntry ? '查看 $dateText 的日记' : '查看 $dateText'),
            ),
          ),
          const SizedBox(width: 12),
          OutlinedButton(
            onPressed: () {
              widget.controller.clearFilters();
              Navigator.pop(context);
            },
            style: OutlinedButton.styleFrom(
              foregroundColor: DiaryStyle.accentColor(context),
              side: BorderSide(color: DiaryStyle.accentColor(context)),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
            ),
            child: const Text('查看全部'),
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
}
