import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';
import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: ArticleDetailAppBar(controller: controller), body: _buildBody(context));
  }

  Widget _buildBody(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(children: [Expanded(child: _buildTabBarView(context)), const ArticleTabBar()]),
    );
  }

  Widget _buildTabBarView(BuildContext context) {
    return TabBarView(
      physics: const NeverScrollableScrollPhysics(),
      children: [SummaryTab(controller: controller), OriginalContentTab(controller: controller)],
    );
  }
}
