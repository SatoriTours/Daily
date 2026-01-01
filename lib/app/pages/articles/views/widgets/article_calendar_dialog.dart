import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/components/common/common_calendar.dart';

/// 文章日历对话框
class ArticleCalendarDialog extends StatefulWidget {
  final Map<DateTime, int> articleCountMap;
  final void Function(DateTime date) onDateSelected;
  final VoidCallback onShowAllArticles;
  final DateTime? initialDisplayedMonth;
  final DateTime? initialSelectedDate;

  const ArticleCalendarDialog({
    super.key,
    required this.articleCountMap,
    required this.onDateSelected,
    required this.onShowAllArticles,
    this.initialDisplayedMonth,
    this.initialSelectedDate,
  });

  @override
  State<ArticleCalendarDialog> createState() => _ArticleCalendarDialogState();
}

class _ArticleCalendarDialogState extends State<ArticleCalendarDialog> {
  late DateTime _displayedMonth;
  DateTime? _selectedDate;

  @override
  void initState() {
    super.initState();
    _displayedMonth = widget.initialDisplayedMonth ?? DateTime.now();
    _selectedDate = widget.initialSelectedDate;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildHeader(context, colorScheme),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
          Expanded(
            child: SingleChildScrollView(
              child: CommonCalendar(
                displayedMonth: _displayedMonth,
                selectedDate: _selectedDate,
                markedDates: widget.articleCountMap,
                onDateSelected: (date) {
                  setState(() => _selectedDate = date);
                  widget.onDateSelected(date);
                  Navigator.pop(context);
                },
                onPreviousMonth: () => setState(() {
                  _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month - 1, 1);
                }),
                onNextMonth: () => setState(() {
                  _displayedMonth = DateTime(_displayedMonth.year, _displayedMonth.month + 1, 1);
                }),
              ),
            ),
          ),
          Divider(height: 1, thickness: 0.5, color: colorScheme.outline.withValues(alpha: 0.5)),
          _buildAllArticlesButton(context, colorScheme),
          SizedBox(height: MediaQuery.of(context).padding.bottom),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context, ColorScheme colorScheme) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingL - 4,
        Dimensions.spacingM,
        Dimensions.spacingL - 4,
        Dimensions.spacingS,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            'article.calendar_title'.t,
            style: AppTypography.getTextTheme().titleMedium?.copyWith(
              fontWeight: FontWeight.w500,
              color: colorScheme.onSurface,
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: Dimensions.iconSizeM, color: colorScheme.onSurfaceVariant),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildAllArticlesButton(BuildContext context, ColorScheme colorScheme) {
    return InkWell(
      onTap: () {
        widget.onShowAllArticles();
        Navigator.pop(context);
      },
      child: Container(
        padding: Dimensions.paddingVerticalM,
        alignment: Alignment.center,
        child: Text(
          'article.view_all_articles'.t,
          style: AppTypography.getTextTheme().labelLarge?.copyWith(
            color: colorScheme.primary,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }
}
