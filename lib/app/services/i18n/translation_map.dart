
/// 翻译映射类
///
/// 基于JSON配置文件的多语言翻译实现
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

  /// 错误消息
  String get errorNetwork => t('error.network');
  String get errorServer => t('error.server');
  String get errorTimeout => t('error.timeout');
  String get errorUnknown => t('error.unknown');
  String get errorValidation => t('error.validation');
  String get errorPermission => t('error.permission');
  String get errorNotFound => t('error.notFound');
  String get errorDuplicate => t('error.duplicate');

  /// 成功消息
  String get successSave => t('success.save');
  String get successDelete => t('success.delete');
  String get successUpdate => t('success.update');
  String get successImport => t('success.import');
  String get successExport => t('success.export');
  String get successBackup => t('success.backup');
  String get successRestore => t('success.restore');

  /// 提示消息
  String get hintSearch => t('hint.search');
  String get hintComment => t('hint.comment');
  String get hintUrl => t('hint.url');
  String get hintTag => t('hint.tag');
  String get hintTitle => t('hint.title');
  String get hintContent => t('hint.content');

  /// 空状态消息
  String get emptyArticles => t('empty.articles');
  String get emptyDiary => t('empty.diary');
  String get emptyBooks => t('empty.books');
  String get emptyTags => t('empty.tags');
  String get emptySearch => t('empty.search');
  String get emptyFavorites => t('empty.favorites');

  /// 占位符文本
  String get placeholderTitle => t('placeholder.title');
  String get placeholderContent => t('placeholder.content');
  String get placeholderSummary => t('placeholder.summary');
  String get placeholderUrl => t('placeholder.url');
  String get placeholderDate => t('placeholder.date');
  String get placeholderAuthor => t('placeholder.author');

  /// UI 通用文本
  String get processing => t('ui.processing');
  String get cancel => t('ui.cancel');
  String get save => t('ui.save');
  String get saveChanges => t('ui.saveChanges');
  String get updateArticle => t('ui.updateArticle');
  String get saveLink => t('ui.saveLink');
  String get link => t('ui.link');
  String get title => t('ui.title');
  String get comment => t('ui.comment');
  String get optional => t('ui.optional');
  String get aiAnalysis => t('ui.aiAnalysis');
  String get inputOrModifyArticleTitle => t('ui.inputOrModifyArticleTitle');
  String get addCommentInfo => t('ui.addCommentInfo');

  /// 书籍相关
  String get addBookTitle => t('book.addBookTitle');
  String get addBookHint => t('book.addBookHint');
  String get addBookConfirm => t('book.addBookConfirm');
  String get addBookCancel => t('book.addBookCancel');
}