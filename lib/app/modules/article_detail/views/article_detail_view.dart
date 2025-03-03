import 'dart:io';

import 'package:daily_satori/app/components/dream_webview/dream_webview.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  // AppBar 相关
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Text(
        getTopLevelDomain(Uri.parse(controller.article.url ?? '').host),
        style: MyFontStyle.appBarTitleStyleThemed(context),
      ),
      centerTitle: true,
      actions: [_buildAppBarActions(context)],
    );
  }

  Widget _buildAppBarActions(BuildContext context) {
    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz, color: AppColors.textPrimary(context)),
      offset: const Offset(0, 50),
      padding: EdgeInsets.zero,
      color: AppColors.cardBackground(context),
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
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
    return PopupMenuItem<int>(
      value: value,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          Icon(icon, size: 20, color: AppColors.textPrimary(context)),
          const SizedBox(width: 12),
          Text(
            title,
            style: TextStyle(fontSize: 14, color: AppColors.textPrimary(context), fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }

  void _handleMenuSelection(int value) {
    switch (value) {
      case 1:
        Get.toNamed(
          Routes.SHARE_DIALOG,
          arguments: {'articleID': controller.article.id, 'shareURL': controller.article.url, 'update': true},
        );
        break;
      case 2:
        _showDeleteConfirmationDialog();
        break;
      case 3:
        Clipboard.setData(ClipboardData(text: controller.article.url ?? ''));
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
      children: [_buildArticleContent(context), _buildArticleScreenshot(context), _buildArticleWebview()],
    );
  }

  Widget _buildTabBar(BuildContext context) {
    return Container(
      decoration: BoxDecoration(border: Border(top: BorderSide(color: AppColors.divider(context), width: 0.5))),
      child: TabBar(
        labelColor: AppColors.primary(context),
        unselectedLabelColor: AppColors.textSecondary(context),
        indicatorColor: AppColors.primary(context),
        indicatorWeight: 3,
        labelStyle: MyFontStyle.tabLabelStyleThemed(context),
        unselectedLabelStyle: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w500,
          color: AppColors.textSecondary(context),
        ),
        tabs: const [Tab(text: 'AI解读'), Tab(text: '网页截图'), Tab(text: '原始链接')],
      ),
    );
  }

  // 文章内容相关
  Widget _buildArticleContent(BuildContext context) {
    final article = controller.article;
    final imagePath = article.images.isEmpty ? '' : (article.images.first.path ?? '');

    return SingleChildScrollView(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_shouldShowHeaderImage(imagePath)) _buildHeaderImage(context, imagePath),
          _buildTitle(context),
          Obx(() => _buildTags(context)),
          _buildContent(context),
          if (article.comment?.isNotEmpty ?? false) _buildComment(context),
          if (article.images.length > 1) _buildImageGallery(context),
        ],
      ),
    );
  }

  bool _shouldShowHeaderImage(String imagePath) {
    return imagePath.isNotEmpty && !imagePath.endsWith('.svg');
  }

  Widget _buildHeaderImage(BuildContext context, String imagePath) {
    return GestureDetector(
      onTap: () => _showFullScreenImage([imagePath]),
      child: Container(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
        width: double.infinity,
        constraints: const BoxConstraints(maxHeight: 200),
        child: ClipRRect(borderRadius: BorderRadius.circular(12), child: _buildImageWithError(imagePath, BoxFit.cover)),
      ),
    );
  }

  Widget _buildTitle(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
      child: Text(
        (controller.article.aiTitle ?? controller.article.title) ?? '',
        style: MyFontStyle.headerTitleStyleThemed(context),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
      child: Text(controller.article.aiContent ?? '', style: MyFontStyle.articleBodyStyleThemed(context)),
    );
  }

  Widget _buildTags(BuildContext context) {
    return controller.tags.value.isNotEmpty
        ? Container(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
          alignment: Alignment.centerLeft,
          child: Text(controller.tags.value, style: MyFontStyle.tagStyleThemed(context)),
        )
        : const SizedBox.shrink();
  }

  Widget _buildComment(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.cardBackground(context).withOpacity(0.5),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.divider(context)),
      ),
      alignment: Alignment.centerLeft,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            "我的备注",
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
          ),
          const SizedBox(height: 8),
          Text(controller.article.comment ?? "", style: MyFontStyle.commentStyleThemed(context)),
        ],
      ),
    );
  }

  Widget _buildImageGallery(BuildContext context) {
    final images = controller.getArticleImages();
    if (images.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
          child: Text(
            "图片集",
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
          ),
        ),
        Container(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 0),
          height: 120,
          child: _buildImageList(context, images),
        ),
      ],
    );
  }

  // 截图和网页视图
  Widget _buildArticleScreenshot(BuildContext context) {
    final screenshots = controller.getArticleScreenshots();
    if (screenshots.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.image_not_supported, size: 48, color: AppColors.textSecondary(context)),
            const SizedBox(height: 16),
            Text("暂无网页截图", style: MyFontStyle.emptyStateStyleThemed(context)),
          ],
        ),
      );
    }

    return ListView.builder(
      itemCount: screenshots.length,
      padding: EdgeInsets.zero,
      itemBuilder: (context, index) {
        return Image.file(
          File(screenshots[index]),
          errorBuilder: (_, error, __) {
            logger.i("加载路径错误 ${screenshots[index]}");
            return const SizedBox.shrink();
          },
        );
      },
    );
  }

  Widget _buildArticleWebview() {
    return DreamWebView(url: controller.article.url ?? '');
  }

  // 通用组件
  Widget _buildImageWithError(String path, BoxFit fit) {
    return Image.file(
      File(path),
      fit: fit,
      alignment: Alignment.topCenter,
      errorBuilder: (_, error, __) {
        logger.i("加载路径错误 $path");
        return const SizedBox.shrink();
      },
    );
  }

  Widget _buildImageList(BuildContext context, List<String> images) {
    return ListView.builder(
      scrollDirection: Axis.horizontal,
      itemCount: images.length,
      itemBuilder: (context, index) {
        return GestureDetector(
          onTap: () => _showFullScreenImage(images, index),
          child: Container(
            margin: const EdgeInsets.only(right: 8.0),
            width: 120,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.divider(context)),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: _buildImageWithError(images[index], BoxFit.cover),
            ),
          ),
        );
      },
    );
  }

  // 对话框
  void _showDeleteConfirmationDialog() {
    Get.defaultDialog(
      title: "确认删除",
      middleText: "您确定要删除吗？",
      confirm: TextButton(
        onPressed: () async {
          await controller.deleteArticle();
          Get.find<ArticlesController>().removeArticleByIdFromList(controller.article.id);
          Get.back();
          Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
        },
        child: const Text("确认", style: TextStyle(color: Colors.red)),
      ),
      cancel: TextButton(onPressed: () => Get.back(), child: const Text("取消")),
    );
  }

  void _showFullScreenImage(List<String> imagePaths, [int initialIndex = 0]) {
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
                      await controller.deleteImage(imagePaths[initialIndex]);
                      Get.back();
                      Get.back();
                      Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
                    },
                    child: const Text("确认", style: TextStyle(color: Colors.red)),
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
          itemCount: imagePaths.length,
          builder: (context, index) {
            return PhotoViewGalleryPageOptions(
              imageProvider: FileImage(File(imagePaths[index])),
              initialScale: PhotoViewComputedScale.contained,
              minScale: PhotoViewComputedScale.contained,
              maxScale: PhotoViewComputedScale.covered * 5.0,
              errorBuilder: (context, error, stackTrace) {
                logger.i("加载路径错误 ${imagePaths[index]}");
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
