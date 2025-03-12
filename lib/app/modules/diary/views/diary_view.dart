import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_list.dart';
import 'widgets/diary_tags_dialog.dart';
import 'widgets/diary_edit_dialog.dart';
import 'widgets/diary_search_bar.dart';
import 'widgets/diary_calendar_dialog.dart';
import 'widgets/diary_fab.dart';

/// 日记页面
class DiaryView extends GetView<DiaryController> {
  const DiaryView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DiaryStyle.backgroundColor(context),
      appBar: _buildAppBar(context),
      body: Stack(children: [Obx(() => _buildMainContent(context)), _buildSearchBar(), _buildFloatingButton()]),
    );
  }

  /// 构建主要内容区域
  Widget _buildMainContent(BuildContext context) {
    // 显示搜索结果状态
    if (controller.searchQuery.isNotEmpty) {
      return _buildFilterView(context, '搜索结果: "${controller.searchQuery.value}"');
    }

    // 显示标签过滤状态
    if (controller.currentTag.isNotEmpty) {
      return _buildFilterView(context, '标签: "#${controller.currentTag.value}"');
    }

    // 显示日期过滤状态
    if (controller.selectedFilterDate.value != null) {
      final dateFormat = DateFormat('yyyy年MM月dd日');
      final dateText = dateFormat.format(controller.selectedFilterDate.value!);
      return _buildFilterView(context, '日期: $dateText');
    }

    // 显示正常状态
    return DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary));
  }

  /// 构建过滤视图（搜索结果、标签过滤、日期过滤等）
  Widget _buildFilterView(BuildContext context, String filterText) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  filterText,
                  style: TextStyle(fontSize: 14, color: DiaryStyle.secondaryTextColor(context)),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              _buildClearFilterButton(context),
            ],
          ),
        ),
        Expanded(child: DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary))),
      ],
    );
  }

  /// 构建清除过滤按钮
  Widget _buildClearFilterButton(BuildContext context) {
    return InkWell(
      onTap: () => controller.clearFilters(),
      borderRadius: BorderRadius.circular(20),
      child: Padding(
        padding: const EdgeInsets.all(4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(FeatherIcons.x, size: 14, color: DiaryStyle.accentColor(context)),
            const SizedBox(width: 4),
            Text('清除', style: TextStyle(fontSize: 12, color: DiaryStyle.accentColor(context))),
          ],
        ),
      ),
    );
  }

  /// 构建搜索栏
  Widget _buildSearchBar() {
    return Obx(() {
      // 使用isSearchVisible来控制搜索栏的显示，而不是依赖于文本内容
      final bool shouldShowSearchBar =
          controller.isSearchVisible.value ||
          controller.searchController.text.isNotEmpty ||
          controller.searchQuery.isNotEmpty;

      return Visibility(
        visible: shouldShowSearchBar,
        child: Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: DiarySearchBar(controller: controller, onClose: () => controller.enableSearch(false)),
        ),
      );
    });
  }

  /// 构建悬浮按钮
  Widget _buildFloatingButton() {
    return Positioned(
      right: 24,
      bottom: 16,
      child: SafeArea(bottom: true, right: true, child: DiaryFab(controller: controller)),
    );
  }

  /// 构建AppBar
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      backgroundColor: DiaryStyle.cardColor(context),
      elevation: 0.5,
      leading: IconButton(
        icon: Icon(FeatherIcons.calendar, color: DiaryStyle.secondaryTextColor(context), size: 20),
        onPressed: () => _showCalendarDialog(context),
      ),
      title: Text('我的日记', style: TextStyle(fontSize: 18, color: DiaryStyle.primaryTextColor(context))),
      actions: [
        IconButton(
          icon: Icon(FeatherIcons.search, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () => _activateSearch(),
        ),
        IconButton(
          icon: Icon(FeatherIcons.tag, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () => _showTagsDialog(context),
        ),
      ],
    );
  }

  /// 激活搜索
  void _activateSearch() {
    // 清除当前搜索内容
    controller.searchController.clear();

    // 如果之前有搜索结果，需要清除
    if (controller.searchQuery.isNotEmpty) {
      controller.clearFilters();
    }

    // 激活搜索栏
    controller.enableSearch(true);
  }

  /// 显示编辑对话框 - 支持Markdown和图片
  void _showEditDialog(BuildContext context, DiaryModel diary) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => DiaryEditDialog(diary: diary, controller: controller),
    );
  }

  /// 显示标签选择对话框 - 支持主题
  void _showTagsDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => DiaryTagsDialog(controller: controller),
    );
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context) {
    // 如果已经有筛选，先清除
    if (controller.searchQuery.isNotEmpty ||
        controller.currentTag.isNotEmpty ||
        controller.selectedFilterDate.value != null) {
      controller.clearFilters();
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isScrollControlled: true,
      builder: (context) => DiaryCalendarDialog(controller: controller),
    );
  }
}
