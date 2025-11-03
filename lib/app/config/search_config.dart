/// 搜索配置
class SearchConfig {
  SearchConfig._();

  static const Duration debounceTime = Duration(milliseconds: 300); // 搜索防抖延迟
  static const int minLength = 2; // 最小搜索长度
  static const int maxLength = 100; // 最大搜索长度
}
