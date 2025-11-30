import 'package:daily_satori/app_exports.dart';
import 'package:flutter/foundation.dart';
import '../controllers/home_controller.dart';
import 'package:daily_satori/app/pages/articles/views/articles_view.dart';
import 'package:daily_satori/app/pages/books/views/books_view.dart';
import 'package:daily_satori/app/pages/diary/views/diary_view.dart';
import 'package:daily_satori/app/pages/ai_chat/views/ai_chat_view.dart';
import 'package:daily_satori/app/pages/settings/views/settings_view.dart';

/// HomeView: 应用主页视图
/// 包含:
/// 1. 底部导航栏
/// 2. 主要内容区域（文章、日记、读书、AI助手、设置）
/// 3. 懒加载机制 - 只有首次访问时才加载页面
/// 4. 状态保持 - 切换回来时保持之前的滚动位置等状态
class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  static const String _tag = 'HomeView';

  @override
  Widget build(BuildContext context) {
    _logBuild();
    return Obx(() {
      final currentIndex = controller.currentIndex.value;
      _logPageSwitch(currentIndex);

      return Scaffold(
        body: _LazyIndexedStack(index: currentIndex, itemCount: 5, itemBuilder: (index) => _buildPage(index)),
        bottomNavigationBar: _buildNavigationBar(currentIndex),
      );
    });
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
        return const SettingsView();
      default:
        return const SizedBox.shrink();
    }
  }

  /// 构建底部导航栏
  Widget _buildNavigationBar(int currentIndex) {
    return BottomNavigationBar(
      currentIndex: currentIndex,
      onTap: controller.changePage,
      type: BottomNavigationBarType.fixed,
      items: [
        BottomNavigationBarItem(
          icon: Icon(Icons.article_outlined),
          activeIcon: Icon(Icons.article),
          label: 'nav.articles'.t,
        ),
        BottomNavigationBarItem(icon: Icon(Icons.book_outlined), activeIcon: Icon(Icons.book), label: 'nav.diary'.t),
        BottomNavigationBarItem(
          icon: Icon(Icons.menu_book_outlined),
          activeIcon: Icon(Icons.menu_book),
          label: 'nav.books'.t,
        ),
        BottomNavigationBarItem(
          icon: Icon(Icons.smart_toy_outlined),
          activeIcon: Icon(Icons.smart_toy),
          label: 'ai_chat.title'.t,
        ),
        BottomNavigationBarItem(
          icon: Icon(Icons.person_outline),
          activeIcon: Icon(Icons.person),
          label: 'nav.settings'.t,
        ),
      ],
    );
  }

  /// 记录页面构建日志
  void _logBuild() {
    if (kDebugMode) {
      logger.i('主页视图构建 [$_tag:${DateTime.now()}]');
    }
  }

  /// 记录页面切换日志
  void _logPageSwitch(int index) {
    if (kDebugMode) {
      final pages = ['nav.articles'.t, 'nav.diary'.t, 'nav.books'.t, 'ai_chat.title'.t, 'nav.settings'.t];
      logger.i('切换到${pages[index]} [$_tag:${DateTime.now()}]');
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
