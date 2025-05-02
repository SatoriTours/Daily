import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 感悟输入组件
class FeelingInput extends StatelessWidget {
  final BooksController controller;

  const FeelingInput({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, -1))],
        border: Border(top: BorderSide(color: Colors.grey.withValues(alpha: 0.2), width: 1)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: TextField(
              controller: controller.feelingController,
              decoration: InputDecoration(
                hintText: '写下你的感悟...',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(20), borderSide: BorderSide.none),
                filled: true,
                fillColor: AppColors.cardBackground(context),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                isDense: true,
              ),
              minLines: 1,
              maxLines: 4,
              style: Get.textTheme.bodyMedium,
            ),
          ),
          const SizedBox(width: 8),
          Material(
            color: AppColors.primary(context),
            borderRadius: BorderRadius.circular(20),
            child: InkWell(
              onTap: controller.saveFeeling,
              borderRadius: BorderRadius.circular(20),
              child: Container(
                padding: const EdgeInsets.all(10),
                child: Icon(Icons.send, color: Colors.white, size: 20),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
