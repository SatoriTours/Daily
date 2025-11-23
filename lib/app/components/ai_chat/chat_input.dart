import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// 聊天输入框组件
class ChatInput extends StatefulWidget {
  /// 发送消息回调
  final ValueChanged<String> onSendMessage;

  /// 输入框控制器
  final TextEditingController? controller;

  /// 焦点节点
  final FocusNode? focusNode;

  /// 是否禁用
  final bool disabled;

  /// 占位提示文本
  final String? hintText;

  /// 最大行数
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

class _ChatInputState extends State<ChatInput> {
  late TextEditingController _controller;
  late FocusNode _focusNode;
  bool _isEmpty = true;

  @override
  void initState() {
    super.initState();
    _controller = widget.controller ?? TextEditingController();
    _focusNode = widget.focusNode ?? FocusNode();
    _controller.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    if (widget.controller == null) {
      _controller.dispose();
    }
    if (widget.focusNode == null) {
      _focusNode.dispose();
    }
    super.dispose();
  }

  void _onTextChanged() {
    setState(() {
      _isEmpty = _controller.text.trim().isEmpty;
    });
  }

  void _onSend() {
    final text = _controller.text.trim();
    if (text.isNotEmpty && !widget.disabled) {
      widget.onSendMessage(text);
      _controller.clear();
      _focusNode.unfocus();
    }
  }

  void _onSubmitted(String value) {
    _onSend();
  }

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
                  color: _isEmpty || widget.disabled ? AppColors.getOnSurfaceVariant(context) : Colors.white,
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
class SimpleChatInput extends StatelessWidget {
  final ValueChanged<String> onSendMessage;
  final TextEditingController? controller;
  final FocusNode? focusNode;
  final bool disabled;
  final String? hintText;

  const SimpleChatInput({
    super.key,
    required this.onSendMessage,
    this.controller,
    this.focusNode,
    this.disabled = false,
    this.hintText,
  });

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
            Expanded(
              child: Container(
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
                    contentPadding: EdgeInsets.symmetric(
                      horizontal: Dimensions.spacingM,
                      vertical: Dimensions.spacingS,
                    ),
                    isDense: true,
                  ),
                  style: AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context)),
                  textInputAction: TextInputAction.send,
                  onSubmitted: disabled
                      ? null
                      : (text) {
                          if (text.trim().isNotEmpty) {
                            onSendMessage(text);
                            controller?.clear();
                          }
                        },
                ),
              ),
            ),
            SizedBox(width: Dimensions.spacingS),
            Material(
              color: disabled ? AppColors.getSurfaceContainerHighest(context) : AppColors.getPrimary(context),
              borderRadius: BorderRadius.circular(Dimensions.radiusM),
              child: InkWell(
                onTap: disabled
                    ? null
                    : () {
                        final text = controller?.text.trim() ?? '';
                        if (text.isNotEmpty) {
                          onSendMessage(text);
                          controller?.clear();
                        }
                      },
                borderRadius: BorderRadius.circular(Dimensions.radiusM),
                child: Container(
                  padding: EdgeInsets.all(Dimensions.spacingS),
                  child: Icon(
                    Icons.send_rounded,
                    color: disabled ? AppColors.getOnSurfaceVariant(context) : Colors.white,
                    size: 20,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
