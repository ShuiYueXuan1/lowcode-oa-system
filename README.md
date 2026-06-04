# 低代码 OA 系统

> 基于"元数据驱动（JSON Schema）"思想的低代码办公自动化系统  
> Vue3 + TypeScript + Element Plus | Spring Boot + MyBatis-Plus + MySQL | Druid | Caffeine

---

## 一、项目概述

### 1.1 低代码核心思想

传统 OA 系统每新增一种表单或审批流程都需要修改代码、重新部署。本项目将所有业务逻辑由"硬编码"转变为"JSON 配置驱动"：

```
管理员可视化配置 → 生成 JSON Schema → 存入 MySQL JSON 列
                                          ↓
                    运行时：前端解析 JSON 动态渲染 / 后端解析 JSON 动态执行
```

- **表单结构**：拖拽生成的 JSON → `FormRenderer` 动态渲染 Vue 组件树
- **审批流程**：链式拖拽生成的 JSON → `ChainBuilder` 反射组装责任链
- **考勤规则**：日历+API 同步生成的 JSON → `AttendanceEngine` 实时判定打卡状态

### 1.2 项目规模

| 类别 | 数量 | 说明 |
|------|------|------|
| 前端页面 | 7 个 | 表单设计器、流程设计器、考勤配置、请假管理、审批工作台、员工打卡、全屏预览 |
| 前端组件 | 10+ 个 | FormRenderer、CalendarPanel、VersionDialog 等 |
| 后端 Controller | 7 个 | REST API 共 20+ 端点 |
| 后端 Service | 5 个接口 + 5 个实现 | 表单/流程/考勤/请假/打卡 |
| 后端 Handler | 5 个 | 责任链节点（抽象基类 + 4 个实现） |
| 后端 Chain | 3 个 | ChainBuilder、ConditionEvaluator、ApproveContext |
| 数据库表 | 12 张 | 8 张核心 + 4 张系统复用 |
| TypeScript 类型 | 3 个模块 | form / flow / attendance |
| Vue Composables | 3 个 | useFormDesigner / useFlowDesigner / useAttendanceDesigner |

### 1.3 技术栈

| 层 | 技术 | 版本 | 用途 |
|---|------|------|------|
| 前端框架 | Vue 3 + Composition API | 3.4 | 响应式 UI |
| 语言 | TypeScript | 5.4 | 类型安全 |
| UI 库 | Element Plus | 2.7 | 组件库 |
| 拖拽 | vuedraggable (SortableJS) | 4.1 | 表单/流程拖拽 |
| 构建 | Vite | 5.2 | 开发 & 打包 |
| 后端框架 | Spring Boot | 3.2.5 | REST API |
| ORM | MyBatis-Plus | 3.5.5 | 数据库操作 + JSON 类型处理 |
| 数据库 | MySQL | 8.x | JSON 列存储 Schema |
| 连接池 | Druid | 1.2.16 | 连接池 + SQL 监控 |
| 缓存 | Caffeine | — | Schema JSON 本地缓存 |
| 工具 | Lombok, Hutool | — | 减少样板代码 |

---

## 二、系统架构

```
┌─────────────────── 设计态 (Design-Time) ───────────────────────┐
│                                                               │
│  [表单设计器]        [流程设计器]        [考勤规则配置]            │
│  拖拽组件→生成JSON   节点链式拖拽→JSON   日历+节假日同步→JSON       │
│       │                  │                   │                │
│       ▼                  ▼                   ▼                │
│  form_schema.JSON   flow_schema.JSON   attendance_schema.JSON │
│      (Caffeine Cache ← 启动预热 / 保存时更新)                    │
└──────────────────────┬────────────────────────────────────────┘
                       │ 元数据驱动
                       ▼
┌─────────────────── 运行态 (Run-Time) ─────────────────────────┐
│                                                              │
│  [请假管理]          [审批工作台]          [员工打卡]            │
│  动态渲染表单→提交   责任链解析→审批流转   规则判定→打卡状态         │
│       │                  │                   │               │
│       ▼                  ▼                   ▼               │
│  leave_instance      process_instance     attendance_record  │
│  (form_data JSON)    + approval_record    (打卡数据)          │
│                      (snapshot_json)                         │
└──────────────────────────────────────────────────────────────┘
```

### 导航结构（前端 6 个 Tab）

```
[低代码 OA 系统]
  设计态: [📝表单设计器] [🔗流程设计器] [🕐考勤规则]
  运行态: [📄请假管理]   [✅审批工作台] [⏰员工打卡]
```

---

## 三、核心模块详解

### 3.1 动态表单引擎

**低代码实现关键**：
- 表单结构完全由 `form_schema.schema_json` 定义（字段类型、标签、校验规则、选项）
- 前端 `FormRenderer.vue` 读取 JSON → `v-if/v-else-if` 动态渲染 8 种字段
- 校验规则从 JSON 转换为 Element Plus 格式，每种字段类型支持专属规则：
  - 文本类（input/textarea）：最小长度、最大长度、正则表达式
  - 数字类（number）：最小值、最大值、倍数、小数位
  - 日期类（date）：最早日期（支持 `today+7` 等相对值）、最晚日期、禁止过去日期
  - 选择类（select/checkbox）：多选开关、最少/最多选中项数
- 支持 `multiple`（多选下拉）和 `readonly`（只读预览）模式

**涉及文件**：`FormDesigner.vue`、`FormRenderer.vue`、`PropertyPanel.vue`、`FormSchemaServiceImpl.java`

### 3.2 动态流程引擎（责任链模式）

**低代码实现关键**：
- 审批链由 `flow_schema.schema_json.nodes` 数组定义（节点顺序、条件分支）
- `ChainBuilder.build()` 解析 JSON → 逐节点评估条件 → 反射实例化 Handler Bean → 串联成链
- `ConditionEvaluator` 支持 6 种运算符（`>` `>=` `<` `<=` `==` `!=`），数字和字符串自动识别
- 每个 Handler 从 `sys_department.leader_id` + `sys_user_role` 实时查询组织架构确定审批人：
  - 直属主管 → 申请人部门的 `leader_id`
  - 部门经理 → 上级部门的 `leader_id`
  - 人事 → ROLE_ADMIN 角色用户 或 人事部门负责人
  - 总经理 → 顶级部门（parent_id=0）的 `leader_id`
- 流程启动时生成 `snapshot_json` 快照，后续 schema 变更不影响在途流程
- 每个节点类型（nodeCode）在链中只能使用一次

**涉及文件**：`FlowDesigner.vue`、`ChainBuilder.java`、`ConditionEvaluator.java`、`ApproveHandler.java` 及 4 个子类

### 3.3 考勤规则引擎

**低代码实现关键**：
- 考勤规则由 `attendance_schema.schema_json` 定义（baseRule + specialDays + syncConfig）
- 签到判定区间：
  ```
  workStart  ─── flex ─── LATE ─── SERIOUS_LATE ───→
              NORMAL      (弹性,阈值]     >阈值
  ```
- 签退判定区间：
  ```
  ←── SERIOUS_EARLY ─── EARLY ─── flex ─── workEnd
       <阈值            [阈值,弹性)   NORMAL
  ```
- 月度视图生成完整日历，判定优先级：**休息日 → 请假（查已批准的 leave_instance）→ 打卡记录 → 缺卡**
- 特殊日支持第三方 API（timor.tech）同步 + 手动微调
- Caffeine 缓存考勤规则，启动时预热，切换规则时更新

**涉及文件**：`AttendanceConfig.vue`、`CalendarPanel.vue`、`AttendanceServiceImpl.java`

---

## 四、数据库设计

### 4.1 核心表（8 张）

| 表名 | 用途 | JSON 列 | 版本管理 |
|------|------|---------|---------|
| `form_schema` | 表单元数据 | `schema_json` | code+version 联合唯一 |
| `flow_schema` | 流程元数据 | `schema_json` | code+version 联合唯一 |
| `attendance_schema` | 考勤规则 | `schema_json` | is_current 标识生效 |
| `approval_node` | 审批节点库 | — | 预置数据 |
| `leave_instance` | 请假业务实例 | `form_data` | — |
| `process_instance` | 流程运行时 | `snapshot_json` | — |
| `approval_record` | 审批流转记录 | — | — |
| `attendance_record` | 考勤打卡记录 | — | — |

### 4.2 系统复用表（4 张）

`sys_user`（用户）、`sys_department`（部门，含 `leader_id` 组织链）、`sys_role`（角色）、`sys_user_role`（用户-角色关联）

### 4.3 JSON 列映射机制

```
HTTP JSON 请求                    MySQL JSON 列                  HTTP JSON 响应
  { schemaJson: {...} }    →     schema_json JSON    →     { schemaJson: {...} }
          │                            │                            │
  Jackson 自动                JacksonTypeHandler           Jackson 自动
  deserialize →              serialize → String →          deserialize ←
  Map<String,Object>          INSERT                        Map<String,Object>
```

关键注解：
```java
@TableName(value = "form_schema", autoResultMap = true)
@TableField(value = "schema_json", typeHandler = JacksonTypeHandler.class)
private Map<String, Object> schemaJson;
```

---

## 五、基础设施

### 5.1 Druid 连接池

- 初始连接 5 / 最小空闲 5 / 最大活跃 20
- 慢 SQL 监控（>1000ms 日志告警）+ Wall 防火墙防注入
- 监控页面：`http://localhost:8080/druid`（admin / admin）

### 5.2 Caffeine 缓存

- 三个缓存实例：formSchema（200条）/ flowSchema（200条）/ attendanceSchema（10条），24 小时过期
- 启动时 `SchemaCacheManager.warmUpCache()` 遍历 DB 预热所有最新版本
- 保存/发布 → 更新缓存；停用 → 清除缓存；运行时 → 优先读缓存，未命中查 DB 并回写

---

## 六、快速启动

### 1. 数据库
```bash
mysql -u root -p < backend/src/main/resources/db/init.sql
```
脚本包含建表 + 完整测试数据（5 员工、4 部门、3 角色、5 种请假类型的表单 + 含条件分支的审批流程 + 16 天特殊日的考勤规则 + 4 条请假实例 + 8 条审批记录 + 15+ 条打卡记录）。

### 2. 后端
```bash
cd backend
# 修改 application.yml 中的数据库用户名/密码
mvn spring-boot:run
```
启动后 `DataInitializer` 自动插入演示数据，`SchemaCacheManager` 自动预热缓存。

### 3. 前端
```bash
cd frontend
npm install
npm run dev
```
打开 http://localhost:5173

---

## 七、前后端 API 对照

| 前端页面 | 方法 | API 端点 | 说明 |
|---------|------|---------|------|
| 表单设计器 → 保存 | POST | `/api/form-schema` | 自动版本管理 |
| 表单设计器 → 版本历史 | GET | `/api/form-schema/versions/{code}` | 所有历史版本 |
| 请假管理 → 加载表单 | GET | `/api/form-schema/code/leave_apply` | 运行时渲染（优先缓存） |
| 流程设计器 → 加载节点库 | GET | `/api/approval-node` | 审批节点列表 |
| 流程设计器 → 保存 | POST | `/api/flow-schema` | 自动版本管理 |
| 流程设计器 → 版本历史 | GET | `/api/flow-schema/versions/{code}` | 所有历史版本 |
| 请假管理 → 提交 | POST | `/api/leave/submit` | 触发责任链 |
| 审批工作台 → 待审批 | GET | `/api/approval/pending?approverId=` | — |
| 审批工作台 → 已处理 | GET | `/api/approval/processed?approverId=` | 审批历史 |
| 审批工作台 → 审批链记录 | GET | `/api/approval/records/{processId}` | — |
| 审批工作台 → 同意 | PUT | `/api/leave/approve/{recordId}` | 流转下一节点 |
| 审批工作台 → 驳回 | PUT | `/api/leave/reject/{recordId}` | 终止流程 |
| 请假管理 → 记录列表 | GET | `/api/leave/my-records?userId=` | 含流程状态摘要 |
| 请假管理 → 详情 | GET | `/api/leave/{leaveId}` | 含审批记录链 |
| 员工打卡 → 签到 | POST | `/api/attendance/sign-in` | 实时判定状态 |
| 员工打卡 → 签退 | POST | `/api/attendance/sign-out` | 实时判定状态 |
| 员工打卡 → 今日状态 | GET | `/api/attendance/today?userId=` | — |
| 员工打卡 → 月度汇总 | GET | `/api/attendance/monthly?userId=&year=&month=` | 完整日历视图 |
| 考勤规则 → 保存 | POST | `/api/attendance-schema` | 新增版本 |
| 考勤规则 → 版本列表 | GET | `/api/attendance-schema/versions` | 所有历史版本 |
| 考勤规则 → 设为当前 | PUT | `/api/attendance-schema/{id}/set-current` | 切换生效规则 |

---

## 八、注意事项

1. **MySQL JSON 列**：实体必须 `@TableName(autoResultMap = true)` + `@TableField(typeHandler = JacksonTypeHandler.class)`
2. **跨域配置**：后端 `WebConfig` 已配置 CORS，前端 `vite.config.ts` 已配置 `/api` 代理到 `:8080`
3. **版本管理**：form/flow 保存时新增行，version = max(version) + 1，旧版本保留不删除；attendance 每次保存新增行，需手动"设为当前"才会生效
4. **流程快照**：提交请假时生成 `snapshot_json`，审批流转时从快照找下一节点，防止 schema 变更影响在途流程
5. **条件分支**：支持 6 种运算符，数字自动识别比较，首个命中即生效；每个 nodeCode 在链中只能使用一次
6. **考勤优先级**：休息日 → 请假（查已批准 `leave_instance`）→ 打卡记录 → 缺卡
7. **弹性时间**：上班和下班均应用弹性分钟，前端校验弹性 < 阈值，否则永远不会触发迟到/早退
8. **缓存一致性**：保存/发布 → 更新缓存；停用 → 清除缓存；启动 → 预热缓存
9. **前后端 Schema 格式**：前端生成的 JSON 直接 POST 到后端存储，运行时 GET 取回渲染，格式必须一致

---

## 九、团队分工与模块职责说明

本项目采用“纵向按业务域切分 + 横向公共能力下沉”的协作模式，确保各成员负载均衡、模块边界清晰。以下为最终确定的团队分工及详细职责说明。

### 1. 分工总览

| 成员 | 角色定位 | 核心负责范围 |
| :--- | :--- | :--- |
| **成员 A** | 表单引擎 + 请假业务（全栈） | 表单设计器/渲染器全栈、请假业务全链路、表单版本管理 |
| **成员 B** | 流程引擎核心（技术攻坚） | 流程设计器前端、审批责任链引擎、条件评估器、流程版本管理、审批后端API |
| **成员 C** | 考勤引擎 + 审批工作台前端 | 考勤规则/判定引擎全栈、员工打卡页、考勤版本管理、审批工作台前端UI |
| **成员 D** | 平台基础设施 + 数据层 | 前后端框架搭建、缓存工具类、数据库设计、数据初始化、统一响应、项目文档 |

### 2. 详细模块分工

#### 成员 A：表单引擎 + 请假业务

> **定位**：“表单+请假”业务闭环负责人，兼顾表单低代码核心与请假业务全栈开发。

| 模块 | 文件 | 功能说明 |
| :--- | :--- | :--- |
| 表单设计器(前端) | `FormDesigner.vue` / `ComponentPanel.vue` / `DesignerCanvas.vue` / `CanvasField.vue` / `PropertyPanel.vue` | 三栏布局、拖拽排序、属性编辑、专属校验规则 |
| 表单渲染+预览 | `FormRenderer.vue` / `SchemaPreviewPage.vue` / `VersionDialog.vue` | 动态渲染、JSON双栏预览、版本回滚 |
| 表单后端 | `FormSchemaController` / `FormSchemaServiceImpl` / `FormSchemaMapper` | 表单CRUD、表单版本管理(saveWithVersion/getVersions) |
| 请假业务(后端) | `LeaveService.java` / `LeaveServiceImpl.java` / `LeaveController.java` | 提交/审批通过/驳回/查询记录完整流程 |
| 请假前端 | `LeaveManagement.vue` / `ApprovalPage.vue`(前端部分) | 申请表单+记录列表、审批详情展示 |
| 表单状态+类型 | `useFormDesigner.ts` / `types/form.ts` / `api/form.ts` | 字段增删改查、Schema导出、表单专属API定义 |

> [!WARNING]
>
> -   接手请假业务后需严格遵循 `snapshot_json` 不可变原则；
> -   表单JSON格式必须与后端Entity字段严格对齐；
> -   Element Plus校验规则命名需做转换（如 `minLength → min`）。

#### 成员 B：流程引擎核心

> **定位**：剥离业务CRUD，专注审批责任链引擎、条件解析等核心技术难点。

| 模块 | 文件 | 功能说明 |
| :--- | :--- | :--- |
| 流程设计器(前端) | `FlowDesigner.vue` / `FlowCanvas.vue` / `FlowNodeCard.vue` / `NodeLibrary.vue` / `ConditionEditor.vue` | 垂直链式画布、节点拖拽、四元组条件配置 |
| 责任链引擎 | `ChainBuilder.java` / `ApproveHandler.java` | JSON解析→反射实例化Handler→串联成链→生成快照 |
| 条件评估器 | `ConditionEvaluator.java` | 6种运算符、数字/字符串自动识别、首个命中生效 |
| 审批Handler族 | `DirectLeaderHandler` / `DeptManagerHandler` / `HrHandler` / `GmHandler` | 4级组织架构审批人实时查询 |
| 流程后端 | `FlowSchemaController` / `FlowSchemaServiceImpl` | 流程CRUD、流程版本管理、节点库API |
| 审批API(后端) | `ApprovalController.java` | pending / processed / records 三个端点（仅后端） |
| 流程状态+类型 | `useFlowDesigner.ts` / `types/flow.ts` / `api/flow.ts` | 节点增删改查、流程专属API定义 |

> [!WARNING]
>
> -   每个 `nodeCode` 全局唯一，不可重复使用；
> -   审批人通过 `sys_department.leader_id` 实时查询，禁止缓存审批人信息；
> -   不再负责请假Service和审批页面前端开发。

#### 成员 C：考勤引擎 + 审批工作台前端

> **定位**：考勤全栈负责人，同时承接审批工作台前端UI开发（对接成员B的后端API）。

| 模块 | 文件 | 功能说明 |
| :--- | :--- | :--- |
| 考勤规则配置 | `AttendanceConfig.vue` / `CalendarPanel.vue` / `RuleEditor.vue` | 基础规则、日历面板、节假日同步、特殊日管理 |
| 考勤判定引擎 | `AttendanceService.java` / `AttendanceServiceImpl.java` | 弹性签到签退、月度日历生成(4级优先级)、请假关联 |
| 考勤后端 | `AttendanceController` / `AttendanceSchemaServiceImpl` | 打卡API、考勤版本管理(is_current模型)、缓存同步 |
| 员工打卡页 | `AttendancePage.vue` | 实时时钟、打卡按钮、月度统计6指标、月份导航限制 |
| 审批工作台(前端) | `ApprovalPage.vue`(完整前端) | 待审批/已处理Tab切换、审批详情、操作按钮 |
| 考勤状态+类型 | `useAttendanceDesigner.ts` / `types/attendance.ts` / `api/attendance.ts` | 节假日同步逻辑、考勤专属API定义 |

> [!WARNING]
>
> -   审批工作台需先与成员B约定好API契约，可先Mock开发；
> -   弹性分钟 < 阈值需前后端双重校验；
> -   考勤判定优先级：休息 → 请假 → 打卡 → 缺卡。

#### 成员 D：平台基础设施 + 数据层

> **定位**：纯底座支撑，不介入任何业务Schema的版本逻辑，专注框架、缓存工具、数据库和文档。

| 模块 | 文件 | 功能说明 |
| :--- | :--- | :--- |
| 后端框架搭建 | `pom.xml` / `OaLowcodeApplication.java` / `application.yml` | SpringBoot3.2.5 + MyBatisPlus + Druid + Caffeine |
| 公共配置 | `WebConfig.java` / `GlobalExceptionHandler.java` / `MyBatisPlusConfig.java` / `MyMetaObjectHandler.java` | CORS、统一异常、分页插件、自动填充 |
| 缓存工具(非业务) | `CacheConfig.java` / `SchemaCacheManager.java` | 提供3个Caffeine实例+预热+失效策略的通用工具类 |
| 统一响应 | `Result.java` | 泛型封装 `{code, message, data}` |
| 数据库设计 | `init.sql` | 12张表DDL + 测试数据(5员工/4部门/3角色/业务记录) |
| 数据初始化 | `DataInitializer.java` | 启动时幂等插入演示数据 |
| 前端框架搭建 | `package.json` / `vite.config.ts` / `tsconfig.json` / `App.vue` | Vite5 + ElementPlus + vuedraggable + 应用导航 |
| 数据层 | 12个Entity + 10个Mapper | JSON列JacksonTypeHandler、BaseMapper |
| API基础封装 | `api/index.ts` | 仅fetch封装+拦截器，不含业务接口定义 |
| 项目文档 | `README.md` | 架构图、模块说明、API对照、分工表 |

> [!WARNING]
>
> -   `SchemaCacheManager` 只提供 get/put/invalidate 方法，不调用任何业务Service；
> -   `DataInitializer` 必须保证幂等性，重复启动不产生脏数据；
> -   JSON列实体必须添加 `autoResultMap = true` 注解；
> -   Druid连接池只保留核心配置，避免过度调优。

## 3. 跨成员协作接口约定

为避免联调阻塞，以下契约需在开发前锁定：

| 协作方 | 约定内容 | 责任人 |
| :--- | :--- | :--- |
| A ↔ B | 请假Service调用ChainBuilder的入参/出参格式；`snapshot_json` 结构规范 | A、B共同确认 |
| B ↔ C | `ApprovalController` 3个端点的 Request/Response DTO | B提供接口文档，C确认 |
| A/C ↔ D | `SchemaCacheManager` 方法签名；`Result` 泛型使用规范 | D提供工具类文档 |
| 全员 ↔ D | `init.sql` 表结构变更需通知D统一维护；Entity JSON列注解规范 | D统筹管理 |
