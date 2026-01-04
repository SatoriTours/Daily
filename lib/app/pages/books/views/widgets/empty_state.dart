import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 空状态组件
class EmptyState extends StatelessWidget {
  const EmptyState({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.menu_book, size: 64, color: Colors.grey),
          Dimensions.verticalSpacerM,
          Text('暂无书籍', style: AppTypography.titleMedium),
          Dimensions.verticalSpacerS,
          Text('点击右下角按钮添加书籍', style: AppTypography.bodyMedium),
        ],
      ),
    );
  }
}
