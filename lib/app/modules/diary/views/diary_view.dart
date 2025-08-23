import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/app/components/indicators/s_filter_indicator.dart';

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
      body: Stack(children: [Obx(() => _buildMainContent(context)), _buildSearchBar(), _buildFloatingButton(context)]),
    );
  }

  /// 构建主要内容区域
  Widget _buildMainContent(BuildContext context) {
    logger.d('构建日记主内容区域');

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
    logger.d('构建过滤视图: $filterText');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildFilterHeader(context, filterText),
        Expanded(
          child: DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary)),
        ),
      ],
    );
  }

  /// 构建过滤器头部
  Widget _buildFilterHeader(BuildContext context, String filterText) {
    return SFilterIndicator(
      title: filterText,
      prefix: '',
      onClear: controller.clearFilters,
      // 调整与现有布局相近的外边距
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 8),
    );
  }

  /// 构建搜索栏
  Widget _buildSearchBar() {
    return Obx(() {
      logger.d('更新搜索栏可见性');
      final bool shouldShowSearchBar = controller.isSearchVisible.value || controller.searchQuery.isNotEmpty;

      return AnimatedPositioned(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
        top: shouldShowSearchBar ? 0 : -60,
        left: 0,
        right: 0,
        height: 60,
        child: DiarySearchBar(controller: controller, onClose: () => controller.enableSearch(false)),
      );
    });
  }

  /// 构建悬浮按钮
  Widget _buildFloatingButton(BuildContext context) {
    return Positioned(
      right: 24,
      bottom: 24,
      child: SafeArea(bottom: true, right: true, child: DiaryFab(controller: controller)),
    );
  }

  /// 构建AppBar
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return SAppBar(
      backgroundColorDark: const Color(0xFF121212),
      backgroundColorLight: DiaryStyle.accentColor(context),
      elevation: 1,
      leading: _buildAppBarButton(context, FeatherIcons.calendar, Colors.white, () => _showCalendarDialog(context)),
      title: Text('我的日记', style: MyFontStyle.appBarTitleStyle),
      onTitleDoubleTap: _scrollToTop,
      actions: [
        _buildAppBarButton(context, FeatherIcons.search, Colors.white, _activateSearch),
        _buildAppBarButton(context, FeatherIcons.tag, Colors.white, () => _showTagsDialog(context)),
      ],
      foregroundColor: Colors.white,
    );
  }

  /// 构建AppBar按钮
  Widget _buildAppBarButton(BuildContext context, IconData icon, Color color, VoidCallback onPressed) {
    return IconButton(
      icon: Icon(icon, color: color, size: 20),
      splashRadius: 24,
      onPressed: onPressed,
      padding: const EdgeInsets.all(12),
      tooltip: icon == FeatherIcons.calendar
          ? '日历'
          : icon == FeatherIcons.search
          ? '搜索'
          : '标签',
    );
  }

  /// 滚动到顶部
  void _scrollToTop() {
    logger.d('双击标题，滚动到顶部');
    if (controller.scrollController.hasClients) {
      controller.scrollController.animateTo(0, duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
    }
  }

  /// 激活搜索
  void _activateSearch() {
    logger.d('激活搜索功能');
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
    logger.d('显示编辑对话框: ${diary.id}');

    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => DiaryEditDialog(diary: diary, controller: controller),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }

  /// 显示标签选择对话框 - 支持主题
  void _showTagsDialog(BuildContext context) {
    logger.d('显示标签对话框');

    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();

    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => DiaryTagsDialog(controller: controller),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context) {
    logger.d('显示日历对话框');

    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();

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
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }
}
