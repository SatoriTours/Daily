import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:image_picker/image_picker.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:flutter/services.dart'; // 导入用于访问剪贴板
import 'dart:io';

import '../../controllers/diary_controller.dart';
import '../../utils/diary_utils.dart';
import 'markdown_toolbar.dart';
import 'image_preview.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// 日记扩展编辑器组件
class DiaryEditor extends StatefulWidget {
  final DiaryController controller;
  final DiaryModel? diary; // 添加可选的日记对象，用于编辑模式

  const DiaryEditor({super.key, required this.controller, this.diary});

  @override
  State<DiaryEditor> createState() => _DiaryEditorState();
}

class _DiaryEditorState extends State<DiaryEditor> {
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
    _lastText = widget.controller.contentController.text;
    _undoHistory.add(_lastText);

    // 监听文本变化以支持撤销和重做
    widget.controller.contentController.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    widget.controller.contentController.removeListener(_onTextChanged);
    super.dispose();
  }

  /// 监听文本变化
  void _onTextChanged() {
    final String currentText = widget.controller.contentController.text;
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
      widget.controller.contentController.text = previousText;

      // 恢复光标位置
      final int cursorPosition = widget.controller.contentController.selection.baseOffset;
      widget.controller.contentController.selection = TextSelection.collapsed(
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
      widget.controller.contentController.text = redoText;

      // 恢复光标位置
      final int cursorPosition = widget.controller.contentController.selection.baseOffset;
      widget.controller.contentController.selection = TextSelection.collapsed(
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
      final int cursorPosition = widget.controller.contentController.selection.baseOffset;
      if (cursorPosition >= 0) {
        // 插入转换后的文本
        final String currentText = widget.controller.contentController.text;
        final String newText =
            currentText.substring(0, cursorPosition) + convertedText + currentText.substring(cursorPosition);

        widget.controller.contentController.text = newText;
        widget.controller.contentController.selection = TextSelection.collapsed(
          offset: cursorPosition + convertedText.length,
        );
      }
    }
  }

  /// 初始化编辑器
  void _initializeEditor() {
    // 如果是编辑模式，需要填充已有内容
    if (widget.diary != null) {
      // 填充日记内容
      widget.controller.contentController.text = widget.diary!.content;

      // 处理已有图片
      if (widget.diary!.images != null && widget.diary!.images!.isNotEmpty) {
        _existingImages = widget.diary!.images!.split(',');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.9,
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom + 8, left: 16, right: 16, top: 4),
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
              onTap: () {
                // 在文本字段内点击时，可以记录当前位置供工具栏使用
              },
            ),
          ),

          // 统一显示所有图片预览（已有 + 新选择）
          if (_existingImages.isNotEmpty || _selectedImages.isNotEmpty)
            ImagePreview(
              images: [..._existingImages, ..._selectedImages.map((e) => e.path)],
              onDelete: (index) {
                setState(() {
                  // 先删除已有图片，再删除新选择的图片
                  if (index < _existingImages.length) {
                    _existingImages.removeAt(index);
                  } else {
                    _selectedImages.removeAt(index - _existingImages.length);
                  }
                });
              },
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
          // Markdown 工具栏 - 现在把撤销、重做和粘贴按钮也放进去
          Expanded(
            child: MarkdownToolbar(
              controller: widget.controller.contentController,
              onSave: null, // 不使用工具栏的保存功能
              undoCallback: _undoHistory.length > 1 ? _undo : null,
              redoCallback: _redoHistory.isNotEmpty ? _redo : null,
              pasteCallback: _handlePaste,
              canUndo: _undoHistory.length > 1,
              canRedo: _redoHistory.isNotEmpty,
            ),
          ),

          // 图片添加按钮（整合了相册选择和拍照功能）
          _buildToolbarButton(context, FeatherIcons.image, 'button.add_image', _showImageOptions, isAccent: false),

          // AI魔法按钮
          _buildToolbarButton(context, FeatherIcons.zap, 'button.ai_magic', _showAiMagicOptions, isAccent: false),

          // 保存按钮
          _buildToolbarButton(context, FeatherIcons.check, 'button.save', _saveDiary, isAccent: true),
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
      message: tooltip.t,
      child: Container(
        width: 36,
        height: 36,
        margin: const EdgeInsets.symmetric(horizontal: 2),
        child: IconButton(
          onPressed: onPressed,
          icon: Icon(
            icon,
            size: 16,
            color: onPressed == null
                ? DiaryStyle.primaryTextColor(context).withAlpha(77) // 禁用状态
                : isAccent
                ? DiaryStyle.accentColor(context)
                : DiaryStyle.primaryTextColor(context),
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
      hintText: 'hint.search_content'.t,
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

  /// 拍照
  void _takePhoto() async {
    final picker = ImagePicker();
    final XFile? photo = await picker.pickImage(source: ImageSource.camera);

    if (photo != null) {
      setState(() {
        _selectedImages.add(photo);
      });
    }
  }

  /// 保存日记
  void _saveDiary() async {
    if (widget.controller.contentController.text.trim().isNotEmpty) {
      // 先隐藏键盘
      FocusManager.instance.primaryFocus?.unfocus();

      // 保存图片并获取路径列表
      final List<String> newImagePaths = await _saveImages();

      // 合并已有图片和新图片
      final List<String> allImagePaths = [..._existingImages, ...newImagePaths];

      // 从内容中提取标签
      final String tags = DiaryUtils.extractTags(widget.controller.contentController.text);

      if (widget.diary != null) {
        // 更新模式 - 更新已有日记
        final updatedDiary = DiaryModel.create(
          id: widget.diary!.id,
          content: widget.controller.contentController.text,
          tags: tags,
          mood: widget.diary!.mood,
          images: allImagePaths.isEmpty ? null : allImagePaths.join(','),
          createdAt: widget.diary!.createdAt,
        );

        // 调用更新方法
        await widget.controller.updateDiary(updatedDiary);
      } else {
        // 创建模式 - 创建新日记
        await widget.controller.createDiary(
          widget.controller.contentController.text,
          tags: tags,
          images: allImagePaths.isEmpty ? null : allImagePaths.join(','),
        );
      }

      // 关闭底部弹窗前确保键盘已收起
      await Future.delayed(const Duration(milliseconds: 100));

      // 关闭底部弹窗
      if (mounted) {
        Navigator.pop(context);
      }

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

  /// 显示AI魔法选项菜单
  void _showAiMagicOptions() {
    // 获取当前选中的文本
    final TextEditingController controller = widget.controller.contentController;
    final TextSelection selection = controller.selection;

    // 如果没有选中文本，显示提示
    if (selection.isCollapsed) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('message.select_text_first'.t)));
      return;
    }

    final String selectedText = controller.text.substring(selection.start, selection.end);

    // 显示AI魔法选项菜单
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(FeatherIcons.grid, color: DiaryStyle.accentColor(context)),
                title: Text('menu.convert_to_table'.t),
                onTap: () {
                  _convertToTable(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.list, color: DiaryStyle.accentColor(context)),
                title: Text('menu.convert_to_list'.t),
                onTap: () {
                  _convertToList(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.terminal, color: DiaryStyle.accentColor(context)),
                title: Text('menu.format_as_code_block'.t),
                onTap: () {
                  _convertToCodeBlock(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.star, color: DiaryStyle.accentColor(context)),
                title: Text('menu.add_emphasis'.t),
                onTap: () {
                  _addEmphasis(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  /// 将选中文本转换为Markdown表格
  void _convertToTable(String text, TextSelection selection) {
    final lines = text.split('\n');
    final StringBuffer tableBuffer = StringBuffer();

    if (lines.isEmpty) return;

    // 检测分隔符（逗号或制表符）
    String separator = ',';
    if (lines[0].contains('\t')) {
      separator = '\t';
    }

    // 处理第一行作为表头
    final List<String> headers = lines[0].split(separator);
    tableBuffer.writeln('| ${headers.join(' | ')} |');

    // 添加分隔行
    tableBuffer.writeln('| ${headers.map((_) => '---').join(' | ')} |');

    // 添加数据行
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].trim().isEmpty) continue;
      final cells = lines[i].split(separator);
      // 确保单元格数量与表头一致
      while (cells.length < headers.length) {
        cells.add('');
      }
      tableBuffer.writeln('| ${cells.join(' | ')} |');
    }

    _replaceSelectedText(tableBuffer.toString(), selection);
  }

  /// 将选中文本转换为Markdown列表
  void _convertToList(String text, TextSelection selection) {
    final lines = text.split('\n').where((line) => line.trim().isNotEmpty).toList();
    final StringBuffer listBuffer = StringBuffer();

    for (final line in lines) {
      listBuffer.writeln('- $line');
    }

    _replaceSelectedText(listBuffer.toString(), selection);
  }

  /// 将选中文本格式化为代码块
  void _convertToCodeBlock(String text, TextSelection selection) {
    final formattedText = '```\n$text\n```';
    _replaceSelectedText(formattedText, selection);
  }

  /// 为选中文本添加强调格式
  void _addEmphasis(String text, TextSelection selection) {
    final formattedText = '**$text**';
    _replaceSelectedText(formattedText, selection);
  }

  /// 替换选中的文本
  void _replaceSelectedText(String newText, TextSelection selection) {
    final TextEditingController controller = widget.controller.contentController;

    controller.value = controller.value.copyWith(
      text: controller.text.replaceRange(selection.start, selection.end, newText),
      selection: TextSelection.collapsed(offset: selection.start + newText.length),
    );
  }

  /// 显示图片选项菜单
  void _showImageOptions() {
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(FeatherIcons.image, color: DiaryStyle.accentColor(context)),
                title: Text('menu.select_from_gallery'.t),
                onTap: () {
                  Navigator.pop(context);
                  _selectImages();
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.camera, color: DiaryStyle.accentColor(context)),
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
}
