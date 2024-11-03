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
            // 点击后跳转到全部文章
            Get.toNamed(Routes.ARTICLES);
          },
        ),
        ElevatedButton.icon(
          icon: const Icon(Icons.favorite),
          label: const Text('收藏'),
          onPressed: () {
            // 点击后跳转到收藏的文章
            // 在此处添加跳转逻辑
          },
        ),
        ElevatedButton.icon(
          icon: const Icon(Icons.settings),
          label: const Text('设置'),
          onPressed: () {
            // 点击后跳转到设置
            Get.toNamed(Routes.SETTINGS);
          },
        ),
      ],
    );
  }

  Widget _buildTagsList() {
    return ListView.builder(
      itemCount: controller.tags.length,
      itemBuilder: (context, index) {
        final tag = controller.tags[index];
        return Padding(
          padding: EdgeInsets.fromLTRB(20, 10, 20, 0),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(Icons.local_offer, size: 18),
              SizedBox(width: 10),
              Text(tag, style: MyFontStyle.tagsListContent),
              Spacer(),
              Text('10', style: MyFontStyle.tagsListContent),
            ],
          ),
        );
      },
    );
  }
}
