import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/index.dart';

class ArticleTags extends StatelessWidget {
  final String tags;

  const ArticleTags({super.key, required this.tags});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return tags.isNotEmpty
        ? Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            spacing: 10.0,
            runSpacing: 10.0,
            children:
                tags.split(', ').map((tag) {
                  return Container(
                    decoration: BoxDecoration(
                      color: colorScheme.primaryContainer.withAlpha(179),
                      borderRadius: BorderRadius.circular(Dimensions.radiusM),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
                    child: Text(
                      tag,
                      style: textTheme.bodySmall?.copyWith(
                        color: colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  );
                }).toList(),
          ),
        )
        : const SizedBox.shrink();
  }
}
