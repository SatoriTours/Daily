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
  final DiaryModel? diary; // 添加可选的日记对象，用于编辑模式

  const DiaryEditor({super.key, required this.controller, this.diary});

  @override
  State<DiaryEditor> createState() => _DiaryEditorState();
}

class _DiaryEditorState extends State<DiaryEditor> {
  final List<XFile> _selectedImages = [];
  List<String> _existingImages = []; // 存储已有的图片路径

  @override
  void initState() {
    super.initState();
    _initializeEditor();
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

          // AI魔法按钮
          _buildToolbarButton(context, FeatherIcons.zap, 'AI魔法', _showAiMagicOptions, isAccent: false),

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
      final List<String> newImagePaths = await _saveImages();

      // 合并已有图片和新图片
      final List<String> allImagePaths = [..._existingImages, ...newImagePaths];

      // 从内容中提取标签
      final String tags = DiaryUtils.extractTags(widget.controller.contentController.text);

      if (widget.diary != null) {
        // 更新模式 - 更新已有日记
        final updatedDiary = DiaryModel(
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
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请先选择需要转换的文本')));
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
                title: const Text('转换为表格'),
                onTap: () {
                  _convertToTable(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.list, color: DiaryStyle.accentColor(context)),
                title: const Text('转换为列表'),
                onTap: () {
                  _convertToList(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.terminal, color: DiaryStyle.accentColor(context)),
                title: const Text('格式化为代码块'),
                onTap: () {
                  _convertToCodeBlock(selectedText, selection);
                  Navigator.pop(context);
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.star, color: DiaryStyle.accentColor(context)),
                title: const Text('添加强调格式'),
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
}
