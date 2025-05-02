import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 观点内容卡片组件
class ViewpointCard extends StatelessWidget {
  final BookViewpointModel viewpoint;
  final VoidCallback? onDelete;

  const ViewpointCard({super.key, required this.viewpoint, this.onDelete});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(context),
            const SizedBox(height: 24),
            _buildContent(context),
            if (viewpoint.example.isNotEmpty) ...[const SizedBox(height: 16), _buildExampleCard(context)],
          ],
        ),
      ),
    );
  }

  /// 构建标题和删除按钮
  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            viewpoint.title,
            style: Get.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: AppColors.primary(context)),
          ),
        ),
      ],
    );
  }

  /// 构建内容文本
  Widget _buildContent(BuildContext context) {
    return Text(viewpoint.content, style: Get.textTheme.bodyLarge?.copyWith(height: 1.5, letterSpacing: 0.3));
  }

  /// 构建例子卡片
  Widget _buildExampleCard(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.only(top: 16),

      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [_buildExampleHeader(context), const SizedBox(height: 8), _buildExampleContent(context)],
      ),
    );
  }

  /// 构建例子标题
  Widget _buildExampleHeader(BuildContext context) {
    return Row(
      children: [
        Icon(Icons.lightbulb_outline, size: 16, color: AppColors.primary(context)),
        const SizedBox(width: 6),
        Text(
          '相关案例',
          style: Get.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        ),
      ],
    );
  }

  /// 构建例子内容
  Widget _buildExampleContent(BuildContext context) {
    return Text(viewpoint.example, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5));
  }
}
