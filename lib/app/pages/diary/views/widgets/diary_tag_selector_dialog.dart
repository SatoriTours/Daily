import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/diary_controller_provider.dart';
import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 日记标签选择对话框 - 用于添加标签到内容中
class DiaryTagSelectorDialog extends ConsumerStatefulWidget {
  final List<String> initialSelectedTags;
  final Function(List<String>) onTagsSelected;

  const DiaryTagSelectorDialog({super.key, required this.initialSelectedTags, required this.onTagsSelected});

  @override
  ConsumerState<DiaryTagSelectorDialog> createState() => _DiaryTagSelectorDialogState();
}

class _DiaryTagSelectorDialogState extends ConsumerState<DiaryTagSelectorDialog> {
  final TextEditingController _newTagController = TextEditingController();
  late List<String> _selectedTags;

  @override
  void initState() {
    super.initState();
    _selectedTags = List.from(widget.initialSelectedTags);
  }

  @override
  void dispose() {
    _newTagController.dispose();
    super.dispose();
  }

  /// 确认选择标签
  void _confirmSelection() {
    widget.onTagsSelected(_selectedTags);
    Navigator.pop(context);
  }

  /// 添加新标签
  void _addNewTag() {
    final String newTag = _newTagController.text.trim();
    if (newTag.isNotEmpty && !_selectedTags.contains(newTag)) {
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

            Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),

            // 新标签输入框
            _buildNewTagInput(context),

            Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),

            // 现有标签列表
            if (ref.watch(diaryControllerProvider).tags.isNotEmpty) _buildExistingTagsList(context),

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
          Icon(FeatherIcons.tag, size: 20, color: DiaryStyles.getPrimaryTextColor(context)),
          const SizedBox(width: 8),
          Text(
            'button.add_tag'.t,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: DiaryStyles.getPrimaryTextColor(context),
            ),
          ),
          const Spacer(),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyles.getPrimaryTextColor(context)),
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
    );
  }

  Widget _buildExistingTagsList(BuildContext context) {
    final tags = ref.watch(diaryControllerProvider).tags;
    return Flexible(
      child: SingleChildScrollView(
        child: Container(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'ui.existing_tags'.t,
                style: TextStyle(fontSize: 12, color: DiaryStyles.getSecondaryTextColor(context)),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: tags.map((tag) {
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
                    selectedColor: DiaryStyles.getAccentColor(context).withAlpha(51),
                    checkmarkColor: DiaryStyles.getAccentColor(context),
                    labelStyle: TextStyle(
                      fontSize: 13,
                      color: isSelected
                          ? DiaryStyles.getAccentColor(context)
                          : DiaryStyles.getPrimaryTextColor(context),
                    ),
                    side: BorderSide(
                      color: isSelected ? DiaryStyles.getAccentColor(context) : DiaryStyles.getDividerColor(context),
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
              onPressed: _confirmSelection,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 12),
                backgroundColor: DiaryStyles.getAccentColor(context),
              ),
              child: Text('button.confirm'.t, style: const TextStyle(color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }
}
