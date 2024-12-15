import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/font_style.dart';

import '../controllers/left_bar_controller.dart';

class LeftBarView extends GetView<LeftBarController> {
  const LeftBarView({super.key});

  @override
  Widget build(BuildContext context) {
    return _buildPage();
  }

  Widget _buildPage() {
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
    return Column(
      children: [
        Padding(
          padding: EdgeInsets.fromLTRB(10, 0, 10, 0),
          child: ListTile(
            onTap: () => controller.isTagsExpanded.toggle(),
            leading: Icon(Icons.local_offer_outlined),
            title: Text('分类', style: TextStyle(fontSize: 16)),
            trailing: Obx(() => AnimatedRotation(
                  turns: controller.isTagsExpanded.value ? 0.5 : 0,
                  duration: Duration(milliseconds: 200),
                  child: Icon(Icons.keyboard_arrow_down),
                )),
            contentPadding: EdgeInsets.symmetric(horizontal: 20),
          ),
        ),
        Obx(() => AnimatedCrossFade(
              firstChild: SingleChildScrollView(
                padding: EdgeInsets.fromLTRB(20, 0, 20, 0),
                child: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: controller.tags.map((tag) {
                    return InkWell(
                      onTap: () {
                        controller.articlesController.showArticleByTagID(tag.id, tag.name ?? '');
                        Get.back();
                      },
                      child: Chip(
                        avatar: Icon(Icons.local_offer, size: 16),
                        label: Text(tag.name ?? '', style: MyFontStyle.tagsListContent),
                        padding: EdgeInsets.symmetric(horizontal: 8),
                      ),
                    );
                  }).toList(),
                ),
              ),
              secondChild: SizedBox.shrink(),
              crossFadeState: controller.isTagsExpanded.value ? CrossFadeState.showFirst : CrossFadeState.showSecond,
              duration: Duration(milliseconds: 200),
            )),
      ],
    );
  }
}
