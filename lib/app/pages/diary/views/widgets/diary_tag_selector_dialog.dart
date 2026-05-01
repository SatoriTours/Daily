import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/diary/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/base/colors.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 日记标签选择器 - 优雅的底部弹出菜单
class DiaryTagSelectorDialog extends ConsumerStatefulWidget {
  final Function(String) onTagSelected;

  const DiaryTagSelectorDialog({super.key, required this.onTagSelected});

  @override
  ConsumerState<DiaryTagSelectorDialog> createState() =>
      _DiaryTagSelectorDialogState();
}

class _DiaryTagSelectorDialogState
    extends ConsumerState<DiaryTagSelectorDialog> {
  final TextEditingController _newTagController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  bool _hasInput = false;

  @override
  void initState() {
    super.initState();
    _newTagController.addListener(_onInputChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _newTagController.removeListener(_onInputChanged);
    _newTagController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onInputChanged() {
    final hasInput = _newTagController.text.trim().isNotEmpty;
    if (_hasInput != hasInput) {
      setState(() {
        _hasInput = hasInput;
      });
    }
  }

  void _selectTag(String tag) {
    widget.onTagSelected(tag);
    AppNavigation.back();
  }

  void _addNewTag() {
    final String newTag = _newTagController.text.trim();
    if (newTag.isNotEmpty) {
      final cleanTag = newTag.startsWith('#') ? newTag.substring(1) : newTag;
      if (cleanTag.isNotEmpty) {
        widget.onTagSelected(cleanTag);
        AppNavigation.back();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final existingTags = ref.watch(diaryControllerProvider).tags;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + Dimensions.spacingM,
        left: Dimensions.spacingM,
        right: Dimensions.spacingM,
        top: Dimensions.spacingS,
      ),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 顶部拖动指示器
          Center(
            child: Container(
              width: 36,
              height: 4,
              margin: const EdgeInsets.only(bottom: Dimensions.spacingM),
              decoration: BoxDecoration(
                color: isDark ? Colors.grey[700] : Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          // 输入区域
          _buildInputField(context),

          // 已有标签
          if (existingTags.isNotEmpty) ...[
            const SizedBox(height: Dimensions.spacingL),
            _buildTagsSection(context, existingTags),
          ],

          // 底部安全区域
          SizedBox(
            height: MediaQuery.of(context).padding.bottom > 0
                ? 0
                : Dimensions.spacingS,
          ),
        ],
      ),
    );
  }

  Widget _buildInputField(BuildContext context) {
    final primaryColor = AppColors.getPrimary(context);

    return TextField(
      controller: _newTagController,
      focusNode: _focusNode,
      style: TextStyle(fontSize: 16, color: AppColors.getOnSurface(context)),
      decoration: InputDecoration(
        hintText: 'hint.enter_new_tag'.t,
        hintStyle: TextStyle(
          color: AppColors.getOnSurfaceVariant(context),
          fontSize: 15,
        ),
        filled: true,
        fillColor: AppColors.getSurfaceContainer(context),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        // 前缀图标 - #
        prefixIcon: Container(
          width: 40,
          alignment: Alignment.center,
          child: Text(
            '#',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: primaryColor,
            ),
          ),
        ),
        // 后缀按钮 - 添加
        suffixIcon: _hasInput
            ? Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: _addNewTag,
                  borderRadius: BorderRadius.circular(20),
                  child: Container(
                    margin: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: primaryColor.withValues(alpha: 0.1),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      FeatherIcons.arrowUp,
                      size: 20,
                      color: primaryColor,
                    ),
                  ),
                ),
              )
            : null,
      ),
      onSubmitted: (_) => _addNewTag(),
    );
  }

  Widget _buildTagsSection(BuildContext context, List<String> tags) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 200),
      child: SingleChildScrollView(
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          alignment: WrapAlignment.start,
          children: tags.map((tag) => _buildTagChip(context, tag)).toList(),
        ),
      ),
    );
  }

  Widget _buildTagChip(BuildContext context, String tag) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => _selectTag(tag),
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: AppColors.getSurfaceContainer(context),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: isDark
                  ? Colors.white10
                  : Colors.black.withValues(alpha: 0.05),
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '#',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: AppColors.getPrimary(context).withValues(alpha: 0.7),
                ),
              ),
              const SizedBox(width: 4),
              Text(
                tag,
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.getOnSurface(context),
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
