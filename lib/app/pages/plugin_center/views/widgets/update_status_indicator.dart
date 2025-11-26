import 'package:flutter/material.dart';

/// 更新状态指示器组件
class UpdateStatusIndicator extends StatelessWidget {
  /// 正在更新的文件名
  final String updatingFileName;

  /// 构造函数
  const UpdateStatusIndicator({super.key, required this.updatingFileName});

  @override
  Widget build(BuildContext context) {
    if (updatingFileName.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
      decoration: BoxDecoration(
        color: Colors.blue.withAlpha(20),
        border: Border(bottom: BorderSide(color: Colors.blue.withAlpha(26), width: 1)),
      ),
      child: Row(
        children: [
          const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2.5, valueColor: AlwaysStoppedAnimation<Color>(Colors.blue)),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('正在更新插件', style: TextStyle(color: Colors.blue, fontSize: 14, fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Text(
                  updatingFileName,
                  style: TextStyle(color: Colors.blue.withAlpha(179), fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
