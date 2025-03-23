import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/html_styles.dart';
import 'package:daily_satori/global.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';
import 'package:daily_satori/app/styles/index.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  // AppBar 相关
  PreferredSizeWidget _buildAppBar(BuildContext context) {
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
      (4, "分享截图", Icons.share),
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
        controller.shareScreenshots();
        break;
    }
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

  // 主体内容
  Widget _buildBody(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(children: [Expanded(child: _buildTabBarView(context)), _buildTabBar(context)]),
    );
  }

  Widget _buildTabBarView(BuildContext context) {
    return TabBarView(
      physics: const NeverScrollableScrollPhysics(),
      children: [_buildSummaryTab(context), _buildHtmlContent(context)],
    );
  }

  Widget _buildTabBar(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      decoration: BoxDecoration(border: Border(top: BorderSide(color: colorScheme.outline, width: 0.5))),
      child: TabBar(
        labelColor: colorScheme.primary,
        unselectedLabelColor: colorScheme.onSurfaceVariant,
        indicatorColor: colorScheme.primary,
        indicatorWeight: 3,
        labelStyle: textTheme.labelLarge,
        tabs: const [Tab(text: 'AI解读'), Tab(text: '原文')],
      ),
    );
  }

  // 文章内容相关
  Widget _buildSummaryTab(BuildContext context) {
    return SingleChildScrollView(
      padding: Dimensions.paddingVerticalM.copyWith(bottom: Dimensions.spacingM),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (controller.articleModel.shouldShowHeaderImage())
            _buildHeaderImage(context, controller.articleModel.getHeaderImagePath()),
          if (controller.articleModel.shouldShowHeaderImage()) const SizedBox(height: 10),
          _buildTitle(context),
          const SizedBox(height: 15),
          Obx(() => _buildTags(context)),
          const SizedBox(height: 15),
          _buildContent(context),
          if (controller.articleModel.comment?.isNotEmpty ?? false) const SizedBox(height: 24),
          if (controller.articleModel.comment?.isNotEmpty ?? false) _buildComment(context),
        ],
      ),
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(
        (controller.articleModel.aiTitle ?? controller.articleModel.title) ?? '',
        style: textTheme.headlineSmall,
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Text(controller.articleModel.aiContent ?? '', style: textTheme.bodyMedium),
    );
  }

  Widget _buildTags(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return controller.tags.value.isNotEmpty
        ? Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            spacing: 10.0,
            runSpacing: 10.0,
            children:
                controller.tags.value.split(', ').map((tag) {
                  return Container(
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer.withAlpha(179),
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
                    child: Text(
                      tag,
                      style: textTheme.bodySmall?.copyWith(
                        color: colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  );
                }).toList(),
          ),
        )
        : const SizedBox.shrink();
  }

  Widget _buildComment(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('评论', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(controller.articleModel.comment ?? '', style: textTheme.bodyMedium),
        ],
      ),
    );
  }

  Widget _buildErrorImage(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      color: colorScheme.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.broken_image_outlined, size: Dimensions.iconSizeL, color: colorScheme.onSurfaceVariant),
      ),
    );
  }

  Widget _buildHeaderImage(BuildContext context, String imagePath) {
    return GestureDetector(
      onTap: () => _showFullScreenImage([imagePath]),
      child: Container(
        padding: Dimensions.paddingPage.copyWith(bottom: 0),
        width: double.infinity,
        constraints: const BoxConstraints(maxHeight: 200),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          child: _buildImageWithError(imagePath, BoxFit.cover),
        ),
      ),
    );
  }

  Widget _buildImageWithError(String path, BoxFit fit) {
    return Image.file(
      File(path),
      fit: fit,
      alignment: Alignment.topCenter,
      errorBuilder: (_, error, __) {
        logger.i("加载路径错误 $path");
        return _buildErrorImage(Get.context!);
      },
    );
  }

  // HTML内容视图
  Widget _buildHtmlContent(BuildContext context) {
    final htmlContent = controller.articleModel.htmlContent;
    if (htmlContent == null || htmlContent.isEmpty) {
      return _buildEmptyHtmlState(context);
    }

    return SingleChildScrollView(
      padding: Dimensions.paddingPage,
      child: Html(
        data: htmlContent,
        style: HtmlStyles.getStyles(context),
        onLinkTap: (url, _, __) {
          if (url != null) {
            _launchUrlExternal(url);
          }
        },
      ),
    );
  }

  // 在外部浏览器中打开URL
  Future<void> _launchUrlExternal(String url) async {
    try {
      final uri = Uri.parse(url);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        logger.e('无法打开链接: $url');
        errorNotice('无法打开链接');
      }
    } catch (e) {
      logger.e('打开链接时出错: $e');
      errorNotice('打开链接时出错');
    }
  }

  Widget _buildEmptyHtmlState(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text("无法加载原文内容", style: textTheme.bodyLarge),
        ],
      ),
    );
  }

  // 对话框
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

  void _showFullScreenImage(List<String> images, {int initialIndex = 0}) {
    Get.dialog(
      Scaffold(
        appBar: AppBar(
          backgroundColor: Colors.black.withAlpha(179),
          elevation: 0,
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.delete, color: Colors.white),
              onPressed: () {
                Get.defaultDialog(
                  title: "确认删除",
                  middleText: "确定要删除这张图片吗?",
                  confirm: TextButton(
                    onPressed: () async {
                      await controller.deleteImage(images[initialIndex]);
                      Get.back();
                      Get.back();
                      Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
                    },
                    child: Text("确认", style: TextStyle(color: Colors.red)),
                  ),
                  cancel: TextButton(onPressed: () => Get.back(), child: const Text("取消")),
                );
              },
            ),
            IconButton(icon: const Icon(Icons.close, color: Colors.white), onPressed: () => Get.back()),
          ],
        ),
        backgroundColor: Colors.black,
        body: PhotoViewGallery.builder(
          scrollDirection: Axis.horizontal,
          pageController: PageController(initialPage: initialIndex),
          itemCount: images.length,
          builder: (context, index) {
            return PhotoViewGalleryPageOptions(
              imageProvider: FileImage(File(images[index])),
              initialScale: PhotoViewComputedScale.contained,
              minScale: PhotoViewComputedScale.contained,
              maxScale: PhotoViewComputedScale.covered * 5.0,
              errorBuilder: (context, error, stackTrace) {
                logger.i("加载路径错误 ${images[index]}");
                return const SizedBox.shrink();
              },
            );
          },
        ),
      ),
      barrierDismissible: true,
    );
  }
}
