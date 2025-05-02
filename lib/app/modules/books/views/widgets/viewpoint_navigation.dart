import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';

/// 观点导航组件
class ViewpointNavigation extends StatelessWidget {
  final BooksController controller;
  final int currentIndex;
  final int total;

  const ViewpointNavigation({super.key, required this.controller, required this.currentIndex, required this.total});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back_ios),
            onPressed: currentIndex > 0 ? controller.previousViewpoint : null,
          ),
          Text('${currentIndex + 1}/$total', style: Get.textTheme.titleMedium),
          IconButton(
            icon: const Icon(Icons.arrow_forward_ios),
            onPressed: currentIndex < total - 1 ? controller.nextViewpoint : null,
          ),
        ],
      ),
    );
  }
}
