import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

import '../../controllers/diary_controller.dart';

/// 日记标签选择对话框 - 用于添加标签到内容中
class DiaryTagSelectorDialog extends StatefulWidget {
  final DiaryController controller;
  final TextEditingController contentController;

  const DiaryTagSelectorDialog({super.key, required this.controller, required this.contentController});

  @override
  State<DiaryTagSelectorDialog> createState() => _DiaryTagSelectorDialogState();
}

class _DiaryTagSelectorDialogState extends State<DiaryTagSelectorDialog> {
  final TextEditingController _newTagController = TextEditingController();
  final Set<String> _selectedTags = {};

  @override
  void initState() {
    super.initState();
    // 从当前内容中提取已有的标签并预选
    _extractExistingTags();
    // 刷新标签列表，确保能看到其他日记新添加的标签
    widget.controller.loadDiaries();
  }

  @override
  void dispose() {
    _newTagController.dispose();
    super.dispose();
  }

  /// 从当前内容中提取已有的标签
  void _extractExistingTags() {
    final String content = widget.contentController.text;
    final RegExp tagRegex = RegExp(r'#([a-zA-Z0-9\u4e00-\u9fa5]+)');
    final Iterable<RegExpMatch> matches = tagRegex.allMatches(content);

    for (final match in matches) {
      final tag = match.group(1);
      if (tag != null && tag.isNotEmpty) {
        _selectedTags.add(tag);
      }
    }
  }

  /// 插入标签到内容中
  void _insertTagsToContent() {
    if (_selectedTags.isEmpty) {
      Navigator.pop(context);
      return;
    }

    final String currentText = widget.contentController.text;

    // 构建要插入的标签文本
    final String tagsText = _selectedTags.map((tag) => '#$tag').join(' ');

    String newText;
    if (currentText.isEmpty) {
      // 如果内容为空，直接添加标签
      newText = tagsText;
    } else {
      // 获取最后一行内容
      final List<String> lines = currentText.split('\n');
      final String lastLine = lines.isNotEmpty ? lines.last : '';

      // 检查最后一行是否只包含标签（只有 #标签名 和空格）
      final bool lastLineIsOnlyTags =
          lastLine.trim().isNotEmpty && RegExp(r'^(#[a-zA-Z0-9\u4e00-\u9fa5]+\s*)+$').hasMatch(lastLine.trim());

      if (lastLineIsOnlyTags) {
        // 如果最后一行只有标签，就合并到最后一行
        if (lines.length > 1) {
          newText = '${lines.sublist(0, lines.length - 1).join('\n')}\n$tagsText';
        } else {
          newText = tagsText;
        }
      } else if (currentText.endsWith('\n')) {
        // 如果已经有换行，直接添加
        newText = '$currentText$tagsText';
      } else {
        // 否则先换行再添加标签
        newText = '$currentText\n$tagsText';
      }
    }

    widget.contentController.text = newText;
    // 将光标移到末尾
    widget.contentController.selection = TextSelection.collapsed(offset: newText.length);

    Navigator.pop(context);
  }

  /// 添加新标签
  void _addNewTag() {
    final String newTag = _newTagController.text.trim();
    if (newTag.isNotEmpty) {
      setState(() {
        _selectedTags.add(newTag);
      });
      _newTagController.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.7),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 标题栏
            _buildHeader(context),

            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),

            // 新标签输入框
            _buildNewTagInput(context),

            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),

            // 现有标签列表
            if (widget.controller.tags.isNotEmpty) _buildExistingTagsList(context),

            // 操作按钮
            _buildActionButtons(context),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          Icon(FeatherIcons.tag, size: 20, color: DiaryStyle.primaryTextColor(context)),
          const SizedBox(width: 8),
          Text(
            'button.add_tag'.t,
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: DiaryStyle.primaryTextColor(context)),
          ),
          const Spacer(),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyle.primaryTextColor(context)),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildNewTagInput(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _newTagController,
              decoration: InputDecoration(
                hintText: 'hint.enter_new_tag'.t,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                isDense: true,
              ),
              style: TextStyle(fontSize: 14, color: DiaryStyle.primaryTextColor(context)),
              onSubmitted: (_) => _addNewTag(),
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: Icon(FeatherIcons.plus, color: DiaryStyle.accentColor(context)),
            onPressed: _addNewTag,
            tooltip: 'button.add'.t,
          ),
        ],
      ),
    );
  }

  Widget _buildExistingTagsList(BuildContext context) {
    return Flexible(
      child: SingleChildScrollView(
        child: Container(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('ui.existing_tags'.t, style: TextStyle(fontSize: 12, color: DiaryStyle.secondaryTextColor(context))),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: widget.controller.tags.map((tag) {
                  final isSelected = _selectedTags.contains(tag);
                  return FilterChip(
                    label: Text('#$tag'),
                    selected: isSelected,
                    onSelected: (selected) {
                      setState(() {
                        if (selected) {
                          _selectedTags.add(tag);
                        } else {
                          _selectedTags.remove(tag);
                        }
                      });
                    },
                    selectedColor: DiaryStyle.accentColor(context).withAlpha(51),
                    checkmarkColor: DiaryStyle.accentColor(context),
                    labelStyle: TextStyle(
                      fontSize: 13,
                      color: isSelected ? DiaryStyle.accentColor(context) : DiaryStyle.primaryTextColor(context),
                    ),
                    side: BorderSide(
                      color: isSelected ? DiaryStyle.accentColor(context) : DiaryStyle.dividerColor(context),
                      width: 1,
                    ),
                    showCheckmark: true,
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActionButtons(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed: () => Navigator.pop(context),
              style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 12)),
              child: Text('button.cancel'.t),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: ElevatedButton(
              onPressed: _selectedTags.isEmpty ? null : _insertTagsToContent,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12),
                backgroundColor: DiaryStyle.accentColor(context),
              ),
              child: Text('button.insert'.t, style: const TextStyle(color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }
}
