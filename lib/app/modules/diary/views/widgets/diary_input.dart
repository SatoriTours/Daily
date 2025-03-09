import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';

import '../../controllers/diary_controller.dart';

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
          padding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            color: DiaryStyle.inputBackgroundColor(context),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  '写点什么...',
                  style: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                    fontSize: 14,
                  ),
                ),
              ),
              Icon(Icons.edit_note, color: DiaryStyle.secondaryTextColor(context), size: 20),
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
                        '撰写日记',
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
                            onPressed: () => _selectImages(setModalState),
                          ),
                          // Markdown预览切换
                          IconButton(
                            icon: Icon(Icons.preview, color: DiaryStyle.accentColor(context)),
                            onPressed: () {
                              // TODO: 添加预览功能
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
                  _buildMarkdownToolbar(context),

                  // 编辑区域
                  Expanded(
                    child: TextField(
                      controller: widget.controller.contentController,
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

                  // 已选图片预览
                  if (_selectedImages.isNotEmpty)
                    Container(
                      height: 100,
                      margin: EdgeInsets.symmetric(vertical: 8),
                      child: ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: _selectedImages.length,
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
                                    image: FileImage(File(_selectedImages[index].path)),
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
                                        _selectedImages.removeAt(index);
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
                      controller: widget.controller.tagsController,
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

                  // 保存按钮
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
                        if (widget.controller.contentController.text.trim().isNotEmpty) {
                          // 保存图片并获取路径列表
                          final List<String> imagePaths = await _saveImages();

                          // 创建日记
                          widget.controller.createDiary(
                            widget.controller.contentController.text,
                            tags: widget.controller.tagsController.text,
                            images: imagePaths.isEmpty ? null : imagePaths.join(','),
                          );

                          // 关闭底部弹窗
                          Navigator.pop(context);

                          // 清理标签
                          widget.controller.tagsController.clear();
                        }
                      },
                      child: const Text('保存', style: TextStyle(fontSize: 16)),
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

  // 构建Markdown工具栏
  Widget _buildMarkdownToolbar(BuildContext context) {
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
          _buildToolbarButton(context, '# 标题', () => _insertMarkdown('# ')),
          _buildToolbarButton(context, '**粗体**', () => _insertMarkdown('**文本**')),
          _buildToolbarButton(context, '*斜体*', () => _insertMarkdown('*文本*')),
          _buildToolbarButton(context, '- 列表', () => _insertMarkdown('- 项目\n- 项目\n- 项目')),
          _buildToolbarButton(context, '1. 有序列表', () => _insertMarkdown('1. 项目\n2. 项目\n3. 项目')),
          _buildToolbarButton(context, '[链接](url)', () => _insertMarkdown('[链接文本](https://example.com)')),
          _buildToolbarButton(context, '> 引用', () => _insertMarkdown('> 引用文本')),
          _buildToolbarButton(context, '`代码`', () => _insertMarkdown('`代码`')),
          _buildToolbarButton(context, '---', () => _insertMarkdown('\n---\n')),
        ],
      ),
    );
  }

  // 构建工具栏按钮
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

  // 在当前光标位置插入Markdown内容
  void _insertMarkdown(String markdown) {
    final TextEditingController controller = widget.controller.contentController;
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
