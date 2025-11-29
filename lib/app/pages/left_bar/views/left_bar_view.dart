import 'package:daily_satori/app/pages/left_bar/controllers/left_bar_controller.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';

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
          Dimensions.verticalSpacerS,
          _buildActions(context),
          Dimensions.verticalSpacerS,
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

  Widget _buildActions(BuildContext context) {
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
                controller.articlesController.clearAllFilters();
                Get.back();
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
                controller.articlesController.toggleFavorite(true);
                Get.back();
              },
            ),
          ),
          Container(width: 1, height: Dimensions.spacingL + Dimensions.spacingXs, color: colorScheme.outline),
          Expanded(
            child: _buildActionButton(
              context,
              icon: Icons.settings,
              label: '设置',
              onPressed: () => Get.toNamed('/settings'),
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

  Widget _buildTagsList(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    if (controller.tags.isEmpty) {
      return Center(
        child: Text('暂无标签', style: TextStyle(color: colorScheme.onSurfaceVariant)),
      );
    }

    return ListView.builder(
      itemCount: controller.tags.length,
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      itemBuilder: (context, index) => _buildTagItem(context, controller.tags[index]),
    );
  }

  Widget _buildTagItem(BuildContext context, TagModel tag) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: () {
        controller.articlesController.filterByTag(tag.id, tag.name ?? '');
        Get.back();
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
