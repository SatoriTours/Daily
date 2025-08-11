import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/inputs/comment_field.dart';
import 'package:flutter/services.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(resizeToAvoidBottomInset: true, appBar: _buildAppBar(context), body: _buildBody(context));
  }

  // 构建主体内容
  Widget _buildBody(BuildContext context) {
    return SafeArea(child: Column(children: [_buildScrollableContent(context), _buildBottomButton(context)]));
  }

  // 构建可滚动内容区域
  Widget _buildScrollableContent(BuildContext context) {
    return Expanded(
      child: SingleChildScrollView(
        padding: Dimensions.paddingPage,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (controller.isUpdate.value) _buildRefreshInlineSwitch(context),
            _buildFormCard(context),
            Dimensions.verticalSpacerM,
          ],
        ),
      ),
    );
  }

  // 构建底部按钮区域
  Widget _buildBottomButton(BuildContext context) {
    return const SizedBox.shrink();
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() => Text(controller.isUpdate.value ? '更新文章' : '保存链接', style: MyFontStyle.appBarTitleStyle)),
      automaticallyImplyLeading: !controller.isUpdate.value,
      actions: [
        IconButton(
          tooltip: '保存',
          icon: const Icon(Icons.check_rounded),
          onPressed: () => controller.onSaveButtonPressed(),
        ),
      ],
    );
  }

  // 构建文章URL区域
  Widget _buildArticleURL(BuildContext context) {
    return _buildFieldWrapper(
      context,
      icon: Icons.link_rounded,
      label: '链接',
      child: SelectableText(controller.shareURL.value, style: MyFontStyle.bodySmall, maxLines: 2),
    );
  }

  // 构建备注信息区域
  Widget _buildCommentSection(BuildContext context) {
    return _buildFieldWrapper(
      context,
      icon: Icons.comment_outlined,
      label: '备注',
      child: CommentField(
        controller: controller.commentController,
        hintText: '添加备注信息（可选）',
        minLines: 4,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
    );
  }

  Widget _buildTitleSection(BuildContext context) {
    return _buildFieldWrapper(
      context,
      icon: Icons.title,
      label: '标题',
      child: TextField(
        controller: controller.titleController,
        maxLines: 2,
        decoration: _inputDecoration(context, '输入或修改标题'),
        inputFormatters: [LengthLimitingTextInputFormatter(120)],
      ),
    );
  }

  Widget _buildTagsSection(BuildContext context) {
    return _buildFieldWrapper(
      context,
      icon: Icons.tag,
      label: '标签',
      child: Obx(() {
        final tags = List<String>.from(controller.tagList); // 使用副本避免构建中修改
        return _TagChipsEditor(tags: tags, onAdd: controller.addTag, onRemove: controller.removeTag);
      }),
    );
  }

  // 顶部紧凑开关（更新模式）
  Widget _buildRefreshInlineSwitch(BuildContext context) {
    return Obx(
      () => Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: Row(
          children: [
            Switch(value: controller.refreshAndAnalyze.value, onChanged: (v) => controller.refreshAndAnalyze.value = v),
            const SizedBox(width: 4),
            Expanded(
              child: GestureDetector(
                onTap: () => controller.refreshAndAnalyze.value = !controller.refreshAndAnalyze.value,
                child: Text('重新抓取并AI分析', style: MyFontStyle.bodyMedium),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 统一表单卡片
  Widget _buildFormCard(BuildContext context) {
    final children = <Widget>[
      _buildArticleURL(context),
      Dimensions.verticalSpacerM,
      _buildTitleSection(context),
      Dimensions.verticalSpacerM,
      _buildTagsSection(context),
      Dimensions.verticalSpacerM,
      _buildCommentSection(context),
    ];
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
      decoration: BoxDecoration(
        color: isDark
            ? theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.18)
            : theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4), width: 0.6),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );
  }

  // 包裹字段标签与控件的统一行
  Widget _buildFieldWrapper(
    BuildContext context, {
    required IconData icon,
    required String label,
    required Widget child,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 8),
            Text(label, style: MyFontStyle.titleSmall.copyWith(fontSize: 15, fontWeight: FontWeight.w600)),
          ],
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }

  InputDecoration _inputDecoration(BuildContext context, String hint) {
    final theme = Theme.of(context);
    return InputDecoration(
      isDense: false,
      hintText: hint,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS), borderSide: BorderSide.none),
      filled: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      fillColor: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.1),
    );
  }

  // 构建保存按钮
  // 底部按钮已移除，使用 AppBar 保存
}

// 标签 Chips 编辑器
class _TagChipsEditor extends StatefulWidget {
  final List<String> tags;
  final ValueChanged<String> onAdd;
  final ValueChanged<String> onRemove;
  const _TagChipsEditor({required this.tags, required this.onAdd, required this.onRemove});

  @override
  State<_TagChipsEditor> createState() => _TagChipsEditorState();
}

class _TagChipsEditorState extends State<_TagChipsEditor> {
  final TextEditingController _input = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  @override
  void dispose() {
    _input.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _commitInput() {
    final text = _input.text.trim();
    if (text.isEmpty) return;
    widget.onAdd(text);
    _input.clear();
    _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: [
        for (final tag in widget.tags)
          Chip(
            label: Text(tag),
            backgroundColor: theme.colorScheme.primary.withValues(alpha: 0.10),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
            onDeleted: () => widget.onRemove(tag),
            deleteIcon: const Icon(Icons.close, size: 16),
            labelStyle: MyFontStyle.bodyMedium.copyWith(fontSize: 14, fontWeight: FontWeight.w500),
            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        SizedBox(
          width: 180,
          child: TextField(
            controller: _input,
            focusNode: _focusNode,
            decoration: InputDecoration(
              isDense: false,
              hintText: widget.tags.isEmpty ? '添加标签' : '继续输入…',
              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
              filled: true,
              fillColor: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.10),
            ),
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _commitInput(),
            onChanged: (v) {
              if (v.endsWith(',') || v.endsWith('，') || v.endsWith(' ')) {
                _input.text = v.substring(0, v.length - 1);
                _commitInput();
              }
            },
          ),
        ),
      ],
    );
  }
}
