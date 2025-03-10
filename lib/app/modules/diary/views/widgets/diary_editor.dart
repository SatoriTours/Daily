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

/// 日记扩展编辑器组件
class DiaryEditor extends StatefulWidget {
  final DiaryController controller;

  const DiaryEditor({super.key, required this.controller});

  @override
  State<DiaryEditor> createState() => _DiaryEditorState();
}

class _DiaryEditorState extends State<DiaryEditor> {
  final List<XFile> _selectedImages = [];

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.8,
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom + 8, left: 16, right: 16, top: 16),
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
              decoration: _getInputDecoration(context),
              style: TextStyle(fontSize: 16, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
            ),
          ),

          // 已选图片预览
          if (_selectedImages.isNotEmpty)
            ImagePreview(
              images: _selectedImages.map((e) => e.path).toList(),
              onDelete:
                  (index) => setState(() {
                    _selectedImages.removeAt(index);
                  }),
            ),

          // 工具栏和操作按钮
          _buildToolbar(context),
        ],
      ),
    );
  }

  /// 构建工具栏
  Widget _buildToolbar(BuildContext context) {
    return Container(
      height: 48,
      margin: const EdgeInsets.only(top: 8),
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
          _buildToolbarButton(context, FeatherIcons.image, '添加图片', _selectImages, isAccent: false),

          // 保存按钮
          _buildToolbarButton(context, FeatherIcons.check, '保存', _saveDiary, isAccent: true),
        ],
      ),
    );
  }

  /// 构建工具栏按钮
  Widget _buildToolbarButton(
    BuildContext context,
    IconData icon,
    String tooltip,
    VoidCallback onPressed, {
    bool isAccent = false,
  }) {
    return Tooltip(
      message: tooltip,
      child: Container(
        width: 36,
        height: 36,
        margin: const EdgeInsets.symmetric(horizontal: 2),
        child: IconButton(
          onPressed: onPressed,
          icon: Icon(
            icon,
            size: 16,
            color: isAccent ? DiaryStyle.accentColor(context) : DiaryStyle.primaryTextColor(context),
          ),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }

  /// 获取输入框装饰
  InputDecoration _getInputDecoration(BuildContext context) {
    return InputDecoration(
      hintText: '记录现在，畅想未来...',
      hintStyle: TextStyle(
        color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[600] : Colors.grey[400],
      ),
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      errorBorder: InputBorder.none,
      disabledBorder: InputBorder.none,
      contentPadding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
    );
  }

  /// 选择图片
  void _selectImages() async {
    final picker = ImagePicker();
    final pickedImages = await picker.pickMultiImage();

    if (pickedImages.isNotEmpty) {
      setState(() {
        _selectedImages.addAll(pickedImages);
      });
    }
  }

  /// 保存日记
  void _saveDiary() async {
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
  }

  /// 保存图片并返回路径
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
