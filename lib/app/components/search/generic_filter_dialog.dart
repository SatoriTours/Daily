import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 通用过滤对话框
///
/// 支持日期、标签、收藏等过滤条件的对话框组件。
///
/// 使用示例：
/// ```dart
/// showDialog(
///   context: context,
///   builder: (context) => GenericFilterDialog(
///     selectedDate: controller.filterDate.value,
///     selectedTags: controller.filterTags,
///     isFavorite: controller.filterFavorite.value,
///     availableTags: ['技术', '生活', '工作'],
///     availableTagIds: [1, 2, 3],
///     onDateSelected: (date) => controller.filterDate.value = date,
///     onTagsSelected: (tags) => controller.filterTags.value = tags,
///     onFavoriteChanged: (favorite) => controller.filterFavorite.value = favorite,
///     onClearAll: () => controller.clearAllFilters(),
///   ),
/// );
/// ```
class GenericFilterDialog extends StatelessWidget {
  /// 当前选中的日期
  final DateTime? selectedDate;

  /// 当前选中的标签ID列表
  final List<int> selectedTags;

  /// 是否只显示收藏
  final bool isFavorite;

  /// 可用的标签列表
  final List<String> availableTags;

  /// 可用标签的ID列表（与availableTags一一对应）
  final List<int> availableTagIds;

  /// 日期选择回调
  final ValueChanged<DateTime?> onDateSelected;

  /// 标签选择回调
  final ValueChanged<List<int>> onTagsSelected;

  /// 收藏状态改变回调
  final ValueChanged<bool> onFavoriteChanged;

  /// 清除所有过滤条件回调
  final VoidCallback onClearAll;

  const GenericFilterDialog({
    super.key,
    this.selectedDate,
    this.selectedTags = const [],
    this.isFavorite = false,
    required this.availableTags,
    required this.availableTagIds,
    required this.onDateSelected,
    required this.onTagsSelected,
    required this.onFavoriteChanged,
    required this.onClearAll,
  });
  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 400, maxHeight: 500),
        padding: Dimensions.paddingCard,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(context),
            Dimensions.verticalSpacerM,
            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildDateFilter(context),
                    Dimensions.verticalSpacerM,
                    _buildTagsFilter(context),
                    Dimensions.verticalSpacerM,
                    _buildFavoriteFilter(context),
                  ],
                ),
              ),
            ),
            Dimensions.verticalSpacerM,
            _buildActions(context),
          ],
        ),
      ),
    );
  }
  Widget _buildHeader(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text('component.filter_title'.t, style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.bold)),
        IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
          tooltip: 'component.cancel'.t,
        ),
      ],
    );
  }
  Widget _buildDateFilter(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.calendar_today,
              size: Dimensions.iconSizeS,
              color: AppColors.getOnSurfaceVariant(context),
            ),
            Dimensions.horizontalSpacerS,
            Text(
              'component.filter_date'.t,
              style: AppTypography.titleMedium.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
        InkWell(
        onTap: () async {
          final date = await showDatePicker(
            context: context,
            initialDate: selectedDate ?? DateTime.now(),
            firstDate: DateTime(2000),
            lastDate: DateTime.now(),
          );
          if (date != null) {
            onDateSelected(date);
          }
        },
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.getSurfaceContainer(context),
              borderRadius: BorderRadius.circular(Dimensions.radiusS),
              border: Border.all(color: AppColors.getOutline(context)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    selectedDate != null
                        ? '${selectedDate!.year}-${selectedDate!.month.toString().padLeft(2, '0')}-${selectedDate!.day.toString().padLeft(2, '0')}'
                        : 'component.filter_select_date'.t,
                    style: AppTypography.bodyMedium,
                  ),
                ),
                if (selectedDate != null) ...[
                  IconButton(
                    icon: const Icon(Icons.clear, size: 16),
                    onPressed: () => onDateSelected(null),
                    tooltip: 'component.clear'.t,
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
  Widget _buildTagsFilter(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.tag,
              size: Dimensions.iconSizeS,
              color: AppColors.getOnSurfaceVariant(context),
            ),
            Dimensions.horizontalSpacerS,
            Text(
              'component.filter_tags'.t,
              style: AppTypography.titleMedium.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
        Wrap(
        spacing: Dimensions.spacingS,
        runSpacing: Dimensions.spacingS,
        children: List.generate(availableTags.length, (index) {
          final tag = availableTags[index];
          final tagId = availableTagIds[index];
          final isSelected = selectedTags.contains(tagId);
          return FilterChip(
            label: Text(tag),
            selected: isSelected,
            onSelected: (selected) {
              final newTags = List<int>.from(selectedTags);
              if (selected) {
                newTags.add(tagId);
              } else {
                newTags.remove(tagId);
              }
              onTagsSelected(newTags);
            },
            backgroundColor: AppColors.getSurfaceContainer(context),
            selectedColor: AppColors.getPrimary(context).withValues(alpha: 0.2),
            labelStyle: AppTypography.bodySmall,
            pressElevation: 2,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS),
              side: BorderSide(
                color: isSelected ? AppColors.getPrimary(context) : Colors.transparent,
                width: 1,
              ),
            ),
          );
        }),
        ),
        Dimensions.verticalSpacerS,
      ],
    );
  }
  Widget _buildFavoriteFilter(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.favorite,
              size: Dimensions.iconSizeS,
              color: AppColors.getOnSurfaceVariant(context),
            ),
            Dimensions.horizontalSpacerS,
            Text(
              'component.filter_favorite'.t,
              style: AppTypography.titleMedium.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
        Row(
          children: [
            Checkbox(
              value: isFavorite,
              onChanged: (value) => onFavoriteChanged(value ?? false),
            ),
            Text('component.filter_favorite'.t, style: AppTypography.bodyMedium),
          ],
        ),
      ],
    );
  }
  Widget _buildActions(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        TextButton(
          onPressed: () {
            onClearAll();
            Navigator.pop(context);
          },
          child: Text(
            'component.filter_clear_all'.t,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getError(context)),
          ),
        ),
        ElevatedButton(
          onPressed: () => Navigator.pop(context),
          style: ButtonStyles.getPrimaryStyle(context),
          child: Text('component.confirm'.t),
        ),
      ],
    );
  }
}
/// 过滤配置辅助类
class FilterConfig {
  final DateTime? date;
  final List<int> tags;
  final bool isFavorite;
  final String? keyword;
  const FilterConfig({this.date, this.tags = const [], this.isFavorite = false, this.keyword});
  bool get hasActiveFilters => date != null || tags.isNotEmpty || isFavorite || keyword?.isNotEmpty == true;
  FilterConfig copyWith({DateTime? date, List<int>? tags, bool? isFavorite, String? keyword}) {
    return FilterConfig(
      date: date ?? this.date,
      tags: tags ?? this.tags,
      isFavorite: isFavorite ?? this.isFavorite,
      keyword: keyword ?? this.keyword,
    );
  }
  @override
  String toString() {
    return 'FilterConfig(date: $date, tags: $tags, isFavorite: $isFavorite, keyword: $keyword)';
  }
}