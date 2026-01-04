import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/pages/ai_chat/models/search_result.dart';
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
    // 调试日志：检查搜索结果
    if (message.type == ChatMessageType.assistant) {
      logger.d('[MessageBubble] 助手消息 - searchResults: ${message.searchResults?.length ?? 0}条');
    }

    // 如果是空内容的助手消息（只有子消息或处理步骤），不渲染外层主内容气泡
    if (message.type == ChatMessageType.assistant && message.content.trim().isEmpty) {
      return Container(
        width: double.infinity,
        padding: isSubMessage ? EdgeInsets.zero : const EdgeInsets.symmetric(horizontal: 12),
        child: Column(
          crossAxisAlignment: _getCrossAxisAlignment(),
          children: [
            _buildMessageHeader(context),
            // 只有在消息未完成时才显示处理步骤
            if (_shouldShowProcessingSteps()) ...[const SizedBox(height: 6), _buildProcessingSteps(context)],
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
          // 消息有内容后不再显示处理步骤
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

  /// 判断是否应该显示处理步骤
  ///
  /// 只有在消息正在处理中（无内容）时才显示处理步骤
  /// 当消息已完成生成内容后，隐藏处理步骤
  bool _shouldShowProcessingSteps() {
    if (message.processingSteps == null || message.processingSteps!.isEmpty) {
      return false;
    }
    // 如果消息已经有内容，说明搜索已完成，隐藏处理步骤
    if (message.content.trim().isNotEmpty) {
      return false;
    }
    // 如果消息状态为已完成，也隐藏处理步骤
    if (message.status == MessageStatus.completed) {
      return false;
    }
    return true;
  }

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
  /// 系统消息、思考消息和用户消息不显示头部
  Widget _buildMessageHeader(BuildContext context) {
    // 用户消息、系统消息、思考消息不显示头部
    if (message.type == ChatMessageType.user ||
        message.type == ChatMessageType.system ||
        message.type == ChatMessageType.thinking) {
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
    final isUser = message.type == ChatMessageType.user;
    const radius = Radius.circular(Dimensions.radiusL);
    const smallRadius = Radius.circular(Dimensions.radiusXs);

    return BoxDecoration(
      color: _getBackgroundColor(context),
      borderRadius: BorderRadius.only(
        topLeft: radius,
        topRight: radius,
        bottomLeft: isUser ? radius : smallRadius,
        bottomRight: isUser ? smallRadius : radius,
      ),
      border: _getBorder(context),
      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, 2))],
    );
  }

  /// 构建思考指示器
  Widget _buildThinkingIndicator(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainerHighest(context).withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
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
    final textColor = AppColors.getOnSurface(context);
    final secondaryColor = AppColors.getOnSurfaceVariant(context);

    return MarkdownStyleSheet(
      // 全局块间距 - 控制列表项等块元素之间的间距
      blockSpacing: 8,
      // 段落样式 - 使用适中字体和紧凑行距
      p: AppTypography.bodyMedium.copyWith(color: textColor, height: 1.5),
      pPadding: const EdgeInsets.only(bottom: 4),
      // 标题样式 - 适中大小
      h1: AppTypography.titleMedium.copyWith(color: textColor, fontWeight: FontWeight.bold, height: 1.3),
      h1Padding: const EdgeInsets.only(top: 8, bottom: 4),
      h2: AppTypography.titleSmall.copyWith(color: textColor, fontWeight: FontWeight.bold, height: 1.3),
      h2Padding: const EdgeInsets.only(top: 6, bottom: 4),
      h3: AppTypography.bodyLarge.copyWith(color: textColor, fontWeight: FontWeight.w600, height: 1.3),
      h3Padding: const EdgeInsets.only(top: 4, bottom: 2),
      h4: AppTypography.bodyMedium.copyWith(color: textColor, fontWeight: FontWeight.w600, height: 1.3),
      // 强调样式
      strong: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.bold, color: AppColors.getPrimary(context)),
      em: AppTypography.bodyMedium.copyWith(fontStyle: FontStyle.italic, color: secondaryColor),
      // 列表样式 - 适中行距
      listBullet: AppTypography.bodyMedium.copyWith(color: textColor, height: 1.4),
      listIndent: 16,
      listBulletPadding: const EdgeInsets.only(right: 4),
      orderedListAlign: WrapAlignment.start,
      unorderedListAlign: WrapAlignment.start,
      // 代码样式
      code: _buildCodeStyle(context),
      codeblockPadding: const EdgeInsets.all(10),
      codeblockDecoration: _buildCodeBlockDecoration(context),
      // 引用块样式
      blockquotePadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      blockquoteDecoration: _buildBlockquoteDecoration(context),
      // 水平线样式
      horizontalRuleDecoration: _buildHorizontalRuleDecoration(context),
    );
  }

  /// 构建代码样式
  TextStyle _buildCodeStyle(BuildContext context) {
    return AppTypography.labelMedium.copyWith(
      fontFamily: 'monospace',
      backgroundColor: AppColors.getSurfaceContainer(context),
      color: AppColors.getPrimary(context),
    );
  }

  /// 构建代码块装饰
  BoxDecoration _buildCodeBlockDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
      border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.2)),
    );
  }

  /// 构建引用块装饰
  BoxDecoration _buildBlockquoteDecoration(BuildContext context) {
    return BoxDecoration(
      color: AppColors.getSurfaceContainer(context).withValues(alpha: 0.5),
      borderRadius: BorderRadius.circular(Dimensions.radiusS),
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
      constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.85),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.getSurfaceContainer(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '处理流程',
            style: AppTypography.labelSmall.copyWith(
              color: AppColors.getOnSurfaceVariant(context),
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          ...List.generate(message.processingSteps!.length, (index) {
            final step = message.processingSteps![index];
            return Padding(
              padding: EdgeInsets.only(bottom: index < message.processingSteps!.length - 1 ? 8 : 0),
              child: _buildStepItem(context, step, index + 1),
            );
          }),
        ],
      ),
    );
  }

  /// 构建单个步骤项
  Widget _buildStepItem(BuildContext context, ProcessingStep step, int stepNumber) {
    Widget leadingIcon;
    Color stepColor;

    switch (step.status) {
      case StepStatus.pending:
        stepColor = AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.5);
        leadingIcon = Icon(Icons.circle_outlined, size: 12, color: stepColor);
        break;
      case StepStatus.processing:
        stepColor = AppColors.getPrimary(context);
        leadingIcon = SizedBox(
          width: 12,
          height: 12,
          child: CircularProgressIndicator(strokeWidth: 1.5, valueColor: AlwaysStoppedAnimation<Color>(stepColor)),
        );
        break;
      case StepStatus.completed:
        stepColor = AppColors.getSuccess(context);
        leadingIcon = Icon(Icons.check_circle, size: 14, color: stepColor);
        break;
      case StepStatus.error:
        stepColor = AppColors.getError(context);
        leadingIcon = Icon(Icons.error, size: 14, color: stepColor);
        break;
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(padding: const EdgeInsets.only(top: 3), child: leadingIcon),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            step.description,
            style: AppTypography.bodyMedium.copyWith(
              color: step.status == StepStatus.pending
                  ? AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.7)
                  : AppColors.getOnSurface(context),
              height: 1.4,
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
      label: Text('ai_chat.retry'.t, style: AppTypography.bodyMedium.copyWith(color: AppColors.getError(context))),
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
        ? AppTypography.bodyLarge.copyWith(color: AppColors.getOnPrimary(context))
        : AppTypography.bodyLarge.copyWith(color: AppColors.getOnSurface(context));

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
          Material(
            color: AppColors.getSurfaceContainer(context),
            borderRadius: BorderRadius.circular(Dimensions.radiusM),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () {
                setState(() {
                  _isExpanded = !_isExpanded;
                  logger.d('[MessageBubble] 搜索结果${_isExpanded ? "展开" : "折叠"}');
                });
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.getOutline(context).withValues(alpha: 0.1)),
                  borderRadius: BorderRadius.circular(Dimensions.radiusM),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.search, size: 16, color: AppColors.getPrimary(context)),
                    const SizedBox(width: 8),
                    Text(
                      '搜索结果 (${widget.searchResults.length})',
                      style: AppTypography.labelMedium.copyWith(
                        color: AppColors.getOnSurface(context),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: 4),
                    Icon(
                      _isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                      size: 18,
                      color: AppColors.getOnSurfaceVariant(context),
                    ),
                  ],
                ),
              ),
            ),
          ),

          // 展开的搜索结果列表
          if (_isExpanded) ...[
            const SizedBox(height: 12),
            ...widget.searchResults.map((result) {
              if (result is SearchResult) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
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
