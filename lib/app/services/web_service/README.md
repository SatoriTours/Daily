# WebService 模块

本模块提供了 Flutter 应用的 Web 服务功能，包括 HTTP 服务器和 WebSocket 隧道。

## 模块结构

```
web_service/
├── web_service.dart            # 主服务管理类
├── app_http_server.dart        # HTTP 服务器实现
├── app_web_socket_tunnel.dart  # WebSocket 隧道实现
├── api_controllers/            # API 控制器
│   ├── api_controller.dart     # 主 API 控制器
│   ├── auth_controller.dart    # 认证控制器
│   ├── article_controller.dart # 文章控制器
│   └── diary_controller.dart   # 日记控制器
└── api_utils/                  # API 工具类
    ├── response_utils.dart     # 响应工具
    ├── request_utils.dart      # 请求工具
    ├── auth_middleware.dart    # 认证中间件
    └── session_manager.dart    # 会话管理
```

## 核心功能

### 1. HTTP 服务器 (AppHttpServer)

HTTP 服务器提供以下功能：

- RESTful API 接口
- 静态资源服务
- 跨域请求支持 (CORS)
- 认证和授权

### 2. WebSocket 隧道 (AppWebSocketTunnel)

WebSocket 隧道提供以下功能：

- 与中央服务器的长连接
- HTTP 请求转发
- 消息路由
- 断线重连机制

## API 结构

### API 版本与路由

- `/api/v1/*`: 旧版 API (简单接口)
- `/api/v2/*`: 新版 RESTful API

### 主要 API 端点

1. **认证 API**
   - `/api/v2/auth/login`: 用户登录
   - `/api/v2/auth/logout`: 用户登出
   - `/api/v2/auth/status`: 获取认证状态

2. **文章 API**
   - `/api/v2/articles`: 文章列表 (GET) 和创建 (POST)
   - `/api/v2/articles/:id`: 获取、更新、删除指定文章
   - `/api/v2/articles/search`: 搜索文章

3. **日记 API**
   - `/api/v2/diary`: 日记列表 (GET) 和创建 (POST)
   - `/api/v2/diary/:id`: 获取、更新、删除指定日记
   - `/api/v2/diary/search`: 搜索日记

4. **文件上传 API**
   - `/api/v2/upload`: 文件上传 (multipart/form-data)

### 静态资源

- `/images/*`: 访问应用图片目录
- `/assets/*`: 访问应用内置资源

## 开发指南

### 添加新 API

1. 在相应的控制器中添加新的路由处理方法
2. 根据需要实现认证和授权检查
3. 使用 ResponseUtils 确保响应格式一致

### API 响应格式

所有 API 响应都遵循以下格式：

```json
{
  "code": 0,       // 0 表示成功，非 0 表示错误
  "msg": "成功",    // 描述信息
  "data": { ... }  // 实际数据 (仅在成功时存在)
}
```

### 安全性

- 所有受保护的 API 都需通过 AuthMiddleware 验证
- 支持两种认证方式：Cookie 和 Bearer Token
- 使用会话机制管理用户认证状态

## 使用示例

### 通过 Web 访问应用

1. 启动应用后，使用 `WebService.i.getAppAddress()` 获取访问地址
2. 在浏览器中打开该地址可访问 Web 界面
3. 使用设备凭证登录后可远程操作应用

### 通过 WebSocket 远程访问

通过 `WebService.i.webSocketTunnel.getWebAccessUrl()` 获取 WebSocket 访问地址，可实现远程控制。
