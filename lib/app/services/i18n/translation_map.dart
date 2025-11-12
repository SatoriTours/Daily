/// 翻译映射类
///
/// 基于YAML配置文件的多语言翻译实现
class TranslationMap {
  final Map<String, dynamic> _translations;

  const TranslationMap(this._translations);

  /// 创建翻译映射
  factory TranslationMap.fromJson(Map<String, dynamic> json) {
    return TranslationMap(json);
  }

  /// 获取翻译文本
  /// 支持点分隔符的嵌套键访问，如 "error.network"
  String t(String key, {String? defaultValue}) {
    final keys = key.split('.');
    dynamic current = _translations;

    for (final k in keys) {
      if (current is Map<String, dynamic> && current.containsKey(k)) {
        current = current[k];
      } else {
        return defaultValue ?? key;
      }
    }

    return current.toString();
  }
}