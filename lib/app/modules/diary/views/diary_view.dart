import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_input.dart';
import 'widgets/diary_list.dart';
import 'widgets/diary_tags_dialog.dart';
import 'widgets/diary_edit_dialog.dart';
import 'widgets/diary_search_bar.dart';
import 'widgets/diary_calendar_dialog.dart';

/// 日记页面
class DiaryView extends GetView<DiaryController> {
  const DiaryView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DiaryStyle.backgroundColor(context),
      appBar: _buildAppBar(context),
      body: Stack(
        children: [
          Obx(() {
            // 显示搜索结果状态
            if (controller.searchQuery.isNotEmpty) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '搜索结果: "${controller.searchQuery.value}"',
                            style: TextStyle(fontSize: 14, color: DiaryStyle.secondaryTextColor(context)),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        InkWell(
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
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary)),
                  ),
                ],
              );
            }

            // 显示标签过滤状态
            if (controller.currentTag.isNotEmpty) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '标签: "#${controller.currentTag.value}"',
                            style: TextStyle(fontSize: 14, color: DiaryStyle.secondaryTextColor(context)),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        InkWell(
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
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary)),
                  ),
                ],
              );
            }

            // 显示日期过滤状态
            if (controller.selectedFilterDate.value != null) {
              final dateFormat = DateFormat('yyyy年MM月dd日');
              final dateText = dateFormat.format(controller.selectedFilterDate.value!);

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '日期: $dateText',
                            style: TextStyle(fontSize: 14, color: DiaryStyle.secondaryTextColor(context)),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        InkWell(
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
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary)),
                  ),
                ],
              );
            }

            // 显示正常状态
            return DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary));
          }),

          // 搜索栏
          Obx(() {
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
          }),

          Positioned(left: 0, right: 0, bottom: 0, child: DiaryInput(controller: controller)),
        ],
      ),
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
          onPressed: () {
            // 清除当前搜索内容
            controller.searchController.clear();

            // 如果之前有搜索结果，需要清除
            if (controller.searchQuery.isNotEmpty) {
              controller.clearFilters();
            }

            // 激活搜索栏
            controller.enableSearch(true);
          },
        ),
        IconButton(
          icon: Icon(FeatherIcons.tag, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () => _showTagsDialog(context),
        ),
      ],
    );
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
    if (controller.searchQuery.isNotEmpty || controller.currentTag.isNotEmpty) {
      controller.clearFilters();
    }

    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      isScrollControlled: true,
      builder:
          (context) => Padding(
            padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
            child: DiaryCalendarDialog(controller: controller),
          ),
    );
  }
}
