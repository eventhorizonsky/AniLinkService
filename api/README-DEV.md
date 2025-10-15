# 后端开发规范

本文档旨在为 `AniLinkService` 后端服务的开发提供一套统一的结构和编码规范，以确保代码的可读性、可维护性和可扩展性。

## 1. 项目根目录结构

后端所有代码均位于 `api/` 模块下，其主要结构如下：

```
api/
├── pom.xml                # Maven 项目配置文件
├── src/main/java/         # Java 源代码
│   └── xyz/ezsky/anilink/
├── src/main/resources/    # 资源文件
└── src/test/              # 测试代码
```

## 2. Java 包（Package）结构规范

所有 Java 类都应放置在 `xyz.ezsky.anilink` 基础包下，并根据其职责划分到不同的子包中。

-   **`xyz.ezsky.anilink`** (根包)
    -   `AniLinkServiceApplication.java`: Spring Boot 主启动类。

-   **`config/`**
    -   **用途**: 存放应用的配置类。
    -   **示例**: `WebConfig.java`, `SecurityConfig.java` 等使用 `@Configuration` 注解的类。

-   **`controller/`**
    -   **用途**: 存放 RESTful API 的控制器（Controller）。
    -   **职责**: 负责接收和解析 HTTP 请求，调用 `Service` 层处理业务逻辑，并向客户端返回响应（通常是 JSON 格式的 `VO`）。**Controller 层不应包含任何业务逻辑**。

-   **`service/`**
    -   **用途**: 存放核心业务逻辑。
    -   **职责**: 实现具体的业务功能，处理数据和调用 `Repository` 层。它是业务逻辑的入口点。

-   **`repository/`**
    -   **用途**: 存放数据访问层接口。
    -   **职责**: 定义与数据库交互的方法，通常是继承 Spring Data JPA 的 `JpaRepository` 或其他类似接口。**不要在此层实现复杂的业务逻辑**。

-   **`listener/`**
    -   **用途**: 存放事件监听器。
    -   **职责**: 监听 Spring 应用事件（如 `ApplicationReadyEvent`）或其他自定义事件，并执行相应的异步或启动任务。

-   **`model/`**
    -   **用途**: 存放数据模型。这是最重要和最复杂的部分，详见下一章节。

-   **`util/`**
    -   **用途**: 存放与业务无关的通用工具类。
    -   **示例**: `DateUtils.java`, `StringUtils.java` 等。

## 3. 模型（Model）分层规范

为了保证各层之间数据流的清晰和解耦，`model` 包被细分为三个子包：

-   **`model/entity/`**
    -   **用途**: 存放数据库实体类（Entity）。
    -   **规范**:
        -   必须使用 `@Entity` 注解。
        -   类名和属性应与数据库表和字段一一对应。
        -   **此对象只能在 `Repository` 层和 `Service` 层内部使用，严禁向上暴露到 `Controller` 层或返回给前端**。

-   **`model/dto/` (Data Transfer Object)**
    -   **用途**: 用于数据传输的对象。
    -   **规范**:
        -   用于 `Service` 层接收来自 `Controller` 层的数据，或在不同的 `Service` 之间传递数据。
        -   结构可以根据业务需求自由组合，不一定与数据库实体完全对应。
        -   **通常用于创建或更新操作的数据载体**。

-   **`model/vo/` (View Object)**
    -   **用途**: 用于视图展示的对象。
    -   **规范**:
        -   `Controller` 层返回给前端的最终数据结构。
        -   **其字段和结构应严格根据前端页面的展示需求来定义**，可以隐藏敏感信息或组合多个来源的数据。
        -   `Service` 层应将 `Entity` 或其他数据处理后，转换为 `VO` 对象，再返回给 `Controller`。

### 数据流向示例

1.  **查询请求**: `Controller` -> `Service` -> `Repository` (获取 `Entity`) -> `Service` (将 `Entity` 转换为 `VO`) -> `Controller` (返回 `VO` 给前端)。
2.  **创建/更新请求**: 前端 -> `Controller` (接收 `DTO`) -> `Service` (处理 `DTO`，转换为 `Entity`) -> `Repository` (持久化 `Entity`)。

## 4. 数据库变更（Migration）规范

-   **工具**: 项目使用 **Liquibase** 进行数据库版本控制。
-   **位置**: 所有变更脚本都应存放在 `src/main/resources/db/changelog/` 目录下。
-   **主文件**: `db.changelog-master.yaml` 是所有变更的入口文件。
-   **脚本组织**:
    -   `common/`: 存放通用的、与具体数据库无关的初始化脚本。
    -   `h2/`, `pgsql/`: 存放针对特定数据库（H2, PostgreSQL）的脚本。
    -   新的变更应创建新的 `yaml` 或 `sql` 文件，并在 `master.yaml` 中按顺序引入。

## 5. 配置文件规范

-   **位置**: `src/main/resources/`
-   `application.properties`: 存放所有环境通用的基础配置。
-   `application-{profile}.properties`: 存放特定环境（如 `h2`, `pgsql`, `prod`）的配置。通过激活不同的 Spring Profile 来加载。

遵守以上规范，可以帮助我们构建一个更加清晰、健壮和易于协作的后端服务。
