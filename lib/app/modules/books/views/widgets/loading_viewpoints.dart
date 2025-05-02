import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';

/// 加载观点组件
class LoadingViewpoints extends StatelessWidget {
  final BookModel book;

  const LoadingViewpoints({super.key, required this.book});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.auto_stories, size: 64, color: Colors.grey),
          const SizedBox(height: 16),
          Text('正在为《${book.title}》提取观点...', style: Get.textTheme.titleMedium),
          const SizedBox(height: 16),
          const CircularProgressIndicator(),
        ],
      ),
    );
  }
}
