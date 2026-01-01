import 'dart:io';

import 'package:daily_satori/app/pages/diary/utils/diary_utils.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/diary_tag_selector_dialog.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/markdown_toolbar.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/image_preview.dart';
import 'package:daily_satori/app/pages/diary/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter/services.dart';

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
    } else {
      // 新建模式，添加默认标题格式
      _contentController.text = '# ';
      _contentController.selection = const TextSelection.collapsed(offset: 2);
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

  /// 处理粘贴操作
  Future<void> _handlePaste() async {
    final ClipboardData? clipboardData = await Clipboard.getData(Clipboard.kTextPlain);
    if (clipboardData != null && clipboardData.text != null) {
      final String pasteText = clipboardData.text!;
      // 自动将链接转换为Markdown链接格式
      final String convertedText = DiaryUtils.autoConvertLinks(pasteText);

      // 获取当前光标位置
      final int cursorPosition = _contentController.selection.baseOffset;
      if (cursorPosition >= 0) {
        // 插入转换后的文本
        final String currentText = _contentController.text;
        final String newText =
            currentText.substring(0, cursorPosition) + convertedText + currentText.substring(cursorPosition);

        _contentController.text = newText;
        _contentController.selection = TextSelection.collapsed(offset: cursorPosition + convertedText.length);
      }
    }
  }

  /// 选择图片
  Future<void> _selectImages() async {
    final ImagePicker picker = ImagePicker();
    final List<XFile> images = await picker.pickMultiImage();

    if (images.isNotEmpty) {
      setState(() {
        _selectedImages.addAll(images);
      });
    }
  }

  /// 拍照
  Future<void> _takePhoto() async {
    final ImagePicker picker = ImagePicker();
    final XFile? photo = await picker.pickImage(source: ImageSource.camera);

    if (photo != null) {
      setState(() {
        _selectedImages.add(photo);
      });
    }
  }

  /// 显示图片选项
  void _showImageOptions() {
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(FeatherIcons.image, color: DiaryStyles.getAccentColor(context)),
                title: Text('menu.select_from_gallery'.t),
                onTap: () {
                  Navigator.pop(context);
                  _selectImages();
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.camera, color: DiaryStyles.getAccentColor(context)),
                title: Text('menu.take_photo'.t),
                onTap: () {
                  Navigator.pop(context);
                  _takePhoto();
                },
              ),
            ],
          ),
        );
      },
    );
  }

  /// 移除图片
  void _removeImage(int index) {
    setState(() {
      // 先删除已有图片，再删除新选择的图片
      if (index < _existingImages.length) {
        _existingImages.removeAt(index);
      } else {
        _selectedImages.removeAt(index - _existingImages.length);
      }
    });
  }

  /// 保存日记
  Future<void> _saveDiary() async {
    if (_contentController.text.isEmpty && _selectedImages.isEmpty && _existingImages.isEmpty) {
      UIUtils.showError('日记内容不能为空');
      return;
    }

    // 先隐藏键盘
    FocusManager.instance.primaryFocus?.unfocus();

    final controller = ref.read(diaryControllerProvider.notifier);

    // 保存图片并获取路径列表
    final List<String> newImagePaths = await _saveImages();

    // 合并已有图片和新图片
    final List<String> allImagePaths = [..._existingImages, ...newImagePaths];

    // 从内容中提取标签
    final String tags = DiaryUtils.extractTags(_contentController.text);

    final String? imagesString = allImagePaths.isNotEmpty ? allImagePaths.join(',') : null;

    if (widget.diary != null) {
      // 更新日记
      await controller.updateDiary(widget.diary!.id, _contentController.text, images: imagesString, tags: tags);
    } else {
      // 创建日记
      await controller.createDiary(
        _contentController.text,
        images: imagesString,
        date: widget.initialDate ?? DateTime.now(),
        tags: tags,
      );
    }

    // 关闭底部弹窗前确保键盘已收起
    await Future.delayed(const Duration(milliseconds: 100));

    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  /// 保存图片并返回路径
  Future<List<String>> _saveImages() async {
    if (_selectedImages.isEmpty) return [];

    List<String> savedPaths = [];
    final String dirPath = FileService.i.diaryImagesBasePath;

    for (int i = 0; i < _selectedImages.length; i++) {
      final XFile image = _selectedImages[i];
      final String fileName = 'diary_img_${DateTime.now().millisecondsSinceEpoch}_$i.jpg';
      final String filePath = '$dirPath/$fileName';

      // 复制图片到应用目录
      final File savedImage = File(FileService.i.toAbsolutePath(filePath));
      await savedImage.writeAsBytes(await image.readAsBytes());

      savedPaths.add(filePath);
    }

    return savedPaths;
  }

  /// 显示标签选择器
  void _showTagSelector() {
    // 刷新标签列表
    ref.read(diaryControllerProvider.notifier).refreshTags();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DiaryTagSelectorDialog(
        onTagSelected: (tag) {
          _insertTagToContent(tag);
        },
      ),
    );
  }

  /// 将标签插入到内容末尾
  void _insertTagToContent(String tag) {
    final String content = _contentController.text;
    final String tagToInsert = '#$tag';

    // 检查内容最后一行是否已经有标签（以 # 开头的行）
    final lines = content.split('\n');
    String newContent;

    if (lines.isNotEmpty) {
      final lastLine = lines.last.trim();

      if (lastLine.startsWith('#') && !lastLine.startsWith('# ') && !lastLine.startsWith('## ')) {
        // 最后一行是标签行（#xxx 格式，不是 Markdown 标题）
        // 直接在后面添加，用空格分隔
        newContent = '$content $tagToInsert';
      } else if (lastLine.isEmpty) {
        // 最后一行是空行，直接添加标签
        newContent = '$content$tagToInsert';
      } else {
        // 最后一行是正文，空一行后添加标签
        newContent = '$content\n\n$tagToInsert';
      }
    } else {
      newContent = tagToInsert;
    }

    _contentController.text = newContent;
    // 将光标移到末尾
    _contentController.selection = TextSelection.collapsed(offset: newContent.length);

    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.9,
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + Dimensions.spacingS,
        left: Dimensions.spacingM,
        right: Dimensions.spacingM,
        top: MediaQuery.of(context).padding.top + Dimensions.spacingS, // 安全区域
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 编辑区域
          Expanded(
            child: TextField(
              controller: _contentController,
              maxLines: null,
              expands: true,
              autofocus: true,
              textAlignVertical: TextAlignVertical.top,
              decoration: _getInputDecoration(context),
              style: TextStyle(fontSize: 16, height: 1.5, color: DiaryStyles.getPrimaryTextColor(context)),
            ),
          ),

          // 统一显示所有图片预览（已有 + 新选择）
          if (_existingImages.isNotEmpty || _selectedImages.isNotEmpty)
            ImagePreview(images: [..._existingImages, ..._selectedImages.map((e) => e.path)], onDelete: _removeImage),

          // 工具栏和操作按钮
          _buildToolbar(context),
        ],
      ),
    );
  }

  /// 获取输入框装饰
  InputDecoration _getInputDecoration(BuildContext context) {
    return const InputDecoration(
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      errorBorder: InputBorder.none,
      disabledBorder: InputBorder.none,
      contentPadding: EdgeInsets.symmetric(vertical: Dimensions.spacingS + 4, horizontal: Dimensions.spacingM),
    );
  }

  /// 构建工具栏
  Widget _buildToolbar(BuildContext context) {
    return Container(
      height: 48,
      margin: const EdgeInsets.only(top: Dimensions.spacingS),
      child: Row(
        children: [
          // Markdown 工具栏
          Expanded(
            child: MarkdownToolbar(
              controller: _contentController,
              onSave: null, // 不使用工具栏的保存功能
              undoCallback: _undoHistory.length > 1 ? _undo : null,
              redoCallback: _redoHistory.isNotEmpty ? _redo : null,
              pasteCallback: _handlePaste,
              canUndo: _undoHistory.length > 1,
              canRedo: _redoHistory.isNotEmpty,
            ),
          ),

          // 图片添加按钮
          _buildToolbarButton(context, FeatherIcons.image, 'button.add_image'.t, _showImageOptions),

          // 标签添加按钮
          _buildToolbarButton(context, FeatherIcons.tag, 'button.add_tag'.t, _showTagSelector),

          // 保存按钮
          _buildToolbarButton(context, FeatherIcons.check, 'button.save'.t, _saveDiary, isAccent: true),
        ],
      ),
    );
  }

  /// 构建工具栏按钮
  Widget _buildToolbarButton(
    BuildContext context,
    IconData icon,
    String tooltip,
    VoidCallback? onPressed, {
    bool isAccent = false,
  }) {
    return Tooltip(
      message: tooltip,
      child: Container(
        width: 36,
        height: 36,
        margin: const EdgeInsets.symmetric(horizontal: Dimensions.spacingXs / 2),
        child: IconButton(
          onPressed: onPressed,
          icon: Icon(
            icon,
            size: Dimensions.iconSizeXs,
            color: onPressed == null
                ? DiaryStyles.getPrimaryTextColor(context).withAlpha(77) // 禁用状态
                : isAccent
                ? DiaryStyles.getAccentColor(context)
                : DiaryStyles.getPrimaryTextColor(context),
          ),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }
}
