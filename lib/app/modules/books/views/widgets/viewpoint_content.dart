import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/book_info.dart';
import 'package:daily_satori/app/modules/books/views/widgets/feeling_input.dart';
import 'package:daily_satori/app/modules/books/views/widgets/viewpoint_card.dart';
import 'package:daily_satori/app/modules/books/views/widgets/viewpoint_navigation.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 观点内容组件
class ViewpointContent extends StatefulWidget {
  final BooksController controller;
  final List<BookViewpointModel> viewpoints;
  final BookModel book;
  final int currentIndex;
  final Function(BookViewpointModel viewpoint)? onDeleteViewpoint;

  const ViewpointContent({
    super.key,
    required this.controller,
    required this.viewpoints,
    required this.book,
    required this.currentIndex,
    this.onDeleteViewpoint,
  });

  @override
  State<ViewpointContent> createState() => _ViewpointContentState();
}

class _ViewpointContentState extends State<ViewpointContent> {
  bool _isBookInfoExpanded = false;

  @override
  Widget build(BuildContext context) {
    if (widget.viewpoints.isEmpty) return const SizedBox();

    final viewpoint = widget.viewpoints[widget.currentIndex];

    return Column(
      children: [
        Expanded(
          child: GestureDetector(
            onHorizontalDragEnd: (details) {
              if (details.primaryVelocity == null) return;

              if (details.primaryVelocity! > 0) {
                // 右滑，显示上一个观点
                widget.controller.previousViewpoint();
              } else if (details.primaryVelocity! < 0) {
                // 左滑，显示下一个观点
                widget.controller.nextViewpoint();
              }
            },
            child: CustomScrollView(
              slivers: [
                // 顶部书籍信息和导航按钮组合
                SliverToBoxAdapter(child: _buildCompactHeader(context, viewpoint)),

                // 观点内容
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                  sliver: SliverToBoxAdapter(
                    child: ViewpointCard(
                      viewpoint: viewpoint,
                      onDelete: widget.onDeleteViewpoint != null ? () => widget.onDeleteViewpoint!(viewpoint) : null,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        // 底部感悟输入栏
        FeelingInput(controller: widget.controller),
      ],
    );
  }

  /// 构建紧凑的顶部区域，整合书籍信息和导航
  Widget _buildCompactHeader(BuildContext context, BookViewpointModel viewpoint) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        children: [
          // 第一行：书名和作者信息，右侧是页码导航
          InkWell(
            onTap: () {
              setState(() {
                _isBookInfoExpanded = !_isBookInfoExpanded;
              });
            },
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                children: [
                  // 左侧：书籍信息
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                widget.book.title,
                                style: Get.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.primary(context),
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Icon(
                              _isBookInfoExpanded ? Icons.expand_less : Icons.expand_more,
                              size: 20,
                              color: AppColors.primary(context),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '作者: ${widget.book.author}',
                          style: Get.textTheme.bodySmall,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),

                  // 右侧：紧凑的导航按钮
                  _buildCompactNavigation(context),
                ],
              ),
            ),
          ),

          // 展开的书籍详细信息
          AnimatedSize(
            duration: const Duration(milliseconds: 200),
            child: _isBookInfoExpanded ? _buildExpandedBookInfo(context) : const SizedBox(height: 0),
          ),

          const Divider(height: 24),
        ],
      ),
    );
  }

  /// 构建展开时显示的书籍详细信息
  Widget _buildExpandedBookInfo(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.cardBackground(context).withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.primary(context).withValues(alpha: 0.1), width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 书籍封面和基本信息
          Row(
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
                child: Center(
                  child: Icon(Icons.menu_book, size: 36, color: AppColors.primary(context).withValues(alpha: 0.5)),
                ),
              ),

              const SizedBox(width: 16),

              // 书籍详细信息
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.book.title, style: Get.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text('作者：${widget.book.author}', style: Get.textTheme.bodyMedium),
                    const SizedBox(height: 4),
                    Text('分类：${widget.book.category}', style: Get.textTheme.bodyMedium),
                    if (widget.book.introduction.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      Text('简介', style: Get.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ],
                ),
              ),
            ],
          ),

          // 书籍简介
          if (widget.book.introduction.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(widget.book.introduction, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5)),
          ],
        ],
      ),
    );
  }

  /// 构建紧凑型导航
  Widget _buildCompactNavigation(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // 上一个按钮
        IconButton(
          icon: Icon(
            Icons.arrow_back_ios,
            size: 16,
            color: widget.currentIndex > 0 ? AppColors.primary(context) : Colors.grey.withValues(alpha: 0.3),
          ),
          onPressed: widget.currentIndex > 0 ? widget.controller.previousViewpoint : null,
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
          visualDensity: VisualDensity.compact,
        ),

        // 页码显示
        Text(
          '${widget.currentIndex + 1}/${widget.viewpoints.length}',
          style: Get.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
        ),

        // 下一个按钮
        IconButton(
          icon: Icon(
            Icons.arrow_forward_ios,
            size: 16,
            color:
                widget.currentIndex < widget.viewpoints.length - 1
                    ? AppColors.primary(context)
                    : Colors.grey.withValues(alpha: 0.3),
          ),
          onPressed: widget.currentIndex < widget.viewpoints.length - 1 ? widget.controller.nextViewpoint : null,
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
          visualDensity: VisualDensity.compact,
        ),
      ],
    );
  }
}
