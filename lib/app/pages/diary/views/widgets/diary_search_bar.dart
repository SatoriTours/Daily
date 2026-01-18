import 'package:daily_satori/app_exports.dart';

/// 日记搜索栏组件
///
/// 自管理 TextEditingController 和 FocusNode
class DiarySearchBar extends StatefulWidget {
  final VoidCallback onClose;
  final Function(String) onSearch;
  final VoidCallback onClearFilters;

  const DiarySearchBar({
    super.key,
    required this.onClose,
    required this.onSearch,
    required this.onClearFilters,
  });

  @override
  State<DiarySearchBar> createState() => _DiarySearchBarState();
}

class _DiarySearchBarState extends State<DiarySearchBar>
    with SingleTickerProviderStateMixin {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;
  late final AnimationController _animController;
  late final Animation<double> _fadeAnimation;
  bool _showClearButton = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
    _focusNode = FocusNode();

    _animController = AnimationController(
      vsync: this,
      duration: Animations.durationNormal,
    );
    _fadeAnimation = CurvedAnimation(
      parent: _animController,
      curve: Curves.easeInOut,
    );
    _animController.forward();

    _showClearButton = _controller.text.isNotEmpty;
    _controller.addListener(_onTextChanged);

    logger.d('搜索栏组件初始化');
  }

  void _onTextChanged() {
    final isTextEmpty = _controller.text.isEmpty;
    if (_showClearButton != isTextEmpty) {
      setState(() {
        _showClearButton = !isTextEmpty;
      });
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onTextChanged);
    _controller.dispose();
    _focusNode.dispose();
    _animController.dispose();
    super.dispose();
  }

  void _performSearch() {
    logger.d('执行搜索: ${_controller.text}');
    final query = _controller.text;
    widget.onSearch(query);

    if (query.trim().isEmpty) {
      _handleClose();
    }
  }

  void _clearSearch() {
    logger.d('清空搜索');
    _controller.clear();
    widget.onClearFilters();
    widget.onClose();
  }

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
          boxShadow: [
            BoxShadow(
              color: Colors.black.withAlpha(15),
              offset: const Offset(0, 2),
              blurRadius: 4,
            ),
          ],
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

  Widget _buildBackButton() {
    return IconButton(
      icon: Icon(
        FeatherIcons.arrowLeft,
        color: DiaryStyles.getPrimaryTextColor(context),
        size: Dimensions.iconSizeM,
      ),
      onPressed: _handleClose,
      splashRadius: Dimensions.spacingL,
      tooltip: '返回',
    );
  }

  Widget _buildSearchField() {
    return Container(
      height: 36,
      margin: Dimensions.paddingHorizontalXs,
      decoration: BoxDecoration(
        color: DiaryStyles.getInputBackgroundColor(context),
        borderRadius: BorderRadius.circular(18),
      ),
      child: TextField(
        controller: _controller,
        focusNode: _focusNode,
        decoration: InputDecoration(
          hintText: '搜索日记内容...',
          hintStyle: TextStyle(
            color: DiaryStyles.getSecondaryTextColor(context),
            fontSize: 14,
          ),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: Dimensions.spacingM,
            vertical: Dimensions.spacingS,
          ),
          isDense: true,
        ),
        style: TextStyle(
          color: DiaryStyles.getPrimaryTextColor(context),
          fontSize: 14,
        ),
        textInputAction: TextInputAction.search,
        onSubmitted: (_) => _performSearch(),
      ),
    );
  }

  Widget _buildSearchButton() {
    return IconButton(
      icon: Icon(
        FeatherIcons.search,
        color: DiaryStyles.getAccentColor(context),
        size: Dimensions.iconSizeM,
      ),
      onPressed: _performSearch,
      splashRadius: Dimensions.spacingL,
      tooltip: '搜索',
    );
  }

  Widget _buildClearButton() {
    return AnimatedOpacity(
      opacity: _showClearButton ? 1.0 : 0.0,
      duration: const Duration(milliseconds: 200),
      child: IconButton(
        icon: Icon(
          FeatherIcons.x,
          color: DiaryStyles.getPrimaryTextColor(context),
          size: Dimensions.iconSizeM,
        ),
        onPressed: _showClearButton ? _clearSearch : null,
        splashRadius: Dimensions.spacingL,
        tooltip: '清除',
      ),
    );
  }
}
