import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';
import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

/// 文章详情页面
/// 包含两个主要标签页：
/// 1. 摘要页面：显示文章的基本信息和AI生成的摘要
/// 2. 原文页面：显示文章的完整内容
class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        // 顶部应用栏
        appBar: ArticleDetailAppBar(controller: controller),
        // 主体内容
        body: Column(
          children: [
            // 内容区域
            Expanded(
              child: TabBarView(
                physics: const NeverScrollableScrollPhysics(),
                children: [SummaryTab(controller: controller), OriginalContentTab(controller: controller)],
              ),
            ),
            // 底部标签栏
            const ArticleTabBar(),
          ],
        ),
      ),
    );
  }
}
