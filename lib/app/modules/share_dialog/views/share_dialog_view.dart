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
          children: [_buildFormCard(context), Dimensions.verticalSpacerM],
        ),
      ),
    );
  }

  // 构建底部按钮区域
  Widget _buildBottomButton(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          border: Border(top: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4), width: 0.6)),
        ),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton(onPressed: () => Get.back(), child: const Text('取消')),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton(
                onPressed: () => controller.onSaveButtonPressed(),
                child: Obx(() => Text(controller.isUpdate.value ? '保存修改' : '保存')),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() => Text(controller.isUpdate.value ? '更新文章' : '保存链接', style: MyFontStyle.appBarTitleStyle)),
      automaticallyImplyLeading: !controller.isUpdate.value,
      // 底部已提供保存按钮，这里不再显示右上角保存
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
        maxLines: 3,
        decoration: _inputDecoration(context, '输入或修改标题'),
        inputFormatters: [LengthLimitingTextInputFormatter(120)],
      ),
    );
  }

  // 重新抓取并AI分析开关（更新模式，美化版）
  Widget _buildRefreshInlineSwitch(BuildContext context) {
    return Obx(
      () => SwitchListTile.adaptive(
        dense: true,
        contentPadding: EdgeInsets.zero,
        visualDensity: VisualDensity.compact,
        title: Text('重新抓取并AI分析', style: MyFontStyle.bodySmall),
        value: controller.refreshAndAnalyze.value,
        onChanged: (v) => controller.refreshAndAnalyze.value = v,
      ),
    );
  }

  // 统一表单卡片
  Widget _buildFormCard(BuildContext context) {
    final children = <Widget>[
      _buildArticleURL(context),
      const SizedBox(height: 8),
      if (controller.isUpdate.value) ...[_buildRefreshInlineSwitch(context), const SizedBox(height: 8)],
      _buildTitleSection(context),
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
  // 保存与取消按钮已移动到底部操作区
}
