import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:url_launcher/url_launcher.dart';

class ArticleDetailAppBar extends StatelessWidget implements PreferredSizeWidget {
  final ArticleDetailController controller;

  const ArticleDetailAppBar({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return AppBar(
      title: Text(
        getTopLevelDomain(Uri.parse(controller.articleModel.url ?? '').host),
        style: textTheme.titleLarge?.copyWith(color: Colors.white),
      ),
      centerTitle: true,
      actions: [_buildAppBarActions(context)],
    );
  }

  Widget _buildAppBarActions(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz, color: colorScheme.onSurface),
      offset: const Offset(0, 50),
      padding: EdgeInsets.zero,
      color: colorScheme.surface,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
      itemBuilder: (context) => _buildPopupMenuItems(context),
      onSelected: _handleMenuSelection,
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

  void _handleMenuSelection(int value) {
    switch (value) {
      case 1:
        _onShareArticle();
        break;
      case 2:
        _showDeleteConfirmationDialog();
        break;
      case 3:
        _onCopyURL();
        break;
      case 4:
        _onOpenInBrowser();
        break;
    }
  }

  void _onOpenInBrowser() {
    launchUrl(Uri.parse(controller.articleModel.url ?? ''));
  }

  void _onShareArticle() {
    Get.toNamed(
      Routes.SHARE_DIALOG,
      arguments: {'articleID': controller.articleModel.id, 'shareURL': controller.articleModel.url, 'update': true},
    );
  }

  void _onCopyURL() {
    Clipboard.setData(ClipboardData(text: controller.articleModel.url ?? ''));
    successNotice('URL已复制到剪贴板');
  }

  void _showDeleteConfirmationDialog() {
    final colorScheme = AppTheme.getColorScheme(Get.context!);

    Get.defaultDialog(
      title: "确认删除",
      middleText: "您确定要删除吗？",
      confirm: TextButton(
        onPressed: () async {
          await controller.deleteArticle();
          Get.find<ArticlesController>().removeArticle(controller.articleModel.id);
          Get.back();
          Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
        },
        child: Text('删除', style: TextStyle(color: colorScheme.error)),
      ),
      cancel: TextButton(onPressed: () => Get.back(), child: Text("取消")),
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
