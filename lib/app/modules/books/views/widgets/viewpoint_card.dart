import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/modules/diary/controllers/diary_controller.dart';
import 'package:daily_satori/app/modules/diary/views/widgets/diary_editor.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;
import 'package:flutter/services.dart';

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
    return Text(viewpoint.title, style: MyFontStyle.headlineSmall.copyWith(fontWeight: FontWeight.bold));
  }

  /// 构建内容
  Widget _buildContent() {
    return SelectableText(viewpoint.content, style: MyFontStyle.bodyLarge.copyWith(height: 1.5));
  }

  /// 构建案例
  Widget _buildExample(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.bookmark, size: 18, color: AppColors.primary(context)),
            const SizedBox(width: 8),
            Text('书籍案例', style: MyFontStyle.labelLarge.copyWith(color: AppColors.primary(context))),
          ],
        ),
        const SizedBox(height: 14),
        Text(viewpoint.example, style: MyFontStyle.bodyLarge),
      ],
    );
  }

  /// 构建底部
  Widget _buildFooter(BuildContext context, BookModel? book) {
    final formattedDate = DateFormat('yyyy-MM-dd').format(viewpoint.createAt);

    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Expanded(
          child: Row(
            children: [
              const Icon(Icons.calendar_today, size: 14, color: Colors.grey),
              const SizedBox(width: 6),
              Text(formattedDate, style: MyFontStyle.labelSmall.copyWith(color: Colors.grey)),
            ],
          ),
        ),
        const SizedBox(width: 12),
        Container(width: 1, height: 16, color: Colors.grey.withAlpha(80)),
        const SizedBox(width: 12),
        _buildQuickJournalButton(context, book),
        const SizedBox(width: 6),
        _buildMoreActionsButton(context, book),
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
          style: MyFontStyle.bodySmall.copyWith(color: Colors.grey),
        ),
      ],
    );
  }

  /// 一键记感想按钮
  Widget _buildQuickJournalButton(BuildContext context, BookModel? book) {
    final primary = AppColors.primary(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Tooltip(
      message: '将此观点的一些思考快速记录到日记',
      child: OutlinedButton.icon(
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          minimumSize: const Size(0, 0),
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          side: BorderSide(color: primary.withValues(alpha: isDark ? 0.9 : 0.6)),
          foregroundColor: primary,
          shape: const StadiumBorder(),
        ),
        onPressed: () => _startJournaling(context, book),
        icon: const Icon(Icons.edit_note, size: 16),
        label: const Text('记感想', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
      ),
    );
  }

  /// 更多操作：复制引用/复制跳转链接
  Widget _buildMoreActionsButton(BuildContext context, BookModel? book) {
    final primary = AppColors.primary(context);
    return PopupMenuButton<String>(
      tooltip: '更多操作',
      icon: Icon(Icons.more_horiz, size: 18, color: primary),
      itemBuilder: (context) => [
        PopupMenuItem<String>(value: 'copy_cite', child: _menuItemRow(Icons.format_quote, '复制引用')),
        PopupMenuItem<String>(value: 'copy_link', child: _menuItemRow(Icons.link, '复制跳转链接')),
      ],
      onSelected: (value) {
        switch (value) {
          case 'copy_cite':
            _copyCitation(context, book);
            break;
          case 'copy_link':
            _copyDeepLink(context);
            break;
        }
      },
    );
  }

  Widget _menuItemRow(IconData icon, String text) {
    return Row(children: [Icon(icon, size: 16), const SizedBox(width: 8), Text(text)]);
  }

  void _copyCitation(BuildContext context, BookModel? book) {
    final title = viewpoint.title.trim();
    final bookTitle = (book?.title ?? '').trim();
    final author = (book?.author ?? '').trim();
    final deepLink = 'app://books/viewpoint/${viewpoint.id}';

    final buffer = StringBuffer()
      ..writeln('> $title')
      ..writeln(bookTitle.isNotEmpty ? '来源：《$bookTitle》${author.isNotEmpty ? ' · $author' : ''}' : '')
      ..writeln('[跳转阅读 >>]($deepLink)');

    Clipboard.setData(ClipboardData(text: buffer.toString()));
    UIUtils.showSuccess('引用已复制');
  }

  void _copyDeepLink(BuildContext context) {
    final deepLink = 'app://books/viewpoint/${viewpoint.id}';
    Clipboard.setData(ClipboardData(text: deepLink));
    UIUtils.showSuccess('链接已复制');
  }

  /// 从当前观点打开日记编辑器并预填来源与跳转链接
  void _startJournaling(BuildContext context, BookModel? book) {
    final diaryController = Get.find<DiaryController>();
    final title = viewpoint.title.trim();
    final bookTitle = (book?.title ?? '').trim();
    final author = (book?.author ?? '').trim();
    // 简化内容：不再使用正文摘录

    final buffer = StringBuffer();
    buffer.writeln('观点：$title');
    if (bookTitle.isNotEmpty) {
      buffer.writeln('来源：《$bookTitle》${author.isNotEmpty ? ' · $author' : ''}');
    }
    buffer.writeln();
    // 保留内部深链用于来源胶囊与回跳，但不增加多余可见内容
    buffer.writeln('[](app://books/viewpoint/${viewpoint.id})');

    diaryController.contentController
      ..clear()
      ..text = buffer.toString()
      ..selection = TextSelection.collapsed(offset: buffer.length);

    // 打开日记编辑器（与日记页一致的样式）
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(controller: diaryController),
    );
  }
}
