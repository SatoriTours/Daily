import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';
import 'package:daily_satori/app/pages/diary/utils/diary_utils.dart';

/// Markdown工具栏组件
class MarkdownToolbar extends StatelessWidget {
  final TextEditingController controller;
  final Function()? onSave;
  final String saveLabel;

  // 添加撤销、重做回调函数
  final VoidCallback? undoCallback;
  final VoidCallback? redoCallback;
  final VoidCallback? pasteCallback;
  final bool canUndo;
  final bool canRedo;

  const MarkdownToolbar({
    super.key,
    required this.controller,
    this.onSave,
    this.saveLabel = '保存',
    this.undoCallback,
    this.redoCallback,
    this.pasteCallback,
    this.canUndo = false,
    this.canRedo = false,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: Row(
        children: [
          // 工具栏 - 带滚动功能
          Expanded(
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: EdgeInsets.zero,
              children: [
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.heading,
                  '标题',
                  () => _formatSelectedText('# '),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.bold,
                  '粗体',
                  () => _formatSelectedText('**'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.italic,
                  '斜体',
                  () => _formatSelectedText('*'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.listUl,
                  '无序列表',
                  () => _formatSelectedText('- '),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.listOl,
                  '有序列表',
                  () => _formatSelectedText('1. '),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.link,
                  '链接',
                  () => _formatAsLink(),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.quoteLeft,
                  '引用',
                  () => _formatSelectedText('> '),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.code,
                  '代码',
                  () => _formatSelectedText('`'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.minus,
                  '分割线',
                  () => DiaryUtils.insertMarkdown(controller, '\n---\n'),
                ),

                // 添加粘贴按钮
                if (pasteCallback != null)
                  _buildToolbarButton(
                    context,
                    Icons.content_paste,
                    '粘贴',
                    pasteCallback,
                  ),

                // 添加撤销按钮
                _buildToolbarButton(
                  context,
                  Icons.undo,
                  '撤销',
                  undoCallback,
                  isActive: canUndo,
                ),

                // 添加重做按钮
                _buildToolbarButton(
                  context,
                  Icons.redo,
                  '重做',
                  redoCallback,
                  isActive: canRedo,
                ),
              ],
            ),
          ),

          // 保存/更新按钮
          if (onSave != null) _buildSaveButton(context, saveLabel, onSave!),
        ],
      ),
    );
  }

  /// 格式化选中的文本
  void _formatSelectedText(String markdownSymbol) {
    final selection = controller.selection;
    final hasSelection = selection.baseOffset != selection.extentOffset;

    if (hasSelection) {
      // 如果有选中文本，直接应用格式
      final start = selection.baseOffset < selection.extentOffset
          ? selection.baseOffset
          : selection.extentOffset;
      final end = selection.baseOffset < selection.extentOffset
          ? selection.extentOffset
          : selection.baseOffset;
      final selectedText = controller.text.substring(start, end);

      String formattedText;
      // 判断是否为段落标记(#, >, -, 1. 等)
      if (['# ', '> ', '- ', '1. '].contains(markdownSymbol)) {
        // 对每一行都应用段落标记
        final lines = selectedText.split('\n');
        formattedText = lines.map((line) => '$markdownSymbol$line').join('\n');
      } else {
        // 对整体应用格式(加粗、斜体、代码等)
        formattedText = '$markdownSymbol$selectedText$markdownSymbol';
      }

      // 更新文本
      final newText = controller.text.replaceRange(start, end, formattedText);
      controller.text = newText;
      controller.selection = TextSelection.collapsed(
        offset: start + formattedText.length,
      );
    } else {
      // 如果没有选中文本，根据标记类型插入
      if (['# ', '> ', '- ', '1. '].contains(markdownSymbol)) {
        // 段落标记直接插入
        DiaryUtils.insertMarkdown(controller, markdownSymbol);
      } else {
        // 格式标记插入一对，并将光标放在中间
        final position = controller.selection.baseOffset;
        if (position >= 0) {
          final pair = '$markdownSymbol$markdownSymbol';
          final newText = controller.text.replaceRange(
            position,
            position,
            pair,
          );
          controller.text = newText;
          controller.selection = TextSelection.collapsed(
            offset: position + markdownSymbol.length,
          );
        } else {
          // 如果光标位置无效，直接在末尾添加
          controller.text += '$markdownSymbol$markdownSymbol';
          controller.selection = TextSelection.collapsed(
            offset: controller.text.length - markdownSymbol.length,
          );
        }
      }
    }
  }

  /// 将选中文本格式化为链接
  void _formatAsLink() {
    final selection = controller.selection;
    final hasSelection = selection.baseOffset != selection.extentOffset;

    if (hasSelection) {
      // 如果有选中文本
      final start = selection.baseOffset < selection.extentOffset
          ? selection.baseOffset
          : selection.extentOffset;
      final end = selection.baseOffset < selection.extentOffset
          ? selection.extentOffset
          : selection.baseOffset;
      final selectedText = controller.text.substring(start, end);

      String formattedText;
      // 检查选中的文本是否是URL
      if (DiaryUtils.isUrl(selectedText)) {
        formattedText = '[链接文本]($selectedText)';
      } else {
        formattedText = '[$selectedText](https://example.com)';
      }

      // 更新文本
      final newText = controller.text.replaceRange(start, end, formattedText);
      controller.text = newText;

      // 如果是URL，将光标定位到"链接文本"部分以便用户修改
      if (DiaryUtils.isUrl(selectedText)) {
        controller.selection = TextSelection(
          baseOffset: start + 1,
          extentOffset: start + 5,
        );
      } else {
        controller.selection = TextSelection.collapsed(
          offset: start + formattedText.length,
        );
      }
    } else {
      // 如果没有选中文本，直接插入一个链接模板
      DiaryUtils.insertMarkdown(controller, '[链接文本](https://example.com)');
    }
  }

  // 构建工具栏图标按钮
  Widget _buildToolbarButton(
    BuildContext context,
    IconData icon,
    String tooltip,
    VoidCallback? onPressed, {
    bool isActive = true,
  }) {
    // 使用isActive参数决定颜色，而不仅仅依赖于onPressed是否为null
    final Color iconColor = (!isActive || onPressed == null)
        ? DiaryStyles.getPrimaryTextColor(context).withAlpha(77) // 禁用状态
        : DiaryStyles.getPrimaryTextColor(context);

    return Tooltip(
      message: tooltip,
      child: Container(
        width: 36,
        height: 36,
        margin: const EdgeInsets.symmetric(horizontal: 2),
        child: IconButton(
          icon: Icon(icon, size: 16, color: iconColor),
          onPressed: isActive ? onPressed : null, // 只有在活跃状态下才传递onPressed
          padding: const EdgeInsets.all(0),
          constraints: const BoxConstraints(),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }

  // 构建保存/更新按钮
  Widget _buildSaveButton(
    BuildContext context,
    String text,
    VoidCallback onPressed,
  ) {
    return Container(
      margin: const EdgeInsets.only(left: 8),
      child: IconButton(
        onPressed: onPressed,
        icon: Icon(
          FeatherIcons.check,
          size: 18,
          color: DiaryStyles.getAccentColor(context),
        ),
        tooltip: text,
        padding: const EdgeInsets.all(0),
        visualDensity: VisualDensity.compact,
      ),
    );
  }
}
