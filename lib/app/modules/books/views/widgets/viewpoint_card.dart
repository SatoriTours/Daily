import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:intl/intl.dart';

/// 观点卡片组件
class ViewpointCard extends StatelessWidget {
  final BookViewpointModel viewpoint;
  final Function() onDelete;

  const ViewpointCard({Key? key, required this.viewpoint, required this.onDelete}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _buildHeader(context),
            if (viewpoint.title.isNotEmpty) ...[const SizedBox(height: 16), _buildTitle()],
            const SizedBox(height: 16),
            _buildContent(),
            if (viewpoint.example.isNotEmpty) ...[const SizedBox(height: 16), _buildExample()],
            if (viewpoint.feeling.isNotEmpty) ...[const SizedBox(height: 16), _buildFeeling()],
            const SizedBox(height: 16),
            _buildFooter(context),
          ],
        ),
      ),
    );
  }

  /// 构建头部
  Widget _buildHeader(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Icon(Icons.bookmark, size: 16, color: AppColors.primary(context)),
            const SizedBox(width: 8),
            Text('书籍观点', style: Get.textTheme.bodySmall?.copyWith(color: AppColors.primary(context))),
          ],
        ),
        IconButton(
          icon: const Icon(Icons.delete_outline, size: 18),
          onPressed: onDelete,
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 24, minHeight: 24),
          tooltip: '删除观点',
        ),
      ],
    );
  }

  /// 构建标题
  Widget _buildTitle() {
    return Text(viewpoint.title, style: Get.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold));
  }

  /// 构建内容
  Widget _buildContent() {
    return Text(viewpoint.content, style: Get.textTheme.bodyMedium?.copyWith(height: 1.6));
  }

  /// 构建案例
  Widget _buildExample() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('案例', style: Get.textTheme.bodySmall?.copyWith(color: Colors.grey)),
        const SizedBox(height: 4),
        Text(viewpoint.example, style: Get.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic)),
      ],
    );
  }

  /// 构建感悟
  Widget _buildFeeling() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('我的感悟', style: Get.textTheme.bodySmall?.copyWith(color: Colors.grey)),
        const SizedBox(height: 4),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 15), borderRadius: BorderRadius.circular(8)),
          child: Text(viewpoint.feeling, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5)),
        ),
      ],
    );
  }

  /// 构建底部
  Widget _buildFooter(BuildContext context) {
    final formattedDate = DateFormat('yyyy-MM-dd').format(viewpoint.createAt);

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Expanded(child: Text('添加时间：$formattedDate', style: Get.textTheme.bodySmall?.copyWith(color: Colors.grey))),
      ],
    );
  }
}
