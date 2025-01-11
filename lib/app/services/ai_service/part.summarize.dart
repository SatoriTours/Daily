part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    // logger.i("[AI总结]: ${getSubstring(text)}");
    final res = await _sendRequest(
      '你是一个文章读者, 总结一个能表达文章核心内容并且能吸引别人阅读的标题,保持原文的意思，注意:不使用“文章提到”或类似的表达方式，一定不要添加个人观点, 标题不要加入引号等特殊字符',
      ' 一句话总结一下内容：```$text``` ',
    );
    return res?.choices.first.message.content ?? '';
  }

  Future<String> summarize(String text) async {
    if (!SettingService.i.aiEnabled()) return text;
    // logger.i("[AI总结]: ${getSubstring(text)}");
    // logger.i("[AI总结] 现有标签是: [${TagsService.i.getTagsString()}]");

    final res = await _sendRequest(
      '你是一个文章读者, 能用中文总结文章的核心内容',
      _summarizePrompt(text),
    );

    String summary = res?.choices.first.message.content ?? '';

    logger.i("[AI总结] AI分析后是: summary => ${getSubstring(summary)}");

    return summary;
  }

  Future<List<String>> getTags(String text) async {
    if (!SettingService.i.aiEnabled()) return <String>[];
    final res = await _sendRequest(
      '你是一个智能助手',
      _tagsPrompt(text),
    );
    final result = res?.choices.first.message.content ?? '';
    logger.i("[AI总结] AI分析后是: tags => $result");
    List<String> tags = [];
    try {
      final aiTags = result.split(',');
      // 从 _commonTags 中找到匹配的 key
      for (var entry in _commonTags.entries) {
        if (entry.value.any((value) => aiTags.any((t) => t.toLowerCase().contains(value.toLowerCase())))) {
          tags.add(entry.key);
        }
      }
    } catch (e) {
      logger.e("[AI标签] JSON解析失败: $e");
    }

    logger.i("[AI总结] 过滤之后的标签是: tags => $tags");
    return tags;
  }

  String _summarizePrompt(String text) => '''
根据以下格式总结文章内容：

概述:
  文章的核心意思

关键内容:
  抽取文章中5个最关键要表达的具体内容, 按照1, 2, 3, 4, 5的方式分段列出。

相关案例:(没有案例和数据就不输出)
  文章最关键的案例或数据,请总结出来,同样按照1, 2, 3等分段列出。

总结要求：
1. 输出内容为纯文本,不包含任何markdown或其他排版格式。
2. 内容精简，只表达核心意思。
3. 不要以“文章主要介绍”或类似的表达方式，直接输出内容就可以。

根据以上要求总结以下文章内容：[$text]
''';

  String _tagsPrompt(String text) => '''
根据下面文章内容, 总结出10个最合适的互联网常用的中文文章标签:
```
$text
```
''';

  // 使用AI, 提示语：根据总结和分析，一般文章有哪些常用的tag， 要求：每个tag长度不超过5，而且tag之间的含义不能太相似，列出50个。用dart的数组返回
  // 已有的tag是: ${(_commonTags + TagsService.i.getStringTags()).join(',')}
  static final Map<String, List<String>> _commonTags = {
    '软件': [
      '编程',
      '软件',
      '代码',
      '开源',
      'java',
      'python',
      'c++',
      'c#',
      'javascript',
      'typescript',
      'go',
      'rust',
      'php',
      'swift',
      'kotlin',
      'dart',
      'scala',
      'haskell',
      'erlang',
      'elixir',
      'ruby',
      'php',
      'swift',
      'kotlin',
      'dart',
      'scala',
      'haskell',
      'erlang',
      'elixir',
      'ruby',
      'php',
      'swift',
      'kotlin',
      'dart',
      'scala',
      'haskell',
      'erlang',
      'elixir',
      'ruby'
    ],
    '硬件': ['硬件', 'CPU', 'GPU', '芯片', '服务器', '硬盘', '内存', '存储'],
    '生活': ['美食', '旅行', '时尚', '体育', '摄影', '音乐', '电影', '游戏', '动漫', '宠物'],
    '效率': ['效率', '习惯', '效率', '时间管理', '目标管理'],
    '新闻': ['新闻', '热点', '事件', '时事', '政治', '经济', '科技', '社会', '文化', '教育', '体育', '娱乐', '财经', '军事', '国际', '国内'],
    '工具': ['工具', 'GitHub', '下载', 'docker', 'gitlab', 'web', '优化', '爬虫', '网络'],
    '成长': ['成长', '智慧', '思维', '学习', '自我提升', '技巧', '习惯', '方法', '考试', '面试', '解决', '思考', '总结'],
    '设计': ['设计', 'UI', 'UX', '用户体验', '交互设计', '视觉设计', '平面设计', '网页设计', '移动应用设计', '产品设计', '用户体验设计'],
    '健康': ['健康', '养生', '心理', '星座', '汽车', '家居', '园艺', '手工', '健身', '饮食', '睡眠', '锻炼', '运动', '减肥'],
    'AI': ['AI', '人工智能', '机器学习', '深度学习', '自然语言处理', '计算机视觉', '语音识别', '数据分析', '大数据', '云计算', '区块链'],
    '互联网': ['互联网', '互联网产品', '互联网公司', '互联网技术', '互联网安全', '互联网发展', '互联网趋势', '互联网创业', '互联网投资', '互联网管理'],
    '云计算': [
      '云',
      'k8s',
      '云计算',
      '云服务',
      '云存储',
      '云安全',
      '云基础设施',
      '云平台',
      '云应用',
      '云管理',
      '云监控',
      '云迁移',
      'vps',
      '虚拟机',
      '阿里云',
      '腾讯云',
      '华为云',
      'AWS',
      'Azure',
      'Google Cloud',
      'Oracle Cloud',
      'IBM Cloud',
    ],
  };
}
