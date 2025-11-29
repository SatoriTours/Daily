import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 日记搜索栏组件
///
/// 纯展示组件,通过参数接收数据和回调函数
class DiarySearchBar extends StatefulWidget {
  /// 搜索文本控制器
  final TextEditingController searchController;

  /// 搜索框焦点节点
  final FocusNode searchFocusNode;

  /// 关闭搜索栏回调
  final VoidCallback onClose;

  /// 执行搜索回调
  final Function(String) onSearch;

  /// 清除过滤回调
  final VoidCallback onClearFilters;

  const DiarySearchBar({
    super.key,
    required this.searchController,
    required this.searchFocusNode,
    required this.onClose,
    required this.onSearch,
    required this.onClearFilters,
  });

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
    _animController = AnimationController(vsync: this, duration: Animations.durationNormal);
    _fadeAnimation = CurvedAnimation(parent: _animController, curve: Curves.easeInOut);

    _animController.forward();

    // 自动聚焦搜索框
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _requestFocus();
    });

    // 初始化清除按钮状态
    _showClearButton = widget.searchController.text.isNotEmpty;

    // 添加文本变化监听器
    widget.searchController.addListener(_onTextChanged);

    logger.d('搜索栏组件初始化');
  }

  /// 文本变化监听器
  void _onTextChanged() {
    final isTextEmpty = widget.searchController.text.isEmpty;
    if (_showClearButton == isTextEmpty) {
      setState(() {
        _showClearButton = !isTextEmpty;
      });
    }
  }

  @override
  void dispose() {
    widget.searchController.removeListener(_onTextChanged);
    _animController.dispose();
    super.dispose();
  }

  /// 请求搜索框焦点
  void _requestFocus() {
    FocusScope.of(context).requestFocus(widget.searchFocusNode);
    widget.searchController.selection = TextSelection.fromPosition(
      TextPosition(offset: widget.searchController.text.length),
    );
  }

  /// 执行搜索
  void _performSearch() {
    logger.d('执行搜索: ${widget.searchController.text}');
    final query = widget.searchController.text;
    widget.onSearch(query);

    if (query.trim().isEmpty) {
      _handleClose();
    }
  }

  /// 清空搜索
  void _clearSearch() {
    logger.d('清空搜索');
    widget.searchController.clear();
    widget.onClearFilters();
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
        padding: Dimensions.paddingS,
        decoration: BoxDecoration(
          color: DiaryStyles.getCardBackgroundColor(context),
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
      icon: Icon(FeatherIcons.arrowLeft, color: DiaryStyles.getPrimaryTextColor(context), size: Dimensions.iconSizeM),
      onPressed: _handleClose,
      splashRadius: Dimensions.spacingL,
      tooltip: '返回',
    );
  }

  /// 构建搜索输入框
  Widget _buildSearchField() {
    return Container(
      height: 36,
      margin: Dimensions.paddingHorizontalXs,
      decoration: BoxDecoration(
        color: DiaryStyles.getInputBackgroundColor(context),
        borderRadius: BorderRadius.circular(18),
      ),
      child: TextField(
        controller: widget.searchController,
        focusNode: widget.searchFocusNode,
        decoration: InputDecoration(
          hintText: '搜索日记内容...',
          hintStyle: TextStyle(color: DiaryStyles.getSecondaryTextColor(context), fontSize: 14),
          border: InputBorder.none,
          contentPadding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
          isDense: true,
        ),
        style: TextStyle(color: DiaryStyles.getPrimaryTextColor(context), fontSize: 14),
        textInputAction: TextInputAction.search,
        onSubmitted: (_) => _performSearch(),
      ),
    );
  }

  /// 构建搜索按钮
  Widget _buildSearchButton() {
    return IconButton(
      icon: Icon(FeatherIcons.search, color: DiaryStyles.getAccentColor(context), size: Dimensions.iconSizeM),
      onPressed: _performSearch,
      splashRadius: Dimensions.spacingL,
      tooltip: '搜索',
    );
  }

  /// 构建清除按钮
  Widget _buildClearButton() {
    return AnimatedOpacity(
      opacity: _showClearButton ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: IconButton(
        icon: Icon(FeatherIcons.x, color: DiaryStyles.getPrimaryTextColor(context), size: Dimensions.iconSizeM),
        onPressed: _showClearButton ? _clearSearch : null,
        splashRadius: Dimensions.spacingL,
        tooltip: '清除',
      ),
    );
  }
}
