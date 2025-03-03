import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/objectbox/tag.dart';

import '../controllers/left_bar_controller.dart';

class LeftBarView extends GetView<LeftBarController> {
  const LeftBarView({super.key});

  @override
  Widget build(BuildContext context) {
    return _buildPage(context);
  }

  Widget _buildPage(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Daily Satori'), centerTitle: true),
      body: Column(
        children: [
          _buildHeader(context),
          const SizedBox(height: 8),
          _buildActions(context),
          const SizedBox(height: 8),
          Divider(color: colorScheme.outline),
          Expanded(child: _buildTagsList(context)),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('欢迎使用 Daily Satori', style: textTheme.headlineSmall),
          const SizedBox(height: 4),
          Text(
            '您的个人阅读助手',
            style: textTheme.bodyMedium?.copyWith(color: AppTheme.getColorScheme(context).onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  Widget _buildActions(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: colorScheme.surface,
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
          Container(width: 1, height: 30, color: colorScheme.outline),
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
          Container(width: 1, height: 30, color: colorScheme.outline),
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
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onPressed,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 24, color: colorScheme.primary),
            const SizedBox(height: 4),
            Text(label, style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurface)),
          ],
        ),
      ),
    );
  }

  Widget _buildTagsList(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    if (controller.tags.isEmpty) {
      return Center(child: Text('暂无标签', style: TextStyle(color: colorScheme.onSurfaceVariant)));
    }

    return ListView.builder(
      itemCount: controller.tags.length,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      itemBuilder: (context, index) => _buildTagItem(context, controller.tags[index]),
    );
  }

  Widget _buildTagItem(BuildContext context, Tag tag) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: () {
        controller.articlesController.showArticleByTagID(tag.id, tag.name ?? '');
        Get.back();
      },
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: colorScheme.outline.withOpacity(0.5), width: 0.5)),
        ),
        child: Row(
          children: [
            Icon(Icons.tag, size: 18, color: colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(child: Text(tag.name ?? '', style: textTheme.bodyMedium)),
            Icon(Icons.chevron_right, size: 16, color: colorScheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}
