import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:image_picker/image_picker.dart';
import 'package:feather_icons/feather_icons.dart';
import 'dart:io';

import '../../controllers/diary_controller.dart';
import '../../utils/diary_utils.dart';
import 'markdown_toolbar.dart';
import 'image_preview.dart';

/// 日记输入组件 - 支持Markdown和图片
class DiaryInput extends StatefulWidget {
  final DiaryController controller;

  const DiaryInput({super.key, required this.controller});

  @override
  State<DiaryInput> createState() => _DiaryInputState();
}

class _DiaryInputState extends State<DiaryInput> {
  final List<XFile> _selectedImages = [];

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(Theme.of(context).brightness == Brightness.dark ? 0.3 : 0.05),
            blurRadius: 8,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: InkWell(
        onTap: () => _showExpandedEditor(context),
        child: Container(
          padding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          decoration: BoxDecoration(
            color: DiaryStyle.inputBackgroundColor(context),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  '记录现在，畅想未来',
                  style: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                    fontSize: 14,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
              Icon(FeatherIcons.edit2, color: DiaryStyle.secondaryTextColor(context), size: 18),
            ],
          ),
        ),
      ),
    );
  }

  // 显示扩展编辑器
  void _showExpandedEditor(BuildContext context) {
    // 重置选中的图片列表
    _selectedImages.clear();

    // 显示底部编辑器模态框
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
                      controller: widget.controller.contentController,
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

                  // 已选图片预览
                  if (_selectedImages.isNotEmpty)
                    ImagePreview(
                      images: _selectedImages.map((e) => e.path).toList(),
                      onDelete:
                          (index) => setModalState(() {
                            _selectedImages.removeAt(index);
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
                            controller: widget.controller.contentController,
                            onSave: null, // 不使用工具栏的保存功能
                          ),
                        ),

                        // 图片添加按钮
                        Container(
                          margin: EdgeInsets.only(left: 8),
                          child: IconButton(
                            onPressed: () => _selectImages(setModalState),
                            icon: Icon(FeatherIcons.image, size: 18, color: DiaryStyle.accentColor(context)),
                            tooltip: '添加图片',
                            padding: EdgeInsets.all(0),
                            visualDensity: VisualDensity.compact,
                          ),
                        ),

                        // 保存按钮
                        Container(
                          margin: EdgeInsets.only(left: 8),
                          child: IconButton(
                            onPressed: () async {
                              if (widget.controller.contentController.text.trim().isNotEmpty) {
                                // 保存图片并获取路径列表
                                final List<String> imagePaths = await _saveImages();

                                // 从内容中提取标签
                                final String tags = DiaryUtils.extractTags(widget.controller.contentController.text);

                                // 创建日记
                                widget.controller.createDiary(
                                  widget.controller.contentController.text,
                                  tags: tags,
                                  images: imagePaths.isEmpty ? null : imagePaths.join(','),
                                );

                                // 关闭底部弹窗
                                Navigator.pop(context);

                                // 清理标签控制器
                                widget.controller.tagsController.clear();
                              }
                            },
                            icon: Icon(FeatherIcons.check, size: 18, color: DiaryStyle.accentColor(context)),
                            tooltip: '保存',
                            padding: EdgeInsets.all(0),
                            visualDensity: VisualDensity.compact,
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

  // 选择图片
  Future<void> _selectImages(StateSetter setModalState) async {
    final picker = ImagePicker();
    final pickedImages = await picker.pickMultiImage();

    if (pickedImages.isNotEmpty) {
      setModalState(() {
        _selectedImages.addAll(pickedImages);
      });
    }
  }

  // 保存图片并返回路径
  Future<List<String>> _saveImages() async {
    if (_selectedImages.isEmpty) return [];

    List<String> savedPaths = [];
    final String dirPath = await widget.controller.getImageSavePath();

    for (int i = 0; i < _selectedImages.length; i++) {
      final XFile image = _selectedImages[i];
      final String fileName = 'diary_img_${DateTime.now().millisecondsSinceEpoch}_$i.jpg';
      final String filePath = '$dirPath/$fileName';

      // 复制图片到应用目录
      final File savedImage = File(filePath);
      await savedImage.writeAsBytes(await image.readAsBytes());

      savedPaths.add(filePath);
    }

    return savedPaths;
  }
}
