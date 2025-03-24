import 'package:flutter/material.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:daily_satori/app/modules/article_detail/controllers/article_detail_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/global.dart';

class OriginalContentTab extends StatelessWidget {
  final ArticleDetailController controller;

  const OriginalContentTab({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    // 检查是否有Markdown内容
    final hasMarkdown =
        controller.articleModel.aiMarkdownContent != null && controller.articleModel.aiMarkdownContent!.isNotEmpty;

    // 优先显示Markdown内容，否则显示HTML内容
    return hasMarkdown ? _buildMarkdownContent(context) : _buildHtmlContent(context);
  }

  // HTML内容视图
  Widget _buildHtmlContent(BuildContext context) {
    final htmlContent = controller.articleModel.htmlContent;
    if (htmlContent == null || htmlContent.isEmpty) {
      return _buildEmptyHtmlState(context);
    }

    // 添加错误边界处理
    return SingleChildScrollView(
      padding: Dimensions.paddingPage,
      child: Builder(
        builder: (context) {
          return Html(
            data: htmlContent,
            style: HtmlStyles.getStyles(context),
            // 改进链接处理方式
            onLinkTap: (url, _, __) {
              if (url != null && url.isNotEmpty) {
                _launchUrlExternal(url);
              }
            },
            // 使用安全的配置
            shrinkWrap: true,
          );
        },
      ),
    );
  }

  // Markdown内容视图
  Widget _buildMarkdownContent(BuildContext context) {
    final markdownContent = controller.articleModel.aiMarkdownContent;
    if (markdownContent == null || markdownContent.isEmpty) {
      return _buildEmptyMarkdownState(context);
    }

    return SingleChildScrollView(
      padding: Dimensions.paddingPage,
      child: MarkdownBody(
        data: markdownContent,
        selectable: true,
        styleSheet: MarkdownStyles.getStyleSheet(context),
        onTapLink: (text, href, title) {
          if (href != null) {
            _launchUrlExternal(href);
          }
        },
      ),
    );
  }

  Widget _buildEmptyMarkdownState(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text("尚未生成Markdown内容", style: textTheme.bodyLarge),
          Dimensions.verticalSpacerM,
          ElevatedButton.icon(
            onPressed: () => _generateMarkdown(),
            icon: Icon(Icons.article_outlined),
            label: Text("生成Markdown"),
          ),
        ],
      ),
    );
  }

  // 在外部浏览器中打开URL
  Future<void> _launchUrlExternal(String url) async {
    // 安全检查URL格式
    if (url.isEmpty || !(url.startsWith('http://') || url.startsWith('https://'))) {
      logger.e('无效链接格式: $url');
      errorNotice('无效链接格式');
      return;
    }

    try {
      final uri = Uri.parse(url);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        logger.e('无法打开链接: $url');
        errorNotice('无法打开链接');
      }
    } catch (e) {
      logger.e('打开链接时出错: $e');
      errorNotice('打开链接时出错');
    }
  }

  Widget _buildEmptyHtmlState(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: Dimensions.iconSizeXl, color: colorScheme.onSurfaceVariant),
          Dimensions.verticalSpacerM,
          Text("无法加载原文内容", style: textTheme.bodyLarge),
        ],
      ),
    );
  }

  void _generateMarkdown() async {
    try {
      final loadingDialog = Get.dialog(
        Center(
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
            child: const Column(
              mainAxisSize: MainAxisSize.min,
              children: [CircularProgressIndicator(), SizedBox(height: 16), Text("正在生成Markdown内容，请稍候...")],
            ),
          ),
        ),
        barrierDismissible: false,
      );

      await controller.generateMarkdownContent();

      Get.back(); // 关闭加载对话框

      if (controller.articleModel.aiMarkdownContent != null && controller.articleModel.aiMarkdownContent!.isNotEmpty) {
        successNotice('Markdown内容生成成功');
      } else {
        errorNotice('生成Markdown内容失败');
      }
    } catch (e) {
      Get.back();
      errorNotice('生成过程中出错: $e');
      logger.e('生成Markdown时出错: $e');
    }
  }
}
