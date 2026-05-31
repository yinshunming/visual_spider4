# Visual Spider - 可视化爬虫管理系统

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **语言**: Java 21
- **数据库**: PostgreSQL 16
- **构建工具**: Maven

### 前端
- **框架**: Vue 3
- **构建工具**: Vite 5
- **UI 组件**: Element Plus
- **状态管理**: Pinia
- **HTTP 客户端**: Axios

## 本地开发环境搭建

### 前置条件

- JDK 21+
- Node.js 18+
- Docker Desktop（用于 PostgreSQL）

### 1. 启动数据库

```bash
docker compose up -d
```

验证 PostgreSQL 启动成功：
```bash
docker ps
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动成功后，访问健康检查接口：
```
http://localhost:8080/api/v1/health
```

### 3. 启动前端

```bash
cd frontend
npm install  # 首次运行需要安装依赖
npm run dev
```

前端启动成功后，访问：
```
http://localhost:5173
```

## 环境变量

后端支持以下环境变量（可选，使用默认值即可本地开发）：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | 数据库主机 |
| DB_PORT | 5432 | 数据库端口 |
| DB_NAME | visual_spider4 | 数据库名称 |
| DB_USERNAME | postgres | 数据库用户名 |
| DB_PASSWORD | 123456 | 数据库密码 |

## 端口映射

| 服务 | 端口 |
|------|------|
| 后端 (Spring Boot) | 8080 |
| 前端 (Vite) | 5173 |
| PostgreSQL | 5432 |

## 项目结构

```
visual_spider4/
├── backend/                 # Spring Boot 后端项目
│   ├── src/
│   │   ├── main/java/com/visualspider/
│   │   │   ├── controller/  # REST API 控制器
│   │   │   ├── service/     # 业务逻辑
│   │   │   ├── repository/  # 数据访问层
│   │   │   ├── entity/      # JPA 实体
│   │   │   ├── dto/         # 数据传输对象
│   │   │   └── exception/   # 异常处理
│   │   └── test/            # 单元测试
│   └── pom.xml
├── frontend/                # Vue 3 前端项目
│   ├── src/
│   │   ├── api/            # API 封装
│   │   ├── stores/         # Pinia 状态管理
│   │   ├── views/          # 页面组件
│   │   ├── App.vue         # 根组件
│   │   └── main.js         # 入口文件
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── docker-compose.yml       # PostgreSQL 容器配置
└── README.md
```

## API 统一响应格式

### 成功响应
```json
{
  "code": 200,
  "data": { ... },
  "message": "success"
}
```

### 错误响应
```json
{
  "code": 400,
  "data": null,
  "message": "错误描述"
}
```

## 健康检查

### 后端健康检查

```
GET /api/v1/health
```

响应示例：
```json
{
  "code": 200,
  "data": {
    "status": "UP",
    "database": "UP",
    "timestamp": "2024-01-01T00:00:00Z"
  },
  "message": "success"
}
```

## 常用命令

### 后端
```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run
```

### 前端
```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```
