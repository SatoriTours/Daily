import 'dart:io';

import 'package:daily_satori/app/databases/database.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/global.dart';
import 'package:share_plus/share_plus.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(getTopLevelDomain(Uri.parse(controller.article.url).host)),
        centerTitle: true,
        actions: [_buildAppBarActions()],
      ),
      body: DefaultTabController(
        length: 3,
        child: Column(
          children: [
            Expanded(
              child: TabBarView(
                physics: NeverScrollableScrollPhysics(),
                children: [
                  _buildArticleContent(),
                  _buildArticleScreenshot(),
                  _buildArticleWebview(context),
                ],
              ),
            ),
            TabBar(
              tabs: const [
                Tab(text: 'AI解读'),
                Tab(text: '网页截图'),
                Tab(text: '原始链接'),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showDeleteConfirmationDialog() {
    Get.defaultDialog(
      id: "confirmDialog",
      title: "确认删除",
      middleText: "您确定要删除吗？",
      confirm: TextButton(
        onPressed: () async {
          await controller.deleteArticle();
          var articlesController = Get.find<ArticlesController>();
          articlesController.removeArticleByIdFromList(controller.article.id);
          Get.back();
          Get.snackbar("提示", "删除成功", snackPosition: SnackPosition.top, backgroundColor: Colors.green);
        },
        child: Text("确认"),
      ),
      cancel: TextButton(
        onPressed: () {
          Navigator.pop(Get.context!);
        },
        child: Text("取消"),
      ),
    );
  }

  Widget _buildAppBarActions() {
    return PopupMenuButton<int>(
      icon: Icon(Icons.more_horiz), // 弹出菜单图标
      offset: Offset(0, 50), // 设置弹出菜单的位置，向下偏移50个像素
      padding: EdgeInsets.all(0),
      itemBuilder: (context) => [
        _buildPopupMenuIteam(1, "刷新", Icons.refresh),
        _buildPopupMenuIteam(2, "删除", Icons.delete),
        _buildPopupMenuIteam(3, "复制链接", Icons.copy),
        _buildPopupMenuIteam(4, "分享截图", Icons.share),
      ],
      onSelected: (value) {
        if (value == 1) {
          Get.toNamed(Routes.SHARE_DIALOG, arguments: {
            'shareURL': controller.article.url,
            'update': true,
          });
        } else if (value == 2) {
          _showDeleteConfirmationDialog();
        } else if (value == 3) {
          Clipboard.setData(ClipboardData(text: controller.article.url));
          successNotice("链接已复制到剪贴板");
        } else if (value == 4) {
          controller.shareScreenshots();
        }
      },
    );
  }

  PopupMenuItem<int> _buildPopupMenuIteam(int index, String title, IconData icon) {
    return PopupMenuItem<int>(
      value: index,
      padding: EdgeInsets.fromLTRB(10, 0, 10, 0),
      child: Row(
        children: [
          Icon(icon), // 添加删除图标
          SizedBox(width: 8), // 添加间距
          Text(title),
        ],
      ),
    );
  }

  Widget _buildArticleScreenshot() {
    final screenshotPath = controller.article.screenshotPath;
    if (screenshotPath != null && screenshotPath.isNotEmpty) {
      return SingleChildScrollView(
        child: SizedBox(
          width: double.infinity,
          child: Image.file(
            File(controller.article.screenshotPath!),
            fit: BoxFit.cover,
            alignment: Alignment.topCenter,
          ),
        ),
      );
    } else {
      return FutureBuilder<List<ArticleScreenshot>>(
        future: controller.getArticleScreenshoots(), // 获取图片列表
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(child: CircularProgressIndicator());
          } else if (snapshot.hasError) {
            return Center(child: Text("加载图片失败"));
          } else if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return Center(child: Text("没有截图可显示"));
          }

          final screenshots = snapshot.data!;

          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: MediaQuery.of(context).size.width, // 设置宽度占满全屏
              child: ListView.builder(
                itemCount: screenshots.length,
                itemBuilder: (context, index) {
                  return Image.file(
                    File(screenshots[index].imagePath ?? ''),
                    fit: BoxFit.cover,
                    errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) {
                      return Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.error, color: Colors.red),
                          Text('文件不存在'),
                        ],
                      );
                    },
                  );
                },
              ),
            ),
          );
        },
      );
    }
  }

  Widget _buildArticleWebview(BuildContext context) {
    return DreamWebView(
      url: controller.article.url,
    );
  }

  Widget _buildArticleContent() {
    return SingleChildScrollView(
      child: Column(
        children: [
          if (controller.article.imagePath?.isNotEmpty ?? false)
            GestureDetector(
              onTap: () {
                _showFullScreenImage(controller.article.imagePath!); // 处理点击事件，显示全屏图片
              },
              child: Container(
                padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
                width: double.infinity,
                constraints: BoxConstraints(
                  maxHeight: 200, // 最大高度为 200
                ),
                child: Image.file(
                  File(controller.article.imagePath!),
                  fit: BoxFit.cover,
                  alignment: Alignment.topCenter,
                  errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) {
                    return Container(); // 不显示任何内容
                  },
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
            child: Text(
              (controller.article.aiTitle ?? controller.article.title) ?? '',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
          ),
          Container(
            padding: const EdgeInsets.all(20.0),
            child: Text(
              (controller.article.aiContent ?? ''),
              style: const TextStyle(fontSize: 16),
            ),
          ),
          Container(
            padding: const EdgeInsets.all(20.0),
            child: _buildImageList(),
          ),
        ],
      ),
    );
  }

  Widget _buildImageList() {
    return SizedBox(
      height: 200, // 设置图片列表的高度
      child: FutureBuilder<List<ArticleImage>>(
        future: controller.getArticleImages(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(child: CircularProgressIndicator());
          } else if (snapshot.hasError) {
            return Center(child: Text("加载图片失败"));
          } else if (!snapshot.hasData || snapshot.data!.isEmpty) {
            return Container();
          }

          final images = snapshot.data!;

          return ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: images.length,
            itemBuilder: (context, index) {
              return GestureDetector(
                onTap: () {
                  _showFullScreenImage(images[index].imagePath ?? '');
                },
                child: Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Image.file(
                    File(images[index].imagePath ?? ''), // 假设 ArticleImage 对象有 imagePath 属性
                    fit: BoxFit.cover,
                    errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) {
                      return Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.error, color: Colors.red),
                          Text('File does not exist'),
                        ],
                      ); // 显示错误图标和消息
                    },
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  void _showFullScreenImage(String imagePath) {
    if (imagePath.isEmpty) {
      return;
    }
    Get.dialog(
      Scaffold(
        backgroundColor: Colors.black,
        body: GestureDetector(
          onTap: () {
            Navigator.pop(Get.context!);
          },
          child: Center(
            child: SizedBox(
              height: MediaQuery.of(Get.context!).size.height, // 设置高度为手机屏幕高度
              child: InteractiveViewer(
                maxScale: 5,
                child: Image.file(
                  File(imagePath),
                  fit: BoxFit.contain,
                  errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) {
                    return Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.error, color: Colors.red),
                        Text('File does not exist'),
                      ],
                    ); // 显示错误图标和消息
                  },
                ),
              ),
            ),
          ),
        ),
      ),
      barrierDismissible: true, // 点击对话框外部也可以关闭
    );
  }
}
