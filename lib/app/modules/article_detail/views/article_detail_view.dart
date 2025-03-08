import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/dream_webview/dream_webview.dart';

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
        getTopLevelDomain(Uri.parse(controller.url ?? '').host),
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
        Get.toNamed(
          Routes.SHARE_DIALOG,
          arguments: {'articleID': controller.articleId, 'shareURL': controller.url, 'update': true},
        );
        break;
      case 2:
        _showDeleteConfirmationDialog();
        break;
      case 3:
        Clipboard.setData(ClipboardData(text: controller.url ?? ''));
        successNotice("链接已复制到剪贴板");
        break;
      case 4:
        controller.shareScreenshots();
        break;
    }
  }

  // 主体内容
  Widget _buildBody(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Column(children: [Expanded(child: _buildTabBarView(context)), _buildTabBar(context)]),
    );
  }

  Widget _buildTabBarView(BuildContext context) {
    return TabBarView(
      physics: const NeverScrollableScrollPhysics(),
      children: [_buildSummaryTab(context), _buildArticleScreenshot(context), _buildWebView(context)],
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
        tabs: const [Tab(text: 'AI解读'), Tab(text: '截图'), Tab(text: '原文')],
      ),
    );
  }

  // 文章内容相关
  Widget _buildSummaryTab(BuildContext context) {
    final article = controller.article;

    return SingleChildScrollView(
      padding: Dimensions.paddingVerticalM.copyWith(bottom: Dimensions.spacingM),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (controller.shouldShowHeaderImage()) _buildHeaderImage(context, controller.getHeaderImagePath()),
          _buildTitle(context),
          Obx(() => _buildTags(context)),
          _buildContent(context),
          if (controller.comment?.isNotEmpty ?? false) _buildComment(context),
          if (article.images.length > 1) _buildImageGallery(context),
        ],
      ),
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return ComponentStyle.articleTitleContainer(
      context,
      Text((controller.aiTitle ?? controller.title) ?? '', style: textTheme.headlineSmall),
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return ComponentStyle.articleContentContainer(
      context,
      Text(controller.aiContent ?? '', style: textTheme.bodyMedium),
    );
  }

  Widget _buildTags(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return controller.tags.value.isNotEmpty
        ? ComponentStyle.articleTagsContainer(
          context,
          Wrap(
            spacing: 8.0, // 水平间距
            runSpacing: 8.0, // 垂直间距
            children:
                controller.tags.value.split(', ').map((tag) {
                  return Container(
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer.withOpacity(0.7),
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 10.0, vertical: 4.0),
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
    final colorScheme = AppTheme.getColorScheme(context);

    return ComponentStyle.articleContentContainer(
      context,
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('编辑评论', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          Dimensions.verticalSpacerS,
          Text(
            controller.comment ?? '',
            style: textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic, color: colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  Widget _buildImageGallery(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final images = controller.getArticleImages();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ComponentStyle.articleTitleContainer(
          context,
          Text('相关图片', style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        ),
        Padding(
          padding: Dimensions.paddingHorizontalL,
          child: GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              childAspectRatio: 1.0,
              crossAxisSpacing: 8,
              mainAxisSpacing: 8,
            ),
            itemCount: images.length,
            itemBuilder: (context, index) {
              final imagePath = images[index];
              return GestureDetector(
                onTap: () => _showFullScreenImage(images, initialIndex: index),
                child: Container(
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(Dimensions.radiusS)),
                  clipBehavior: Clip.antiAlias,
                  child: Image.file(
                    File(imagePath),
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stackTrace) {
                      logger.i("加载路径错误 $imagePath");
                      return _buildErrorImage(context);
                    },
                  ),
                ),
              );
            },
          ),
        ),
        Dimensions.verticalSpacerM,
      ],
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

  // 截图和网页视图
  Widget _buildArticleScreenshot(BuildContext context) {
    final screenshots = controller.getArticleScreenshots();
    if (screenshots.isEmpty) {
      return _buildEmptyScreenshotState(context);
    }

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Column(
        children:
            screenshots.map((screenshot) {
              return Image.file(
                File(screenshot),
                fit: BoxFit.fitWidth,
                width: double.infinity,
                errorBuilder: (context, error, stackTrace) {
                  logger.i("加载路径错误 $screenshot");
                  return _buildErrorImage(context);
                },
              );
            }).toList(),
      ),
    );
  }

  Widget _buildEmptyScreenshotState(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.image_not_supported_outlined, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text("暂无网页截图", style: textTheme.bodyLarge),
        ],
      ),
    );
  }

  // 使用浏览器显示原始网页
  Widget _buildWebView(BuildContext context) {
    final url = controller.url;
    if (url == null || url.isEmpty) {
      return _buildEmptyWebViewState(context);
    }

    return DreamWebView(
      url: url,
      onWebViewCreated: (webController) {
        // 可以在这里保存WebView控制器的引用以便后续使用
      },
    );
  }

  Widget _buildEmptyWebViewState(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.link_off, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text("无法加载原始网页", style: textTheme.bodyLarge),
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
          Get.find<ArticlesController>().removeArticleByIdFromList(controller.articleId);
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
          backgroundColor: Colors.black.withOpacity(0.7),
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

  Widget _buildDeleteDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return AlertDialog(
      title: Text('删除确认', style: textTheme.titleLarge),
      content: Text('确定要删除这篇文章吗？此操作不可撤销。', style: textTheme.bodyMedium),
      actions: [
        TextButton(onPressed: () => Get.back(), child: Text('取消', style: TextStyle(color: colorScheme.secondary))),
        TextButton(
          onPressed: () {
            Get.back(); // 关闭弹窗
            controller.deleteArticle();
            Get.back(); // 返回文章列表
            Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
          },
          child: Text("确认", style: TextStyle(color: colorScheme.error)),
        ),
      ],
    );
  }
}
