import 'package:flutter/material.dart' show VoidCallback;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';

class LeftBarView extends ConsumerWidget {
  const LeftBarView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _buildPage(context, ref);
  }

  Widget _buildPage(BuildContext context, WidgetRef ref) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Daily Satori'), centerTitle: true),
      body: Column(
        children: [
          _buildHeader(context),
          Dimensions.verticalSpacerS,
          _buildActions(context, ref),
          Dimensions.verticalSpacerS,
          Divider(color: colorScheme.outline),
          Expanded(child: _buildTagsList(context, ref)),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(
        Dimensions.spacingM,
        Dimensions.spacingM,
        Dimensions.spacingM,
        Dimensions.spacingS,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('欢迎使用 Daily Satori', style: textTheme.headlineMedium),
          Dimensions.verticalSpacerXs,
          Text(
            '您的个人阅读助手',
            style: textTheme.bodyMedium?.copyWith(color: AppTheme.getColorScheme(context).onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  Widget _buildActions(BuildContext context, WidgetRef ref) {
    final state = ref.watch(leftBarControllerProvider);
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      margin: Dimensions.paddingHorizontalM,
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: Dimensions.borderRadiusM,
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(13), blurRadius: 5, offset: const Offset(0, 2))],
      ),
      child: Row(
        children: [
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.article_outlined,
              label: '全部',
              onPressed: () {
                ref.read(articlesControllerProvider.notifier).clearAllFilters();
                AppNavigation.back();
              },
            ),
          ),
          Container(width: 1, height: Dimensions.spacingL + Dimensions.spacingXs, color: colorScheme.outline),
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.favorite,
              label: '收藏',
              onPressed: () {
                ref.read(articlesControllerProvider.notifier).toggleFavorite(true);
                AppNavigation.back();
              },
            ),
          ),
          Container(width: 1, height: Dimensions.spacingL + Dimensions.spacingXs, color: colorScheme.outline),
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.settings,
              label: '设置',
              onPressed: () => AppNavigation.toNamed('/settings'),
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
      borderRadius: Dimensions.borderRadiusM,
      child: Padding(
        padding: Dimensions.paddingVerticalM,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: Dimensions.iconSizeL, color: colorScheme.primary),
            Dimensions.verticalSpacerXs,
            Text(label, style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurface)),
          ],
        ),
      ),
    );
  }

  Widget _buildTagsList(BuildContext context, WidgetRef ref) {
    final colorScheme = AppTheme.getColorScheme(context);
    final state = ref.watch(leftBarControllerProvider);

    if (state.tags.isEmpty) {
      return Center(
        child: Text('暂无标签', style: TextStyle(color: colorScheme.onSurfaceVariant)),
      );
    }

    return ListView.builder(
      itemCount: state.tags.length,
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      itemBuilder: (context, index) => _buildTagItem(context, ref, state.tags[index]),
    );
  }

  Widget _buildTagItem(BuildContext context, WidgetRef ref, TagModel tag) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: () {
        ref.read(articlesControllerProvider.notifier).filterByTag(tag.id, tag.name ?? '');
        AppNavigation.back();
      },
      borderRadius: Dimensions.borderRadiusS,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS + 2),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: colorScheme.outline.withAlpha(128), width: 0.5)),
        ),
        child: Row(
          children: [
            Icon(Icons.tag, size: Dimensions.iconSizeS, color: colorScheme.primary),
            Dimensions.horizontalSpacerM,
            Expanded(child: Text(tag.name ?? '', style: textTheme.bodyMedium)),
            Icon(Icons.chevron_right, size: Dimensions.iconSizeXs, color: colorScheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}
