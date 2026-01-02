import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/components/index.dart';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:daily_satori/app/pages/share_dialog/providers/share_dialog_controller_provider.dart';

import 'package:flutter/services.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends ConsumerStatefulWidget {
  const ShareDialogView({super.key});

  @override
  ConsumerState<ShareDialogView> createState() => _ShareDialogViewState();
}

class _ShareDialogViewState extends ConsumerState<ShareDialogView> {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // 从 go_router 获取路由参数
    final state = GoRouterState.of(context);
    final args = state.extra as Map<String, dynamic>?;
    if (args != null) {
      // 使用 Future.microtask 避免在构建过程中修改状态
      Future.microtask(() => ref.read(shareDialogControllerProvider.notifier).initialize(args));
    }
  }

  @override
  Widget build(BuildContext context) {
    final controllerState = ref.watch(shareDialogControllerProvider);
    final isUpdate = controllerState.isUpdate;

    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: _buildAppBar(context, isUpdate),
      body: _buildBody(context, ref, isUpdate),
    );
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context, bool isUpdate) {
    return SAppBar(
      title: Text(isUpdate ? 'ui.updateArticle'.t : 'ui.saveLink'.t, style: const TextStyle(color: Colors.white)),
      centerTitle: true,
      // 更新模式下不显示返回按钮
      leading: isUpdate ? const SizedBox.shrink() : null,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
    );
  }

  // 构建主体内容
  Widget _buildBody(BuildContext context, WidgetRef ref, bool isUpdate) {
    return SafeArea(
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: Dimensions.paddingPage,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLinkSection(context, ref, isUpdate),
                  Dimensions.verticalSpacerL,
                  _buildTitleSection(context, ref, isUpdate),
                  Dimensions.verticalSpacerL,
                  _buildCommentSection(context, ref),
                ],
              ),
            ),
          ),
          _buildBottomButton(context, ref, isUpdate),
        ],
      ),
    );
  }

  // 构建底部按钮区域
  Widget _buildBottomButton(BuildContext context, WidgetRef ref, bool isUpdate) {
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
                onPressed: () => AppNavigation.back(),
                child: Text('ui.cancel'.t, style: AppTypography.buttonText),
              ),
            ),
            Dimensions.horizontalSpacerM,
            Expanded(
              flex: 2,
              child: FilledButton(
                style: StyleGuide.getPrimaryButtonStyle(context),
                onPressed: () => ref.read(shareDialogControllerProvider.notifier).onSaveButtonPressed(context),
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
  Widget _buildLinkSection(BuildContext context, WidgetRef ref, bool isUpdate) {
    final theme = Theme.of(context);
    final controllerState = ref.watch(shareDialogControllerProvider);

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
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.refresh_rounded,
                    size: Dimensions.iconSizeXs - 2,
                    color: controllerState.refreshAndAnalyze
                        ? theme.colorScheme.primary
                        : theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.mediumHigh),
                  ),
                  Dimensions.horizontalSpacerXs,
                  Text(
                    'ui.aiAnalysis'.t,
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: controllerState.refreshAndAnalyze
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurfaceVariant.withValues(alpha: Opacities.highOpaque),
                    ),
                  ),
                  Dimensions.horizontalSpacerS,
                  Transform.scale(
                    scale: 0.8,
                    child: Switch.adaptive(
                      value: controllerState.refreshAndAnalyze,
                      onChanged: (v) => ref.read(shareDialogControllerProvider.notifier).toggleRefreshAndAnalyze(v),
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                  ),
                ],
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
            controllerState.shareURL,
            style: AppTypography.bodySmall.copyWith(color: AppColors.getOnSurfaceVariant(context), height: 1.4),
            maxLines: 2,
          ),
        ),
      ],
    );
  }

  // 构建标题区域
  Widget _buildTitleSection(BuildContext context, WidgetRef ref, bool isUpdate) {
    final theme = Theme.of(context);
    final titleController = ref.read(shareDialogControllerProvider.notifier).titleController;

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
          controller: titleController,
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
  Widget _buildCommentSection(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final commentController = ref.read(shareDialogControllerProvider.notifier).commentController;

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
          controller: commentController,
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
