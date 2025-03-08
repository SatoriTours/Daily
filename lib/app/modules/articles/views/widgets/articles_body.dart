import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/components/inputs/search_text_field.dart';
import 'package:daily_satori/app/components/lists/articles_list.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 文章列表页面的主体内容组件
class ArticlesBody extends StatelessWidget {
  final ArticlesController controller;

  const ArticlesBody({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.articles.isEmpty) {
        return const Center(child: ArticlesEmptyView());
      }

      return Column(children: [_buildSearchField(), _buildArticlesList()]);
    });
  }

  /// 构建搜索输入框
  Widget _buildSearchField() {
    if (!controller.enableSearch.value) {
      return const SizedBox.shrink();
    }

    return SearchTextField(
      controller: controller.searchController,
      hintText: '搜索文章',
      isVisible: true,
      onClear: controller.searchArticles,
      onSubmitted: (_) => controller.searchArticles(),
    );
  }

  /// 构建文章列表
  Widget _buildArticlesList() {
    return Expanded(
      child: ArticlesList(
        articles: controller.articles,
        scrollController: controller.scrollController,
        onRefresh: controller.reloadArticles,
        isLoading: controller.isLoading.value,
        onArticleUpdated: () => _handleLastArticleUpdate(),
      ),
    );
  }

  /// 处理最后一篇文章的更新
  void _handleLastArticleUpdate() {
    if (controller.articles.isNotEmpty) {
      controller.updateArticle(controller.articles.last.id);
    }
  }
}
