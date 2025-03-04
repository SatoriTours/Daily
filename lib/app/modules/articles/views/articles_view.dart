import 'dart:io';

import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';

import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/components/inputs/search_text_field.dart';
import 'package:daily_satori/app/components/lists/articles_list.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return AppBar(
      title: Obx(() => Text(controller.appBarTitle(), style: textTheme.titleLarge?.copyWith(color: Colors.white))),
      centerTitle: true,
      leading: IconButton(icon: const Icon(Icons.menu), onPressed: () => Get.toNamed(Routes.LEFT_BAR)),
      actions: [
        IconButton(icon: const Icon(Icons.search), onPressed: controller.toggleSearchState),
        Obx(() {
          if (WebService.i.webSocketTunnel.isConnected.value) {
            return IconButton(icon: const Icon(Icons.circle, color: Colors.green), onPressed: () {});
          } else {
            return IconButton(icon: const Icon(Icons.circle, color: Colors.red), onPressed: () {});
          }
        }),
      ],
    );
  }

  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.articleModels.isEmpty) {
        return ArticlesEmptyView(
          onAddArticle: () {
            // 这里可以添加引导用户添加文章的逻辑
          },
        );
      }
      return Column(
        children: [
          if (controller.enableSearch.value)
            SearchTextField(
              controller: controller.searchController,
              hintText: '搜索文章',
              isVisible: controller.enableSearch.value,
              onClear: controller.searchArticles,
              onSubmitted: (_) => controller.searchArticles(),
            ),
          Expanded(
            child: ArticlesList(
              articleModels: controller.articleModels,
              scrollController: controller.scrollController,
              onRefresh: controller.reloadArticles,
              isLoading: controller.isLoading.value,
              onArticleUpdated: () => controller.updateArticleInList(controller.articleModels.last.id),
            ),
          ),
        ],
      );
    });
  }
}
