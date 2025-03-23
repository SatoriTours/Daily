import 'dart:io';

import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/components/articles/article_info_item.dart';
import 'package:daily_satori/app/components/articles/article_action_bar.dart';

/// 文章卡片组件
class ArticleCard extends StatelessWidget {
  final ArticleModel articleModel;
  final VoidCallback? onArticleUpdated;

  const ArticleCard({super.key, required this.articleModel, this.onArticleUpdated});

  @override
  Widget build(BuildContext context) {
    final isProcessing = articleModel.entity.status != 'completed' && articleModel.entity.status != '';
    final colorScheme = AppTheme.getColorScheme(context);

    return Stack(
      children: [
        Card(
          margin: EdgeInsets.zero,
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          child: InkWell(
            onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: articleModel),
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [_buildArticleContent(context), _buildActionBar(context)],
              ),
            ),
          ),
        ),
        if (isProcessing)
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedBorder(borderRadius: BorderRadius.circular(10), color: colorScheme.primary),
            ),
          ),
      ],
    );
  }

  Widget _buildArticleContent(BuildContext context) {
    final hasImage = articleModel.hasHeaderImage();
    final imagePath = articleModel.getHeaderImagePath();
    final hasTitle =
        (articleModel.aiTitle != null && articleModel.aiTitle!.isNotEmpty) ||
        (articleModel.title != null && articleModel.title!.isNotEmpty);

    return Stack(
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (hasImage) _buildImage(context, imagePath),
            if (hasImage) const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [if (hasTitle) _buildTitle(context) else _buildUrlAsTitle(context)],
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildUrlAsTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Text(
      articleModel.url ?? '',
      style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurfaceVariant),
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Text(
      articleModel.aiTitle ?? articleModel.title ?? '',
      style: textTheme.titleMedium,
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildImage(BuildContext context, String path) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox(
        width: 90,
        height: 70,
        child: Image.file(
          File(path),
          fit: BoxFit.cover,
          errorBuilder:
              (_, __, ___) => Container(
                width: 90,
                height: 70,
                decoration: ComponentStyle.imageContainerDecoration(context),
                child: Icon(Icons.image_not_supported, color: Theme.of(context).colorScheme.onSurfaceVariant, size: 24),
              ),
        ),
      ),
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final url = Uri.parse(articleModel.url ?? '');

    return Container(
      margin: const EdgeInsets.only(top: 6),
      height: 24,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          ArticleInfoItem(icon: Icons.public, text: getTopLevelDomain(url.host)),
          const SizedBox(width: 12),
          ArticleInfoItem(
            icon: Icons.access_time,
            text: articleModel.createdAt != null ? GetTimeAgo.parse(articleModel.createdAt!, pattern: 'MM-dd') : '未知时间',
          ),
          const Spacer(),
          ArticleActionBar(articleModel: articleModel, onArticleUpdated: onArticleUpdated),
        ],
      ),
    );
  }
}

/// 动画边框组件
class AnimatedBorder extends StatefulWidget {
  final BorderRadius borderRadius;
  final Color color;
  final double strokeWidth;

  const AnimatedBorder({Key? key, required this.borderRadius, required this.color, this.strokeWidth = 2.0})
    : super(key: key);

  @override
  State<AnimatedBorder> createState() => _AnimatedBorderState();
}

class _AnimatedBorderState extends State<AnimatedBorder> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return CustomPaint(
          painter: BorderPainter(
            progress: _controller.value,
            borderRadius: widget.borderRadius,
            color: widget.color,
            strokeWidth: widget.strokeWidth,
          ),
          child: Container(),
        );
      },
    );
  }
}

class BorderPainter extends CustomPainter {
  final double progress;
  final BorderRadius borderRadius;
  final Color color;
  final double strokeWidth;

  BorderPainter({required this.progress, required this.borderRadius, required this.color, required this.strokeWidth});

  @override
  void paint(Canvas canvas, Size size) {
    final paint =
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = strokeWidth
          ..strokeCap = StrokeCap.round;

    final path = Path();
    final rect = Rect.fromLTWH(0, 0, size.width, size.height);
    path.addRRect(borderRadius.toRRect(rect));

    final pathMetrics = path.computeMetrics().first;
    final pathLength = pathMetrics.length;

    final dashLength = pathLength * 0.2; // 虚线长度为总长度的20%
    final dashOffset = pathLength * progress; // 根据动画进度计算偏移量

    final extractPath = pathMetrics.extractPath(dashOffset, dashOffset + dashLength);

    canvas.drawPath(extractPath, paint);
  }

  @override
  bool shouldRepaint(BorderPainter oldPainter) {
    return oldPainter.progress != progress ||
        oldPainter.color != color ||
        oldPainter.strokeWidth != strokeWidth ||
        oldPainter.borderRadius != borderRadius;
  }
}
