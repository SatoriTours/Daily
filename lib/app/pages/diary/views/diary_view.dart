import 'package:daily_satori/app/pages/diary/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

import 'package:daily_satori/app_exports.dart';
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
    final controller = ref.read(diaryControllerProvider.notifier);
    final showSearchBar = state.isSearchVisible || state.searchQuery.isNotEmpty;

    return Scaffold(
      backgroundColor: DiaryStyles.getBackgroundColor(context),
      appBar: _buildAppBar(context, state, controller),
      body: Stack(
        children: [
          _DiaryContent(state: state, controller: controller),
          _buildSearchBar(showSearchBar, state, controller),
          _buildFab(context),
        ],
      ),
    );
  }

  PreferredSizeWidget _buildAppBar(BuildContext context, DiaryControllerState state, DiaryController controller) {
    return SAppBar(
      backgroundColorDark: AppColors.backgroundDark,
      backgroundColorLight: DiaryStyles.getAccentColor(context),
      elevation: 1,
      leading: IconButton(
        icon: const Icon(FeatherIcons.calendar, color: Colors.white, size: Dimensions.iconSizeM),
        onPressed: () => _showCalendarDialog(context, state, controller),
        tooltip: '日历',
      ),
      title: Text('我的日记', style: AppTypography.titleLarge),
      onTitleDoubleTap: () => _scrollToTop(state),
      actions: [
        IconButton(
          icon: const Icon(FeatherIcons.search, color: Colors.white, size: Dimensions.iconSizeM),
          onPressed: () {
            state.searchController?.clear();
            if (state.searchQuery.isNotEmpty) controller.clearFilters();
            controller.enableSearch(true);
          },
          tooltip: '搜索',
        ),
        IconButton(
          icon: const Icon(FeatherIcons.tag, color: Colors.white, size: Dimensions.iconSizeM),
          onPressed: () => _showTagsDialog(context, state, controller),
          tooltip: '标签',
        ),
      ],
      foregroundColor: Colors.white,
    );
  }

  Widget _buildSearchBar(bool show, DiaryControllerState state, DiaryController controller) {
    return AnimatedPositioned(
      duration: Animations.durationNormal,
      curve: Curves.easeInOut,
      top: show ? 0 : -60,
      left: 0,
      right: 0,
      height: 60,
      child: DiarySearchBar(
        searchController: state.searchController ?? TextEditingController(),
        searchFocusNode: state.searchFocusNode ?? FocusNode(),
        onClose: () => controller.enableSearch(false),
        onSearch: controller.search,
        onClearFilters: controller.clearFilters,
      ),
    );
  }

  Widget _buildFab(BuildContext context) {
    return Positioned(
      right: Dimensions.spacingL,
      bottom: Dimensions.spacingL,
      child: SafeArea(bottom: true, right: true, child: DiaryFab(onPressed: () => _showEditorDialog(context))),
    );
  }

  void _scrollToTop(DiaryControllerState state) {
    final sc = state.scrollController;
    if (sc != null && sc.hasClients) {
      sc.animateTo(0, duration: Animations.durationNormal, curve: Curves.easeInOut);
    }
  }

  void _showEditorDialog(BuildContext context, [DiaryModel? diary]) {
    FocusManager.instance.primaryFocus?.unfocus();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (_) => diary != null ? DiaryEditor(diary: diary) : const DiaryEditor(),
    ).then((_) => FocusManager.instance.primaryFocus?.unfocus());
  }

  void _showTagsDialog(BuildContext context, DiaryControllerState state, DiaryController controller) {
    FocusManager.instance.primaryFocus?.unfocus();
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (_) => DiaryTagsDialog(
        tags: state.tags,
        onTagSelected: (tag) {
          controller.filterByTag(tag);
          AppNavigation.back();
        },
        onClearFilters: () {
          controller.clearFilters();
          AppNavigation.back();
        },
      ),
    ).then((_) => FocusManager.instance.primaryFocus?.unfocus());
  }

  void _showCalendarDialog(BuildContext context, DiaryControllerState state, DiaryController controller) {
    FocusManager.instance.primaryFocus?.unfocus();
    if (state.searchQuery.isNotEmpty || state.currentTag.isNotEmpty || state.selectedFilterDate != null) {
      controller.clearFilters();
    }
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      isScrollControlled: true,
      builder: (_) => const DiaryCalendarDialog(),
    ).then((_) => FocusManager.instance.primaryFocus?.unfocus());
  }
}

/// 日记内容区域
class _DiaryContent extends ConsumerWidget {
  final DiaryControllerState state;
  final DiaryController controller;

  const _DiaryContent({required this.state, required this.controller});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final diaries = ref.watch(diaryStateProvider.select((s) => s.diaries));
    final filterText = _getFilterText();

    if (filterText != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          FilterIndicator(
            title: filterText,
            onClear: controller.clearFilters,
            margin: const EdgeInsets.fromLTRB(
              Dimensions.spacingM,
              Dimensions.spacingS + 4,
              Dimensions.spacingM,
              Dimensions.spacingS,
            ),
          ),
          Expanded(child: _buildList(context, diaries)),
        ],
      );
    }
    return _buildList(context, diaries);
  }

  String? _getFilterText() {
    if (state.searchQuery.isNotEmpty) return '搜索结果: "${state.searchQuery}"';
    if (state.currentTag.isNotEmpty) return '标签: "#${state.currentTag}"';
    if (state.selectedFilterDate != null) {
      return '日期: ${DateFormat('yyyy年MM月dd日').format(state.selectedFilterDate!)}';
    }
    return null;
  }

  Widget _buildList(BuildContext context, List<DiaryModel> diaries) {
    return DiaryList(
      diaries: diaries,
      isLoading: state.isLoadingDiaries,
      scrollController: state.scrollController,
      onEdit: (diary) => _showEditDialog(context, diary),
      onDelete: controller.deleteDiary,
    );
  }

  void _showEditDialog(BuildContext context, DiaryModel diary) {
    FocusManager.instance.primaryFocus?.unfocus();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (_) => DiaryEditor(diary: diary),
    ).then((_) => FocusManager.instance.primaryFocus?.unfocus());
  }
}