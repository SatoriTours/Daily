import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:feather_icons/feather_icons.dart';

import '../../controllers/diary_controller.dart';
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

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(duration: const Duration(milliseconds: 300), vsync: this);
    _rotationAnimation = Tween<double>(
      begin: 0,
      end: 0.125,
    ).animate(CurvedAnimation(parent: _animationController, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    // 使用SizedBox包裹Container以精确控制按钮区域大小
    return SizedBox(
      width: 48,
      height: 48,
      child: Center(
        child: Container(
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: DiaryStyle.accentColor(context).withAlpha(isDark ? 25 : 35),
                blurRadius: 5,
                spreadRadius: 0,
                offset: const Offset(0, 1),
              ),
            ],
          ),
          child: ClipOval(
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: () {
                  _animationController.forward().then((_) {
                    _animationController.reverse();
                    _showExpandedEditor(context);
                  });
                },
                child: Ink(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        DiaryStyle.accentColor(context),
                        DiaryStyle.accentColor(context).withBlue((DiaryStyle.accentColor(context).b * 0.85).toInt()),
                      ],
                    ),
                  ),
                  child: RotationTransition(
                    turns: _rotationAnimation,
                    child: const Center(child: Icon(FeatherIcons.plus, size: 15, color: Colors.white)),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // 显示扩展编辑器
  void _showExpandedEditor(BuildContext context) {
    // 清空内容控制器
    widget.controller.contentController.clear();

    // 显示底部编辑器模态框
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => DiaryEditor(controller: widget.controller),
    );
  }
}
