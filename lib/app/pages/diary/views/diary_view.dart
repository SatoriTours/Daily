import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/components/indicators/s_filter_indicator.dart';
import 'package:daily_satori/app/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/providers/diary_state_provider.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'widgets/diary_calendar_dialog.dart';
import 'widgets/diary_editor.dart';
import 'widgets/diary_fab.dart';
import 'widgets/diary_list.dart';
import 'widgets/diary_search_bar.dart';
import 'widgets/diary_tags_dialog.dart';

/// 日记页面
class DiaryView extends ConsumerWidget {
  const DiaryView({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(diaryControllerProvider);
    final shouldShowSearchBar = state.isSearchVisible || state.searchQuery.isNotEmpty;

    return Scaffold(
      backgroundColor: DiaryStyles.getBackgroundColor(context),
      appBar: _buildAppBar(context, ref),
      body: Stack(
        children: [
          _buildMainContent(context, ref),
          // 搜索栏（仅在需要时显示）
          _buildSearchBar(shouldShowSearchBar, ref),
          _buildFloatingButton(context, ref),
        ],
      ),
    );
  }

  /// 构建主要内容区域
  Widget _buildMainContent(BuildContext context, WidgetRef ref) {
    logger.d('构建日记主内容区域');
    final state = ref.watch(diaryControllerProvider);
    final diaries = ref.watch(diaryStateProvider).diaries;

    // 显示搜索结果状态
    if (state.searchQuery.isNotEmpty) {
      return _buildFilterView(context, ref, '搜索结果: "${state.searchQuery}"', diaries);
    }

    // 显示标签过滤状态
    if (state.currentTag.isNotEmpty) {
      return _buildFilterView(context, ref, '标签: "#${state.currentTag}"', diaries);
    }

    // 显示日期过滤状态
    if (state.selectedFilterDate != null) {
      final dateFormat = DateFormat('yyyy年MM月dd日');
      final dateText = dateFormat.format(state.selectedFilterDate!);
      return _buildFilterView(context, ref, '日期: $dateText', diaries);
    }

    // 显示正常状态
    return DiaryList(
      diaries: diaries,
      isLoading: state.isLoadingDiaries,
      scrollController: state.scrollController,
      onEdit: (diary) => _showEditDialog(context, ref, diary),
      onDelete: (id) {
        ref.read(diaryControllerProvider.notifier).deleteDiary(id);
      },
    );
  }

  /// 构建过滤视图（搜索结果、标签过滤、日期过滤等）
  Widget _buildFilterView(BuildContext context, WidgetRef ref, String filterText, List<DiaryModel> diaries) {
    logger.d('构建过滤视图: $filterText');
    final state = ref.watch(diaryControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildFilterHeader(context, filterText, ref),
        Expanded(
          child: DiaryList(
            diaries: diaries,
            isLoading: state.isLoadingDiaries,
            scrollController: state.scrollController,
            onEdit: (diary) => _showEditDialog(context, ref, diary),
            onDelete: (id) {
              ref.read(diaryControllerProvider.notifier).deleteDiary(id);
            },
          ),
        ),
      ],
    );
  }

  /// 构建过滤器头部
  Widget _buildFilterHeader(BuildContext context, String filterText, WidgetRef ref) {
    return FilterIndicator(
      title: filterText,
      onClear: () => ref.read(diaryControllerProvider.notifier).clearFilters(),
      // 调整与现有布局相近的外边距
      margin: const EdgeInsets.fromLTRB(
        Dimensions.spacingM,
        Dimensions.spacingS + 4,
        Dimensions.spacingM,
        Dimensions.spacingS,
      ),
    );
  }

  /// 构建搜索栏
  Widget _buildSearchBar(bool shouldShowSearchBar, WidgetRef ref) {
    final state = ref.watch(diaryControllerProvider);
    return AnimatedPositioned(
      duration: Animations.durationNormal,
      curve: Curves.easeInOut,
      top: shouldShowSearchBar ? 0 : -60,
      left: 0,
      right: 0,
      height: 60,
      child: DiarySearchBar(
        searchController: state.searchController ?? TextEditingController(),
        searchFocusNode: state.searchFocusNode ?? FocusNode(),
        onClose: () => ref.read(diaryControllerProvider.notifier).enableSearch(false),
        onSearch: (query) => ref.read(diaryControllerProvider.notifier).search(query),
        onClearFilters: () => ref.read(diaryControllerProvider.notifier).clearFilters(),
      ),
    );
  }

  /// 构建悬浮按钮
  Widget _buildFloatingButton(BuildContext context, WidgetRef ref) {
    return Positioned(
      right: Dimensions.spacingL,
      bottom: Dimensions.spacingL,
      child: SafeArea(bottom: true, right: true, child: DiaryFab(onPressed: () => _showEditorDialog(context, ref))),
    );
  }

  /// 构建AppBar
  PreferredSizeWidget _buildAppBar(BuildContext context, WidgetRef ref) {
    return SAppBar(
      backgroundColorDark: AppColors.backgroundDark,
      backgroundColorLight: DiaryStyles.getAccentColor(context),
      elevation: 1,
      leading: _buildAppBarButton(
        context,
        FeatherIcons.calendar,
        Colors.white,
        () => _showCalendarDialog(context, ref),
      ),
      title: Text('我的日记', style: AppTypography.titleLarge),
      onTitleDoubleTap: () => _scrollToTop(ref),
      actions: [
        _buildAppBarButton(context, FeatherIcons.search, Colors.white, () => _activateSearch(context, ref)),
        _buildAppBarButton(context, FeatherIcons.tag, Colors.white, () => _showTagsDialog(context, ref)),
      ],
      foregroundColor: Colors.white,
    );
  }

  /// 构建AppBar按钮
  Widget _buildAppBarButton(BuildContext context, IconData icon, Color color, VoidCallback onPressed) {
    return IconButton(
      icon: Icon(icon, color: color, size: Dimensions.iconSizeM),
      splashRadius: Dimensions.spacingL,
      onPressed: onPressed,
      padding: const EdgeInsets.all(Dimensions.spacingS + 4),
      tooltip: icon == FeatherIcons.calendar
          ? '日历'
          : icon == FeatherIcons.search
          ? '搜索'
          : '标签',
    );
  }

  /// 滚动到顶部
  void _scrollToTop(WidgetRef ref) {
    logger.d('双击标题，滚动到顶部');
    final state = ref.read(diaryControllerProvider);
    final scrollController = state.scrollController;
    if (scrollController != null && scrollController.hasClients) {
      scrollController.animateTo(0, duration: Animations.durationNormal, curve: Curves.easeInOut);
    }
  }

  /// 激活搜索
  void _activateSearch(BuildContext context, WidgetRef ref) {
    logger.d('激活搜索功能');
    final state = ref.read(diaryControllerProvider);
    // 清除当前搜索内容
    state.searchController?.clear();
    // 如果之前有搜索结果，需要清除
    if (state.searchQuery.isNotEmpty) {
      ref.read(diaryControllerProvider.notifier).clearFilters();
    }
    // 激活搜索栏
    ref.read(diaryControllerProvider.notifier).enableSearch(true);
  }

  /// 显示编辑对话框 - 支持Markdown和图片
  void _showEditDialog(BuildContext context, WidgetRef ref, DiaryModel diary) {
    logger.d('显示编辑对话框: ${diary.id}');
    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(diary: diary),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }

  /// 显示标签选择对话框 - 支持主题
  void _showTagsDialog(BuildContext context, WidgetRef ref) {
    logger.d('显示标签对话框');
    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => const DiaryTagsDialog(),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }

  /// 显示编辑器对话框（创建新日记）
  void _showEditorDialog(BuildContext context, WidgetRef ref) {
    logger.d('显示编辑器对话框');
    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => const DiaryEditor(),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }

  /// 显示日历选择对话框
  void _showCalendarDialog(BuildContext context, WidgetRef ref) {
    logger.d('显示日历对话框');
    final state = ref.read(diaryControllerProvider);
    // 隐藏当前可能的键盘
    FocusManager.instance.primaryFocus?.unfocus();
    // 如果已经有筛选，先清除
    if (state.searchQuery.isNotEmpty || state.currentTag.isNotEmpty || state.selectedFilterDate != null) {
      ref.read(diaryControllerProvider.notifier).clearFilters();
    }
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      isScrollControlled: true,
      builder: (context) => const DiaryCalendarDialog(),
    ).then((_) {
      // 确保对话框关闭后键盘也被隐藏
      FocusManager.instance.primaryFocus?.unfocus();
    });
  }
}
