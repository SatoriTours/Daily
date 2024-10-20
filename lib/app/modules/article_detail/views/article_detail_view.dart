import 'dart:io';

import 'package:daily_satori/app/databases/database.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/global.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(getTopLevelDomain(Uri.parse(controller.article.url).host)),
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(Icons.refresh), // 刷新图标
            onPressed: () {
              Get.toNamed(Routes.SHARE_DIALOG, arguments: {
                'shareURL': controller.article.url,
                'update': true,
              });
            },
          ),
          IconButton(
            icon: Icon(Icons.delete), // 删除图标
            onPressed: () {
              _showDeleteConfirmationDialog();
            },
          ),
        ],
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
          await articlesController.reloadArticles(); // TODO: 这个地方改成从列表中删除, 而不是直接刷新
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

  Widget _buildArticleScreenshot() {
    return SingleChildScrollView(
      child: SizedBox(
        width: double.infinity,
        child: Image.file(
          File(controller.article.screenshotPath ?? ''), // 假设文章对象中有一个 imageUrl 属性
          fit: BoxFit.cover,
          alignment: Alignment.topCenter,
        ),
      ),
    );
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
