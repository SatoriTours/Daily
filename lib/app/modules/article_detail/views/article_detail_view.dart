import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus;

import '../controllers/article_detail_controller.dart';
import 'widgets/article_detail_app_bar.dart';
import 'widgets/original_content_tab.dart';
import 'widgets/summary_tab.dart';
import 'widgets/tab_bar_widget.dart';

/// 文章详情页面
/// 包含两个主要标签页：
/// 1. 摘要页面：显示文章的基本信息和AI生成的摘要
/// 2. 原文页面：显示文章的完整内容
class ArticleDetailView extends GetView<ArticleDetailController> {
  const ArticleDetailView({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        // 顶部应用栏
        appBar: ArticleDetailAppBar(controller: controller),
        // 主体内容
        body: Column(
          children: [
            // 更明显的处理中横幅（AI运行中显示）
            Obx(() {
              final _ = controller.rebuildTick.value; // 监听列表更新
              final status = controller.articleModel.status;
              final processing = status == ArticleStatus.pending || status == ArticleStatus.webContentFetched;
              if (!processing) return const SizedBox.shrink();

              final colorScheme = Theme.of(context).colorScheme;
              final textTheme = Theme.of(context).textTheme;
              return Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                child: Card(
                  color: colorScheme.primaryContainer.withValues(alpha: 0.18),
                  elevation: 1,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        // 左侧细色条，低调强调
                        Container(
                          width: 6,
                          height: 48,
                          decoration: BoxDecoration(
                            color: colorScheme.primary.withValues(alpha: 0.55),
                            borderRadius: BorderRadius.circular(3),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Icon(Icons.hourglass_bottom, size: 22, color: colorScheme.primary.withValues(alpha: 0.95)),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'AI处理中…',
                                style: textTheme.bodyMedium?.copyWith(
                                  color: colorScheme.onSurface,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '正在整理标题、摘要与Markdown，完成后将自动更新本页',
                                style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurfaceVariant),
                              ),
                              const SizedBox(height: 8),
                              ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: LinearProgressIndicator(
                                  minHeight: 4,
                                  backgroundColor: colorScheme.onSurfaceVariant.withValues(alpha: 0.22),
                                  color: colorScheme.primary.withValues(alpha: 0.85),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }),

            // 内容区域
            Expanded(
              child: Obx(() {
                // 读取 rebuildTick 以触发重建
                final _ = controller.rebuildTick.value;
                return TabBarView(
                  physics: const NeverScrollableScrollPhysics(),
                  children: [
                    SummaryTab(controller: controller),
                    OriginalContentTab(controller: controller),
                  ],
                );
              }),
            ),
            // 底部标签栏
            const ArticleTabBar(),
          ],
        ),
      ),
    );
  }
}
