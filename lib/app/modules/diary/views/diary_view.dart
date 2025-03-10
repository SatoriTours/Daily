import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_input.dart';
import 'widgets/image_preview.dart';
import 'widgets/diary_list.dart';
import 'widgets/diary_toolbar.dart';
import 'widgets/diary_input_decoration.dart';
import 'widgets/diary_tags_dialog.dart';

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
          DiaryList(controller: controller, onEditDiary: (diary) => _showEditDialog(context, diary)),
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
      title: Text('我的日记', style: TextStyle(fontSize: 18, color: DiaryStyle.primaryTextColor(context))),
      actions: [
        IconButton(
          icon: Icon(FeatherIcons.search, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () => controller.enableSearch(true),
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
    final contentController = TextEditingController(text: diary.content);
    final List<String> currentImages = diary.images?.split(',') ?? [];
    final List<String> imagesToDelete = [];

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Container(
              height: MediaQuery.of(context).size.height * 0.8,
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).viewInsets.bottom + 8,
                left: 16,
                right: 16,
                top: 16,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // 编辑区域
                  Expanded(
                    child: TextField(
                      controller: contentController,
                      maxLines: null,
                      expands: true,
                      autofocus: true,
                      textAlignVertical: TextAlignVertical.top,
                      decoration: DiaryInputDecoration.get(context),
                      style: TextStyle(fontSize: 16, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                    ),
                  ),

                  // 显示现有图片
                  if (currentImages.isNotEmpty)
                    ImagePreview(
                      images: currentImages,
                      onDelete:
                          (index) => setModalState(() {
                            // 标记要删除的图片
                            imagesToDelete.add(currentImages[index]);
                            currentImages.removeAt(index);
                          }),
                    ),

                  // 工具栏和操作按钮
                  DiaryToolbar(
                    controller: contentController,
                    onImagePick: () => controller.pickAndSaveImages(context, setModalState, currentImages),
                    onSave:
                        () => controller.updateDiaryWithImages(
                          context,
                          diary,
                          contentController,
                          currentImages,
                          imagesToDelete,
                        ),
                    saveLabel: '更新',
                  ),
                ],
              ),
            );
          },
        );
      },
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
}
