import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// 分享页面视图
/// 用于保存链接或添加备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final screenHeight = MediaQuery.of(context).size.height;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Align(
        alignment: Alignment.bottomCenter,
        child: Container(
          height: screenHeight * 0.5, // 设置为屏幕高度的一半
          decoration: BoxDecoration(
            color: isDark ? theme.colorScheme.surface : theme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
            boxShadow: [
              if (!isDark)
                BoxShadow(
                  color: Colors.black.withOpacity(0.1),
                  blurRadius: 10,
                  spreadRadius: 1,
                  offset: const Offset(0, -2),
                ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _Header(controller: controller, theme: theme, isDark: isDark),
              Expanded(child: _SharePageContent(controller: controller, theme: theme, isDark: isDark)),
            ],
          ),
        ),
      ),
    );
  }
}

/// 顶部标题栏组件
class _Header extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _Header({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border(bottom: BorderSide(color: theme.dividerColor.withOpacity(0.1), width: 1)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Obx(
              () => Text(
                controller.shareURL.value.isNotEmpty ? '保存链接' : '更新文章',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close_rounded),
            onPressed: () => controller.clickChannelBtn(),
            padding: EdgeInsets.zero,
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}

/// 分享页面内容组件
class _SharePageContent extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _SharePageContent({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: _ShareContentCard(controller: controller, theme: theme, isDark: isDark),
            ),
          ),
        ),
        _ErrorMessage(controller: controller),
        _ActionButtons(controller: controller, theme: theme, isDark: isDark),
      ],
    );
  }
}

/// 分享内容卡片
class _ShareContentCard extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _ShareContentCard({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerLow : theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          if (!isDark)
            BoxShadow(color: Colors.black.withAlpha(10), blurRadius: 6, spreadRadius: 1, offset: const Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _UrlSection(controller: controller, theme: theme, isDark: isDark),
          _CommentSection(controller: controller, theme: theme, isDark: isDark),
        ],
      ),
    );
  }
}

/// URL显示区域组件
class _UrlSection extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _UrlSection({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.shareURL.value.isEmpty) {
        return const SizedBox.shrink();
      }

      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(Icons.link_rounded, size: 20, color: theme.colorScheme.primary),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  "网页链接",
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: theme.colorScheme.onSurface),
                ),
              ),
              Text(
                controller.getShortUrl(controller.shareURL.value),
                style: TextStyle(fontSize: 14, color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
            decoration: BoxDecoration(
              color: isDark ? theme.colorScheme.surfaceContainerLowest : theme.colorScheme.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: isDark ? Colors.grey.shade800 : Colors.grey.shade200, width: 1),
            ),
            child: Text(
              controller.shareURL.value,
              style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 15),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(height: 24),
          Divider(color: theme.dividerColor.withOpacity(0.5)),
          const SizedBox(height: 16),
        ],
      );
    });
  }
}

/// 评论输入区域组件
class _CommentSection extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _CommentSection({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Icon(Icons.comment_outlined, size: 20, color: theme.colorScheme.primary),
            const SizedBox(width: 10),
            Text(
              "备注信息",
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: theme.colorScheme.onSurface),
            ),
          ],
        ),
        const SizedBox(height: 12),
        TextField(
          controller: controller.commentController,
          decoration: InputDecoration(
            hintText: "添加备注信息（可选）",
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withOpacity(0.7), fontSize: 15),
            filled: true,
            fillColor: isDark ? theme.colorScheme.surfaceContainerLowest : theme.colorScheme.surfaceContainerLowest,
            border: _buildInputBorder(),
            enabledBorder: _buildInputBorder(),
            focusedBorder: _buildInputBorder(isActive: true),
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          ),
          style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 15),
          minLines: 4,
          maxLines: 6,
          keyboardType: TextInputType.multiline,
          textInputAction: TextInputAction.newline,
          cursorColor: theme.colorScheme.primary,
        ),
      ],
    );
  }

  InputBorder _buildInputBorder({bool isActive = false}) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(
        color: isActive ? theme.colorScheme.primary : (isDark ? Colors.grey.shade800 : Colors.grey.shade200),
        width: isActive ? 1.5 : 1,
      ),
    );
  }
}

/// 错误信息显示组件
class _ErrorMessage extends StatelessWidget {
  final ShareDialogController controller;

  const _ErrorMessage({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.errorMessage.value.isEmpty) {
        return const SizedBox.shrink();
      }

      return Container(
        margin: const EdgeInsets.fromLTRB(24, 0, 24, 8),
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
        decoration: BoxDecoration(color: Colors.red.withOpacity(0.1), borderRadius: BorderRadius.circular(10)),
        child: Row(
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 18),
            const SizedBox(width: 10),
            Expanded(
              child: Text(controller.errorMessage.value, style: const TextStyle(color: Colors.red, fontSize: 14)),
            ),
          ],
        ),
      );
    });
  }
}

/// 操作按钮组件
class _ActionButtons extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _ActionButtons({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerLow : theme.colorScheme.surface,
        boxShadow: [
          if (!isDark) BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 4, offset: const Offset(0, -1)),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: OutlinedButton(
              style: OutlinedButton.styleFrom(
                foregroundColor: theme.colorScheme.onSurface,
                padding: const EdgeInsets.symmetric(vertical: 12),
                side: BorderSide(color: theme.colorScheme.outline),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: () => controller.clickChannelBtn(),
              child: const Text("取消", style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: theme.colorScheme.primary,
                foregroundColor: theme.colorScheme.onPrimary,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              onPressed: () => controller.onSaveButtonPressed(),
              child: Obx(
                () => Text(
                  controller.isLoading.value ? "保存中..." : "保存",
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
