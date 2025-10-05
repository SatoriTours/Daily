import 'package:daily_satori/app_exports.dart';

import 'diary_card.dart';
import 'diary_empty_state.dart';
import 'diary_loading.dart';

/// 日记列表组件
///
/// 这是一个纯展示组件,通过参数接收数据和回调函数,不直接依赖Controller
class DiaryList extends StatelessWidget {
  /// 日记列表数据
  final List<DiaryModel> diaries;

  /// 加载状态
  final bool isLoading;

  /// 滚动控制器
  final ScrollController? scrollController;

  /// 编辑日记回调
  final Function(DiaryModel) onEdit;

  /// 删除日记回调
  final Function(int) onDelete;

  const DiaryList({
    super.key,
    required this.diaries,
    required this.isLoading,
    required this.onEdit,
    required this.onDelete,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    logger.d('构建日记列表: ${diaries.length}条日记');

    // 加载状态
    if (isLoading && diaries.isEmpty) {
      return const DiaryLoading();
    }

    // 空状态
    if (diaries.isEmpty) {
      return const DiaryEmptyState();
    }

    // 有内容状态
    return _buildDiaryListView(context);
  }

  /// 构建日记列表视图
  Widget _buildDiaryListView(BuildContext context) {
    // 获取日记并按创建时间降序排序（最新的在前面）
    final sortedDiaries = List<DiaryModel>.from(diaries)..sort((a, b) => b.createdAt.compareTo(a.createdAt));

    return ListView.builder(
      controller: scrollController,
      padding: const EdgeInsets.only(left: 16, right: 16, top: 8, bottom: 80),
      itemCount: sortedDiaries.length,
      itemBuilder: (context, index) => _buildDiaryCard(context, sortedDiaries[index]),
    );
  }

  /// 构建单个日记卡片
  Widget _buildDiaryCard(BuildContext context, DiaryModel diary) {
    return DiaryCard(diary: diary, onDelete: () => onDelete(diary.id), onEdit: () => onEdit(diary));
  }
}
