import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:shelf/shelf.dart';
import 'package:daily_satori/app/objectbox/session.dart';
import 'package:daily_satori/app/repositories/session_repository.dart';

/// 用户会话类
class Session {
  final String id;
  bool isAuthenticated = false;
  String? username;

  // 内部存储的时间，从数据库加载或新建时设置
  final DateTime _creationTime;
  DateTime _lastAccessTime;

  // 构造函数，用于创建新会话
  Session(this.id) : _creationTime = DateTime.now(), _lastAccessTime = DateTime.now();

  /// 从实体创建会话
  factory Session.fromEntity(SessionEntity entity) {
    final session = Session._fromEntity(entity.sessionId, entity.createdAt, entity.lastAccessedAt);
    session.isAuthenticated = entity.isAuthenticated;
    session.username = entity.username;
    return session;
  }

  // 私有构造函数，用于从实体创建会话
  Session._fromEntity(this.id, this._creationTime, this._lastAccessTime);

  // 公开的getter方法
  DateTime get createdAt => _creationTime;
  DateTime get lastAccessedAt => _lastAccessTime;

  /// 更新最后访问时间
  void touch() {
    _lastAccessTime = DateTime.now();
    SessionRepository.updateLastAccessedAt(id, _lastAccessTime);
  }

  /// 设置为已认证状态
  void authenticate(String name) {
    username = name;
    isAuthenticated = true;
    touch();
    SessionRepository.authenticate(id, name);
  }

  /// 清除认证状态
  void clearAuthentication() {
    isAuthenticated = false;
    username = null;
    touch();
    SessionRepository.clearAuthentication(id);
  }

  /// 是否已过期（30分钟不活动即过期）
  bool isExpired() {
    return DateTime.now().difference(_lastAccessTime).inMinutes > 30;
  }

  /// 转换为Map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'isAuthenticated': isAuthenticated,
      'username': username,
      'createdAt': _creationTime.toIso8601String(),
      'lastAccessedAt': _lastAccessTime.toIso8601String(),
    };
  }

  /// 转换为数据库实体
  SessionEntity toEntity() {
    return SessionEntity(
      sessionId: id,
      isAuthenticated: isAuthenticated,
      username: username,
      createdAt: _creationTime,
      lastAccessedAt: _lastAccessTime,
    );
  }
}

/// 会话管理器
class SessionManager {
  static final Random _random = Random.secure();

  /// 创建会话
  static Session createSession() {
    final sessionId = _generateSessionId();
    final session = Session(sessionId);

    // 保存到数据库
    final entity = session.toEntity();
    SessionRepository.saveSession(entity);

    // 每创建会话时，清理过期会话
    _cleanExpiredSessions();

    return session;
  }

  /// 获取会话
  static Future<Session?> getSession(String sessionId) async {
    // 从数据库获取会话
    final entity = SessionRepository.findBySessionId(sessionId);

    if (entity != null) {
      final session = Session.fromEntity(entity);

      if (session.isExpired()) {
        // 如果会话已过期，删除并返回null
        SessionRepository.removeSession(sessionId);
        return null;
      }

      // 更新最后访问时间
      session.touch();
      return session;
    }

    return null;
  }

  /// 销毁会话
  static void destroySession(String sessionId) {
    SessionRepository.removeSession(sessionId);
  }

  /// 创建带会话的响应
  static Response createSessionResponse(Response response, Session session) {
    return response.change(headers: {'Set-Cookie': 'session_id=${session.id}; HttpOnly; Path=/; SameSite=Strict'});
  }

  /// 生成会话ID
  static String _generateSessionId() {
    final values = List<int>.generate(32, (i) => _random.nextInt(256));
    return base64Url.encode(values);
  }

  /// 清理过期会话
  static void _cleanExpiredSessions() {
    SessionRepository.cleanExpiredSessions();
  }
}
