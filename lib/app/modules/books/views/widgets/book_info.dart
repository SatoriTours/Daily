import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 书籍信息组件
class BookInfo extends StatelessWidget {
  final BookModel book;
  final int viewpointId;

  const BookInfo({super.key, required this.book, required this.viewpointId});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 书籍封面
        Container(
          width: 80,
          height: 120,
          decoration: BoxDecoration(
            color: AppColors.cardBackground(context),
            borderRadius: BorderRadius.circular(8),
            boxShadow: [
              BoxShadow(color: Colors.black.withValues(alpha: 0.1), blurRadius: 4, offset: const Offset(0, 2)),
            ],
          ),
          child:
              viewpointId == 0
                  ? Center(
                    child: Icon(Icons.menu_book, size: 36, color: AppColors.primary(context).withValues(alpha: 0.5)),
                  )
                  : null,
        ),
        const SizedBox(width: 16),
        // 书籍信息
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(book.title, style: Get.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Text('作者：${book.author}', style: Get.textTheme.bodyMedium),
              const SizedBox(height: 4),
              Text('分类：${book.category}', style: Get.textTheme.bodyMedium),
            ],
          ),
        ),
      ],
    );
  }
}
