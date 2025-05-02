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
            Row(
              children: [
                Expanded(
                  child: Text(
                    viewpoint.title,
                    style: Get.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.primary(context),
                    ),
                  ),
                ),
                if (onDelete != null)
                  IconButton(
                    icon: const Icon(Icons.delete_outline, size: 18),
                    onPressed: onDelete,
                    padding: const EdgeInsets.all(4),
                    constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                    tooltip: '删除观点',
                    visualDensity: VisualDensity.compact,
                  ),
              ],
            ),
            const Divider(height: 24),
            Text(viewpoint.content, style: Get.textTheme.bodyLarge?.copyWith(height: 1.5, letterSpacing: 0.3)),
            if (viewpoint.example.isNotEmpty) ...[const SizedBox(height: 16), _buildExampleCard(context)],
          ],
        ),
      ),
    );
  }

  /// 构建例子卡片
  Widget _buildExampleCard(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.cardBackground(context).withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.primary(context).withValues(alpha: 0.1), width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline, size: 16, color: AppColors.primary(context)),
              const SizedBox(width: 6),
              Text(
                '相关案例',
                style: Get.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.primary(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(viewpoint.example, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5)),
        ],
      ),
    );
  }
}
