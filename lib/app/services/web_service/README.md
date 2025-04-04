# 整个RESTful API的实现。以下是API结构概述：

## 工具类

1. ResponseUtils：统一处理API响应
2. RequestUtils：处理请求解析
3. AuthMiddleware：身份验证中间件
4. SessionManager：会话管理

## 控制器

1. AuthController：身份认证接口
2. ArticleController：文章管理接口
3. DiaryController：日记管理接口
4. ApiController：主控制器，集成所有子控制器

## API路由

1. /api/v2/auth/*：身份认证相关API
2. /api/v2/articles/*：文章管理API
3. /api/v2/diary/*：日记管理API
4. /api/v2/upload：文件上传API

## 静态文件支持

Web服务器支持以下静态资源访问：

1. /images：图片文件目录，访问应用存储的图片文件

## 文件上传

使用 `/api/v2/upload` 端点支持文件上传，要求：
1. 使用 multipart/form-data 格式
2. 需要认证
3. 文件会根据类型自动保存到对应的子目录
4. 返回包含文件URL的响应

## 这个实现具有以下特点：

1. 使用会话机制进行认证，支持Cookie和Bearer Token两种认证方式
2. 所有受保护资源的API都经过认证验证
3. 统一的错误处理和响应格式
4. 完整的CRUD操作和搜索功能
5. 优雅的路由结构和控制器设计
6. 代码结构清晰，易于维护和扩展
7. 若要使用这些API，客户端需要首先通过/api/v2/auth/login进行登录，然后使用返回的会话Cookie或Token来访问其他API。
8. 支持完整的静态文件托管和文件上传功能
