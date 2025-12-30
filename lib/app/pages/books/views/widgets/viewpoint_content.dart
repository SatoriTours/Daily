import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/books/views/widgets/viewpoint_card.dart';
import 'package:daily_satori/app/providers/books_controller_provider.dart';
import 'package:daily_satori/app/providers/diary_controller_provider.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/diary_editor.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

/// 观点内容组件
class ViewpointContent extends ConsumerStatefulWidget {
  final List<BookViewpointModel> viewpoints;
  final BookModel book;
  final int currentIndex;
  final Function(BookViewpointModel viewpoint)? onDeleteViewpoint;
  const ViewpointContent({
    super.key,
    required this.viewpoints,
    required this.book,
    required this.currentIndex,
    this.onDeleteViewpoint,
  });
  @override
  ConsumerState<ViewpointContent> createState() => _ViewpointContentState();
}

class _ViewpointContentState extends ConsumerState<ViewpointContent> {
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
      _pageController.animateToPage(widget.currentIndex, duration: Animations.durationNormal, curve: Curves.easeInOut);
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
    return Column(
      children: [
        _buildHeader(context),
        Expanded(child: _buildViewpointPageView()),
      ],
    );
  }

  /// 构建水平滑动的观点视图
  Widget _buildViewpointPageView() {
    return PageView.builder(
      controller: _pageController,
      onPageChanged: (index) {
        ref.read(booksControllerProvider.notifier).goToViewpointIndex(index);
      },
      itemCount: widget.viewpoints.length,
      itemBuilder: (context, index) {
        final viewpoint = widget.viewpoints[index];
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            Dimensions.spacingM,
            Dimensions.spacingS,
            Dimensions.spacingM,
            Dimensions.spacingM,
          ),
          child: ViewpointCard(viewpoint: viewpoint, book: widget.book),
        );
      },
    );
  }

  /// 构建顶部区域
  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(Dimensions.spacingM, Dimensions.spacingS + 4, Dimensions.spacingM, 0),
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
      borderRadius: Dimensions.borderRadiusS,
      child: Padding(
        padding: Dimensions.paddingVerticalXs,
        child: Row(
          children: [
            Expanded(child: _buildBookBasicInfo(context)),
            _buildQuickJournalButton(context),
            Dimensions.horizontalSpacerS,
            _buildNavigationButtons(context),
          ],
        ),
      ),
    );
  }

  /// 顶部"记感想"按钮，减少滚动
  Widget _buildQuickJournalButton(BuildContext context) {
    final primary = AppColors.getPrimary(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return OutlinedButton.icon(
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingS + 2, vertical: Dimensions.spacingXs + 2),
        minimumSize: const Size(0, 0),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        side: BorderSide(color: primary.withValues(alpha: isDark ? Opacities.veryLowOpaque : Opacities.higherOpaque)),
        foregroundColor: primary,
        shape: const StadiumBorder(),
      ),
      onPressed: _openJournalForCurrentViewpoint,
      icon: const Icon(Icons.edit_note, size: Dimensions.iconSizeXs),
      label: const Text('记感想', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
    );
  }

  void _openJournalForCurrentViewpoint() {
    final idx = ref.read(booksControllerProvider).currentViewpointIndex;
    if (idx < 0 || idx >= widget.viewpoints.length) return;
    final vp = widget.viewpoints[idx];
    final diaryState = ref.read(diaryControllerProvider);
    final contentController = diaryState.contentController;
    if (contentController == null) return;

    final title = vp.title.trim();
    final bookTitle = widget.book.title.trim();
    final author = widget.book.author.trim();
    final buffer = StringBuffer();
    buffer.writeln('观点：$title');
    if (bookTitle.isNotEmpty) {
      buffer.writeln('来源：《$bookTitle》${author.isNotEmpty ? ' · $author' : ''}');
    }
    buffer.writeln();
    // 添加隐藏深链，供来源胶囊识别与回跳使用
    buffer.writeln('[](app://books/viewpoint/${vp.id})');
    contentController
      ..clear()
      ..text = buffer.toString()
      ..selection = TextSelection.collapsed(offset: buffer.length);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => const DiaryEditor(),
    );
  }

  /// 构建书籍基本信息
  Widget _buildBookBasicInfo(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildBookTitleRow(context),
        const SizedBox(height: Dimensions.spacingXs / 2),
        _buildAuthorInfo(context),
      ],
    );
  }

  /// 构建书籍标题行
  Widget _buildBookTitleRow(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            widget.book.title,
            style: AppTheme.getTextTheme(
              context,
            ).titleMedium?.copyWith(fontWeight: FontWeight.bold, color: AppColors.getPrimary(context)),
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
      size: Dimensions.iconSizeM,
      color: AppColors.getPrimary(context),
    );
  }

  /// 构建作者信息
  Widget _buildAuthorInfo(BuildContext context) {
    return Text(
      '作者: ${widget.book.author}',
      style: AppTheme.getTextTheme(context).bodySmall,
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
        size: Dimensions.iconSizeXs,
        color: canGoPrevious ? AppColors.getPrimary(context) : Colors.grey.withValues(alpha: Opacities.high),
      ),
      onPressed: canGoPrevious ? ref.read(booksControllerProvider.notifier).previousViewpoint : null,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: Dimensions.chipHeight, minHeight: Dimensions.chipHeight),
      visualDensity: VisualDensity.compact,
    );
  }

  /// 构建页码计数器
  Widget _buildPageCounter(BuildContext context) {
    return Text(
      '${widget.currentIndex + 1}/${widget.viewpoints.length}',
      style: AppTheme.getTextTheme(context).bodyMedium?.copyWith(fontWeight: FontWeight.w500),
    );
  }

  /// 构建下一页按钮
  Widget _buildNextButton(BuildContext context) {
    final bool canGoNext = widget.currentIndex < widget.viewpoints.length - 1;
    return IconButton(
      icon: Icon(
        Icons.arrow_forward_ios,
        size: Dimensions.iconSizeXs,
        color: canGoNext ? AppColors.getPrimary(context) : Colors.grey.withValues(alpha: Opacities.high),
      ),
      onPressed: canGoNext ? ref.read(booksControllerProvider.notifier).nextViewpoint : null,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: Dimensions.chipHeight, minHeight: Dimensions.chipHeight),
      visualDensity: VisualDensity.compact,
    );
  }

  /// 构建展开的书籍详细信息
  Widget _buildExpandedBookInfo(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: Dimensions.spacingS + 4),
      padding: Dimensions.paddingM,
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
      color: AppColors.getSurface(context).withValues(alpha: Opacities.half),
      borderRadius: Dimensions.borderRadiusM,
      border: Border.all(color: AppColors.getPrimary(context).withValues(alpha: Opacities.low), width: 1),
    );
  }

  /// 构建书籍信息顶部行（封面和详情）
  Widget _buildBookInfoTopRow(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [_buildBookCover(context), Dimensions.horizontalSpacerM, _buildBookDetails(context)],
    );
  }

  /// 构建书籍封面
  Widget _buildBookCover(BuildContext context) {
    return Container(
      width: 80,
      height: 120,
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: Dimensions.borderRadiusS,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: Opacities.low),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Center(
        child: Icon(
          Icons.menu_book,
          size: Dimensions.iconSizeXxl - 12,
          color: AppColors.getPrimary(context).withValues(alpha: Opacities.half),
        ),
      ),
    );
  }

  /// 构建书籍详细信息
  Widget _buildBookDetails(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildBookTitle(context),
          Dimensions.verticalSpacerS,
          _buildBookAuthor(context),
          const SizedBox(height: Dimensions.spacingXs),
          _buildBookCategory(context),
          _buildIntroductionTitle(context),
        ],
      ),
    );
  }

  /// 构建书籍标题
  Widget _buildBookTitle(BuildContext context) {
    return Text(
      widget.book.title,
      style: AppTheme.getTextTheme(context).titleMedium?.copyWith(fontWeight: FontWeight.bold),
    );
  }

  /// 构建书籍作者信息
  Widget _buildBookAuthor(BuildContext context) {
    return Text('作者：${widget.book.author}', style: AppTheme.getTextTheme(context).bodyMedium);
  }

  /// 构建书籍分类信息
  Widget _buildBookCategory(BuildContext context) {
    return Text('分类：${widget.book.category}', style: AppTheme.getTextTheme(context).bodyMedium);
  }

  /// 构建简介标题（如果有简介）
  Widget _buildIntroductionTitle(BuildContext context) {
    if (widget.book.introduction.isEmpty) {
      return const SizedBox();
    }
    return Padding(
      padding: const EdgeInsets.only(top: Dimensions.spacingS + 4),
      child: Text('简介', style: AppTheme.getTextTheme(context).titleSmall?.copyWith(fontWeight: FontWeight.bold)),
    );
  }

  /// 构建书籍简介内容
  Widget _buildBookIntroduction(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: Dimensions.spacingS + 4),
      child: Text(widget.book.introduction, style: AppTheme.getTextTheme(context).bodyMedium?.copyWith(height: 1.5)),
    );
  }
}
