import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/colors.dart';
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
      appBar: AppBar(title: const Text('Daily Satori'), centerTitle: true),
      body: Column(
        children: [
          _buildHeader(),
          const SizedBox(height: 16),
          _buildActions(),
          const SizedBox(height: 16),
          Divider(color: AppColors.divider),
          Expanded(child: _buildTagsList()),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('欢迎使用 Daily Satori', style: MyFontStyle.articleTitleStyle),
          const SizedBox(height: 8),
          Text('您的个人阅读助手', style: MyFontStyle.cardSubtitleStyle),
        ],
      ),
    );
  }

  Widget _buildActions() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _buildActionButton(
            icon: Icons.article_outlined,
            label: '全部',
            onPressed: () {
              controller.articlesController.showAllArticles();
              Get.back();
            },
          ),
          _buildActionButton(
            icon: Icons.favorite,
            label: '收藏',
            onPressed: () {
              controller.articlesController.toggleOnlyFavorite(true);
              Get.back();
            },
          ),
          _buildActionButton(icon: Icons.settings, label: '设置', onPressed: () => Get.toNamed(Routes.SETTINGS)),
        ],
      ),
    );
  }

  Widget _buildActionButton({required IconData icon, required String label, required VoidCallback onPressed}) {
    return ElevatedButton(
      onPressed: onPressed,
      style: ElevatedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [Icon(icon, size: 24), const SizedBox(height: 4), Text(label, style: MyFontStyle.buttonTextStyle)],
      ),
    );
  }

  Widget _buildTagsList() {
    return Column(children: [_buildTagsHeader(), Expanded(child: _buildTagsContent())]);
  }

  Widget _buildTagsHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: InkWell(
        onTap: () => controller.isTagsExpanded.toggle(),
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Icon(Icons.local_offer_outlined, color: AppColors.primary),
              const SizedBox(width: 16),
              Text('分类', style: MyFontStyle.listTitleStyle),
              const Spacer(),
              Obx(
                () => AnimatedRotation(
                  turns: controller.isTagsExpanded.value ? 0.5 : 0,
                  duration: const Duration(milliseconds: 200),
                  child: Icon(Icons.keyboard_arrow_down, color: AppColors.primary),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTagsContent() {
    return Obx(
      () => AnimatedCrossFade(
        firstChild: _buildTagsGrid(),
        secondChild: const SizedBox.shrink(),
        crossFadeState: controller.isTagsExpanded.value ? CrossFadeState.showFirst : CrossFadeState.showSecond,
        duration: const Duration(milliseconds: 300),
      ),
    );
  }

  Widget _buildTagsGrid() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Wrap(spacing: 12, runSpacing: 12, children: controller.tags.map((tag) => _buildTagChip(tag)).toList()),
    );
  }

  Widget _buildTagChip(tag) {
    return InkWell(
      onTap: () {
        controller.articlesController.showArticleByTagID(tag.id, tag.name ?? '');
        Get.back();
      },
      child: Chip(
        avatar: Icon(Icons.local_offer, size: 16, color: AppColors.primary),
        label: Text(tag.name ?? '', style: MyFontStyle.chipTextStyle),
        backgroundColor: AppColors.primary.withOpacity(0.1),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
    );
  }
}
