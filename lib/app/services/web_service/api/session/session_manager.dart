import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/objectbox/session.dart';
import 'package:shelf/shelf.dart';

/// Web 会话
///
/// 会话数据持久化在 ObjectBox（见 `SessionRepository`）。
class Session {
  Session(this.id) : _creationTime = DateTime.now(), _lastAccessTime = DateTime.now();

  factory Session.fromModel(SessionModel model) {
    final session = Session._fromEntity(model.sessionId, model.createdAt, model.lastAccessedAt);
    session.isAuthenticated = model.isAuthenticated;
    session.username = model.username;
    return session;
  }

  Session._fromEntity(this.id, this._creationTime, this._lastAccessTime);

  final String id;
  bool isAuthenticated = false;
  String? username;

  final DateTime _creationTime;
  DateTime _lastAccessTime;

  DateTime get createdAt => _creationTime;
  DateTime get lastAccessedAt => _lastAccessTime;

  Future<void> touch() async {
    _lastAccessTime = DateTime.now();
    SessionRepository.i.updateLastAccessedAt(id, _lastAccessTime);
  }

  Future<void> authenticate(String name) async {
    username = name;
    isAuthenticated = true;
    await touch();
    SessionRepository.i.authenticate(id, name);
  }

  Future<void> clearAuthentication() async {
    isAuthenticated = false;
    username = null;
    await touch();
    SessionRepository.i.clearAuthentication(id);
  }

  /// 30 分钟不活动即过期
  bool isExpired() => DateTime.now().difference(_lastAccessTime).inMinutes > 30;

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'isAuthenticated': isAuthenticated,
      'username': username,
      'createdAt': _creationTime.toIso8601String(),
      'lastAccessedAt': _lastAccessTime.toIso8601String(),
    };
  }

  SessionModel toModel() {
    final entity = SessionEntity(
      sessionId: id,
      isAuthenticated: isAuthenticated,
      username: username,
      createdAt: _creationTime,
      lastAccessedAt: _lastAccessTime,
    );
    return SessionModel(entity);
  }
}

/// 会话管理器
class SessionManager {
  static final Random _random = Random.secure();

  static Future<Session> createSession() async {
    final sessionId = _generateSessionId();
    final session = Session(sessionId);

    SessionRepository.i.save(session.toModel());
    _cleanExpiredSessions();

    return session;
  }

  static Future<Session?> getSession(String sessionId) async {
    final model = SessionRepository.i.findBySessionId(sessionId);
    if (model == null) return null;

    final session = Session.fromModel(model);
    if (session.isExpired()) {
      SessionRepository.i.removeBySessionId(sessionId);
      return null;
    }

    session.touch();
    return session;
  }

  static void destroySession(String sessionId) {
    SessionRepository.i.removeBySessionId(sessionId);
  }

  static Response createSessionResponse(Response response, Session session) {
    return response.change(headers: {'Set-Cookie': 'session_id=${session.id}; HttpOnly; Path=/; SameSite=Strict'});
  }

  static String _generateSessionId() {
    final values = List<int>.generate(32, (_) => _random.nextInt(256));
    return base64Url.encode(values);
  }

  static void _cleanExpiredSessions() {
    SessionRepository.i.cleanExpiredSessions();
  }
}
