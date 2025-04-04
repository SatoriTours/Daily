import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// 分享对话框视图
/// 用于保存链接或添加备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 24.0),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      elevation: 8,
      clipBehavior: Clip.antiAlias,
      child: _DialogContent(controller: controller, theme: theme, isDark: isDark),
    );
  }
}

/// 对话框内容组件
class _DialogContent extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _DialogContent({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: MediaQuery.of(context).size.width * 0.9,
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.6),
      color: isDark ? theme.colorScheme.surfaceContainerHigh : Colors.white,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _Header(controller: controller, theme: theme, isDark: isDark),
          Expanded(child: _Body(controller: controller, theme: theme, isDark: isDark)),
          _ErrorMessage(controller: controller),
          _ActionButtons(controller: controller, theme: theme, isDark: isDark),
        ],
      ),
    );
  }
}

/// 对话框头部组件
class _Header extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _Header({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerHighest : theme.colorScheme.primaryContainer.withAlpha(51),
        border: Border(bottom: BorderSide(color: theme.dividerColor.withAlpha(26), width: 1)),
      ),
      child: Row(
        children: [
          Icon(Icons.bookmark_add_rounded, color: theme.colorScheme.primary, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Obx(
              () => Text(
                controller.shareURL.value.isNotEmpty ? '保存链接' : '添加备注',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: theme.colorScheme.onSurface),
              ),
            ),
          ),
          IconButton(
            icon: Icon(Icons.close_rounded, size: 20, color: theme.colorScheme.onSurfaceVariant),
            onPressed: () => controller.clickChannelBtn(),
            padding: EdgeInsets.zero,
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}

/// 对话框主体内容组件
class _Body extends StatelessWidget {
  final ShareDialogController controller;
  final ThemeData theme;
  final bool isDark;

  const _Body({required this.controller, required this.theme, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 6),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isDark ? theme.colorScheme.surfaceContainerLow : theme.colorScheme.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              if (!isDark) BoxShadow(color: Colors.black.withAlpha(13), blurRadius: 6, offset: const Offset(0, 2)),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _UrlSection(controller: controller, theme: theme, isDark: isDark),
              _CommentSection(controller: controller, theme: theme, isDark: isDark),
            ],
          ),
        ),
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
            children: [
              Icon(Icons.link_rounded, size: 16, color: theme.colorScheme.primary),
              const SizedBox(width: 8),
              Text(
                "网页链接",
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: theme.colorScheme.onSurface),
              ),
              const Spacer(),
              Text(
                controller.getShortUrl(controller.shareURL.value),
                style: TextStyle(fontSize: 12, color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
            decoration: BoxDecoration(
              color: isDark ? theme.colorScheme.surfaceContainerLowest : Colors.white,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: isDark ? Colors.grey.shade800 : Colors.grey.shade200, width: 1),
            ),
            child: Text(
              controller.shareURL.value,
              style: TextStyle(color: theme.colorScheme.onSurface.withAlpha(204), fontSize: 13),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(height: 16),
          const Divider(),
          const SizedBox(height: 12),
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
          children: [
            Icon(Icons.comment_outlined, size: 16, color: theme.colorScheme.primary),
            const SizedBox(width: 8),
            Text(
              "备注信息",
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: theme.colorScheme.onSurface),
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: controller.commentController,
          decoration: InputDecoration(
            hintText: "添加备注信息（可选）",
            hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withAlpha(153), fontSize: 13),
            filled: true,
            fillColor: isDark ? theme.colorScheme.surfaceContainerLowest : Colors.white,
            border: _buildInputBorder(),
            enabledBorder: _buildInputBorder(),
            focusedBorder: _buildInputBorder(isActive: true),
            contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          ),
          style: TextStyle(color: theme.colorScheme.onSurface, fontSize: 14),
          minLines: 3,
          maxLines: 5,
          keyboardType: TextInputType.multiline,
          textInputAction: TextInputAction.newline,
          cursorColor: theme.colorScheme.primary,
        ),
      ],
    );
  }

  InputBorder _buildInputBorder({bool isActive = false}) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(10),
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
        margin: const EdgeInsets.fromLTRB(20, 0, 20, 4),
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        decoration: BoxDecoration(color: Colors.red.withAlpha(26), borderRadius: BorderRadius.circular(8)),
        child: Row(
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 16),
            const SizedBox(width: 8),
            Expanded(
              child: Text(controller.errorMessage.value, style: const TextStyle(color: Colors.red, fontSize: 13)),
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
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 12),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerHighest : theme.colorScheme.primaryContainer.withAlpha(26),
        border: Border(top: BorderSide(color: theme.dividerColor.withAlpha(26), width: 1)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          OutlinedButton(
            style: OutlinedButton.styleFrom(
              foregroundColor: theme.colorScheme.onSurface,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              side: BorderSide(color: theme.colorScheme.outline),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            onPressed: () => controller.clickChannelBtn(),
            child: const Text("取消"),
          ),
          const SizedBox(width: 12),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: theme.colorScheme.primary,
              foregroundColor: theme.colorScheme.onPrimary,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              elevation: 0,
            ),
            onPressed: () => controller.onSaveButtonPressed(),
            child: Obx(() => Text(controller.isLoading.value ? "保存中..." : "保存")),
          ),
        ],
      ),
    );
  }
}
