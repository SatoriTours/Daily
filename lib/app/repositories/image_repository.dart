import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/models/image_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片仓储类
///
/// 提供操作图片实体的静态方法集合
class ImageRepository {
  // 私有构造函数防止实例化
  ImageRepository._();

  // 获取Box的静态方法
  static Box<Image> get _box => ObjectboxService.i.box<Image>();

  /// 查找所有图片
  static List<ImageModel> all() {
    return _box.getAll().map((e) => ImageModel(e)).toList();
  }

  /// 根据ID查找图片
  static ImageModel? find(int id) {
    final image = _box.get(id);
    return image != null ? ImageModel(image) : null;
  }

  /// 根据路径查找图片
  static ImageModel? findByPath(String path) {
    final query = _box.query(Image_.path.equals(path)).build();
    final image = query.findFirst();
    query.close();
    return image != null ? ImageModel(image) : null;
  }

  /// 使用数据创建图片模型
  static ImageModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final image = Image(url: data['url'], path: data['path']);

    // 设置关联
    image.article.target = articleModel.entity;

    return ImageModel(image);
  }

  /// 保存图片
  static Future<int> create(ImageModel imageModel) async {
    return await _box.putAsync(imageModel.entity);
  }

  /// 更新图片
  static Future<int> update(ImageModel imageModel) async {
    return await _box.putAsync(imageModel.entity);
  }

  /// 删除图片
  static bool destroy(int id) {
    return _box.remove(id);
  }

  /// 根据文章ID删除图片
  static int deleteByArticleId(int articleId) {
    // 查询所有与指定文章ID关联的图片
    final query = _box.query(Image_.article.equals(articleId)).build();
    final images = query.find();
    query.close();

    // 如果没有找到图片，直接返回true
    if (images.isEmpty) {
      return 0;
    }

    // 删除所有找到的图片
    return _box.removeMany(images.map((image) => image.id).toList());
  }
}
