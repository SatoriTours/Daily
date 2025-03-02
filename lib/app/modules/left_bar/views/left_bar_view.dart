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
    return _buildPage(context);
  }

  Widget _buildPage(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Daily Satori'), centerTitle: true),
      body: Column(
        children: [
          _buildHeader(context),
          const SizedBox(height: 8),
          _buildActions(context),
          const SizedBox(height: 8),
          Divider(color: AppColors.divider(context)),
          Expanded(child: _buildTagsList(context)),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('欢迎使用 Daily Satori', style: MyFontStyle.headerTitleStyleThemed(context)),
          const SizedBox(height: 4),
          Text('您的个人阅读助手', style: MyFontStyle.cardSubtitleStyleThemed(context)),
        ],
      ),
    );
  }

  Widget _buildActions(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: Theme.of(context).brightness == Brightness.dark ? AppColors.cardBackgroundDark : Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 5, offset: const Offset(0, 2))],
      ),
      child: Row(
        children: [
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.article_outlined,
              label: '全部',
              onPressed: () {
                controller.articlesController.showAllArticles();
                Get.back();
              },
            ),
          ),
          Container(width: 1, height: 30, color: AppColors.divider(context)),
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.favorite,
              label: '收藏',
              onPressed: () {
                controller.articlesController.toggleOnlyFavorite(true);
                Get.back();
              },
            ),
          ),
          Container(width: 1, height: 30, color: AppColors.divider(context)),
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.settings,
              label: '设置',
              onPressed: () => Get.toNamed(Routes.SETTINGS),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionButton(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onPressed,
  }) {
    return InkWell(
      onTap: onPressed,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 20, color: AppColors.primary(context)),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textPrimary(context)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTagsList(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Row(
            children: [
              Icon(Icons.category, size: 18, color: AppColors.primary(context)),
              const SizedBox(width: 8),
              Text(
                '分类',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Container(
                  height: 32,
                  decoration: BoxDecoration(
                    color:
                        Theme.of(context).brightness == Brightness.dark
                            ? AppColors.cardBackgroundDark.withOpacity(0.7)
                            : Colors.grey.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    children: [
                      const SizedBox(width: 10),
                      Icon(Icons.search, size: 16, color: AppColors.textSecondary(context)),
                      const SizedBox(width: 6),
                      Expanded(
                        child: TextField(
                          decoration: InputDecoration(
                            hintText: '搜索分类',
                            hintStyle: TextStyle(fontSize: 13, color: AppColors.textSecondary(context)),
                            border: InputBorder.none,
                            isDense: true,
                            contentPadding: const EdgeInsets.symmetric(vertical: 8),
                          ),
                          style: TextStyle(fontSize: 13, color: AppColors.textPrimary(context)),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(vertical: 0),
            children: [...controller.tags.map((tag) => _buildTagItem(context, tag)).toList()],
          ),
        ),
      ],
    );
  }

  Widget _buildTagItem(BuildContext context, tag) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Column(
      children: [
        InkWell(
          onTap: () {
            controller.articlesController.showArticleByTagID(tag.id, tag.name ?? '');
            Get.back();
          },
          hoverColor: AppColors.primary(context).withOpacity(0.05),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Icon(Icons.label_outline, size: 16, color: AppColors.primary(context)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    tag.name ?? '',
                    style: TextStyle(fontSize: 14, color: AppColors.textPrimary(context)),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Icon(Icons.chevron_right, size: 16, color: AppColors.textSecondary(context)),
              ],
            ),
          ),
        ),
        Divider(
          height: 1,
          thickness: 0.5,
          indent: 44,
          endIndent: 0,
          color: AppColors.divider(context).withOpacity(0.5),
        ),
      ],
    );
  }
}
