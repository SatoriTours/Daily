import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';

import '../../controllers/diary_controller.dart';

/// 日记搜索栏组件
class DiarySearchBar extends StatefulWidget {
  final DiaryController controller;
  final VoidCallback onClose;

  const DiarySearchBar({super.key, required this.controller, required this.onClose});

  @override
  State<DiarySearchBar> createState() => _DiarySearchBarState();
}

class _DiarySearchBarState extends State<DiarySearchBar> with SingleTickerProviderStateMixin {
  late AnimationController _animController;
  late Animation<double> _fadeAnimation;
  bool _showClearButton = false;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(vsync: this, duration: const Duration(milliseconds: 300));
    _fadeAnimation = CurvedAnimation(parent: _animController, curve: Curves.easeInOut);

    _animController.forward();

    // 自动聚焦搜索框
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _requestFocus();
    });

    // 初始化清除按钮状态
    _showClearButton = widget.controller.searchController.text.isNotEmpty;

    // 添加文本变化监听器
    widget.controller.searchController.addListener(_onTextChanged);

    logger.d('搜索栏组件初始化');
  }

  /// 文本变化监听器
  void _onTextChanged() {
    final isTextEmpty = widget.controller.searchController.text.isEmpty;
    if (_showClearButton == isTextEmpty) {
      setState(() {
        _showClearButton = !isTextEmpty;
      });
    }
  }

  @override
  void dispose() {
    widget.controller.searchController.removeListener(_onTextChanged);
    _animController.dispose();
    super.dispose();
  }

  /// 请求搜索框焦点
  void _requestFocus() {
    FocusScope.of(context).requestFocus(widget.controller.searchFocusNode);
    widget.controller.searchController.selection = TextSelection.fromPosition(
      TextPosition(offset: widget.controller.searchController.text.length),
    );
  }

  /// 执行搜索
  void _performSearch() {
    logger.d('执行搜索: ${widget.controller.searchController.text}');
    final query = widget.controller.searchController.text;
    widget.controller.search(query);

    if (query.trim().isEmpty) {
      _handleClose();
    }
  }

  /// 清空搜索
  void _clearSearch() {
    logger.d('清空搜索');
    widget.controller.searchController.clear();
    widget.controller.clearFilters();
    widget.onClose();
  }

  /// 处理关闭搜索栏
  void _handleClose() {
    logger.d('关闭搜索栏');
    _animController.reverse().then((_) {
      widget.onClose();
    });
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _fadeAnimation,
      child: Container(
        height: 60,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          color: DiaryStyle.cardColor(context),
          boxShadow: [BoxShadow(color: Colors.black.withAlpha(15), offset: const Offset(0, 2), blurRadius: 4)],
        ),
        child: Row(
          children: [
            _buildBackButton(),
            Expanded(child: _buildSearchField()),
            _buildSearchButton(),
            _buildClearButton(),
          ],
        ),
      ),
    );
  }

  /// 构建返回按钮
  Widget _buildBackButton() {
    return IconButton(
      icon: Icon(FeatherIcons.arrowLeft, color: DiaryStyle.primaryTextColor(context), size: 20),
      onPressed: _handleClose,
      splashRadius: 24,
      tooltip: '返回',
    );
  }

  /// 构建搜索输入框
  Widget _buildSearchField() {
    return Container(
      height: 36,
      margin: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: DiaryStyle.inputBackgroundColor(context),
        borderRadius: BorderRadius.circular(18),
      ),
      child: TextField(
        controller: widget.controller.searchController,
        focusNode: widget.controller.searchFocusNode,
        decoration: InputDecoration(
          hintText: '搜索日记内容...',
          hintStyle: TextStyle(color: DiaryStyle.secondaryTextColor(context), fontSize: 14),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          isDense: true,
        ),
        style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontSize: 14),
        textInputAction: TextInputAction.search,
        onSubmitted: (_) => _performSearch(),
      ),
    );
  }

  /// 构建搜索按钮
  Widget _buildSearchButton() {
    return IconButton(
      icon: Icon(FeatherIcons.search, color: DiaryStyle.accentColor(context), size: 20),
      onPressed: _performSearch,
      splashRadius: 24,
      tooltip: '搜索',
    );
  }

  /// 构建清除按钮
  Widget _buildClearButton() {
    return AnimatedOpacity(
      opacity: _showClearButton ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: IconButton(
        icon: Icon(FeatherIcons.x, color: DiaryStyle.primaryTextColor(context), size: 20),
        onPressed: _showClearButton ? _clearSearch : null,
        splashRadius: 24,
        tooltip: '清除',
      ),
    );
  }
}
