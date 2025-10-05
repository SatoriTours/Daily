import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

/// 文章搜索栏组件
///
/// 纯展示组件,通过回调函数与外部交互
class ArticlesSearchBar extends StatefulWidget {
  final TextEditingController searchController;
  final FocusNode searchFocusNode;
  final VoidCallback onBack;
  final VoidCallback onSearch;
  final VoidCallback onClear;

  const ArticlesSearchBar({
    super.key,
    required this.searchController,
    required this.searchFocusNode,
    required this.onBack,
    required this.onSearch,
    required this.onClear,
  });

  @override
  State<ArticlesSearchBar> createState() => _ArticlesSearchBarState();
}

class _ArticlesSearchBarState extends State<ArticlesSearchBar> {
  @override
  void initState() {
    super.initState();
    // 监听文本变化以更新清除按钮的显示
    widget.searchController.addListener(() {
      setState(() {});
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    // 当搜索栏出现时自动聚焦
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.searchFocusNode.canRequestFocus) {
        FocusScope.of(context).requestFocus(widget.searchFocusNode);
        widget.searchController.selection = TextSelection.fromPosition(
          TextPosition(offset: widget.searchController.text.length),
        );
      }
    });

    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(10), offset: const Offset(0, 1), blurRadius: 3)],
      ),
      child: Row(
        children: [
          IconButton(
            icon: Icon(FeatherIcons.arrowLeft, color: colorScheme.onSurface, size: 20),
            onPressed: widget.onBack,
            splashRadius: 20,
          ),
          Expanded(
            child: TextField(
              controller: widget.searchController,
              focusNode: widget.searchFocusNode,
              decoration: InputDecoration(
                hintText: '搜索文章...',
                hintStyle: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                isDense: true,
              ),
              style: textTheme.bodyMedium,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => widget.onSearch(),
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.search, color: colorScheme.onSurface, size: 20),
            onPressed: widget.onSearch,
            splashRadius: 20,
          ),
          if (widget.searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(FeatherIcons.x, color: colorScheme.onSurface, size: 20),
              onPressed: widget.onClear,
              splashRadius: 20,
            ),
        ],
      ),
    );
  }
}
