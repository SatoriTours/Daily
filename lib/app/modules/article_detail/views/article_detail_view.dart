import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';
import 'package:photo_view/photo_view.dart';
import 'package:photo_view/photo_view_gallery.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(),
      body: _buildBody(),
    );
  }

  // AppBar 相关
  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      title: Text(getTopLevelDomain(Uri.parse(controller.article.url ?? '').host)),
      centerTitle: true,
      actions: [_buildAppBarActions()],
    );
  }

  Widget _buildAppBarActions() {
    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz),
      offset: Offset(0, 50),
      padding: EdgeInsets.zero,
      itemBuilder: _buildPopupMenuItems,
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

    return menuItems.map((item) => _buildPopupMenuItem(item.$1, item.$2, item.$3)).toList();
  }

  PopupMenuItem<int> _buildPopupMenuItem(int value, String title, IconData icon) {
    return PopupMenuItem<int>(
      value: value,
      padding: EdgeInsets.symmetric(horizontal: 10),
      child: Row(
        children: [
          Icon(icon),
          SizedBox(width: 8),
          Text(title),
        ],
      ),
    );
  }

  void _handleMenuSelection(int value) {
    switch (value) {
      case 1:
        Get.toNamed(Routes.SHARE_DIALOG, arguments: {
          'articleID': controller.article.id,
          'shareURL': controller.article.url,
          'update': true,
        });
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
  Widget _buildBody() {
    return DefaultTabController(
      length: 3,
      child: Column(
        children: [
          Expanded(child: _buildTabBarView()),
          _buildTabBar(),
        ],
      ),
    );
  }

  Widget _buildTabBarView() {
    return TabBarView(
      physics: NeverScrollableScrollPhysics(),
      children: [
        _buildArticleContent(),
        _buildArticleScreenshot(),
        _buildArticleWebview(),
      ],
    );
  }

  Widget _buildTabBar() {
    return TabBar(
      tabs: const [
        Tab(text: 'AI解读'),
        Tab(text: '网页截图'),
        Tab(text: '原始链接'),
      ],
    );
  }

  // 文章内容相关
  Widget _buildArticleContent() {
    final article = controller.article;
    final imagePath = article.images.isEmpty ? '' : (article.images.first.path ?? '');

    return SingleChildScrollView(
      child: Column(
        children: [
          if (_shouldShowHeaderImage(imagePath)) _buildHeaderImage(imagePath),
          _buildTitle(),
          Obx(() => _buildTags()),
          _buildContent(),
          if (article.comment?.isNotEmpty ?? false) _buildComment(),
          _buildImageGallery(),
        ],
      ),
    );
  }

  bool _shouldShowHeaderImage(String imagePath) {
    return imagePath.isNotEmpty && !imagePath.endsWith('.svg');
  }

  Widget _buildHeaderImage(String imagePath) {
    return GestureDetector(
      onTap: () => _showFullScreenImage([imagePath]),
      child: Container(
        padding: const EdgeInsets.fromLTRB(20, 5, 20, 0),
        width: double.infinity,
        constraints: BoxConstraints(maxHeight: 200),
        child: _buildImageWithError(imagePath, BoxFit.cover),
      ),
    );
  }

  Widget _buildTitle() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 10),
      child: Text(
        (controller.article.aiTitle ?? controller.article.title) ?? '',
        style: MyFontStyle.articleTitleStyle,
      ),
    );
  }

  Widget _buildContent() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
      child: Text(
        controller.article.aiContent ?? '',
        style: MyFontStyle.articleBodyStyle,
      ),
    );
  }

  Widget _buildTags() {
    return controller.tags.value.isNotEmpty
        ? Container(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
            alignment: Alignment.centerLeft,
            child: Text(controller.tags.value, style: MyFontStyle.tagStyle),
          )
        : SizedBox.shrink();
  }

  Widget _buildComment() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      alignment: Alignment.centerLeft,
      child: Text(
        "我的备注：${controller.article.comment}",
        style: MyFontStyle.commentStyle,
      ),
    );
  }

  Widget _buildImageGallery() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 0),
      height: 200,
      child: _buildImageList(),
    );
  }

  // 截图和网页视图
  Widget _buildArticleScreenshot() {
    final screenshots = controller.getArticleScreenshots();
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: SizedBox(
        width: Get.width,
        child: ListView.builder(
          itemCount: screenshots.length,
          itemBuilder: (_, index) => _buildImageWithError(screenshots[index], BoxFit.cover),
        ),
      ),
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
        return SizedBox.shrink();
      },
    );
  }

  Widget _buildImageList() {
    final images = controller.getArticleImages();
    return ListView.builder(
      scrollDirection: Axis.horizontal,
      itemCount: images.length,
      itemBuilder: (context, index) {
        return GestureDetector(
          onTap: () => _showFullScreenImage(images, index),
          child: Padding(
            padding: const EdgeInsets.only(right: 8.0),
            child: _buildImageWithError(images[index], BoxFit.cover),
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
        child: Text("确认"),
      ),
      cancel: TextButton(
        onPressed: () => Get.back(),
        child: Text("取消"),
      ),
    );
  }

  void _showFullScreenImage(List<String> imagePaths, [int initialIndex = 0]) {
    Get.dialog(
      Scaffold(
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: Icon(Icons.close, color: Colors.white),
              onPressed: () => Get.close(),
            ),
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
                return SizedBox.shrink();
              },
            );
          },
        ),
      ),
      barrierDismissible: true,
    );
  }
}
