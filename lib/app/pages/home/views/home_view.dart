import 'package:daily_satori/app_exports.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/pages/home/providers/home_controller_provider.dart';
import 'package:daily_satori/app/pages/articles/views/articles_view.dart';
import 'package:daily_satori/app/pages/books/views/books_view.dart';
import 'package:daily_satori/app/pages/diary/views/diary_view.dart';
import 'package:daily_satori/app/pages/ai_chat/views/ai_chat_view.dart';
import 'package:daily_satori/app/pages/weekly_summary/views/weekly_summary_view.dart';

/// HomeView: 应用主页视图
/// 包含:
/// 1. 底部导航栏
/// 2. 主要内容区域（文章、日记、读书、AI助手、设置）
/// 3. 懒加载机制 - 只有首次访问时才加载页面
/// 4. 状态保持 - 切换回来时保持之前的滚动位置等状态
class HomeView extends ConsumerWidget {
  const HomeView({super.key});

  static const String _tag = 'HomeView';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    _logBuild();

    // 确保 articleStateProvider 在应用启动时就被初始化
    // 这样它就能监听服务层的文章更新事件
    ref.watch(articleStateProvider);

    final state = ref.watch(homeControllerProvider);
    _logPageSwitch(state.currentIndex);

    return Scaffold(
      body: _LazyIndexedStack(index: state.currentIndex, itemCount: 5, itemBuilder: (index) => _buildPage(index)),
      bottomNavigationBar: _buildNavigationBar(state.currentIndex, ref),
    );
  }

  /// 根据索引构建对应页面
  Widget _buildPage(int index) {
    switch (index) {
      case 0:
        return const ArticlesView();
      case 1:
        return const DiaryView();
      case 2:
        return const BooksView();
      case 3:
        return const AIChatView();
      case 4:
        return const WeeklySummaryView();
      default:
        return const SizedBox.shrink();
    }
  }

  /// 构建底部导航栏
  Widget _buildNavigationBar(int currentIndex, WidgetRef ref) {
    return BottomNavigationBar(
      currentIndex: currentIndex,
      onTap: (index) => ref.read(homeControllerProvider.notifier).changePage(index),
      type: BottomNavigationBarType.fixed,
      items: [
        BottomNavigationBarItem(
          icon: const Icon(Icons.article_outlined),
          activeIcon: const Icon(Icons.article),
          label: 'nav.articles'.t,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.book_outlined),
          activeIcon: const Icon(Icons.book),
          label: 'nav.diary'.t,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.menu_book_outlined),
          activeIcon: const Icon(Icons.menu_book),
          label: 'nav.books'.t,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.smart_toy_outlined),
          activeIcon: const Icon(Icons.smart_toy),
          label: 'ai_chat.title'.t,
        ),
        BottomNavigationBarItem(
          icon: const Icon(Icons.person_outlined),
          activeIcon: const Icon(Icons.person),
          label: 'nav.settings'.t,
        ),
      ],
    );
  }

  /// 记录页面构建日志
  void _logBuild() {
    if (kDebugMode) {
      logger.i('主页视图构建 [$_tag]');
    }
  }

  /// 记录页面切换日志
  void _logPageSwitch(int index) {
    if (kDebugMode) {
      final pages = ['nav.articles'.t, 'nav.diary'.t, 'nav.books'.t, 'ai_chat.title'.t, 'weekly_summary.title'.t];
      logger.i('切换到 ${pages[index]} 页面');
    }
  }
}

// ============================================================================
// 懒加载 IndexedStack 组件
// ============================================================================

/// 懒加载 IndexedStack
///
/// 与普通 IndexedStack 不同，此组件只在首次访问某个页面时才创建该页面，
/// 同时通过 AutomaticKeepAliveClientMixin 保持已创建页面的状态。
class _LazyIndexedStack extends StatefulWidget {
  /// 当前显示的页面索引
  final int index;

  /// 总页面数量
  final int itemCount;

  /// 页面构建器
  final Widget Function(int index) itemBuilder;

  const _LazyIndexedStack({required this.index, required this.itemCount, required this.itemBuilder});

  @override
  State<_LazyIndexedStack> createState() => _LazyIndexedStackState();
}

class _LazyIndexedStackState extends State<_LazyIndexedStack> {
  /// 记录哪些页面已经被加载过
  late List<bool> _loadedPages;

  @override
  void initState() {
    super.initState();
    _loadedPages = List.filled(widget.itemCount, false);
    // 首页默认加载
    _loadedPages[widget.index] = true;
    if (kDebugMode) {
      logger.d('[_LazyIndexedStack] 初始化，首先加载页面: ${widget.index}');
    }
  }

  @override
  void didUpdateWidget(_LazyIndexedStack oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 当切换到新页面时，标记该页面为已加载
    if (!_loadedPages[widget.index]) {
      _loadedPages[widget.index] = true;
      if (kDebugMode) {
        logger.d('[_LazyIndexedStack] 懒加载页面: ${widget.index}');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return IndexedStack(
      index: widget.index,
      children: List.generate(widget.itemCount, (index) {
        // 只有已加载的页面才真正构建，未加载的用占位符
        if (_loadedPages[index]) {
          return _KeepAliveWrapper(child: widget.itemBuilder(index));
        }
        return const SizedBox.shrink();
      }),
    );
  }
}

/// KeepAlive 包装器
///
/// 使用 AutomaticKeepAliveClientMixin 保持子组件状态，
/// 防止页面切换时丢失滚动位置等状态。
class _KeepAliveWrapper extends StatefulWidget {
  final Widget child;

  const _KeepAliveWrapper({required this.child});

  @override
  State<_KeepAliveWrapper> createState() => _KeepAliveWrapperState();
}

class _KeepAliveWrapperState extends State<_KeepAliveWrapper> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return widget.child;
  }
}
