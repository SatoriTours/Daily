import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/session.dart';

/// Session模型类
class SessionModel extends EntityModel<SessionEntity> {
  SessionModel(super.entity);

  // ==================== 属性访问器 ====================

  String get sessionId => entity.sessionId;
  set sessionId(String value) => entity.sessionId = value;

  bool get isAuthenticated => entity.isAuthenticated;
  set isAuthenticated(bool value) => entity.isAuthenticated = value;

  String? get username => entity.username;
  set username(String? value) => entity.username = value;

  DateTime get lastAccessedAt => entity.lastAccessedAt;
  set lastAccessedAt(DateTime value) => entity.lastAccessedAt = value;
}
