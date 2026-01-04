

import 'package:daily_satori/app_exports.dart';
class ArticleTabBar extends StatelessWidget {
  const ArticleTabBar({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      decoration: BoxDecoration(border: Border(top: BorderSide(color: colorScheme.outline, width: 0.5))),
      child: TabBar(
        labelColor: colorScheme.primary,
        unselectedLabelColor: colorScheme.onSurfaceVariant,
        indicatorColor: colorScheme.primary,
        indicatorWeight: 3,
        labelStyle: textTheme.labelLarge,
        tabs: const [Tab(text: 'AI解读'), Tab(text: '原文')],
      ),
    );
  }
}