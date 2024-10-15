import 'dart:io';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html/flutter_widget_from_html.dart';

import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    controller.article = Get.arguments;

    return Scaffold(
      appBar: AppBar(
        title: Text(getTopLevelDomain(Uri.parse(controller.article.url).host)),
        centerTitle: true,
      ),
      body: DefaultTabController(
        length: 3,
        child: Column(
          children: [
            TabBar(
              tabs: const [
                Tab(text: '文章详情'),
                Tab(text: '网页截图'),
                Tab(text: '原始链接'),
              ],
            ),
            Expanded(
              child: TabBarView(
                children: [
                  _buildArticleContent(),
                  _buildArticleScreenshot(),
                  _buildArticleWebview(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildArticleScreenshot() {
    return SingleChildScrollView(
      child: Container(
        width: double.infinity,
        child: Image.file(
          File(controller.article.screenshotPath ?? ''), // 假设文章对象中有一个 imageUrl 属性
          fit: BoxFit.cover,
          alignment: Alignment.topCenter,
        ),
      ),
    );
  }

  Widget _buildArticleWebview() {
    return Obx(() {
      // controller.isLoading.value = true;
      return Stack(
        children: [
          DreamWebView(
            url: controller.article.url,
            onLoadStart: (url) => controller.isLoading.value = true,
            onLoadStop: () => controller.isLoading.value = false,
          ),
          if (controller.isLoading.value) Center(child: CircularProgressIndicator()),
        ],
      );
    });
  }

  Widget _buildArticleContent() {
    return SingleChildScrollView(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
            child: Text(
              (controller.article.aiTitle ?? controller.article.title),
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(20.0), // 添加边距 10px
            child: HtmlWidget(
              controller.article.htmlContent ?? '',
              // style: TextStyle(fontSize: 20),
            ),
          ),
        ],
      ),
    );
  }
}
