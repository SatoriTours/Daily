import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

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
      title: Obx(() => Text(controller.getTitle(), style: textTheme.titleLarge?.copyWith(color: Colors.white))),
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
      if (controller.articles.isEmpty) {
        return const Center(child: ArticlesEmptyView());
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
              articles: controller.articles,
              scrollController: controller.scrollController,
              onRefresh: controller.reloadArticles,
              isLoading: controller.isLoading.value,
              onArticleUpdated: () => controller.updateArticle(controller.articles.last.id),
            ),
          ),
        ],
      );
    });
  }
}
