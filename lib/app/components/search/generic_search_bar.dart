import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// 通用搜索栏组件
///
/// 支持搜索、过滤、清除等功能的高度可定制搜索栏组件。
/// 提供收起和展开两种状态，支持搜索和过滤操作。
///
/// 使用示例：
/// ```dart
/// GenericSearchBar(
///   controller: controller.searchController,
///   focusNode: controller.searchFocusNode,
///   isSearchVisible: controller.isSearchVisible.value,
///   onToggleSearch: controller.toggleSearch,
///   onSearch: controller.performSearch,
///   onClear: controller.clearSearch,
///   showFilterButton: true,
///   onFilterTap: controller.showFilterDialog,
/// )
/// ```
class GenericSearchBar extends StatelessWidget {
  /// 文本编辑控制器
  final TextEditingController controller;

  /// 焦点节点
  final FocusNode focusNode;

  /// 提示文本
  final String? hintText;

  /// 搜索回调
  final ValueChanged<String> onSearch;

  /// 清除回调
  final VoidCallback onClear;

  /// 搜索框是否可见
  final bool isSearchVisible;

  /// 切换搜索框可见性回调
  final VoidCallback onToggleSearch;

  /// 是否显示过滤按钮
  final bool showFilterButton;

  /// 过滤按钮点击回调
  final VoidCallback? onFilterTap;

  /// 背景颜色
  final Color? backgroundColor;

  /// 搜索栏高度
  final double? height;

  /// 内边距
  final EdgeInsetsGeometry? padding;

  const GenericSearchBar({
    super.key,
    required this.controller,
    required this.focusNode,
    this.hintText,
    required this.onSearch,
    required this.onClear,
    required this.isSearchVisible,
    required this.onToggleSearch,
    this.showFilterButton = true,
    this.onFilterTap,
    this.backgroundColor,
    this.height,
    this.padding,
  });
  @override
  Widget build(BuildContext context) {
    if (!isSearchVisible) {
      return _buildCollapsedSearchBar(context);
    }
    return _buildExpandedSearchBar(context);
  }
  /// 收起状态的搜索栏
  Widget _buildCollapsedSearchBar(BuildContext context) {
    return Container(
      height: height ?? Dimensions.buttonHeight,
      padding: padding ?? Dimensions.paddingHorizontalM,
      child: Row(
        children: [
          if (showFilterButton && onFilterTap != null) ...[
            _buildFilterButton(context),
            Dimensions.horizontalSpacerS,
          ],
          Expanded(
            child: InkWell(
              onTap: onToggleSearch,
              borderRadius: BorderRadius.circular(Dimensions.radiusXl),
              child: Container(
                height: Dimensions.buttonHeight - 8,
                decoration: BoxDecoration(
                  color: backgroundColor ?? AppColors.getSurfaceContainer(context),
                  borderRadius: BorderRadius.circular(Dimensions.radiusXl),
                ),
                padding: Dimensions.paddingHorizontalM,
                child: Row(
                  children: [
                    Icon(
                      Icons.search,
                      size: Dimensions.iconSizeM,
                      color: AppColors.getOnSurfaceVariant(context),
                    ),
                    Dimensions.horizontalSpacerS,
                    Text(
                      hintText ?? 'component.search_placeholder'.t,
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.getOnSurfaceVariant(context),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
  /// 展开状态的搜索栏
  Widget _buildExpandedSearchBar(BuildContext context) {
    return Container(
      height: height ?? Dimensions.buttonHeight,
      padding: padding ?? Dimensions.paddingHorizontalM,
      child: Row(
        children: [
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: backgroundColor ?? AppColors.getSurfaceContainer(context),
                borderRadius: BorderRadius.circular(Dimensions.radiusXl),
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back, size: Dimensions.iconSizeM),
                    onPressed: onToggleSearch,
                    tooltip: 'component.tooltip_back'.t,
                  ),
                  Expanded(
                    child: TextField(
                      controller: controller,
                      focusNode: focusNode,
                      decoration: InputDecoration(
                        hintText: hintText ?? 'component.search_placeholder'.t,
                        border: InputBorder.none,
                        contentPadding: Dimensions.paddingVerticalS,
                        isDense: true,
                      ),
                      style: AppTypography.bodyMedium,
                      onSubmitted: onSearch,
                      autofocus: true,
                    ),
                  ),
                  if (controller.text.isNotEmpty)
                    IconButton(
                      icon: const Icon(Icons.clear, size: Dimensions.iconSizeM),
                      onPressed: () {
                        controller.clear();
                        onClear();
                      },
                      tooltip: 'component.tooltip_clear'.t,
                    ),
                  IconButton(
                    icon: const Icon(Icons.search, size: Dimensions.iconSizeM),
                    onPressed: () => onSearch(controller.text),
                    tooltip: 'component.tooltip_search'.t,
                  ),
                ],
              ),
            ),
          ),
          if (showFilterButton && onFilterTap != null) ...[
            Dimensions.horizontalSpacerS,
            _buildFilterButton(context),
          ],
        ],
      ),
    );
  }
  /// 过滤按钮
  Widget _buildFilterButton(BuildContext context) {
    return Tooltip(
      message: 'component.tooltip_filter'.t,
      child: IconButton(
        icon: Icon(
          Icons.filter_list,
          size: Dimensions.iconSizeL,
          color: AppColors.getPrimary(context),
        ),
        onPressed: onFilterTap,
      ),
    );
  }
}