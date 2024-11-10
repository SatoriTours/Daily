import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/left_bar_controller.dart';

class LeftBarView extends GetView<LeftBarController> {
  const LeftBarView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Daily Satori'),
        centerTitle: true,
      ),
      body: Column(
        children: [
          _buildActions(),
          Expanded(child: _buildTagsList()),
        ],
      ),
    );
  }

  Widget _buildActions() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        ElevatedButton.icon(
          icon: const Icon(Icons.article_outlined),
          label: const Text('全部'),
          onPressed: () {
            controller.articlesController.showAllArticles();
            Get.back();
          },
        ),
        ElevatedButton.icon(
          icon: const Icon(Icons.favorite),
          label: const Text('收藏'),
          onPressed: () {
            controller.articlesController.toggleOnlyFavorite(true);
            Get.back();
          },
        ),
        ElevatedButton.icon(
          icon: const Icon(Icons.settings),
          label: const Text('设置'),
          onPressed: () => Get.toNamed(Routes.SETTINGS),
        ),
      ],
    );
  }

  Widget _buildTagsList() {
    return ListView.builder(
      itemCount: controller.tags.length,
      itemBuilder: (context, index) {
        final tag = controller.tags[index];
        return InkWell(
          onTap: () {
            controller.articlesController.showArticleByTagID(tag.id, tag.title ?? '');
            Get.back();
          },
          child: Padding(
            padding: EdgeInsets.fromLTRB(40, 10, 20, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(Icons.local_offer, size: 18),
                SizedBox(width: 10),
                Text(tag.title ?? '', style: MyFontStyle.tagsListContent),
                // Spacer(),
                // Text('10', style: MyFontStyle.tagsListContent),
              ],
            ),
          ),
        );
      },
    );
  }
}
