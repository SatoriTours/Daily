import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/pages/share_dialog/controllers/share_dialog_controller.dart';
import 'package:flutter/services.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final isUpdate = controller.isUpdate.value;
      return Scaffold(
        resizeToAvoidBottomInset: true,
        appBar: _buildAppBar(context, isUpdate),
        body: _buildBody(context, isUpdate),
      );
    });
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context, bool isUpdate) {
    return AppBar(
      title: Text(isUpdate ? 'ui.updateArticle'.t : 'ui.saveLink'.t, style: AppTypography.appBarTitle),
      automaticallyImplyLeading: !isUpdate,
      elevation: 0,
    );
  }

  // 构建主体内容
  Widget _buildBody(BuildContext context, bool isUpdate) {
    return SafeArea(
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: Dimensions.paddingPage,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLinkSection(context, isUpdate),
                  Dimensions.verticalSpacerL,
                  _buildTitleSection(context, isUpdate),
                  Dimensions.verticalSpacerL,
                  _buildCommentSection(context),
                ],
              ),
            ),
          ),
          _buildBottomButton(context, isUpdate),
        ],
      ),
    );
  }

  // 构建底部按钮区域
  Widget _buildBottomButton(BuildContext context, bool isUpdate) {
    return Container(
      padding: Dimensions.paddingBottomForm,
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: AppBorders.getTopBorder(AppColors.getOutline(context), opacity: Opacities.medium),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: Opacities.ultraLow),
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
                style: StyleGuide.getOutlinedButtonStyle(context),
                onPressed: () => Get.back(),
                child: Text('ui.cancel'.t, style: AppTypography.buttonText),
              ),
            ),
            Dimensions.horizontalSpacerM,
            Expanded(
              flex: 2,
              child: FilledButton(
                style: StyleGuide.getPrimaryButtonStyle(context),
                onPressed: () => controller.onSaveButtonPressed(),
                child: Text(
                  isUpdate ? 'ui.saveChanges'.t : 'ui.save'.t,
                  style: AppTypography.buttonText.copyWith(fontWeight: FontWeight.w600),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 构建链接区域（简洁版）
  Widget _buildLinkSection(BuildContext context, bool isUpdate) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.link_rounded,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(alpha: Opacities.highOpaque),
            ),
            Dimensions.horizontalSpacerS,
            Text('ui.link'.t, style: AppTypography.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
            const Spacer(),
            if (isUpdate) ...[
              Obx(
                () => Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.refresh_rounded,
                      size: Dimensions.iconSizeXs - 2,
                      color: controller.refreshAndAnalyze.value
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.mediumHigh),
                    ),
                    Dimensions.horizontalSpacerXs,
                    Text(
                      'ui.aiAnalysis'.t,
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: controller.refreshAndAnalyze.value
                            ? theme.colorScheme.primary
                            : theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.highOpaque),
                      ),
                    ),
                    Dimensions.horizontalSpacerS,
                    Transform.scale(
                      scale: 0.8,
                      child: Switch.adaptive(
                        value: controller.refreshAndAnalyze.value,
                        onChanged: (v) => controller.refreshAndAnalyze.value = v,
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
        Dimensions.verticalSpacerS,
        Container(
          width: double.infinity,
          padding: Dimensions.paddingInput,
          decoration: BoxDecoration(
            color: AppColors.getSurfaceContainerHighest(context).withValues(alpha: Opacities.high),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            border: Border.all(
              color: AppColors.getOutlineVariant(context).withValues(alpha: Opacities.mediumHigh),
              width: Dimensions.borderWidthXs,
            ),
          ),
          child: SelectableText(
            controller.shareURL.value,
            style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context), height: 1.4),
            maxLines: 2,
          ),
        ),
      ],
    );
  }

  // 构建标题区域
  Widget _buildTitleSection(BuildContext context, bool isUpdate) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.title_rounded,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(alpha: Opacities.highOpaque),
            ),
            Dimensions.horizontalSpacerS,
            Text('ui.title'.t, style: AppTypography.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
          ],
        ),
        Dimensions.verticalSpacerS,
        TextField(
          controller: controller.titleController,
          maxLines: null,
          minLines: 2,
          maxLength: InputConfig.maxLength,
          style: AppTypography.bodyMedium.copyWith(fontSize: 15, height: 1.5),
          decoration: InputDecoration(
            hintText: 'ui.inputOrModifyArticleTitle'.t,
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.half)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: Opacities.high),
                width: 0.5,
              ),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: Opacities.medium),
                width: 0.5,
              ),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(color: theme.colorScheme.primary.withValues(alpha: Opacities.medium), width: 1),
            ),
            filled: true,
            fillColor: theme.colorScheme.surface,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: Dimensions.spacingM - 2,
              vertical: Dimensions.spacingM - 2,
            ),
            counterStyle: AppTypography.bodySmall.copyWith(
              fontSize: 11,
              color: theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.medium),
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
            Icon(
              Icons.comment_outlined,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(alpha: Opacities.highOpaque),
            ),
            Dimensions.horizontalSpacerS,
            Text('ui.comment'.t, style: AppTypography.titleSmall.copyWith(fontSize: 14, fontWeight: FontWeight.w600)),
            Dimensions.horizontalSpacerXs,
            Text(
              'ui.optional'.t,
              style: AppTypography.bodySmall.copyWith(
                fontSize: 12,
                color: theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.medium),
              ),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
        TextField(
          controller: controller.commentController,
          maxLines: null,
          minLines: 4,
          style: AppTypography.bodyMedium.copyWith(fontSize: 14, height: 1.6),
          decoration: InputDecoration(
            hintText: 'ui.addCommentInfo'.t,
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.half)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: Opacities.high),
                width: 0.5,
              ),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(alpha: Opacities.medium),
                width: 0.5,
              ),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(color: theme.colorScheme.primary.withValues(alpha: Opacities.medium), width: 1),
            ),
            filled: true,
            fillColor: theme.colorScheme.surface,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: Dimensions.spacingM - 2,
              vertical: Dimensions.spacingM - 2,
            ),
          ),
        ),
      ],
    );
  }
}
