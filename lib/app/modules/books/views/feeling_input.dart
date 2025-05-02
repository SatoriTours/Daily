import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 感悟输入组件
class FeelingInput extends StatefulWidget {
  final BooksController controller;

  const FeelingInput({super.key, required this.controller});

  @override
  State<FeelingInput> createState() => _FeelingInputState();
}

class _FeelingInputState extends State<FeelingInput> {
  late FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

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
        children: [Expanded(child: _buildTextField(context)), const SizedBox(width: 8), _buildSendButton(context)],
      ),
    );
  }

  /// 构建文本输入框
  Widget _buildTextField(BuildContext context) {
    return TextField(
      controller: widget.controller.feelingController,
      focusNode: _focusNode,
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
      keyboardType: TextInputType.multiline,
      textInputAction: TextInputAction.newline,
    );
  }

  /// 构建发送按钮
  Widget _buildSendButton(BuildContext context) {
    return Material(
      color: AppColors.primary(context),
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: () {
          widget.controller.saveFeeling();
          FocusScope.of(context).requestFocus(_focusNode);
        },
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.all(10),
          child: const Icon(Icons.send, color: Colors.white, size: 20),
        ),
      ),
    );
  }
}
