import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/modules/ai_chat/models/search_result.dart';
import 'chat_message.dart';
import 'search_result_card.dart';

/// 聊天消息气泡组件
///
/// 根据消息类型渲染不同样式的气泡
/// 支持用户消息、助手消息、系统消息、工具消息等多种类型
/// 可显示处理步骤、搜索结果、子消息等附加信息
class MessageBubble extends StatelessWidget {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 聊天消息数据
  final ChatMessage message;

  /// 重试回调函数
  /// 当消息发送失败时，用户可点击重试按钮触发
  final VoidCallback? onRetry;

  /// 是否为子消息
  /// 子消息会使用不同的布局和样式
  final bool isSubMessage;

  const MessageBubble({super.key, required this.message, this.onRetry, this.isSubMessage = false});

  // ========================================================================
  // UI构建
  // ========================================================================

  @override
  Widget build(BuildContext context) {
    // 如果是空内容的助手消息（只有子消息或处理步骤），不渲染外层主内容气泡
    if (message.type == ChatMessageType.assistant && message.content.trim().isEmpty) {
      return Container(
        width: double.infinity,
        padding: isSubMessage ? EdgeInsets.zero : const EdgeInsets.symmetric(horizontal: 12),
        child: Column(
          crossAxisAlignment: _getCrossAxisAlignment(),
          children: [
            _buildMessageHeader(context),
            if (message.processingSteps != null && message.processingSteps!.isNotEmpty) ...[
              const SizedBox(height: 6),
              _buildProcessingSteps(context),
            ],
            if (message.searchResults != null && message.searchResults!.isNotEmpty) ...[
              const SizedBox(height: 8),
              _buildSearchResults(context),
            ],
            if (message.subMessages != null && message.subMessages!.isNotEmpty) ...[
              const SizedBox(height: 8),
              _buildSubMessages(context),
            ],
          ],
        ),
      );
    }

    return Container(
      width: double.infinity,
      padding: isSubMessage ? EdgeInsets.zero : const EdgeInsets.symmetric(horizontal: 12),
      child: Column(
        crossAxisAlignment: _getCrossAxisAlignment(),
        children: [
          _buildMessageHeader(context),
          const SizedBox(height: 6),
          _buildMessageContent(context),
          if (message.processingSteps != null && message.processingSteps!.isNotEmpty) ...[
            const SizedBox(height: 8),
            _buildProcessingSteps(context),
          ],
          if (message.searchResults != null && message.searchResults!.isNotEmpty) ...[
            const SizedBox(height: 8),
            _buildSearchResults(context),
          ],
          if (message.subMessages != null && message.subMessages!.isNotEmpty) ...[
            const SizedBox(height: 8),
            _buildSubMessages(context),
          ],
          if (message.hasError && onRetry != null) ...[const SizedBox(height: 8), _buildRetryButton(context)],
        ],
      ),
    );
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 获取对齐方式
  ///
  /// 用户消息右对齐，其他消息左对齐
  CrossAxisAlignment _getCrossAxisAlignment() {
    switch (message.type) {
      case ChatMessageType.user:
        return CrossAxisAlignment.end;
      case ChatMessageType.assistant:
      case ChatMessageType.system:
      case ChatMessageType.tool:
      case ChatMessageType.thinking:
        return CrossAxisAlignment.start;
    }
  }

  /// 构建消息头部
  ///
  /// 显示消息发送者标识和时间戳
  /// 系统消息和思考消息不显示头部
  Widget _buildMessageHeader(BuildContext context) {
    if (message.type == ChatMessageType.system || message.type == ChatMessageType.thinking) {
      return const SizedBox.shrink();
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (message.type == ChatMessageType.assistant) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
            decoration: BoxDecoration(
              color: AppColors.getPrimary(context).withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(Dimensions.radiusXs),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.smart_toy_outlined, size: 14, color: AppColors.getPrimary(context)),
                const SizedBox(width: 4),
                Text(
                  'ai_chat.assistant'.t,
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.getPrimary(context),
                    fontWeight: FontWeight.w500,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ] else if (message.type == ChatMessageType.user) ...[
          Text(
            'ai_chat.you'.t,
            style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
          ),
        ] else if (message.type == ChatMessageType.tool) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
            decoration: BoxDecoration(
              color: AppColors.getSecondaryContainer(context),
              borderRadius: BorderRadius.circular(Dimensions.radiusXs),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.build_circle_outlined, size: 14, color: AppColors.getOnSecondaryContainer(context)),
                const SizedBox(width: 4),
                Text(
                  message.toolName ?? 'ai_chat.tool'.t,
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.getOnSecondaryContainer(context),
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ],
        const Spacer(),
        Text(
          _formatTime(message.timestamp),
          style: AppTypography.labelSmall.copyWith(color: AppColors.getOnSurfaceVariant(context)),
        ),
      ],
    );
  }

  /// 构建消息内容
  ///
  /// 根据消息类型选择合适的渲染方式
  /// Markdown 渲染用于助手消息，普通文本用于其他类型
  Widget _buildMessageContent(BuildContext context) {
    return Container(
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.8),
      padding: Dimensions.paddingS,
      decoration: _buildMessageDecoration(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (message.showThinking && message.type == ChatMessageType.thinking) _buildThinkingIndicator(context),
          _buildMessageText(context),
        ],
      ),
    );
  }

  /// 构建消息装饰
  BoxDecoration _buildMessageDecoration(BuildContext context) {
    return BoxDecoration(
      color: _getBackgroundColor(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: _getBorder(context),
      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.03), blurRadius: 3, offset: const Offset(0, 1))],
    );
  }

  /// 构建思考指示器
  Widget _buildThinkingIndicator(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(AppColors.getOnSurfaceVariant(context)),
              ),
            ),
            Dimensions.horizontalSpacerS,
            Text(
              'ai_chat.thinking'.t,
              style: AppTypography.bodySmall.copyWith(
                color: AppColors.getOnSurfaceVariant(context),
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
      ],
    );
  }

  /// 构建消息文本
  Widget _buildMessageText(BuildContext context) {
    if (message.type == ChatMessageType.assistant && message.content.trim().isNotEmpty) {
      return MarkdownBody(data: message.content, selectable: true, styleSheet: _buildMarkdownStyleSheet(context));
    }
    return Text(message.content, style: _getTextStyle(context));
  }

  /// 构建 Markdown 样式表
  MarkdownStyleSheet _buildMarkdownStyleSheet(BuildContext context) {
    return MarkdownStyleSheet(
      // 段落样式
      p: _getTextStyle(context).copyWith(height: 1.4),
      pPadding: const EdgeInsets.only(bottom: 4),
      // 标题样式
      h1: _buildHeadingStyle(context, AppTypography.titleLarge),
      h1Padding: const EdgeInsets.only(top: 8, bottom: 4),
      h2: _buildHeadingStyle(context, AppTypography.titleMedium),
      h2Padding: const EdgeInsets.only(top: 6, bottom: 3),
      h3: _buildHeadingStyle(context, AppTypography.titleSmall, FontWeight.w600),
      h3Padding: const EdgeInsets.only(top: 4, bottom: 2),
      h4: AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurface(context), fontWeight: FontWeight.w600),
      // 强调样式
      strong: _getTextStyle(context).copyWith(fontWeight: FontWeight.bold, color: AppColors.getPrimary(context)),
      em: _getTextStyle(context).copyWith(fontStyle: FontStyle.italic, color: AppColors.getOnSurfaceVariant(context)),
      // 列表样式
      listBullet: _getTextStyle(context).copyWith(height: 1.3),
      listIndent: 16,
      listBulletPadding: const EdgeInsets.only(right: 4),
      // 代码样式
      code: _buildCodeStyle(context),
      codeblockPadding: const EdgeInsets.all(12),
      codeblockDecoration: _buildCodeBlockDecoration(context),
      // 引用块样式
      blockquotePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      blockquoteDecoration: _buildBlockquoteDecoration(context),
      // 水平线样式
      horizontalRuleDecoration: _buildHorizontalRuleDecoration(context),
    );
  }

  /// 构建标题样式
  TextStyle _buildHeadingStyle(BuildContext context, TextStyle baseStyle, [FontWeight weight = FontWeight.bold]) {
    return baseStyle.copyWith(color: AppColors.getOnSurface(context), fontWeight: weight, height: 1.3);
  }

  /// 构建代码样式
  TextStyle _buildCodeStyle(BuildContext context) {
    return AppTypography.bodySmall.copyWith(
      fontFamily: 'monospace',
      backgroundColor: AppColors.getSurfaceContainer(context),
      color: AppColors.getPrimary(context),
    );
  }

  /// 构建代码块装饰
  BoxDecoration _buildCodeBlockDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context),
      borderRadius: BorderRadius.circular(8),
      border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.2)),
    );
  }

  /// 构建引用块装饰
  BoxDecoration _buildBlockquoteDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context).withValues(alpha: 0.5),
      borderRadius: BorderRadius.circular(8),
      border: Border(left: BorderSide(color: AppColors.getPrimary(context), width: 4)),
    );
  }

  /// 构建水平线装饰
  BoxDecoration _buildHorizontalRuleDecoration(BuildContext context) {
    return BoxDecoration(
      border: Border(top: BorderSide(color: AppColors.getOutline(context).withValues(alpha: 0.3), width: 1)),
    );
  }

  /// 构建处理步骤列表
  ///
  /// 显示AI处理过程中的各个步骤，包括进行中、完成和错误状态
  Widget _buildProcessingSteps(BuildContext context) {
    if (message.processingSteps != null && message.processingSteps!.isNotEmpty) {
      logger.d('[MessageBubble] 渲染处理步骤: ${message.processingSteps!.length} 个');
    }

    return Container(
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.8),
      padding: Dimensions.paddingS,
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.15), width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: List.generate(message.processingSteps!.length, (index) {
          final step = message.processingSteps![index];
          return Padding(
            padding: EdgeInsets.only(bottom: index < message.processingSteps!.length - 1 ? 6 : 0),
            child: _buildStepItem(context, step, index + 1),
          );
        }),
      ),
    );
  }

  /// 构建单个步骤项
  Widget _buildStepItem(BuildContext context, ProcessingStep step, int stepNumber) {
    Widget leadingIcon;
    switch (step.status) {
      case StepStatus.pending:
        leadingIcon = Icon(Icons.radio_button_unchecked, size: 14, color: AppColors.getOnSurfaceVariant(context));
        break;
      case StepStatus.processing:
        leadingIcon = SizedBox(
          width: 14,
          height: 14,
          child: CircularProgressIndicator(
            strokeWidth: 1.5,
            valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
          ),
        );
        break;
      case StepStatus.completed:
        leadingIcon = Icon(Icons.check_circle, size: 14, color: Colors.green);
        break;
      case StepStatus.error:
        leadingIcon = Icon(Icons.error, size: 14, color: AppColors.getError(context));
        break;
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(width: 18, height: 18, child: Center(child: leadingIcon)),
        Dimensions.horizontalSpacerXs,
        Expanded(
          child: Text(
            '$stepNumber. ${step.description}',
            style: AppTypography.bodySmall.copyWith(
              color: step.status == StepStatus.error ? AppColors.getError(context) : AppColors.getOnSurface(context),
            ),
          ),
        ),
      ],
    );
  }

  /// 构建子消息
  ///
  /// 嵌套显示子消息，使用左侧缩进表示层级关系
  Widget _buildSubMessages(BuildContext context) {
    if (message.subMessages != null && message.subMessages!.isNotEmpty) {
      logger.d('[MessageBubble] 渲染子消息: ${message.subMessages!.length} 个');
    }

    return Container(
      padding: const EdgeInsets.only(left: 24), // 左侧缩进显示层级关系
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: message.subMessages!.map((subMessage) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: MessageBubble(
              message: subMessage,
              onRetry: onRetry,
              isSubMessage: true, // 标记为子消息
            ),
          );
        }).toList(),
      ),
    );
  }

  /// 构建重试按钮
  ///
  /// 当消息发送失败时显示，允许用户重新发送
  Widget _buildRetryButton(BuildContext context) {
    return TextButton.icon(
      onPressed: () {
        logger.i('[MessageBubble] 用户点击重试按钮');
        onRetry?.call();
      },
      icon: Icon(Icons.refresh, size: Dimensions.iconSizeS, color: AppColors.getError(context)),
      label: Text('ai_chat.retry'.t, style: AppTypography.bodySmall.copyWith(color: AppColors.getError(context))),
    );
  }

  /// 获取背景颜色
  ///
  /// 根据消息类型返回对应的背景颜色
  Color _getBackgroundColor(BuildContext context) {
    switch (message.type) {
      case ChatMessageType.user:
        return AppColors.getPrimary(context);
      case ChatMessageType.assistant:
        return AppColors.getSurfaceContainer(context);
      case ChatMessageType.system:
        return AppColors.getSurfaceVariant(context);
      case ChatMessageType.tool:
        return AppColors.getSecondaryContainer(context);
      case ChatMessageType.thinking:
        return AppColors.getTertiaryContainer(context);
    }
  }

  /// 获取边框样式
  ///
  /// 工具消息显示边框，其他类型无边框
  Border? _getBorder(BuildContext context) {
    if (message.type == ChatMessageType.tool) {
      return Border.all(color: AppColors.getSecondary(context).withValues(alpha: 0.3), width: 1);
    }
    return null;
  }

  /// 获取文本样式
  ///
  /// 根据消息类型和错误状态返回合适的文本样式
  TextStyle _getTextStyle(BuildContext context) {
    final baseStyle = message.type == ChatMessageType.user
        ? AppTypography.bodyMedium.copyWith(color: AppColors.getOnPrimary(context))
        : AppTypography.bodyMedium.copyWith(color: AppColors.getOnSurface(context));

    if (message.hasError) {
      return baseStyle.copyWith(color: AppColors.getError(context));
    }

    return baseStyle;
  }

  /// 构建搜索结果列表
  ///
  /// 使用可折叠组件显示搜索结果，避免占用过多空间
  Widget _buildSearchResults(BuildContext context) {
    if (message.searchResults != null && message.searchResults!.isNotEmpty) {
      logger.d('[MessageBubble] 渲染搜索结果: ${message.searchResults!.length} 条');
    }
    return _CollapsibleSearchResults(searchResults: message.searchResults!);
  }

  /// 格式化时间
  ///
  /// 将时间戳转换为相对时间或绝对时间格式
  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final difference = now.difference(time);

    if (difference.inMinutes < 1) {
      return '刚刚';
    } else if (difference.inHours < 1) {
      return '${difference.inMinutes}分钟前';
    } else if (difference.inDays < 1) {
      return '${difference.inHours}小时前';
    } else {
      return '${time.month}/${time.day} ${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
    }
  }
}

/// 可折叠的搜索结果组件
///
/// 提供折叠/展开功能，默认折叠以节省空间
class _CollapsibleSearchResults extends StatefulWidget {
  final List<dynamic> searchResults;

  const _CollapsibleSearchResults({required this.searchResults});

  @override
  State<_CollapsibleSearchResults> createState() => _CollapsibleSearchResultsState();
}

class _CollapsibleSearchResultsState extends State<_CollapsibleSearchResults> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.85),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 折叠/展开按钮
          InkWell(
            onTap: () {
              setState(() {
                _isExpanded = !_isExpanded;
                logger.d('[MessageBubble] 搜索结果${_isExpanded ? "展开" : "折叠"}');
              });
            },
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.getSurfaceContainer(context),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
                border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.2), width: 0.5),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    _isExpanded ? Icons.expand_less : Icons.expand_more,
                    size: 18,
                    color: AppColors.getPrimary(context),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _isExpanded ? '收起搜索结果' : '查看搜索结果 (${widget.searchResults.length} 条)',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.getPrimary(context),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          ),

          // 展开的搜索结果列表
          if (_isExpanded) ...[
            const SizedBox(height: 8),
            ...widget.searchResults.map((result) {
              if (result is SearchResult) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: SearchResultCard(result: result),
                );
              }
              return const SizedBox.shrink();
            }),
          ],
        ],
      ),
    );
  }
}
