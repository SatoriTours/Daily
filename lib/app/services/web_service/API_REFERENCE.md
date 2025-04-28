# API 参考文档

本文档详细介绍了Daily Satori应用的REST API接口，用于Web页面功能对接。

## 基本信息

- **基础URL**: `http://<设备IP>:8888/api/v2`
- **认证方式**: 基于会话的认证，需要先通过登录API获取会话凭证
- **响应格式**: 所有API均返回JSON格式数据

## 通用响应格式

所有API请求的响应都遵循以下格式:

```json
{
  "code": 0,       // 0表示成功，非0表示错误
  "msg": "成功",    // 状态消息
  "data": {...}    // 响应数据（仅在成功时返回）
}
```

## 错误码

| 错误码 | 描述 |
|-------|------|
| 0     | 成功 |
| 400   | 请求参数错误 |
| 401   | 未授权或身份验证失败 |
| 404   | 资源不存在 |
| 500   | 服务器内部错误 |

## 认证 API

### 登录

**请求**:
- 方法: `POST`
- 路径: `/auth/login`
- 内容类型: `application/json`
- 参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| password | string | 是 | 服务器访问密码 |

**示例请求**:
```json
{
  "password": "your_password"
}
```

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true
  }
}
```

**注意**: 登录成功后，服务器会设置会话Cookie，后续请求需要包含此Cookie。

### 退出登录

**请求**:
- 方法: `POST`
- 路径: `/auth/logout`
- 需要认证: 是

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true
  }
}
```

### 检查认证状态

**请求**:
- 方法: `GET`
- 路径: `/auth/status`

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "authenticated": true
  }
}
```

## 文章 API

### 获取文章列表

**请求**:
- 方法: `GET`
- 路径: `/articles`
- 需要认证: 是
- 查询参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| page  | int  | 否  | 页码，默认为1 |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "items": [
      {
        "id": 1,
        "title": "文章标题",
        "content": "文章内容...",
        "url": "https://example.com/article",
        "isFavorite": false,
        "comment": "用户评论",
        "coverImage": "/images/cover.jpg",
        "createdAt": "2023-06-15T10:30:00.000Z",
        "updatedAt": "2023-06-15T10:30:00.000Z"
      },
      // 更多文章...
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "totalItems": 45,
      "totalPages": 3
    }
  }
}
```

### 搜索文章

**请求**:
- 方法: `GET`
- 路径: `/articles/search`
- 需要认证: 是
- 查询参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| q     | string | 是 | 搜索关键词 |
| page  | int    | 否 | 页码，默认为1 |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "items": [
      // 匹配的文章列表
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "totalItems": 5,
      "totalPages": 1
    }
  }
}
```

### 获取单个文章

**请求**:
- 方法: `GET`
- 路径: `/articles/{id}`
- 需要认证: 是
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 文章ID |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 1,
    "title": "文章标题",
    "content": "文章内容...",
    "url": "https://example.com/article",
    "isFavorite": false,
    "comment": "用户评论",
    "coverImage": "/images/cover.jpg",
    "createdAt": "2023-06-15T10:30:00.000Z",
    "updatedAt": "2023-06-15T10:30:00.000Z"
  }
}
```

### 创建文章

**请求**:
- 方法: `POST`
- 路径: `/articles`
- 需要认证: 是
- 内容类型: `application/json`
- 参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| title | string | 是 | 文章标题 |
| content | string | 否 | 文章内容 |
| url | string | 否 | 文章原始URL |
| isFavorite | boolean | 否 | 是否收藏，默认false |
| comment | string | 否 | 用户评论 |
| coverImage | string | 否 | 封面图片路径 |

**示例请求**:
```json
{
  "title": "新文章标题",
  "content": "新文章内容",
  "url": "https://example.com/new-article",
  "isFavorite": true,
  "comment": "这是我的评论",
  "coverImage": "/images/new-cover.jpg"
}
```

**成功响应** (状态码: 201):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 2,
    "title": "新文章标题",
    "content": "新文章内容",
    "url": "https://example.com/new-article",
    "isFavorite": true,
    "comment": "这是我的评论",
    "coverImage": "/images/new-cover.jpg",
    "createdAt": "2023-06-16T14:20:00.000Z",
    "updatedAt": "2023-06-16T14:20:00.000Z"
  }
}
```

### 更新文章

**请求**:
- 方法: `PUT`
- 路径: `/articles/{id}`
- 需要认证: 是
- 内容类型: `application/json`
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 文章ID |

- 请求体参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| title | string | 否 | 文章标题 |
| content | string | 否 | 文章内容 |
| url | string | 否 | 文章原始URL |
| isFavorite | boolean | 否 | 是否收藏 |
| comment | string | 否 | 用户评论 |
| coverImage | string | 否 | 封面图片路径 |

**示例请求**:
```json
{
  "title": "更新后的标题",
  "isFavorite": true
}
```

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 1,
    "title": "更新后的标题",
    "content": "文章内容...",
    "url": "https://example.com/article",
    "isFavorite": true,
    "comment": "用户评论",
    "coverImage": "/images/cover.jpg",
    "createdAt": "2023-06-15T10:30:00.000Z",
    "updatedAt": "2023-06-16T15:45:00.000Z"
  }
}
```

### 删除文章

**请求**:
- 方法: `DELETE`
- 路径: `/articles/{id}`
- 需要认证: 是
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 文章ID |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true
  }
}
```

### 获取网页信息

**请求**:
- 方法: `POST`
- 路径: `/articles/fetch-webpage`
- 需要认证: 是
- 内容类型: `application/json`
- 参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| url | string | 是 | 要获取信息的网页URL |

**示例请求**:
```json
{
  "url": "https://example.com/article"
}
```

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "title": "网页标题",
    "content": "网页内容...",
    "coverImage": "https://example.com/image.jpg"
  }
}
```

## 日记 API

### 获取日记列表

**请求**:
- 方法: `GET`
- 路径: `/diary`
- 需要认证: 是
- 查询参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| page  | int  | 否  | 页码，默认为1 |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "items": [
      {
        "id": 1,
        "content": "今天的日记内容...",
        "tags": "生活,工作",
        "mood": "开心",
        "images": "/images/diary1.jpg",
        "createdAt": "2023-06-15T20:30:00.000Z",
        "updatedAt": "2023-06-15T20:30:00.000Z"
      },
      // 更多日记...
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "totalItems": 30,
      "totalPages": 2
    }
  }
}
```

### 搜索日记

**请求**:
- 方法: `GET`
- 路径: `/diary/search`
- 需要认证: 是
- 查询参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| q     | string | 是 | 搜索关键词 |
| page  | int    | 否 | 页码，默认为1 |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "items": [
      // 匹配的日记列表
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "totalItems": 3,
      "totalPages": 1
    }
  }
}
```

### 获取单个日记

**请求**:
- 方法: `GET`
- 路径: `/diary/{id}`
- 需要认证: 是
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 日记ID |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 1,
    "content": "今天的日记内容...",
    "tags": "生活,工作",
    "mood": "开心",
    "images": "/images/diary1.jpg",
    "createdAt": "2023-06-15T20:30:00.000Z",
    "updatedAt": "2023-06-15T20:30:00.000Z"
  }
}
```

### 创建日记

**请求**:
- 方法: `POST`
- 路径: `/diary`
- 需要认证: 是
- 内容类型: `application/json`
- 参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| content | string | 是 | 日记内容 |
| tags | string | 否 | 标签，逗号分隔 |
| mood | string | 否 | 心情 |
| images | string | 否 | 图片路径，逗号分隔 |

**示例请求**:
```json
{
  "content": "今天是美好的一天...",
  "tags": "生活,心情",
  "mood": "愉快",
  "images": "/images/day.jpg"
}
```

**成功响应** (状态码: 201):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 2,
    "content": "今天是美好的一天...",
    "tags": "生活,心情",
    "mood": "愉快",
    "images": "/images/day.jpg",
    "createdAt": "2023-06-16T19:45:00.000Z",
    "updatedAt": "2023-06-16T19:45:00.000Z"
  }
}
```

### 更新日记

**请求**:
- 方法: `PUT`
- 路径: `/diary/{id}`
- 需要认证: 是
- 内容类型: `application/json`
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 日记ID |

- 请求体参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| content | string | 否 | 日记内容 |
| tags | string | 否 | 标签，逗号分隔 |
| mood | string | 否 | 心情 |
| images | string | 否 | 图片路径，逗号分隔 |

**示例请求**:
```json
{
  "content": "更新后的日记内容",
  "mood": "平静"
}
```

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 1,
    "content": "更新后的日记内容",
    "tags": "生活,工作",
    "mood": "平静",
    "images": "/images/diary1.jpg",
    "createdAt": "2023-06-15T20:30:00.000Z",
    "updatedAt": "2023-06-16T21:10:00.000Z"
  }
}
```

### 删除日记

**请求**:
- 方法: `DELETE`
- 路径: `/diary/{id}`
- 需要认证: 是
- 路径参数:

| 参数名 | 类型 | 描述 |
|-------|------|------|
| id    | int  | 日记ID |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true
  }
}
```

## 文件上传 API

### 上传文件

**请求**:
- 方法: `POST`
- 路径: `/upload`
- 需要认证: 是
- 内容类型: `multipart/form-data`
- 参数:

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| file  | file | 是 | 要上传的文件 |

**成功响应** (状态码: 200):
```json
{
  "code": 0,
  "msg": "成功",
  "data": [
    {
      "original_name": "image.jpg",
      "saved_path": "img/1624567890_image.jpg",
      "url": "/public/img/1624567890_image.jpg",
      "size": 102400
    }
  ]
}
```

## 静态资源访问

除了API接口外，服务器还提供以下静态资源访问路径:

### 图片文件

- URL路径: `/images/{文件路径}`
- 访问应用存储的图片文件

### 公共文件

- URL路径: `/public/{文件路径}`
- 访问通过文件上传API上传的文件

### 内置资源

- URL路径: `/assets/{文件路径}`
- 访问应用内置的静态资源

## 错误处理

当API请求发生错误时，将返回相应的错误代码和消息，示例:

```json
{
  "code": 401,
  "msg": "未登录或会话已过期",
  "data": null
}
```

## WebSocket 远程访问

除了HTTP接口外，应用还支持通过WebSocket进行远程访问:

- WebSocket访问URL: `http://<服务器地址>:<端口>/mobile/<设备ID>`
- 通过WebSocket可以远程触发HTTP请求，接收响应
