import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:intl/intl.dart';

// ...existing imports...
/// 观点卡片组件
class ViewpointCard extends StatelessWidget {
  final BookViewpointModel viewpoint;
  final BookModel? book;
  const ViewpointCard({super.key, required this.viewpoint, required this.book});
  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 20),
            _buildTitle(),
            const SizedBox(height: 12),
            _buildViewpointBookInfo(context, book),
            const SizedBox(height: 20),
            _buildContent(),
            if (viewpoint.example.isNotEmpty) ...[const SizedBox(height: 20), _buildExample(context)],
            const SizedBox(height: 20),
            _buildFooter(context, book),
          ],
        ),
      ),
    );
  }

  /// 构建标题
  Widget _buildTitle() {
    return Text(viewpoint.title, style: AppTypography.headingMedium.copyWith(fontWeight: FontWeight.bold));
  }

  /// 构建内容
  Widget _buildContent() {
    return SelectableText(viewpoint.content, style: AppTypography.bodyLarge.copyWith(height: 1.5));
  }

  /// 构建案例
  Widget _buildExample(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.bookmark, size: 18, color: AppColors.getPrimary(context)),
            const SizedBox(width: 8),
            Text('书籍案例', style: AppTypography.labelLarge.copyWith(color: AppColors.getPrimary(context))),
          ],
        ),
        const SizedBox(height: 14),
        Text(viewpoint.example, style: AppTypography.bodyLarge),
      ],
    );
  }

  /// 构建底部
  Widget _buildFooter(BuildContext context, BookModel? book) {
    final formattedDate = DateFormat('yyyy-MM-dd').format(viewpoint.createdAt);
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Expanded(
          child: Row(
            children: [
              const Icon(Icons.calendar_today, size: 14, color: Colors.grey),
              const SizedBox(width: 6),
              Text(formattedDate, style: AppTypography.labelSmall.copyWith(color: Colors.grey)),
            ],
          ),
        ),
      ],
    );
  }

  /// 构建观点对应的书籍的名字和作者
  Widget _buildViewpointBookInfo(BuildContext context, BookModel? book) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Icon(Icons.menu_book, size: 14, color: Colors.grey),
        const SizedBox(width: 6),
        Text(
          book != null ? '《${book.title}》· ${book.author}' : '未知书籍',
          style: AppTypography.bodySmall.copyWith(color: Colors.grey),
        ),
      ],
    );
  }

  // 已移除底部“记感想”入口，改为在页面顶部/悬浮层提供统一入口
}
