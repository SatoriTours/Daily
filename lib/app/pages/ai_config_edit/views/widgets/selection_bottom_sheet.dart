import 'package:flutter/material.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 选择底部弹出窗口
///
/// 用于显示选项列表供用户选择
class SelectionBottomSheet extends StatelessWidget {
  /// 标题
  final String title;

  /// 选项列表
  final List<String> items;

  /// 当前选中的值
  final String selectedValue;

  /// 选中回调
  final Function(int) onSelected;

  const SelectionBottomSheet({
    super.key,
    required this.title,
    required this.items,
    required this.selectedValue,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: Dimensions.paddingPage,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(context),
            Dimensions.verticalSpacerM,
            const Divider(height: 1),
            _buildItemList(context),
          ],
        ),
      ),
    );
  }

  /// 构建头部
  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        Icon(Icons.list_alt, size: Dimensions.iconSizeM, color: AppColors.getPrimary(context)),
        Dimensions.horizontalSpacerM,
        Text(title, style: AppTypography.titleLarge),
      ],
    );
  }

  /// 构建选项列表
  Widget _buildItemList(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.4),
      child: ListView.builder(
        shrinkWrap: true,
        physics: const ClampingScrollPhysics(),
        padding: EdgeInsets.zero,
        itemCount: items.length,
        itemBuilder: (context, index) => _buildSelectionItem(context, items[index], items[index] == selectedValue, () {
          onSelected(index);
          AppNavigation.back();
        }),
      ),
    );
  }

  /// 构建单个选项
  Widget _buildSelectionItem(BuildContext context, String item, bool isSelected, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      child: Container(
        color: isSelected ? AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow) : null,
        padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingS, vertical: Dimensions.spacingM),
        child: Text(
          item,
          style: AppTypography.bodyMedium.copyWith(
            color: isSelected ? AppColors.getPrimary(context) : AppColors.getOnSurface(context),
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}

/// 显示选择底部弹出窗口的工具方法
///
/// 封装了底部弹出窗口的显示逻辑
void showSelectionBottomSheet({
  required BuildContext context,
  required String title,
  required List<String> items,
  required String selectedValue,
  required Function(int) onSelected,
}) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: AppColors.getSurface(context),
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL))),
    builder: (context) =>
        SelectionBottomSheet(title: title, items: items, selectedValue: selectedValue, onSelected: onSelected),
  );
}
