import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/article_detail_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';

class ArticleDetailAppBar extends ConsumerWidget implements PreferredSizeWidget {
  final ArticleModel? article;

  const ArticleDetailAppBar({super.key, required this.article});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final textTheme = AppTheme.getTextTheme(context);

    return AppBar(
      title: Text(
        article != null
            ? StringUtils.getTopLevelDomain(Uri.parse(article!.url ?? '').host)
            : '',
        style: textTheme.titleLarge?.copyWith(color: Colors.white),
      ),
      centerTitle: true,
      actions: [
        _buildAppBarActions(context, ref),
      ],
    );
  }

  Widget _buildAppBarActions(BuildContext context, WidgetRef ref) {
    final colorScheme = AppTheme.getColorScheme(context);

    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz, color: colorScheme.onSurface),
      offset: const Offset(0, 50),
      padding: EdgeInsets.zero,
      color: colorScheme.surface,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
      itemBuilder: (context) => _buildPopupMenuItems(context),
      onSelected: (value) => _handleMenuSelection(context, ref, value),
    );
  }

  List<PopupMenuItem<int>> _buildPopupMenuItems(BuildContext context) {
    final menuItems = [
      (1, "刷新", Icons.refresh),
      (2, "删除", Icons.delete),
      (3, "复制链接", Icons.copy),
      (4, "浏览器打开", Icons.open_in_browser),
    ];

    return menuItems.map((item) => _buildPopupMenuItem(context, item.$1, item.$2, item.$3)).toList();
  }

  PopupMenuItem<int> _buildPopupMenuItem(BuildContext context, int value, String title, IconData icon) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return PopupMenuItem<int>(
      value: value,
      padding: Dimensions.paddingHorizontalM,
      child: Row(
        children: [
          Icon(icon, size: Dimensions.iconSizeS, color: colorScheme.onSurface),
          Dimensions.horizontalSpacerS,
          Text(title, style: textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }

  void _handleMenuSelection(BuildContext context, WidgetRef ref, int value) {
    if (article == null) return;

    switch (value) {
      case 1:
        _onShareArticle(context);
        break;
      case 2:
        _showDeleteConfirmationDialog(context, ref);
        break;
      case 3:
        _onCopyURL(context);
        break;
      case 4:
        _onOpenInBrowser(context);
        break;
    }
  }

  void _onOpenInBrowser(BuildContext context) {
    if (article?.url == null) return;
    launchUrl(Uri.parse(article!.url ?? ''));
  }

  void _onShareArticle(BuildContext context) {
    AppNavigation.toNamed(Routes.shareDialog, arguments: {'articleID': article!.id});
  }

  void _onCopyURL(BuildContext context) {
    if (article?.url == null) return;
    Clipboard.setData(ClipboardData(text: article!.url ?? ''));
    UIUtils.showSuccess('URL已复制到剪贴板');
  }

  void _showDeleteConfirmationDialog(BuildContext context, WidgetRef ref) async {
    await DialogUtils.showConfirm(
      title: "确认删除",
      message: "您确定要删除吗？",
      confirmText: "删除",
      cancelText: "取消",
      onConfirm: () async {
        await ref.read(articleDetailControllerProvider.notifier).deleteArticle();
        UIUtils.showSuccess('删除成功', title: '提示');
      },
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
