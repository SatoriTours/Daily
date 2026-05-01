import 'package:daily_satori/app_exports.dart';

import 'diary_card.dart';
import 'diary_empty_state.dart';
import 'diary_loading.dart';

/// 日记列表组件
///
/// 自管理 ScrollController
class DiaryList extends StatefulWidget {
  final List<DiaryModel> diaries;
  final bool isLoading;
  final Function(DiaryModel) onEdit;
  final Function(int) onDelete;

  const DiaryList({
    super.key,
    required this.diaries,
    this.isLoading = false,
    required this.onEdit,
    required this.onDelete,
  });

  static void scrollToTop(BuildContext context) {
    final state = context.findAncestorStateOfType<_DiaryListState>();
    state?._scrollToTop();
  }

  @override
  State<DiaryList> createState() => _DiaryListState();
}

class _DiaryListState extends State<DiaryList> {
  late final ScrollController _scrollController;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
  }

  void _scrollToTop() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        0,
        duration: Animations.durationNormal,
        curve: Curves.easeInOut,
      );
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    logger.d('构建日记列表: ${widget.diaries.length}条日记');

    if (widget.isLoading && widget.diaries.isEmpty) {
      return const DiaryLoading();
    }

    if (widget.diaries.isEmpty) {
      return const DiaryEmptyState();
    }

    return _buildDiaryListView(context);
  }

  Widget _buildDiaryListView(BuildContext context) {
    final sortedDiaries = List<DiaryModel>.from(widget.diaries)
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.only(
        left: Dimensions.spacingM,
        right: Dimensions.spacingM,
        top: Dimensions.spacingS,
        bottom: 80,
      ),
      itemCount: sortedDiaries.length,
      itemBuilder: (context, index) =>
          _buildDiaryCard(context, sortedDiaries[index]),
    );
  }

  Widget _buildDiaryCard(BuildContext context, DiaryModel diary) {
    return DiaryCard(
      diary: diary,
      onDelete: () => widget.onDelete(diary.id),
      onEdit: () => widget.onEdit(diary),
    );
  }
}
