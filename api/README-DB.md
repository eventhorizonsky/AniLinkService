# 数据库配置说明

本服务支持 H2（默认）和 PostgreSQL 两种数据库。

## 默认 H2 数据库
- 默认使用本地文件型 H2 数据库，数据文件位于 `./data/anilink`。
- 无需额外配置，适合本地开发和轻量级部署。

## 切换到 PostgreSQL
- 启动容器时通过环境变量 `DB_PROFILE=pgsql` 激活 PostgreSQL 配置。
- 需同时设置以下环境变量（可选，带默认值）：
  - `DB_HOST`（默认：localhost）
  - `DB_PORT`（默认：5432）
  - `DB_NAME`（默认：anilink）
  - `DB_USER`（默认：postgres）
  - `DB_PASS`（默认：postgres）

### Docker 启动 PostgreSQL 示例
```sh
docker run -e DB_PROFILE=pgsql \
  -e DB_HOST=192.168.1.100 \
  -e DB_PORT=5432 \
  -e DB_NAME=anilink \
  -e DB_USER=postgres \
  -e DB_PASS=yourpassword \
  -p 8081:8081 your-image-name
```

## 配置文件说明
- `application.properties`：主配置，默认激活 H2。
- `application-h2.properties`：H2 数据库配置。
- `application-pgsql.properties`：PostgreSQL 配置。

如需自定义配置，可挂载覆盖 `/app/config` 目录。

## Liquibase 使用指引

本项目采用 Liquibase 进行数据库结构管理，支持多数据库适配。

### 目录结构
- `db/changelog/db.changelog-master.yaml`：主入口，统一管理所有变更。
- `db/changelog/common/`：存放通用 SQL 变更，推荐尽量只维护 common。
- `db/changelog/h2/`、`db/changelog/pgsql/`：如有数据库差异，仅将特殊变更放入对应目录。

### 多数据库适配推荐做法
- changelog 文件中可通过 `dbms` 属性区分数据库类型：

```yaml
- changeSet:
    id: 1-pg
    author: yourname
    dbms: postgresql
    changes:
      - ...
- changeSet:
    id: 1-h2
    author: yourname
    dbms: h2
    changes:
      - ...
```
- 通用变更直接写在 common，特殊变更用 `dbms` 或 `context` 控制。
- H2 可通过 `MODE=PostgreSQL` 提高兼容性，但并非所有语法都兼容。

### 本地开发建议
- 推荐开发时使用 H2 的 PostgreSQL 模式：
  `spring.datasource.url=jdbc:h2:file:./data/anilink;MODE=PostgreSQL`
- 生产环境使用 PostgreSQL，确保 changelog 在两种数据库下均可顺利执行。

### 参考文档
- [Liquibase 官方文档](https://docs.liquibase.com/)
- [H2 Database Modes](https://www.h2database.com/html/features.html#compatibility)