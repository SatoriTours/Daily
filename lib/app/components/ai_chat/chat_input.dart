import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 聊天输入框组件
///
/// 提供功能完善的聊天输入界面，包括：
/// - 多行文本输入
/// - 发送按钮动态状态
/// - 禁用状态支持
/// - 自定义样式
class ChatInput extends StatefulWidget {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 发送消息回调函数
  final ValueChanged<String> onSendMessage;

  /// 输入框控制器（可选，如未提供则内部创建）
  final TextEditingController? controller;

  /// 焦点节点（可选，如未提供则内部创建）
  final FocusNode? focusNode;

  /// 是否禁用输入
  final bool disabled;

  /// 输入框占位提示文本
  final String? hintText;

  /// 输入框最大行数
  final int maxLines;

  /// 是否显示发送按钮
  final bool showSendButton;

  /// 自定义发送图标
  final IconData? sendIcon;

  /// 自定义发送按钮颜色
  final Color? sendButtonColor;

  const ChatInput({
    super.key,
    required this.onSendMessage,
    this.controller,
    this.focusNode,
    this.disabled = false,
    this.hintText,
    this.maxLines = 5,
    this.showSendButton = true,
    this.sendIcon,
    this.sendButtonColor,
  });

  @override
  State<ChatInput> createState() => _ChatInputState();
}

/// ChatInput 的状态类
class _ChatInputState extends State<ChatInput> {
  // ========================================================================
  // 状态变量
  // ========================================================================

  late TextEditingController _controller;
  late FocusNode _focusNode;
  bool _isEmpty = true;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void initState() {
    super.initState();
    _controller = widget.controller ?? TextEditingController();
    _focusNode = widget.focusNode ?? FocusNode();
    _controller.addListener(_onTextChanged);

    logger.d('[ChatInput] 初始化输入框');
  }

  @override
  void dispose() {
    logger.d('[ChatInput] 销毁输入框');

    if (widget.controller == null) {
      _controller.dispose();
    }
    if (widget.focusNode == null) {
      _focusNode.dispose();
    }
    super.dispose();
  }

  // ========================================================================
  // 事件处理方法
  // ========================================================================

  /// 文本变化回调
  ///
  /// 更新空状态，用于控制发送按钮的显示状态
  void _onTextChanged() {
    final wasEmpty = _isEmpty;
    final isEmpty = _controller.text.trim().isEmpty;

    if (wasEmpty != isEmpty) {
      setState(() {
        _isEmpty = isEmpty;
      });
    }
  }

  /// 发送消息
  ///
  /// 验证内容非空且未禁用后，调用回调并清空输入框
  void _onSend() {
    final text = _controller.text.trim();

    if (text.isEmpty) {
      logger.d('[ChatInput] 消息为空，忽略发送');
      return;
    }

    if (widget.disabled) {
      logger.d('[ChatInput] 输入框已禁用，忽略发送');
      return;
    }

    logger.i('[ChatInput] 发送消息: ${text.substring(0, text.length > 50 ? 50 : text.length)}...');
    widget.onSendMessage(text);
    _controller.clear();
    _focusNode.unfocus();
  }

  /// 提交回调（键盘回车键）
  void _onSubmitted(String value) {
    _onSend();
  }

  // ========================================================================
  // UI构建方法
  // ========================================================================

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(top: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.2))),
      ),
      child: SafeArea(
        child: Column(
          children: [
            _buildInputField(context),
            if (widget.showSendButton) SizedBox(height: Dimensions.spacingS),
            if (widget.showSendButton) _buildSendButton(context),
          ],
        ),
      ),
    );
  }

  /// 构建输入框
  Widget _buildInputField(BuildContext context) {
    return Container(
      constraints: BoxConstraints(minHeight: 40, maxHeight: 120),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainerHighest(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.3)),
      ),
      child: TextField(
        controller: _controller,
        focusNode: _focusNode,
        maxLines: widget.maxLines,
        minLines: 1,
        enabled: !widget.disabled,
        textCapitalization: TextCapitalization.sentences,
        decoration: InputDecoration(
          hintText: widget.hintText ?? 'ai_chat.input_hint'.t,
          hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          border: InputBorder.none,
          contentPadding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
          isDense: true,
        ),
        style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
        onSubmitted: _onSubmitted,
      ),
    );
  }

  /// 构建发送按钮
  Widget _buildSendButton(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeInOut,
          child: Material(
            color: _isEmpty || widget.disabled
                ? AppColors.getSurfaceContainerHighest(context)
                : (widget.sendButtonColor ?? AppColors.getPrimary(context)),
            borderRadius: BorderRadius.circular(Dimensions.radiusM),
            elevation: _isEmpty || widget.disabled ? 0 : 2,
            child: InkWell(
              onTap: _isEmpty || widget.disabled ? null : _onSend,
              borderRadius: BorderRadius.circular(Dimensions.radiusM),
              child: Container(
                padding: EdgeInsets.all(Dimensions.spacingS),
                child: Icon(
                  widget.sendIcon ?? Icons.send_rounded,
                  color: _isEmpty || widget.disabled ? AppColors.getOnSurfaceVariant(context) : AppColors.getOnPrimary(context),
                  size: 20,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// 聊天输入框的简化版本
///
/// 提供更简洁的输入界面，适用于快速集成场景
/// 自动处理发送逻辑，无需额外状态管理
class SimpleChatInput extends StatelessWidget {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 发送消息回调函数
  final ValueChanged<String> onSendMessage;

  /// 输入框控制器
  final TextEditingController? controller;

  /// 焦点节点
  final FocusNode? focusNode;

  /// 是否禁用
  final bool disabled;

  /// 占位提示文本
  final String? hintText;

  const SimpleChatInput({
    super.key,
    required this.onSendMessage,
    this.controller,
    this.focusNode,
    this.disabled = false,
    this.hintText,
  });

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 处理发送操作
  void _handleSend(String text) {
    final trimmedText = text.trim();

    if (trimmedText.isEmpty) {
      logger.d('[SimpleChatInput] 消息为空，忽略发送');
      return;
    }

    if (disabled) {
      logger.d('[SimpleChatInput] 输入框已禁用，忽略发送');
      return;
    }

    logger.i(
      '[SimpleChatInput] 发送消息: ${trimmedText.substring(0, trimmedText.length > 50 ? 50 : trimmedText.length)}...',
    );
    onSendMessage(trimmedText);
    controller?.clear();
  }

  // ========================================================================
  // UI构建
  // ========================================================================

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        border: Border(top: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.2))),
      ),
      child: SafeArea(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(child: _buildInputField(context)),
            SizedBox(width: Dimensions.spacingS),
            _buildSendButton(context),
          ],
        ),
      ),
    );
  }

  /// 构建输入框
  Widget _buildInputField(BuildContext context) {
    return Container(
      constraints: BoxConstraints(minHeight: 40, maxHeight: 120),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainerHighest(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.3)),
      ),
      child: TextField(
        controller: controller,
        focusNode: focusNode,
        enabled: !disabled,
        maxLines: null,
        minLines: 1,
        decoration: InputDecoration(
          hintText: hintText ?? 'ai_chat.input_hint'.t,
          hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          border: InputBorder.none,
          contentPadding: EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
          isDense: true,
        ),
        style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
        textInputAction: TextInputAction.send,
        onSubmitted: disabled ? null : _handleSend,
      ),
    );
  }

  /// 构建发送按钮
  Widget _buildSendButton(BuildContext context) {
    return Material(
      color: disabled ? AppColors.getSurfaceContainerHighest(context) : AppColors.getPrimary(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: InkWell(
        onTap: disabled ? null : () => _handleSend(controller?.text ?? ''),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        child: Container(
          padding: EdgeInsets.all(Dimensions.spacingS),
          child: Icon(
            Icons.send_rounded,
            color: disabled ? AppColors.getOnSurfaceVariant(context) : AppColors.getOnPrimary(context),
            size: 20,
          ),
        ),
      ),
    );
  }
}
