import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// 分享对话框视图
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    _processArguments();
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 24.0),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      elevation: 8,
      clipBehavior: Clip.antiAlias,
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.6),
        color: isDark ? theme.colorScheme.surfaceContainerHigh : Colors.white,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _buildHeader(context),
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 6),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [_buildContent(context)]),
                ),
              ),
            ),
            _buildErrorMessage(context),
            _buildActionButtons(context),
          ],
        ),
      ),
    );
  }

  /// 构建标题栏
  Widget _buildHeader(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerHighest : theme.colorScheme.primaryContainer.withOpacity(0.2),
        border: Border(bottom: BorderSide(color: theme.dividerColor.withOpacity(0.1), width: 1)),
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

  /// 构建主要内容区域
  Widget _buildContent(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerLow : theme.colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          if (!isDark) BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 6, offset: const Offset(0, 2)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 网页链接区域
          Obx(() {
            if (controller.shareURL.value.isEmpty) return const SizedBox.shrink();

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
                    style: TextStyle(color: theme.colorScheme.onSurface.withOpacity(0.8), fontSize: 13),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 12),
              ],
            );
          }),

          // 备注信息区域
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
              hintStyle: TextStyle(color: theme.colorScheme.onSurfaceVariant.withOpacity(0.6), fontSize: 13),
              filled: true,
              fillColor: isDark ? theme.colorScheme.surfaceContainerLowest : Colors.white,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: BorderSide(color: isDark ? Colors.grey.shade800 : Colors.grey.shade200, width: 1),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: BorderSide(color: isDark ? Colors.grey.shade800 : Colors.grey.shade200, width: 1),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: BorderSide(color: theme.colorScheme.primary, width: 1.5),
              ),
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
      ),
    );
  }

  /// 构建错误信息显示
  Widget _buildErrorMessage(BuildContext context) {
    return Obx(
      () =>
          controller.errorMessage.value.isNotEmpty
              ? Container(
                margin: const EdgeInsets.fromLTRB(20, 0, 20, 4),
                padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                decoration: BoxDecoration(color: Colors.red.withOpacity(0.1), borderRadius: BorderRadius.circular(8)),
                child: Row(
                  children: [
                    Icon(Icons.error_outline, color: Colors.red, size: 16),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        controller.errorMessage.value,
                        style: const TextStyle(color: Colors.red, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              )
              : const SizedBox.shrink(),
    );
  }

  /// 构建操作按钮
  Widget _buildActionButtons(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 12),
      decoration: BoxDecoration(
        color: isDark ? theme.colorScheme.surfaceContainerHighest : theme.colorScheme.primaryContainer.withOpacity(0.1),
        border: Border(top: BorderSide(color: theme.dividerColor.withOpacity(0.1), width: 1)),
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
            child: const Text("取消"),
            onPressed: () => controller.clickChannelBtn(),
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
            child: Obx(
              () =>
                  controller.isLoading.value
                      ? SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.onPrimary),
                        ),
                      )
                      : const Text("保存"),
            ),
            onPressed: () => controller.onSaveButtonPressed(),
          ),
        ],
      ),
    );
  }

  /// 处理传入的参数
  void _processArguments() {
    if (Get.arguments?['shareURL'] != null) {
      controller.updateShareURL(Get.arguments?['shareURL']);
    }

    if (Get.arguments?['update'] != null) {
      controller.updateIsUpdate(Get.arguments?['update']);
    }

    if (Get.arguments?['articleID'] != null) {
      controller.updateArticleID(Get.arguments?['articleID']);
    }

    if (Get.arguments?['needBackToApp'] != null) {
      controller.updateNeedBackToApp(Get.arguments?['needBackToApp']);
    }
  }
}
