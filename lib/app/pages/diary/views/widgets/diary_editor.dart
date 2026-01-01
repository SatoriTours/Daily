import 'dart:io';

import 'package:daily_satori/app/pages/diary/utils/diary_utils.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/diary_tag_selector_dialog.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/markdown_toolbar.dart';
import 'package:daily_satori/app/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

/// 日记扩展编辑器组件
class DiaryEditor extends ConsumerStatefulWidget {
  final DiaryModel? diary; // 可选的日记对象，用于编辑模式
  final DateTime? initialDate; // 初始日期，用于新建模式

  const DiaryEditor({super.key, this.diary, this.initialDate});

  @override
  ConsumerState<DiaryEditor> createState() => _DiaryEditorState();
}

class _DiaryEditorState extends ConsumerState<DiaryEditor> {
  final TextEditingController _contentController = TextEditingController();
  final List<XFile> _selectedImages = [];
  List<String> _existingImages = []; // 存储已有的图片路径
  List<String> _selectedTags = []; // 存储选中的标签

  // 用于撤销和重做的历史记录管理
  final List<String> _undoHistory = [];
  final List<String> _redoHistory = [];
  String _lastText = '';

  @override
  void initState() {
    super.initState();
    _initializeEditor();

    // 设置初始文本和历史
    _lastText = _contentController.text;
    _undoHistory.add(_lastText);

    // 监听文本变化以支持撤销和重做
    _contentController.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    _contentController.removeListener(_onTextChanged);
    _contentController.dispose();
    super.dispose();
  }

  /// 初始化编辑器
  void _initializeEditor() {
    if (widget.diary != null) {
      _contentController.text = widget.diary!.content;
      _existingImages = widget.diary!.imagesList;
      // 解析标签字符串为列表
      if (widget.diary!.tags != null && widget.diary!.tags!.isNotEmpty) {
        _selectedTags = widget.diary!.tags!.split(' ').where((t) => t.isNotEmpty).toList();
      }
    }
  }

  /// 监听文本变化
  void _onTextChanged() {
    final String currentText = _contentController.text;
    if (currentText != _lastText) {
      _undoHistory.add(currentText);
      _redoHistory.clear(); // 新的变更会清除重做历史
      if (_undoHistory.length > 100) {
        // 限制历史记录长度
        _undoHistory.removeAt(0);
      }
      _lastText = currentText;

      // 强制更新状态，确保撤销/重做按钮状态正确
      setState(() {});
    }
  }

  /// 撤销操作
  void _undo() {
    if (_undoHistory.length > 1) {
      final currentText = _undoHistory.removeLast();
      _redoHistory.add(currentText);
      final previousText = _undoHistory.last;
      _lastText = previousText; // 更新最后的文本，避免触发监听器
      _contentController.text = previousText;

      // 恢复光标位置
      final int cursorPosition = _contentController.selection.baseOffset;
      _contentController.selection = TextSelection.collapsed(
        offset: cursorPosition < previousText.length ? cursorPosition : previousText.length,
      );

      // 强制更新状态
      setState(() {});
    }
  }

  /// 重做操作
  void _redo() {
    if (_redoHistory.isNotEmpty) {
      final redoText = _redoHistory.removeLast();
      _undoHistory.add(redoText);
      _lastText = redoText; // 更新最后的文本，避免触发监听器
      _contentController.text = redoText;

      // 恢复光标位置
      final int cursorPosition = _contentController.selection.baseOffset;
      _contentController.selection = TextSelection.collapsed(
        offset: cursorPosition < redoText.length ? cursorPosition : redoText.length,
      );

      // 强制更新状态
      setState(() {});
    }
  }

  /// 选择图片
  Future<void> _pickImage() async {
    final ImagePicker picker = ImagePicker();
    final List<XFile> images = await picker.pickMultiImage();

    if (images.isNotEmpty) {
      setState(() {
        _selectedImages.addAll(images);
      });
    }
  }

  /// 移除选中的图片
  void _removeImage(int index) {
    setState(() {
      _selectedImages.removeAt(index);
    });
  }

  /// 移除已有的图片
  void _removeExistingImage(int index) {
    setState(() {
      _existingImages.removeAt(index);
    });
  }

  /// 保存日记
  Future<void> _saveDiary() async {
    if (_contentController.text.isEmpty && _selectedImages.isEmpty && _existingImages.isEmpty) {
      UIUtils.showError('日记内容不能为空');
      return;
    }

    final controller = ref.read(diaryControllerProvider.notifier);

    // 转换标签列表为字符串
    final String tagsString = _selectedTags.join(' ');

    // 合并图片
    final allImages = [..._existingImages, ..._selectedImages.map((e) => e.path)];
    final String? imagesString = allImages.isNotEmpty ? allImages.join(',') : null;

    if (widget.diary != null) {
      // 更新日记
      await controller.updateDiary(widget.diary!.id, _contentController.text, images: imagesString, tags: tagsString);
    } else {
      // 创建日记
      await controller.createDiary(
        _contentController.text,
        images: imagesString,
        date: widget.initialDate ?? DateTime.now(),
        tags: tagsString,
      );
    }

    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  /// 显示标签选择器
  void _showTagSelector() {
    showDialog(
      context: context,
      builder: (context) => DiaryTagSelectorDialog(
        initialSelectedTags: _selectedTags,
        onTagsSelected: (tags) {
          setState(() {
            _selectedTags = tags;
          });
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.diary != null ? '编辑日记' : '写日记'),
        actions: [IconButton(icon: const Icon(FeatherIcons.check), onPressed: _saveDiary)],
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: Dimensions.paddingL,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 日期显示
                  Text(
                    DiaryUtils.formatDate(widget.diary?.createdAt ?? widget.initialDate ?? DateTime.now()),
                    style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
                  ),
                  const SizedBox(height: Dimensions.spacingS),

                  // 标签显示
                  if (_selectedTags.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: Dimensions.spacingS),
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: _selectedTags
                            .map(
                              (tag) => Chip(
                                label: Text(tag),
                                onDeleted: () {
                                  setState(() {
                                    _selectedTags.remove(tag);
                                  });
                                },
                                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                visualDensity: VisualDensity.compact,
                              ),
                            )
                            .toList(),
                      ),
                    ),

                  // 内容输入框
                  TextField(
                    controller: _contentController,
                    maxLines: null,
                    decoration: const InputDecoration(hintText: '记录当下的想法...', border: InputBorder.none),
                    style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
                  ),
                  const SizedBox(height: Dimensions.spacingL),

                  // 图片预览区域
                  if (_existingImages.isNotEmpty || _selectedImages.isNotEmpty)
                    SizedBox(
                      height: 100,
                      child: ListView(
                        scrollDirection: Axis.horizontal,
                        children: [
                          ..._existingImages.asMap().entries.map((entry) {
                            // 解析相对路径为绝对路径
                            final resolvedPath = FileService.i.resolveLocalMediaPath(entry.value);
                            return Padding(
                              padding: const EdgeInsets.only(right: 8.0),
                              child: Stack(
                                children: [
                                  Image.file(File(resolvedPath), width: 100, height: 100, fit: BoxFit.cover),
                                  Positioned(
                                    top: 0,
                                    right: 0,
                                    child: GestureDetector(
                                      onTap: () => _removeExistingImage(entry.key),
                                      child: Container(
                                        padding: const EdgeInsets.all(2),
                                        decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
                                        child: const Icon(Icons.close, size: 16, color: Colors.white),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }),
                          ..._selectedImages.asMap().entries.map((entry) {
                            return Padding(
                              padding: const EdgeInsets.only(right: 8.0),
                              child: Stack(
                                children: [
                                  Image.file(File(entry.value.path), width: 100, height: 100, fit: BoxFit.cover),
                                  Positioned(
                                    top: 0,
                                    right: 0,
                                    child: GestureDetector(
                                      onTap: () => _removeImage(entry.key),
                                      child: Container(
                                        padding: const EdgeInsets.all(2),
                                        decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
                                        child: const Icon(Icons.close, size: 16, color: Colors.white),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),

          // 底部工具栏
          Container(
            padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
            decoration: BoxDecoration(
              color: AppColors.getSurface(context),
              border: Border(top: BorderSide(color: AppColors.getOutline(context))),
            ),
            child: Row(
              children: [
                IconButton(icon: const Icon(FeatherIcons.image), onPressed: _pickImage, tooltip: '添加图片'),
                IconButton(icon: const Icon(FeatherIcons.tag), onPressed: _showTagSelector, tooltip: '添加标签'),
              ],
            ),
          ),

          // Markdown 工具栏
          MarkdownToolbar(
            controller: _contentController,
            undoCallback: _undoHistory.length > 1 ? _undo : null,
            redoCallback: _redoHistory.isNotEmpty ? _redo : null,
            canUndo: _undoHistory.length > 1,
            canRedo: _redoHistory.isNotEmpty,
          ),
        ],
      ),
    );
  }
}
