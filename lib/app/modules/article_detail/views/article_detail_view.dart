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
                physics: NeverScrollableScrollPhysics(),
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

  Widget _buildArticleWebview() {
    controller.isLoading.value = true;
    return Obx(() {
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

  final String artilceHtmlCss = """
<style>
/* 基本样式 */
body {
  font-family: 'Georgia', serif; /* 使用优雅的字体 */
  line-height: 1.6; /* 增加行高以提高可读性 */
  margin: 0;
  padding: 20px;
  background-color: #f4f4f4; /* 背景颜色 */
  color: #333; /* 文字颜色 */
}

/* 文章容器 */
.article {
  max-width: 800px; /* 最大宽度 */
  margin: auto; /* 居中 */
  padding: 20px;
  background: white; /* 文章背景 */
  border-radius: 8px; /* 圆角 */
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); /* 阴影效果 */
}

/* 标题样式 */
.article h1, .article h2, .article h3 {
  color: #2c3e50; /* 深色标题 */
  margin-top: 20px;
  margin-bottom: 10px;
}

/* 段落样式 */
.article p {
  margin-bottom: 15px; /* 段落间距 */
}

/* 图片样式 */
.article img {
  max-width: 100%; /* 自适应宽度 */
  height: auto; /* 保持比例 */
  border-radius: 5px; /* 圆角 */
}

/* 引用样式 */
.article blockquote {
  border-left: 4px solid #3498db; /* 左侧边框 */
  padding-left: 15px; /* 内边距 */
  color: #555; /* 引用文字颜色 */
  font-style: italic; /* 斜体 */
  margin: 20px 0; /* 上下间距 */
}

/* 链接样式 */
.article a {
  color: #3498db; /* 链接颜色 */
  text-decoration: none; /* 去掉下划线 */
}

.article a:hover {
  text-decoration: underline; /* 悬停时下划线 */
}
</style>
""";
}
