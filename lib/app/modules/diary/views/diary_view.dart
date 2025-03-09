import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_card.dart';
import 'widgets/diary_input.dart';

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
          icon: Icon(Icons.search, color: DiaryStyle.secondaryTextColor(context)),
          onPressed: () {
            controller.enableSearch(true);
          },
        ),
        IconButton(
          icon: Icon(Icons.tag, color: DiaryStyle.secondaryTextColor(context)),
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
                Icons.note_alt_outlined,
                size: 72,
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
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          itemCount: groupedDiaries.length,
          itemBuilder: (context, index) {
            final date = groupedDiaries.keys.elementAt(index);
            final diariesForDate = groupedDiaries[date]!;

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 16, bottom: 12, left: 4),
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
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 0),
      child: Text(
        _formatDate(date),
        style: TextStyle(
          fontSize: 14,
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
    final tagsController = TextEditingController(text: diary.tags);
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
                bottom: MediaQuery.of(context).viewInsets.bottom + 16,
                left: 16,
                right: 16,
                top: 16,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // 顶部标题栏
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '编辑日记',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: DiaryStyle.primaryTextColor(context),
                        ),
                      ),
                      Row(
                        children: [
                          // 添加图片按钮
                          IconButton(
                            icon: Icon(Icons.photo_library, color: DiaryStyle.accentColor(context)),
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
                          ),
                          // Markdown预览切换
                          IconButton(
                            icon: Icon(Icons.preview, color: DiaryStyle.accentColor(context)),
                            onPressed: () {
                              // 显示Markdown预览
                              showDialog(
                                context: context,
                                builder:
                                    (context) => AlertDialog(
                                      title: Text('预览', style: TextStyle(color: DiaryStyle.primaryTextColor(context))),
                                      content: Container(
                                        width: double.maxFinite,
                                        height: 400,
                                        child: Markdown(
                                          data: contentController.text,
                                          styleSheet: MarkdownStyleSheet(
                                            p: TextStyle(color: DiaryStyle.primaryTextColor(context)),
                                            h1: TextStyle(color: DiaryStyle.primaryTextColor(context)),
                                            h2: TextStyle(color: DiaryStyle.primaryTextColor(context)),
                                            h3: TextStyle(color: DiaryStyle.primaryTextColor(context)),
                                          ),
                                        ),
                                      ),
                                      actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('关闭'))],
                                      backgroundColor: DiaryStyle.bottomSheetColor(context),
                                    ),
                              );
                            },
                          ),
                          IconButton(
                            icon: Icon(Icons.close, color: DiaryStyle.secondaryTextColor(context)),
                            onPressed: () {
                              Navigator.pop(context);
                            },
                          ),
                        ],
                      ),
                    ],
                  ),

                  // Markdown 工具栏
                  _buildMarkdownToolbar(context, contentController),

                  // 编辑区域
                  Expanded(
                    child: TextField(
                      controller: contentController,
                      maxLines: null,
                      expands: true,
                      textAlignVertical: TextAlignVertical.top,
                      decoration: InputDecoration(
                        hintText: '支持Markdown格式，写下你的想法...',
                        hintStyle: TextStyle(
                          color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[600] : Colors.grey[400],
                        ),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(vertical: 12),
                      ),
                      style: TextStyle(fontSize: 16, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                    ),
                  ),

                  // 显示现有图片
                  if (currentImages.isNotEmpty)
                    Container(
                      height: 100,
                      margin: EdgeInsets.symmetric(vertical: 8),
                      child: ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: currentImages.length,
                        itemBuilder: (context, index) {
                          return Stack(
                            children: [
                              Container(
                                width: 100,
                                height: 100,
                                margin: EdgeInsets.only(right: 8),
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(8),
                                  image: DecorationImage(
                                    image: FileImage(File(currentImages[index])),
                                    fit: BoxFit.cover,
                                  ),
                                ),
                              ),
                              Positioned(
                                right: 10,
                                top: 5,
                                child: GestureDetector(
                                  onTap:
                                      () => setModalState(() {
                                        // 标记要删除的图片
                                        imagesToDelete.add(currentImages[index]);
                                        currentImages.removeAt(index);
                                      }),
                                  child: Container(
                                    padding: EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: Colors.black.withOpacity(0.5),
                                      shape: BoxShape.circle,
                                    ),
                                    child: Icon(Icons.close, size: 16, color: Colors.white),
                                  ),
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    ),

                  // 标签输入框
                  Container(
                    margin: EdgeInsets.only(top: 8),
                    child: TextField(
                      controller: tagsController,
                      style: TextStyle(fontSize: 14, color: DiaryStyle.primaryTextColor(context)),
                      decoration: InputDecoration(
                        hintText: '添加标签，用逗号分隔',
                        hintStyle: TextStyle(
                          color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                        ),
                        prefixIcon: Icon(Icons.tag, size: 18, color: DiaryStyle.secondaryTextColor(context)),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(24),
                          borderSide: BorderSide(color: DiaryStyle.dividerColor(context)),
                        ),
                        contentPadding: EdgeInsets.symmetric(vertical: 10),
                      ),
                    ),
                  ),

                  // 更新按钮
                  Container(
                    margin: EdgeInsets.only(top: 16),
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: DiaryStyle.accentColor(context),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                        padding: EdgeInsets.symmetric(vertical: 12),
                      ),
                      onPressed: () async {
                        if (contentController.text.trim().isNotEmpty) {
                          // 删除被标记的图片
                          for (String path in imagesToDelete) {
                            final file = File(path);
                            if (await file.exists()) {
                              await file.delete();
                            }
                          }

                          // 创建更新后的日记
                          final updatedDiary = DiaryModel(
                            id: diary.id,
                            content: contentController.text,
                            tags: tagsController.text,
                            mood: diary.mood,
                            images: currentImages.isEmpty ? null : currentImages.join(','),
                            createdAt: diary.createdAt,
                          );

                          controller.updateDiary(updatedDiary);
                          Navigator.pop(context);
                        }
                      },
                      child: const Text('更新', style: TextStyle(fontSize: 16)),
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

  /// 构建Markdown工具栏
  Widget _buildMarkdownToolbar(BuildContext context, TextEditingController contentController) {
    return Container(
      height: 40,
      margin: EdgeInsets.only(top: 8, bottom: 8),
      decoration: BoxDecoration(
        color: DiaryStyle.inputBackgroundColor(context),
        borderRadius: BorderRadius.circular(8),
      ),
      child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          _buildToolbarButton(context, '# 标题', () => _insertMarkdown(contentController, '# ')),
          _buildToolbarButton(context, '**粗体**', () => _insertMarkdown(contentController, '**文本**')),
          _buildToolbarButton(context, '*斜体*', () => _insertMarkdown(contentController, '*文本*')),
          _buildToolbarButton(context, '- 列表', () => _insertMarkdown(contentController, '- 项目\n- 项目\n- 项目')),
          _buildToolbarButton(context, '1. 有序列表', () => _insertMarkdown(contentController, '1. 项目\n2. 项目\n3. 项目')),
          _buildToolbarButton(
            context,
            '[链接](url)',
            () => _insertMarkdown(contentController, '[链接文本](https://example.com)'),
          ),
          _buildToolbarButton(context, '> 引用', () => _insertMarkdown(contentController, '> 引用文本')),
          _buildToolbarButton(context, '`代码`', () => _insertMarkdown(contentController, '`代码`')),
          _buildToolbarButton(context, '---', () => _insertMarkdown(contentController, '\n---\n')),
        ],
      ),
    );
  }

  /// 构建工具栏按钮
  Widget _buildToolbarButton(BuildContext context, String label, VoidCallback onPressed) {
    return InkWell(
      onTap: onPressed,
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: 12),
        alignment: Alignment.center,
        child: Text(label, style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontSize: 14)),
      ),
    );
  }

  /// 在当前光标位置插入Markdown内容
  void _insertMarkdown(TextEditingController controller, String markdown) {
    final int currentPosition = controller.selection.baseOffset;

    // 处理光标位置无效的情况
    if (currentPosition < 0) {
      controller.text = controller.text + markdown;
      controller.selection = TextSelection.collapsed(offset: controller.text.length);
      return;
    }

    // 在光标位置插入Markdown
    final String newText =
        controller.text.substring(0, currentPosition) + markdown + controller.text.substring(currentPosition);

    controller.text = newText;
    controller.selection = TextSelection.collapsed(offset: currentPosition + markdown.length);
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
                    icon: Icon(Icons.close, size: 20, color: DiaryStyle.secondaryTextColor(context)),
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
                      title: Text('#$tag', style: TextStyle(fontSize: 15, color: DiaryStyle.primaryTextColor(context))),
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

  /// 格式化日期 - flomo风格
  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final dateOnly = DateTime(date.year, date.month, date.day);

    if (dateOnly.isAtSameMomentAs(DateTime(now.year, now.month, now.day))) {
      return '今天';
    } else if (dateOnly.isAtSameMomentAs(yesterday)) {
      return '昨天';
    } else {
      return DateFormat('yyyy年MM月dd日').format(date);
    }
  }
}
