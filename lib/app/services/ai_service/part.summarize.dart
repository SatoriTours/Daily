part of 'ai_service.dart';

extension PartSummarize on AiService {
  Future<String> summarizeOneLine(String text) async {
    // logger.i("[AI总结]: ${getSubstring(text)}");
    final res = await _sendRequest(
      '你是一个文章读者, 能总结文章的核心内容',
      ' 一句话总结一下内容：```$text``` ',
    );
    return res?.choices.first.message.content ?? '';
  }

  Future<(String, List<String>)> summarize(String text) async {
    // logger.i("[AI总结]: ${getSubstring(text)}");
    // logger.i("[AI总结] 现有标签是: [${TagsService.i.getTagsString()}]");

    final res = await _sendRequest(
      '你是一个文章读者, 能用中文总结文章的核心内容以及选择最合适的标签(tag)',
      _summarizePrompt(text),
      responseFormat: _summarizeResponseFormat,
    );

    // logger.i("[AI总结] AI返回: ${res?.toString()}");

    String summary = '';
    List<String> tags = [];
    try {
      final json = jsonDecode(res?.choices.first.message.content ?? '{}');
      summary = json['summary'] as String? ?? '';
      tags = (json['tags'] as List?)?.cast<String>() ?? [];
    } catch (e) {
      logger.e("[AI总结] 解析返回结果失败: $e");
    }

    logger.i("[AI总结] AI分析后是: summary => ${getSubstring(summary)}, tags => $tags");

    return (summary, tags);
  }

  ResponseFormat get _summarizeResponseFormat {
    return ResponseFormat.jsonSchema(
      jsonSchema: JsonSchemaObject(
        name: 'result',
        description: '内容解析结果',
        strict: true,
        schema: {
          'type': 'object',
          'properties': {
            'summary': {'type': 'string'},
            'tags': {
              'type': 'array',
              'items': {'type': 'string'},
            },
          },
          'additionalProperties': false,
          'required': ['summary', 'tags'],
        },
      ),
    );
  }

  String _summarizePrompt(String text) => '''
请对以下文章进行总结成新的文章：
```
$text
```

已有的tag是: ${(_commonTags + TagsService.i.getStringTags()).join(',')}

注意事项：
1. 提取文章的主要观点和关键信息, 一定用中文输出.
2. 确保总结简洁明了, 直接给结果，不要给其他任何的解释.
3. 保持原文的意思，不要添加个人观点.
4. 请注意```, 文章内容不包括 ```, 翻译时要保持其完整性.
5. 从上面给定的已有tag中,选出最能表达文章核心含义的3个tag,这是强制要求,不要自己创造.
''';

  // 使用AI, 提示语：根据总结和分析，一般文章有哪些常用的tag， 要求：每个tag长度不超过5，而且tag之间的含义不能太相似，列出50个。用dart的数组返回
  static final List<String> _commonTags = [
    // 技术与科学
    'AI', // 人工智能
    '编程', // 编程开发
    '网络', // 互联网
    '数据', // 数据分析
    '医疗', // 医疗健康
    '航天', // 航空航天
    '生物', // 生物科技
    '环保', // 环境保护
    '能源', // 能源技术
    '物理', // 物理科学

    // 商业与经济
    '创业', // 创业经营
    '投资', // 投资理财
    '营销', // 市场营销
    '股市', // 证券交易
    '贸易', // 国际贸易
    '零售', // 零售业务
    '房产', // 房地产
    '金融', // 金融服务
    '保险', // 保险业务
    '物流', // 物流运输

    // 文化与教育
    '教育', // 教育培训
    '文学', // 文学创作
    '艺术', // 艺术创作
    '历史', // 历史研究
    '哲学', // 哲学思想
    '戏剧', // 戏剧表演
    '民俗', // 民间文化
    '考古', // 考古发现
    '军事', // 军事战略
    '法律', // 法律知识

    // 生活与娱乐
    '美食', // 饮食文化
    '旅行', // 旅游度假
    '时尚', // 时尚潮流
    '体育', // 体育运动
    '摄影', // 摄影技术
    '音乐', // 音乐艺术
    '电影', // 电影作品
    '游戏', // 电子游戏
    '动漫', // 动画漫画
    '宠物', // 宠物饲养

    // 社会与生活
    '职场', // 职业发展
    '情感', // 情感生活
    '育儿', // 育儿经验
    '心理', // 心理健康
    '养生', // 养生保健
    '星座', // 星座运势
    '汽车', // 汽车文化
    '家居', // 家居生活
    '园艺', // 园艺种植
    '手工', // 手工制作

    // 效率与生产力
    '时间', // 时间管理
    '目标', // 目标设定
    '习惯', // 习惯养成
    '专注', // 专注力提升
    '笔记', // 笔记方法
    '工具', // 效率工具
    '流程', // 流程优化
    '团队', // 团队协作
    '学习', // 学习方法
    '规划', // 生活规划

    // 云服务与技术
    '云计算', // 云计算服务
    '容器', // 容器技术
    '微服务', // 微服务架构
    '数据库', // 数据库服务
    '存储', // 云存储服务
    '网络', // 网络服务
    '安全', // 云安全服务
    '监控', // 监控服务
    '部署', // 部署服务
    'DevOps', // DevOps实践
  ];
}
