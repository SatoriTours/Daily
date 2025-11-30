import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 对话框工具类
class DialogUtils {
  // 私有构造函数，防止实例化
  DialogUtils._();

  // 加载对话框状态
  static bool _isLoadingShown = false;

  /// 显示提示对话框
  static Future<void> showAlert({
    required String title,
    required String message,
    String buttonText = '确定',
    VoidCallback? onConfirm,
  }) async {
    await Get.dialog(
      _CustomDialog(
        title: title,
        content: message,
        actions: [
          TextButton(
            onPressed: () {
              _closeDialog();
              if (onConfirm != null) onConfirm();
            },
            child: Text(buttonText),
          ),
        ],
      ),
    );
  }

  /// 显示确认对话框
  static Future<void> showConfirm({
    required String title,
    required String message,
    String confirmText = '确定',
    String cancelText = '取消',
    VoidCallback? onConfirm,
    VoidCallback? onCancel,
  }) async {
    await Get.dialog<bool>(
      _CustomDialog(
        title: title,
        content: message,
        actions: [
          Expanded(
            child: TextButton(
              onPressed: () {
                _closeDialog();
                if (onCancel != null) onCancel();
              },
              child: Text(cancelText),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: TextButton(
              onPressed: () {
                _closeDialog();
                if (onConfirm != null) onConfirm();
              },
              child: Text(confirmText),
            ),
          ),
        ],
      ),
    );
  }

  /// 显示输入对话框
  static Future<void> showInputDialog({
    required String title,
    String? initialValue,
    String hintText = '',
    String confirmText = '确定',
    String cancelText = '取消',
    TextInputType keyboardType = TextInputType.text,
    required void Function(String value) onConfirm,
    VoidCallback? onCancel,
  }) async {
    final TextEditingController controller = TextEditingController(text: initialValue);
    await Get.dialog<String>(
      _CustomDialog(
        title: title,
        content: '',
        contentWidget: TextField(
          controller: controller,
          decoration: InputDecoration(hintText: hintText),
          keyboardType: keyboardType,
        ),
        actions: [
          Expanded(
            child: TextButton(
              onPressed: () {
                _closeDialog();
                if (onCancel != null) onCancel();
              },
              child: Text(cancelText),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: TextButton(
              onPressed: () {
                _closeDialog();
                onConfirm(controller.text);
              },
              child: Text(confirmText),
            ),
          ),
        ],
      ),
    );
  }

  /// 显示全屏加载提示
  static void showLoading({String tips = '', Color? barrierColor}) {
    logger.i("显示加载提示: $tips $_isLoadingShown");
    if (_isLoadingShown) return; // 如果已经显示了loading，直接返回
    logger.i("显示加载提示1: $tips");

    final context = Get.context;
    final textTheme = context != null ? Theme.of(context).textTheme.bodyMedium : null;
    Get.dialog(
      PopScope(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(),
              if (tips.isNotEmpty) ...[const SizedBox(height: 16), Text(tips, style: textTheme)],
            ],
          ),
        ),
      ),
      barrierDismissible: false,
      barrierColor: barrierColor ?? const Color(0x80000000),
    );
    _isLoadingShown = true;
  }

  /// 隐藏加载提示
  static void hideLoading() {
    if (_isLoadingShown) {
      _isLoadingShown = false;
      _closeDialog();
    }
  }

  // 下载进度对话框状态
  static bool _isProgressShown = false;
  static final _progressValue = 0.0.obs;
  static final _progressText = ''.obs;

  /// 显示下载进度对话框
  ///
  /// [title] 对话框标题
  /// [initialText] 初始提示文本
  static void showDownloadProgress({String title = '正在下载', String initialText = '准备下载...'}) {
    logger.i("[DialogUtils] 显示下载进度对话框");
    if (_isProgressShown) return;

    _progressValue.value = 0.0;
    _progressText.value = initialText;

    Get.dialog(
      PopScope(
        canPop: false,
        child: _DownloadProgressDialog(title: title, progressValue: _progressValue, progressText: _progressText),
      ),
      barrierDismissible: false,
      barrierColor: const Color(0x80000000),
    );
    _isProgressShown = true;
  }

  /// 更新下载进度
  ///
  /// [received] 已下载字节数
  /// [total] 总字节数
  static void updateDownloadProgress(int received, int total) {
    if (!_isProgressShown) return;

    if (total > 0) {
      _progressValue.value = received / total;
      final receivedMB = (received / 1024 / 1024).toStringAsFixed(2);
      final totalMB = (total / 1024 / 1024).toStringAsFixed(2);
      final percent = (_progressValue.value * 100).toStringAsFixed(1);
      _progressText.value = '$receivedMB MB / $totalMB MB ($percent%)';
    } else {
      // 未知总大小，只显示已下载
      final receivedMB = (received / 1024 / 1024).toStringAsFixed(2);
      _progressText.value = '已下载 $receivedMB MB';
    }
  }

  /// 隐藏下载进度对话框
  static void hideDownloadProgress() {
    if (_isProgressShown) {
      logger.i("[DialogUtils] 隐藏下载进度对话框");
      _isProgressShown = false;
      _progressValue.value = 0.0;
      _progressText.value = '';
      _closeDialog();
    }
  }

  static void _closeDialog() {
    Get.closeDialog();
  }
}

/// 自定义对话框组件
class _CustomDialog extends StatelessWidget {
  final String title;
  final String content;
  final Widget? contentWidget; // 自定义内容组件(可选)
  final List<Widget> actions;

  const _CustomDialog({required this.title, required this.content, this.contentWidget, required this.actions});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dialogTheme = theme.dialogTheme;

    return Dialog(
      backgroundColor: dialogTheme.backgroundColor ?? theme.colorScheme.surface,
      elevation: dialogTheme.elevation ?? 0,
      shape: dialogTheme.shape ?? RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
      child: Padding(
        padding: Dimensions.paddingDialog,
        child: IntrinsicHeight(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 标题
              Text(
                title,
                style:
                    dialogTheme.titleTextStyle ??
                    theme.textTheme.headlineSmall?.copyWith(color: theme.colorScheme.onSurface),
              ),
              const SizedBox(height: 16),
              // 内容 - 使用 ConstrainedBox 限制最大高度并支持滚动
              ConstrainedBox(
                constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.5),
                child: SingleChildScrollView(
                  child:
                      contentWidget ??
                      (content.isNotEmpty
                          ? Text(
                              content,
                              style:
                                  dialogTheme.contentTextStyle ??
                                  theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                            )
                          : const SizedBox.shrink()),
                ),
              ),
              const SizedBox(height: 24),
              // 按钮区域 - 水平布局
              Row(mainAxisAlignment: MainAxisAlignment.end, children: actions),
            ],
          ),
        ),
      ),
    );
  }
}

/// 下载进度对话框组件
class _DownloadProgressDialog extends StatelessWidget {
  final String title;
  final Rx<double> progressValue;
  final RxString progressText;

  const _DownloadProgressDialog({required this.title, required this.progressValue, required this.progressText});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dialogTheme = theme.dialogTheme;

    return Dialog(
      backgroundColor: dialogTheme.backgroundColor ?? theme.colorScheme.surface,
      elevation: dialogTheme.elevation ?? 0,
      shape: dialogTheme.shape ?? RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusL)),
      child: Padding(
        padding: Dimensions.paddingDialog,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题
            Text(
              title,
              style:
                  dialogTheme.titleTextStyle ??
                  theme.textTheme.headlineSmall?.copyWith(color: theme.colorScheme.onSurface),
            ),
            const SizedBox(height: Dimensions.spacingL),
            // 进度条
            Obx(
              () => LinearProgressIndicator(
                value: progressValue.value > 0 ? progressValue.value : null,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.primary),
                minHeight: 6,
                borderRadius: BorderRadius.circular(Dimensions.radiusXs),
              ),
            ),
            const SizedBox(height: Dimensions.spacingM),
            // 进度文本
            Obx(
              () => Text(
                progressText.value,
                style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
