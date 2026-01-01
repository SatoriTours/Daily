import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/pages/diary/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/providers/diary_state_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

/// 日记日历对话框
class DiaryCalendarDialog extends ConsumerStatefulWidget {
  const DiaryCalendarDialog({super.key});

  @override
  ConsumerState<DiaryCalendarDialog> createState() => _DiaryCalendarDialogState();
}

class _DiaryCalendarDialogState extends ConsumerState<DiaryCalendarDialog> {
  late DateTime _selectedDate;
  late DateTime _displayedMonth;
  Map<DateTime, int> _diaryCountMap = {};

  @override
  void initState() {
    super.initState();
    _selectedDate = DateTime.now();
    _displayedMonth = DateTime(_selectedDate.year, _selectedDate.month, 1);
  }

  Map<DateTime, int> _getDailyDiaryCounts(List<DiaryModel> diaries) {
    final Map<DateTime, int> counts = {};
    for (final diary in diaries) {
      final date = DateTime(diary.createdAt.year, diary.createdAt.month, diary.createdAt.day);
      counts[date] = (counts[date] ?? 0) + 1;
    }
    return counts;
  }

  @override
  Widget build(BuildContext context) {
    final diaries = ref.watch(diaryStateProvider.select((s) => s.diaries));
    _diaryCountMap = _getDailyDiaryCounts(diaries);

    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildHeader(context),
          Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
          _buildCalendarHeader(context),
          Expanded(child: SingleChildScrollView(child: _buildCalendar(context))),
          Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
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
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w500,
              color: DiaryStyles.getPrimaryTextColor(context),
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyles.getSecondaryTextColor(context)),
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
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyles.getAccentColor(context)),
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
        decoration: BoxDecoration(
          color: AppColors.getSurface(context),
          shape: BoxShape.circle,
          border: Border.all(color: DiaryStyles.getDividerColor(context)),
        ),
        child: Icon(icon, size: 16, color: DiaryStyles.getPrimaryTextColor(context)),
      ),
    );
  }

  Widget _buildCalendar(BuildContext context) {
    final daysInMonth = DateUtils.getDaysInMonth(_displayedMonth.year, _displayedMonth.month);
    final firstDayOfWeek = DateTime(_displayedMonth.year, _displayedMonth.month, 1).weekday;

    // 调整周起始日，让周一为第一天 (1=Mon, 7=Sun)
    // 如果日历显示习惯是周日开始，需要调整逻辑。这里假设周一作为第一列。
    // Flutter DateUtils weekday is 1 for Monday.

    final List<Widget> dayWidgets = [];

    // Weekday headers
    final weekDays = ['一', '二', '三', '四', '五', '六', '日'];
    for (var day in weekDays) {
      dayWidgets.add(
        Center(
          child: Text(day, style: TextStyle(fontSize: 12, color: DiaryStyles.getSecondaryTextColor(context))),
        ),
      );
    }

    // Empty slots before first day
    for (var i = 1; i < firstDayOfWeek; i++) {
      dayWidgets.add(const SizedBox());
    }

    // Days
    for (var i = 1; i <= daysInMonth; i++) {
      final date = DateTime(_displayedMonth.year, _displayedMonth.month, i);
      final isToday = DateUtils.isSameDay(date, DateTime.now());
      final isSelected = DateUtils.isSameDay(date, _selectedDate);
      final count = _diaryCountMap[date] ?? 0;

      dayWidgets.add(
        InkWell(
          onTap: () {
            setState(() {
              _selectedDate = date;
            });
            ref.read(diaryControllerProvider.notifier).filterByDate(date);
            Navigator.pop(context);
          },
          child: Container(
            margin: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: isSelected
                  ? DiaryStyles.getAccentColor(context)
                  : (isToday ? DiaryStyles.getAccentColor(context).withValues(alpha: 0.1) : null),
              shape: BoxShape.circle,
              border: isToday && !isSelected ? Border.all(color: DiaryStyles.getAccentColor(context)) : null,
            ),
            child: Stack(
              alignment: Alignment.center,
              children: [
                Text(
                  '$i',
                  style: TextStyle(
                    color: isSelected ? Colors.white : DiaryStyles.getPrimaryTextColor(context),
                    fontWeight: isToday || isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
                if (count > 0)
                  Positioned(
                    bottom: 4,
                    child: Container(
                      width: 4,
                      height: 4,
                      decoration: BoxDecoration(
                        color: isSelected ? Colors.white : DiaryStyles.getAccentColor(context),
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: GridView.count(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        crossAxisCount: 7,
        children: dayWidgets,
      ),
    );
  }

  Widget _buildAllDiariesButton(BuildContext context) {
    return InkWell(
      onTap: () {
        ref.read(diaryControllerProvider.notifier).clearFilters();
        Navigator.pop(context);
      },
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 16),
        alignment: Alignment.center,
        child: Text(
          '查看全部日记',
          style: TextStyle(fontSize: 16, color: DiaryStyles.getAccentColor(context), fontWeight: FontWeight.w500),
        ),
      ),
    );
  }
}
