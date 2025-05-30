import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/viewpoint_card.dart';
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
  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: widget.currentIndex);
  }

  @override
  void didUpdateWidget(ViewpointContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.currentIndex != widget.currentIndex) {
      _pageController.animateToPage(
        widget.currentIndex,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.viewpoints.isEmpty) return const SizedBox();
    return Column(children: [_buildHeader(context), Expanded(child: _buildViewpointPageView())]);
  }

  /// 构建水平滑动的观点视图
  Widget _buildViewpointPageView() {
    return PageView.builder(
      controller: _pageController,
      onPageChanged: (index) {
        widget.controller.currentViewpointIndex.value = index;
      },
      itemCount: widget.viewpoints.length,
      itemBuilder: (context, index) {
        final viewpoint = widget.viewpoints[index];
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: ViewpointCard(viewpoint: viewpoint, book: widget.book),
        );
      },
    );
  }

  /// 构建顶部区域
  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        children: [_buildBookInfoRow(context), _buildExpandedInfoSection(context), const Divider(height: 24)],
      ),
    );
  }

  /// 构建展开信息过渡动画区域
  Widget _buildExpandedInfoSection(BuildContext context) {
    return AnimatedSize(
      duration: const Duration(milliseconds: 200),
      child: _isBookInfoExpanded ? _buildExpandedBookInfo(context) : const SizedBox(height: 0),
    );
  }

  /// 构建书籍信息行
  Widget _buildBookInfoRow(BuildContext context) {
    return InkWell(
      onTap: () {
        setState(() {
          _isBookInfoExpanded = !_isBookInfoExpanded;
        });
      },
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [Expanded(child: _buildBookBasicInfo(context)), _buildNavigationButtons(context)]),
      ),
    );
  }

  /// 构建书籍基本信息
  Widget _buildBookBasicInfo(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_buildBookTitleRow(context), const SizedBox(height: 2), _buildAuthorInfo(context)],
    );
  }

  /// 构建书籍标题行
  Widget _buildBookTitleRow(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            widget.book.title,
            style: Get.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: AppColors.primary(context)),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        _buildExpandIcon(context),
      ],
    );
  }

  /// 构建展开/收起图标
  Widget _buildExpandIcon(BuildContext context) {
    return Icon(
      _isBookInfoExpanded ? Icons.expand_less : Icons.expand_more,
      size: 20,
      color: AppColors.primary(context),
    );
  }

  /// 构建作者信息
  Widget _buildAuthorInfo(BuildContext context) {
    return Text(
      '作者: ${widget.book.author}',
      style: Get.textTheme.bodySmall,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }

  /// 构建导航按钮组
  Widget _buildNavigationButtons(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [_buildPreviousButton(context), _buildPageCounter(context), _buildNextButton(context)],
    );
  }

  /// 构建上一页按钮
  Widget _buildPreviousButton(BuildContext context) {
    final bool canGoPrevious = widget.currentIndex > 0;
    return IconButton(
      icon: Icon(
        Icons.arrow_back_ios,
        size: 16,
        color: canGoPrevious ? AppColors.primary(context) : Colors.grey.withValues(alpha: 0.3),
      ),
      onPressed: canGoPrevious ? widget.controller.previousViewpoint : null,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
      visualDensity: VisualDensity.compact,
    );
  }

  /// 构建页码计数器
  Widget _buildPageCounter(BuildContext context) {
    return Text(
      '${widget.currentIndex + 1}/${widget.viewpoints.length}',
      style: Get.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
    );
  }

  /// 构建下一页按钮
  Widget _buildNextButton(BuildContext context) {
    final bool canGoNext = widget.currentIndex < widget.viewpoints.length - 1;
    return IconButton(
      icon: Icon(
        Icons.arrow_forward_ios,
        size: 16,
        color: canGoNext ? AppColors.primary(context) : Colors.grey.withValues(alpha: 0.3),
      ),
      onPressed: canGoNext ? widget.controller.nextViewpoint : null,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
      visualDensity: VisualDensity.compact,
    );
  }

  /// 构建展开的书籍详细信息
  Widget _buildExpandedBookInfo(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(16),
      decoration: _buildExpandedInfoDecoration(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildBookInfoTopRow(context),
          if (widget.book.introduction.isNotEmpty) _buildBookIntroduction(context),
        ],
      ),
    );
  }

  /// 构建展开信息的装饰样式
  BoxDecoration _buildExpandedInfoDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.cardBackground(context).withValues(alpha: 0.5),
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: AppColors.primary(context).withValues(alpha: 0.1), width: 1),
    );
  }

  /// 构建书籍信息顶部行（封面和详情）
  Widget _buildBookInfoTopRow(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_buildBookCover(context), const SizedBox(width: 16), _buildBookDetails(context)],
    );
  }

  /// 构建书籍封面
  Widget _buildBookCover(BuildContext context) {
    return Container(
      width: 80,
      height: 120,
      decoration: BoxDecoration(
        color: AppColors.cardBackground(context),
        borderRadius: BorderRadius.circular(8),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.1), blurRadius: 4, offset: const Offset(0, 2))],
      ),
      child: Center(child: Icon(Icons.menu_book, size: 36, color: AppColors.primary(context).withValues(alpha: 0.5))),
    );
  }

  /// 构建书籍详细信息
  Widget _buildBookDetails(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildBookTitle(context),
          const SizedBox(height: 8),
          _buildBookAuthor(context),
          const SizedBox(height: 4),
          _buildBookCategory(context),
          _buildIntroductionTitle(context),
        ],
      ),
    );
  }

  /// 构建书籍标题
  Widget _buildBookTitle(BuildContext context) {
    return Text(widget.book.title, style: Get.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold));
  }

  /// 构建书籍作者信息
  Widget _buildBookAuthor(BuildContext context) {
    return Text('作者：${widget.book.author}', style: Get.textTheme.bodyMedium);
  }

  /// 构建书籍分类信息
  Widget _buildBookCategory(BuildContext context) {
    return Text('分类：${widget.book.category}', style: Get.textTheme.bodyMedium);
  }

  /// 构建简介标题（如果有简介）
  Widget _buildIntroductionTitle(BuildContext context) {
    if (widget.book.introduction.isEmpty) {
      return const SizedBox();
    }
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Text('简介', style: Get.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
    );
  }

  /// 构建书籍简介内容
  Widget _buildBookIntroduction(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Text(widget.book.introduction, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5)),
    );
  }
}
