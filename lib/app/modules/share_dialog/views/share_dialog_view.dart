import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';
import 'package:flutter/services.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(resizeToAvoidBottomInset: true, appBar: _buildAppBar(context), body: _buildBody(context));
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() => Text(controller.isUpdate.value ? '更新文章' : '保存链接', style: MyFontStyle.appBarTitleStyle)),
      automaticallyImplyLeading: !controller.isUpdate.value,
      elevation: 0,
    );
  }

  // 构建主体内容
  Widget _buildBody(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLinkSection(context),
                  const SizedBox(height: 20),
                  if (controller.isUpdate.value) ...[_buildRefreshSwitch(context), const SizedBox(height: 20)],
                  _buildTitleSection(context),
                  const SizedBox(height: 20),
                  _buildCommentSection(context),
                ],
              ),
            ),
          ),
          _buildBottomButton(context),
        ],
      ),
    );
  }

  // 构建底部按钮区域
  Widget _buildBottomButton(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border(top: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.2), width: 0.5)),
        boxShadow: [
          BoxShadow(
            color: theme.colorScheme.shadow.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton(
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  side: BorderSide(color: theme.colorScheme.outline.withValues(alpha: 0.3), width: 0.8),
                  foregroundColor: theme.colorScheme.onSurface,
                ),
                onPressed: () => Get.back(),
                child: Text('取消', style: MyFontStyle.bodyMedium.copyWith(fontSize: 15, fontWeight: FontWeight.w500)),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: Obx(
                () => FilledButton(
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    backgroundColor: theme.colorScheme.primary,
                    foregroundColor: theme.colorScheme.onPrimary,
                    elevation: 1,
                  ),
                  onPressed: () => controller.onSaveButtonPressed(),
                  child: Text(
                    controller.isUpdate.value ? '保存修改' : '保存',
                    style: MyFontStyle.bodyMedium.copyWith(fontSize: 15, fontWeight: FontWeight.w600),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 构建链接区域（简洁版）
  Widget _buildLinkSection(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.link_rounded, size: 18, color: theme.colorScheme.primary.withValues(alpha: 0.8)),
            const SizedBox(width: 8),
            Text('链接', style: MyFontStyle.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
          ],
        ),
        const SizedBox(height: 10),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.25), width: 0.5),
          ),
          child: SelectableText(
            controller.shareURL.value,
            style: MyFontStyle.bodySmall.copyWith(fontSize: 13, color: theme.colorScheme.onSurfaceVariant, height: 1.4),
            maxLines: 2,
          ),
        ),
      ],
    );
  }

  // 构建重新抓取开关（优化版）
  Widget _buildRefreshSwitch(BuildContext context) {
    final theme = Theme.of(context);
    return Obx(
      () => Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: controller.refreshAndAnalyze.value
              ? theme.colorScheme.primaryContainer.withValues(alpha: 0.15)
              : theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.2),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: controller.refreshAndAnalyze.value
                ? theme.colorScheme.primary.withValues(alpha: 0.3)
                : theme.colorScheme.outlineVariant.withValues(alpha: 0.25),
            width: 0.5,
          ),
        ),
        child: Row(
          children: [
            Icon(
              Icons.refresh_rounded,
              size: 18,
              color: controller.refreshAndAnalyze.value
                  ? theme.colorScheme.primary
                  : theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.7),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '重新抓取并AI分析',
                style: MyFontStyle.bodyMedium.copyWith(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: controller.refreshAndAnalyze.value
                      ? theme.colorScheme.onSurface
                      : theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.85),
                ),
              ),
            ),
            Switch.adaptive(
              value: controller.refreshAndAnalyze.value,
              onChanged: (v) => controller.refreshAndAnalyze.value = v,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ],
        ),
      ),
    );
  }

  // 构建标题区域
  Widget _buildTitleSection(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.title_rounded, size: 18, color: theme.colorScheme.primary.withValues(alpha: 0.8)),
            const SizedBox(width: 8),
            Text('标题', style: MyFontStyle.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
          ],
        ),
        const SizedBox(height: 10),
        TextField(
          controller: controller.titleController,
          maxLines: null,
          minLines: 2,
          maxLength: 120,
          style: MyFontStyle.bodyMedium.copyWith(fontSize: 15, height: 1.5),
          decoration: InputDecoration(
            hintText: '输入或修改文章标题',
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3), width: 0.5),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.25), width: 0.5),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.6), width: 1),
            ),
            filled: true,
            fillColor: theme.colorScheme.surface,
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            counterStyle: MyFontStyle.bodySmall.copyWith(
              fontSize: 11,
              color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.6),
            ),
          ),
          inputFormatters: [LengthLimitingTextInputFormatter(120)],
        ),
      ],
    );
  }

  // 构建备注区域
  Widget _buildCommentSection(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.comment_outlined, size: 18, color: theme.colorScheme.primary.withValues(alpha: 0.8)),
            const SizedBox(width: 8),
            Text('备注', style: MyFontStyle.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
            const SizedBox(width: 6),
            Text(
              '（可选）',
              style: MyFontStyle.bodySmall.copyWith(
                fontSize: 12,
                color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.6),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        TextField(
          controller: controller.commentController,
          maxLines: null,
          minLines: 4,
          style: MyFontStyle.bodyMedium.copyWith(fontSize: 14, height: 1.6),
          decoration: InputDecoration(
            hintText: '添加备注信息，记录你的想法...',
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.5)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.3), width: 0.5),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.25), width: 0.5),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.6), width: 1),
            ),
            filled: true,
            fillColor: theme.colorScheme.surface,
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          ),
        ),
      ],
    );
  }
}
