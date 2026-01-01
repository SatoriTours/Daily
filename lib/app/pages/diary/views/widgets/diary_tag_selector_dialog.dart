import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/diary/providers/diary_controller_provider.dart';
import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 日记标签选择器 - 简洁的底部弹出菜单样式
class DiaryTagSelectorDialog extends ConsumerStatefulWidget {
  /// 选择标签后的回调，返回选中的单个标签
  final Function(String) onTagSelected;

  const DiaryTagSelectorDialog({super.key, required this.onTagSelected});

  @override
  ConsumerState<DiaryTagSelectorDialog> createState() => _DiaryTagSelectorDialogState();
}

class _DiaryTagSelectorDialogState extends ConsumerState<DiaryTagSelectorDialog> {
  final TextEditingController _newTagController = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    // 延迟请求焦点，确保 widget 已经构建完成
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _newTagController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  /// 选择已有标签
  void _selectTag(String tag) {
    widget.onTagSelected(tag);
    Navigator.pop(context);
  }

  /// 添加新标签
  void _addNewTag() {
    final String newTag = _newTagController.text.trim();
    if (newTag.isNotEmpty) {
      // 移除开头的 # 符号（如果用户输入了的话）
      final cleanTag = newTag.startsWith('#') ? newTag.substring(1) : newTag;
      if (cleanTag.isNotEmpty) {
        widget.onTagSelected(cleanTag);
        Navigator.pop(context);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final existingTags = ref.watch(diaryControllerProvider).tags;

    return Container(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom + 16, left: 16, right: 16, top: 16),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 标题
          Row(
            children: [
              Icon(FeatherIcons.tag, size: 18, color: DiaryStyles.getAccentColor(context)),
              const SizedBox(width: 8),
              Text(
                'button.add_tag'.t,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: DiaryStyles.getPrimaryTextColor(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // 新标签输入框
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _newTagController,
                  focusNode: _focusNode,
                  decoration: InputDecoration(
                    hintText: 'hint.enter_new_tag'.t,
                    prefixText: '# ',
                    prefixStyle: TextStyle(color: DiaryStyles.getAccentColor(context), fontWeight: FontWeight.w500),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    isDense: true,
                  ),
                  style: TextStyle(fontSize: 14, color: DiaryStyles.getPrimaryTextColor(context)),
                  onSubmitted: (_) => _addNewTag(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                icon: Icon(FeatherIcons.plus, color: DiaryStyles.getAccentColor(context)),
                onPressed: _addNewTag,
                tooltip: 'button.add'.t,
              ),
            ],
          ),

          // 已有标签列表
          if (existingTags.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              'ui.existing_tags'.t,
              style: TextStyle(fontSize: 12, color: DiaryStyles.getSecondaryTextColor(context)),
            ),
            const SizedBox(height: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 150),
              child: SingleChildScrollView(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: existingTags.map((tag) {
                    return ActionChip(
                      label: Text('#$tag'),
                      onPressed: () => _selectTag(tag),
                      backgroundColor: DiaryStyles.getCardBackgroundColor(context),
                      labelStyle: TextStyle(fontSize: 13, color: DiaryStyles.getPrimaryTextColor(context)),
                      side: BorderSide(color: DiaryStyles.getDividerColor(context), width: 1),
                    );
                  }).toList(),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
