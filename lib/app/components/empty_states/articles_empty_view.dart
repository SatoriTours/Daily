import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';

/// 文章列表空状态组件
class ArticlesEmptyView extends StatelessWidget {
  const ArticlesEmptyView({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 120,
            height: 120,
            decoration: BoxDecoration(color: AppColors.primary(context).withOpacity(0.1), shape: BoxShape.circle),
            child: Icon(Icons.article_outlined, size: 60, color: AppColors.primary(context)),
          ),
          const SizedBox(height: 24),
          Text(
            '还没有收藏内容',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
          ),
          const SizedBox(height: 12),
          Text('您可以通过分享功能添加新文章', style: TextStyle(fontSize: 14, color: AppColors.textSecondary(context))),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}
