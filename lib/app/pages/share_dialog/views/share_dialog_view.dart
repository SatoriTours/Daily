import 'package:go_router/go_router.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/share_dialog/providers/share_dialog_controller_provider.dart';

class ShareDialogView extends ConsumerStatefulWidget {
  const ShareDialogView({super.key});

  @override
  ConsumerState<ShareDialogView> createState() => _ShareDialogViewState();
}

class _ShareDialogViewState extends ConsumerState<ShareDialogView> {
  late final TextEditingController _titleController;
  late final TextEditingController _commentController;

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController();
    _commentController = TextEditingController();

    _titleController.addListener(() {
      ref
          .read(shareDialogControllerProvider.notifier)
          .onTitleChanged(_titleController.text);
    });
  }

  @override
  void dispose() {
    _titleController.dispose();
    _commentController.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final state = GoRouterState.of(context);
    final args = state.extra as Map<String, dynamic>?;
    if (args != null) {
      Future.microtask(
        () => ref.read(shareDialogControllerProvider.notifier).initialize(args),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final controllerState = ref.watch(shareDialogControllerProvider);
    final isUpdate = controllerState.isUpdate;

    if (isUpdate && _titleController.text.isEmpty) {
      _titleController.text = controllerState.articleTitle;
    }

    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: _buildAppBar(isUpdate),
      body: _buildBody(isUpdate),
      bottomNavigationBar: _buildBottomButton(isUpdate),
    );
  }

  PreferredSizeWidget _buildAppBar(bool isUpdate) {
    return SAppBar(
      title: Text(
        isUpdate ? 'ui.updateArticle'.t : 'ui.saveLink'.t,
        style: const TextStyle(color: Colors.white),
      ),
      centerTitle: true,
      leading: isUpdate ? const SizedBox.shrink() : null,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
    );
  }

  Widget _buildBody(bool isUpdate) {
    final controllerState = ref.watch(shareDialogControllerProvider);
    final theme = Theme.of(context);

    return SafeArea(
      child: SingleChildScrollView(
        padding: Dimensions.paddingPage,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildLinkSection(theme, controllerState, isUpdate),
            const SizedBox(height: Dimensions.spacingL),
            _buildTitleSection(theme),
            const SizedBox(height: Dimensions.spacingL),
            _buildTagsSection(theme),
            const SizedBox(height: Dimensions.spacingL),
            _buildCommentSection(theme),
          ],
        ),
      ),
    );
  }

  Widget _buildLinkSection(
    ThemeData theme,
    ShareDialogControllerState state,
    bool isUpdate,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.link_rounded,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(
                alpha: Opacities.highOpaque,
              ),
            ),
            const SizedBox(width: Dimensions.spacingS),
            Text(
              'ui.link'.t,
              style: AppTypography.titleSmall.copyWith(
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            const Spacer(),
            if (isUpdate) _buildAiAnalysisToggle(theme, state),
          ],
        ),
        const SizedBox(height: Dimensions.spacingS),
        Container(
          width: double.infinity,
          padding: Dimensions.paddingInput,
          decoration: BoxDecoration(
            color: AppColors.getSurfaceContainerHighest(
              context,
            ).withValues(alpha: Opacities.high),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            border: Border.all(
              color: AppColors.getOutlineVariant(
                context,
              ).withValues(alpha: Opacities.mediumHigh),
              width: Dimensions.borderWidthXs,
            ),
          ),
          child: SelectableText(
            state.shareURL,
            style: AppTypography.bodySmall.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
              height: 1.4,
            ),
            maxLines: 2,
          ),
        ),
      ],
    );
  }

  Widget _buildAiAnalysisToggle(
    ThemeData theme,
    ShareDialogControllerState state,
  ) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.refresh_rounded,
          size: Dimensions.iconSizeXs - 2,
          color: state.refreshAndAnalyze
              ? theme.colorScheme.primary
              : theme.colorScheme.onSurfaceVariant.withValues(
                  alpha: Opacities.mediumHigh,
                ),
        ),
        const SizedBox(width: Dimensions.spacingXs),
        Text(
          'ui.aiAnalysis'.t,
          style: AppTypography.bodySmall.copyWith(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: state.refreshAndAnalyze
                ? theme.colorScheme.primary
                : theme.colorScheme.onSurfaceVariant.withValues(
                    alpha: Opacities.highOpaque,
                  ),
          ),
        ),
        const SizedBox(width: Dimensions.spacingS),
        Transform.scale(
          scale: 0.8,
          child: Switch.adaptive(
            value: state.refreshAndAnalyze,
            onChanged: ref
                .read(shareDialogControllerProvider.notifier)
                .toggleRefreshAndAnalyze,
            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
        ),
      ],
    );
  }

  Widget _buildTitleSection(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.title_rounded,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(
                alpha: Opacities.highOpaque,
              ),
            ),
            const SizedBox(width: Dimensions.spacingS),
            Text(
              'ui.title'.t,
              style: AppTypography.titleSmall.copyWith(
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: Dimensions.spacingS),
        TextField(
          controller: _titleController,
          maxLines: null,
          minLines: 2,
          style: AppTypography.bodyMedium.copyWith(fontSize: 15, height: 1.5),
          decoration: InputDecoration(
            hintText: 'ui.inputOrModifyArticleTitle'.t,
            hintStyle: TextStyle(
              color: theme.colorScheme.onSurfaceVariant.withValues(
                alpha: Opacities.half,
              ),
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(
                  alpha: Opacities.high,
                ),
                width: 0.5,
              ),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(
                  alpha: Opacities.medium,
                ),
                width: 0.5,
              ),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.primary.withValues(
                  alpha: Opacities.medium,
                ),
                width: 1,
              ),
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

  Widget _buildTagsSection(ThemeData theme) {
    final state = ref.watch(shareDialogControllerProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.tag_outlined,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(
                alpha: Opacities.highOpaque,
              ),
            ),
            const SizedBox(width: Dimensions.spacingS),
            Text(
              'ui.tags'.t,
              style: AppTypography.titleSmall.copyWith(
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(width: Dimensions.spacingXs),
            Text(
              'ui.optional'.t,
              style: AppTypography.bodySmall.copyWith(
                fontSize: 12,
                color: theme.colorScheme.onSurfaceVariant.withValues(
                  alpha: Opacities.medium,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: Dimensions.spacingS),
        _buildTagChips(state.tagList, theme),
      ],
    );
  }

  Widget _buildTagChips(List<String> tags, ThemeData theme) {
    if (tags.isEmpty) {
      return Text(
        'ui.optional'.t,
        style: AppTypography.bodySmall.copyWith(
          color: theme.colorScheme.onSurfaceVariant.withValues(
            alpha: Opacities.medium,
          ),
        ),
      );
    }

    return Wrap(
      spacing: Dimensions.spacingS,
      runSpacing: Dimensions.spacingS,
      children: tags.map((tag) {
        return Chip(
          label: Text(tag),
          deleteIcon: const Icon(Icons.close, size: 16),
          onDeleted: () =>
              ref.read(shareDialogControllerProvider.notifier).removeTag(tag),
          materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        );
      }).toList(),
    );
  }

  Widget _buildCommentSection(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.comment_outlined,
              size: Dimensions.iconSizeS,
              color: theme.colorScheme.primary.withValues(
                alpha: Opacities.highOpaque,
              ),
            ),
            const SizedBox(width: Dimensions.spacingS),
            Text(
              'ui.comment'.t,
              style: AppTypography.titleSmall.copyWith(
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(width: Dimensions.spacingXs),
            Text(
              'ui.optional'.t,
              style: AppTypography.bodySmall.copyWith(
                fontSize: 12,
                color: theme.colorScheme.onSurfaceVariant.withValues(
                  alpha: Opacities.medium,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: Dimensions.spacingS),
        TextField(
          controller: _commentController,
          maxLines: null,
          minLines: 4,
          style: AppTypography.bodyMedium.copyWith(fontSize: 14, height: 1.6),
          decoration: InputDecoration(
            hintText: 'ui.addCommentInfo'.t,
            hintStyle: TextStyle(
              color: theme.colorScheme.onSurfaceVariant.withValues(
                alpha: Opacities.half,
              ),
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(
                  alpha: Opacities.high,
                ),
                width: 0.5,
              ),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.outlineVariant.withValues(
                  alpha: Opacities.medium,
                ),
                width: 0.5,
              ),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
              borderSide: BorderSide(
                color: theme.colorScheme.primary.withValues(
                  alpha: Opacities.medium,
                ),
                width: 1,
              ),
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

  Widget _buildBottomButton(bool isUpdate) {
    return Container(
      padding: Dimensions.paddingBottomForm,
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: AppBorders.getTopBorder(
          AppColors.getOutline(context),
          opacity: Opacities.medium,
        ),
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
                onPressed: AppNavigation.back,
                child: Text('ui.cancel'.t, style: AppTypography.buttonText),
              ),
            ),
            const SizedBox(width: Dimensions.spacingM),
            Expanded(
              flex: 2,
              child: FilledButton(
                style: StyleGuide.getPrimaryButtonStyle(context),
                onPressed: _onSave,
                child: Text(
                  isUpdate ? 'ui.saveChanges'.t : 'ui.save'.t,
                  style: AppTypography.buttonText.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _onSave() {
    ref
        .read(shareDialogControllerProvider.notifier)
        .onSaveButtonPressed(
          context,
          title: _titleController.text,
          comment: _commentController.text,
        );
  }
}
