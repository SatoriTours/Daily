import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:image_picker/image_picker.dart';
import 'package:feather_icons/feather_icons.dart';
import 'dart:io';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';

import '../controllers/diary_controller.dart';
import '../utils/diary_utils.dart';
import 'widgets/diary_card.dart';
import 'widgets/diary_input.dart';
import 'widgets/markdown_toolbar.dart';
import 'widgets/image_preview.dart';

/// 日记页面
class DiaryView extends GetView<DiaryController> {
  const DiaryView({super.key});

  @override
  Widget build(BuildContext context) {
    // 使用组件实例化
    final appBar = AppBar(
      backgroundColor: DiaryStyle.cardColor(context),
      elevation: 0.5,
      title: Text('我的日记', style: TextStyle(fontSize: 18, color: DiaryStyle.primaryTextColor(context))),
      actions: [
        IconButton(
          icon: Icon(FeatherIcons.search, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () {
            controller.enableSearch(true);
          },
        ),
        IconButton(
          icon: Icon(FeatherIcons.tag, color: DiaryStyle.secondaryTextColor(context), size: 20),
          onPressed: () {
            _showTagsDialog(context);
          },
        ),
      ],
    );
    final inputArea = DiaryInput(controller: controller);

    return Scaffold(
      backgroundColor: DiaryStyle.backgroundColor(context),
      appBar: appBar,
      body: Stack(children: [_buildDiaryList(context), Positioned(left: 0, right: 0, bottom: 0, child: inputArea)]),
    );
  }

  /// 构建日记列表
  Widget _buildDiaryList(BuildContext context) {
    return Obx(() {
      if (controller.isLoading.value && controller.diaries.isEmpty) {
        return Center(child: CircularProgressIndicator(color: DiaryStyle.accentColor(context)));
      }

      if (controller.diaries.isEmpty) {
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                FeatherIcons.book,
                size: 64,
                color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[700] : Colors.grey[300],
              ),
              const SizedBox(height: 16),
              Text('还没有日记，开始记录今天的想法吧', style: TextStyle(fontSize: 16, color: DiaryStyle.secondaryTextColor(context))),
            ],
          ),
        );
      }

      // 按日期分组日记
      final groupedDiaries = _groupDiariesByDate(controller.diaries);

      return Padding(
        padding: const EdgeInsets.only(bottom: 80), // 为底部输入框留出空间
        child: ListView.builder(
          controller: controller.scrollController,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          itemCount: groupedDiaries.length,
          itemBuilder: (context, index) {
            final date = groupedDiaries.keys.elementAt(index);
            final diariesForDate = groupedDiaries[date]!;

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 8, left: 4),
                  child: _buildDateHeader(context, date),
                ),
                ...diariesForDate.map(
                  (diary) => DiaryCard(
                    diary: diary,
                    onDelete: () => controller.deleteDiary(diary.id),
                    onEdit: () => _showEditDialog(context, diary),
                  ),
                ),
              ],
            );
          },
        ),
      );
    });
  }

  /// 构建日期标题 - 支持主题
  Widget _buildDateHeader(BuildContext context, DateTime date) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
      margin: const EdgeInsets.only(bottom: 4),
      decoration: BoxDecoration(
        color: DiaryStyle.tagBackgroundColor(context).withOpacity(0.5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        DiaryUtils.formatDate(date),
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: DiaryStyle.secondaryTextColor(context),
          letterSpacing: 0.5,
        ),
      ),
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
                      decoration: InputDecoration(
                        hintText: '记录现在，畅想未来...',
                        hintStyle: TextStyle(
                          color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[600] : Colors.grey[400],
                        ),
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        errorBorder: InputBorder.none,
                        disabledBorder: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(vertical: 12, horizontal: 16),
                      ),
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

                  // Markdown 工具栏和操作按钮
                  Container(
                    height: 48,
                    margin: EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        // Markdown 工具栏
                        Expanded(
                          child: MarkdownToolbar(
                            controller: contentController,
                            onSave: null, // 不使用工具栏的保存功能
                          ),
                        ),

                        // 图片添加按钮
                        Tooltip(
                          message: '添加图片',
                          child: Container(
                            width: 36,
                            height: 36,
                            margin: EdgeInsets.symmetric(horizontal: 2),
                            child: IconButton(
                              onPressed: () async {
                                final picker = ImagePicker();
                                final pickedImages = await picker.pickMultiImage();

                                if (pickedImages.isNotEmpty) {
                                  // 保存图片并获取路径
                                  List<String> newImagePaths = [];
                                  final String dirPath = await controller.getImageSavePath();

                                  for (int i = 0; i < pickedImages.length; i++) {
                                    final XFile image = pickedImages[i];
                                    final String fileName = 'diary_img_${DateTime.now().millisecondsSinceEpoch}_$i.jpg';
                                    final String filePath = '$dirPath/$fileName';

                                    // 复制图片到应用目录
                                    final File savedImage = File(filePath);
                                    await savedImage.writeAsBytes(await image.readAsBytes());

                                    newImagePaths.add(filePath);
                                  }

                                  setModalState(() {
                                    currentImages.addAll(newImagePaths);
                                  });
                                }
                              },
                              icon: Icon(FeatherIcons.image, size: 16, color: DiaryStyle.primaryTextColor(context)),
                              padding: EdgeInsets.all(0),
                              constraints: BoxConstraints(),
                              visualDensity: VisualDensity.compact,
                            ),
                          ),
                        ),

                        // 更新按钮
                        Tooltip(
                          message: '更新',
                          child: Container(
                            width: 36,
                            height: 36,
                            margin: EdgeInsets.symmetric(horizontal: 2),
                            child: IconButton(
                              onPressed: () async {
                                if (contentController.text.trim().isNotEmpty) {
                                  // 删除被标记的图片
                                  for (String path in imagesToDelete) {
                                    final file = File(path);
                                    if (await file.exists()) {
                                      await file.delete();
                                    }
                                  }

                                  // 从内容中提取标签
                                  final String tags = DiaryUtils.extractTags(contentController.text);

                                  // 创建更新后的日记
                                  final updatedDiary = DiaryModel(
                                    id: diary.id,
                                    content: contentController.text,
                                    tags: tags,
                                    mood: diary.mood,
                                    images: currentImages.isEmpty ? null : currentImages.join(','),
                                    createdAt: diary.createdAt,
                                  );

                                  controller.updateDiary(updatedDiary);
                                  Navigator.pop(context);
                                }
                              },
                              icon: Icon(FeatherIcons.check, size: 16, color: DiaryStyle.accentColor(context)),
                              padding: EdgeInsets.all(0),
                              constraints: BoxConstraints(),
                              visualDensity: VisualDensity.compact,
                            ),
                          ),
                        ),
                      ],
                    ),
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
      builder: (context) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '选择标签',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                  ),
                  IconButton(
                    icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyle.secondaryTextColor(context)),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
            Container(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.4),
              child: Obx(() {
                if (controller.tags.isEmpty) {
                  return Padding(
                    padding: const EdgeInsets.all(20),
                    child: Center(
                      child: Text('没有找到标签', style: TextStyle(color: DiaryStyle.secondaryTextColor(context))),
                    ),
                  );
                }

                return ListView.separated(
                  shrinkWrap: true,
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  itemCount: controller.tags.length,
                  separatorBuilder:
                      (context, index) => Divider(
                        height: 1,
                        thickness: 0.5,
                        indent: 20,
                        endIndent: 20,
                        color: DiaryStyle.dividerColor(context),
                      ),
                  itemBuilder: (context, index) {
                    final tag = controller.tags[index];
                    return ListTile(
                      dense: true,
                      leading: Icon(FeatherIcons.hash, size: 16, color: DiaryStyle.accentColor(context)),
                      title: Text(tag, style: TextStyle(fontSize: 15, color: DiaryStyle.primaryTextColor(context))),
                      onTap: () {
                        controller.filterByTag(tag);
                        Navigator.pop(context);
                      },
                    );
                  },
                );
              }),
            ),
            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              child: InkWell(
                onTap: () {
                  controller.clearFilters();
                  Navigator.pop(context);
                },
                child: Container(
                  alignment: Alignment.center,
                  padding: EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    color: DiaryStyle.inputBackgroundColor(context),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Text(
                    '清除筛选',
                    style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontWeight: FontWeight.w500),
                  ),
                ),
              ),
            ),
            SizedBox(height: 8),
          ],
        );
      },
    );
  }

  /// 按日期分组日记
  Map<DateTime, List<DiaryModel>> _groupDiariesByDate(List<DiaryModel> diaries) {
    final Map<DateTime, List<DiaryModel>> grouped = {};

    for (final diary in diaries) {
      final date = DateTime(diary.createdAt.year, diary.createdAt.month, diary.createdAt.day);

      if (!grouped.containsKey(date)) {
        grouped[date] = [];
      }

      grouped[date]!.add(diary);
    }

    return grouped;
  }
}
