import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/article_detail/providers/article_detail_controller_provider.dart';

class ArticleDetailAppBar extends ConsumerWidget
    implements PreferredSizeWidget {
  final int articleId;
  final ArticleModel? article;

  const ArticleDetailAppBar({super.key, required this.articleId, this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(
      articleDetailControllerProvider(articleId),
    );
    final currentArticle = article ?? controllerState.articleModel;
    final textTheme = AppTheme.getTextTheme(context);
    final titleText = currentArticle != null
        ? StringUtils.getTopLevelDomain(
            Uri.parse(currentArticle.url ?? '').host,
          )
        : '';

    final isProcessing =
        currentArticle?.status == ArticleStatus.pending ||
        currentArticle?.status == ArticleStatus.webContentFetched;

    return AppBar(
      title: AnimatedDefaultTextStyle(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
        style:
            (isProcessing ? textTheme.titleMedium : textTheme.titleLarge)
                ?.copyWith(
                  color: Colors.white,
                  fontSize: isProcessing ? 16 : null,
                ) ??
            const TextStyle(),
        child: Text(titleText),
      ),
      centerTitle: true,
      actions: [
        _buildLoadingIndicator(isProcessing),
        _buildAppBarActions(context, ref, currentArticle),
      ],
    );
  }

  Widget _buildLoadingIndicator(bool isProcessing) {
    if (!isProcessing) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.only(right: Dimensions.spacingS),
      child: SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          color: Colors.white.withValues(alpha: 0.8),
        ),
      ),
    );
  }

  Widget _buildAppBarActions(
    BuildContext context,
    WidgetRef ref,
    ArticleModel? currentArticle,
  ) {
    final colorScheme = AppTheme.getColorScheme(context);

    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz, color: colorScheme.onSurface),
      offset: const Offset(0, 50),
      padding: EdgeInsets.zero,
      color: colorScheme.surface,
      elevation: 4,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      itemBuilder: (context) => _buildPopupMenuItems(context),
      onSelected: (value) =>
          _handleMenuSelection(context, ref, value, currentArticle),
    );
  }

  List<PopupMenuItem<int>> _buildPopupMenuItems(BuildContext context) {
    final menuItems = [
      (1, "刷新", Icons.refresh),
      (2, "删除", Icons.delete),
      (3, "复制链接", Icons.copy),
      (4, "浏览器打开", Icons.open_in_browser),
    ];

    return menuItems
        .map((item) => _buildPopupMenuItem(context, item.$1, item.$2, item.$3))
        .toList();
  }

  PopupMenuItem<int> _buildPopupMenuItem(
    BuildContext context,
    int value,
    String title,
    IconData icon,
  ) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return PopupMenuItem<int>(
      value: value,
      padding: Dimensions.paddingHorizontalM,
      child: Row(
        children: [
          Icon(icon, size: Dimensions.iconSizeS, color: colorScheme.onSurface),
          Dimensions.horizontalSpacerS,
          Text(
            title,
            style: textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }

  void _handleMenuSelection(
    BuildContext context,
    WidgetRef ref,
    int value,
    ArticleModel? currentArticle,
  ) {
    switch (value) {
      case 1:
        _onShareArticle(context, currentArticle);
        break;
      case 2:
        _showDeleteConfirmationDialog(context, ref, currentArticle);
        break;
      case 3:
        _onCopyURL(context, currentArticle);
        break;
      case 4:
        _onOpenInBrowser(context, currentArticle);
        break;
    }
  }

  void _onOpenInBrowser(BuildContext context, ArticleModel? currentArticle) {
    if (currentArticle?.url == null) return;
    launchUrl(Uri.parse(currentArticle!.url ?? ''));
  }

  void _onShareArticle(BuildContext context, ArticleModel? currentArticle) {
    if (currentArticle == null) return;
    AppNavigation.toNamed(
      Routes.shareDialog,
      arguments: {'articleID': currentArticle.id},
    );
  }

  void _onCopyURL(BuildContext context, ArticleModel? currentArticle) {
    if (currentArticle?.url == null) return;
    Clipboard.setData(ClipboardData(text: currentArticle!.url ?? ''));
    UIUtils.showSuccess('URL已复制到剪贴板');
  }

  void _showDeleteConfirmationDialog(
    BuildContext context,
    WidgetRef ref,
    ArticleModel? currentArticle,
  ) async {
    if (currentArticle == null) return;

    await DialogUtils.showConfirm(
      title: "确认删除",
      message: "您确定要删除吗？",
      confirmText: "删除",
      cancelText: "取消",
      onConfirm: () async {
        await ref
            .read(articleDetailControllerProvider(articleId).notifier)
            .deleteArticle();
        UIUtils.showSuccess('删除成功', title: '提示');
      },
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
