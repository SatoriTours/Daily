import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app_exports.dart';

import '../../controllers/diary_controller.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;
import 'diary_editor.dart';

/// 日记悬浮按钮组件
class DiaryFab extends StatefulWidget {
  final DiaryController controller;

  const DiaryFab({super.key, required this.controller});

  @override
  State<DiaryFab> createState() => _DiaryFabState();
}

class _DiaryFabState extends State<DiaryFab> with SingleTickerProviderStateMixin {
  late AnimationController _animationController;
  late Animation<double> _rotationAnimation;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _initAnimations();
    logger.d('初始化日记悬浮按钮');
  }

  /// 初始化动画控制器和动画
  void _initAnimations() {
    _animationController = AnimationController(duration: const Duration(milliseconds: 300), vsync: this);

    _rotationAnimation = Tween<double>(
      begin: 0,
      end: 0.125,
    ).animate(CurvedAnimation(parent: _animationController, curve: Curves.easeInOut));

    _scaleAnimation = Tween<double>(
      begin: 1.0,
      end: 0.95,
    ).animate(CurvedAnimation(parent: _animationController, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ScaleTransition(
      scale: _scaleAnimation,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: _handleFabPressed,
          customBorder: const CircleBorder(),
          child: Ink(
            decoration: _buildFabDecoration(context),
            width: 42,
            height: 42,
            child: Center(
              child: RotationTransition(
                turns: _rotationAnimation,
                child: Icon(FeatherIcons.plus, size: 18, color: theme.colorScheme.onPrimary),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// 构建FAB装饰
  BoxDecoration _buildFabDecoration(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return BoxDecoration(
      shape: BoxShape.circle,
      color: theme.colorScheme.primary,
      boxShadow: [
        BoxShadow(
          color: theme.colorScheme.primary.withAlpha(isDark ? 30 : 50),
          blurRadius: 8,
          spreadRadius: 0,
          offset: const Offset(0, 2),
        ),
      ],
    );
  }

  /// 处理FAB点击
  void _handleFabPressed() {
    logger.d('点击悬浮按钮，显示编辑器');
    _playPressAnimation();
    _showExpandedEditor(context);
  }

  /// 播放按钮按下动画
  void _playPressAnimation() {
    _animationController.forward().then((_) {
      Future.delayed(const Duration(milliseconds: 100), () {
        if (mounted) {
          _animationController.reverse();
        }
      });
    });
  }

  /// 显示扩展编辑器
  void _showExpandedEditor(BuildContext context) {
    // 清空内容控制器
    widget.controller.contentController.clear();

    // 显示底部编辑器模态框
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(controller: widget.controller),
    );
  }
}
