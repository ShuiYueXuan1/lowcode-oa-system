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

## 九、团队分工

### 成员 A：表单引擎（前端核心）

**负责范围**：表单设计器 + 表单渲染器 + 属性面板 + 预览 + API 层 + 类型系统

| 模块 | 文件 | 功能 |
|------|------|------|
| 表单设计器 | `FormDesigner.vue` | 左（组件库）/中（画布拖拽排序）/右（属性面板）三栏布局，工具栏（保存/清空/预览/版本历史） |
| 组件库面板 | `ComponentPanel.vue` | 8 种可拖拽字段类型（HTML5 DnD） |
| 画布 | `DesignerCanvas.vue` + `CanvasField.vue` | 拖入放置区 + vuedraggable 排序 + 字段预览卡片 |
| 属性面板 | `PropertyPanel.vue` | 基础信息编辑 + **按字段类型的专属校验规则**（文本/数字/日期/选择各有不同） |
| 表单渲染器 | `FormRenderer.vue` | 读取 JSON → 动态渲染 8 种字段 + 校验规则转换 + 多选支持 + 只读模式 |
| 全屏预览页 | `SchemaPreviewPage.vue` | 可视化渲染 + 原始 JSON 双栏，支持三种 Schema 类型各异的预览 |
| 版本历史弹窗 | `VersionDialog.vue` | 版本列表 + 预览入口 + 考勤规则"设为当前" |
| API 客户端 | `api/index.ts` | 统一 fetch 封装 + 全量后端接口定义（6 组 API） |
| 类型系统 | `types/form.ts` | FormSchema / FormField / ValidationRule（含各类型专属规则字段） |
| 状态管理 | `composables/useFormDesigner.ts` | 字段增删改查、属性编辑、Schema 导出、拖入位置计算 |

**还需要关注**：前端生成的 JSON 必须与后端实体格式一致；Element Plus 校验规则命名差异（minLength→min）；多选 select 默认值需为 `[]`。

---

### 成员 B：流程引擎（前后端核心）

**负责范围**：流程设计器 + 责任链引擎 + 审批流转 + 请假业务全链路

| 模块 | 文件 | 功能 |
|------|------|------|
| 流程设计器 | `FlowDesigner.vue` + `FlowCanvas.vue` + `FlowNodeCard.vue` | 节点库加载（后端 API）→ 垂直链式画布拖拽（vuedraggable）→ 箭头连接线 |
| 节点库面板 | `NodeLibrary.vue` | 已使用节点置灰禁用、拖拽添加 |
| 条件分支编辑器 | `ConditionEditor.vue` | field/operator/value/action 四元组配置弹窗 |
| **责任链构建器** | `ChainBuilder.java` | 解析 flow_schema JSON → 条件评估 → 反射实例化 Handler Bean → 串联成链 → 生成快照 |
| **条件评估器** | `ConditionEvaluator.java` | 6 种运算符、数字/字符串自动识别、首个命中生效 |
| **审批 Handler 基类** | `ApproveHandler.java` | 抽象基类（next 链式结构 + doHandle 模板方法） |
| 直属主管 Handler | `DirectLeaderHandler.java` | 查 `sys_user.dept_id` → `sys_department.leader_id` |
| 部门经理 Handler | `DeptManagerHandler.java` | 向上级部门查找 leader_id |
| 人事 Handler | `HrHandler.java` | ROLE_ADMIN 角色查询 → 人事部门回退 |
| 总经理 Handler | `GmHandler.java` | 顶级部门（parent_id=0）leader_id |
| 组织架构查询 | `SysUserMapper` + `SysDepartmentMapper` + `SysUserRoleMapper` | 复用现有系统表 |
| 请假 Service | `LeaveService.java` + `LeaveServiceImpl.java` | 提交（6 步流程）→ 审批通过（快照流转）→ 审批驳回（终止）→ 查询记录 |
| 请假 Controller | `LeaveController.java` | POST submit / PUT approve / PUT reject / GET my-records / GET detail |
| 审批工作台 | `ApprovalPage.vue` | 左侧待审批/已处理 Tab 切换 + 右侧审批详情（表单数据+审批链+操作按钮） |
| 审批 API | `ApprovalController.java` | pending / processed / records 三个端点 |
| 请假管理页 | `LeaveManagement.vue` | 左侧申请表单 + 右侧记录列表（左右并排，提交后即时刷新） |
| 类型+状态 | `types/flow.ts` + `composables/useFlowDesigner.ts` | FlowSchema / FlowCondition + 节点增删改查 |

**还需要关注**：每个 nodeCode 仅可使用一次；审批人通过 `sys_department.leader_id` 实时查询；`snapshot_json` 是流转依据，提交后不可变。

---

### 成员 C：考勤引擎（前后端核心）

**负责范围**：考勤规则配置 + 考勤判定引擎 + 员工打卡页

| 模块 | 文件 | 功能 |
|------|------|------|
| 考勤规则配置 | `AttendanceConfig.vue` | 基础规则编辑 + 日历面板 + 节假日 API 同步 + 特殊日列表 + 版本管理 |
| 日历面板 | `CalendarPanel.vue` | 月份视图、特殊日标注（节假日绿/调休蓝）、点击循环切换、只读模式、自由翻页 |
| 规则编辑器 | `RuleEditor.vue` | 上班/下班时间、弹性分钟、迟到/早退阈值、弹性<阈值前端校验 |
| **考勤判定引擎** | `AttendanceService.java` + `AttendanceServiceImpl.java` | 签到/签退弹性判定、月度完整日历生成（4 级优先级）、请假关联 |
| 考勤 Controller | `AttendanceController.java` | POST sign-in / POST sign-out / GET today / GET monthly |
| 考勤规则 Service | `AttendanceSchemaService.java` + `AttendanceSchemaServiceImpl.java` | 版本管理 + is_current 切换 + 缓存同步 |
| 员工打卡页 | `AttendancePage.vue` | 实时时钟 + 打卡按钮 + 月度统计（6 项指标）+ 月度表格（完整日历）+ 月份导航限制 |
| 类型+状态 | `types/attendance.ts` + `composables/useAttendanceDesigner.ts` | BaseRule / SpecialDay / SyncConfig + 节假日同步逻辑 |

**还需要关注**：弹性分钟 < 阈值（前端校验 + 规则说明）；判定优先级：休息 → 请假 → 打卡 → 缺卡；月度视图截止到今天；月份导航受入职日期限制。

---

### 成员 D：系统整合 + 基础设施 — 

**负责范围**：项目搭建、数据库设计、缓存、版本管理、文档

| 模块 | 文件 | 功能 |
|------|------|------|
| 后端项目搭建 | `pom.xml` + `OaLowcodeApplication.java` + `application.yml` | Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + Druid 连接池 + Caffeine 缓存 |
| CORS 配置 | `WebConfig.java` | 允许前端跨域请求 |
| 全局异常处理 | `GlobalExceptionHandler.java` | 统一错误响应格式 |
| 分页插件 | `MyBatisPlusConfig.java` | PaginationInnerInterceptor |
| 自动填充 | `MyMetaObjectHandler.java` | createTime / updateTime |
| 数据库设计 | `init.sql` | 12 张表完整 DDL + 测试数据（5 员工、4 部门、3 角色、请假/审批/打卡记录） |
| 数据初始化器 | `DataInitializer.java` | 启动时幂等插入演示数据（表单+流程+考勤规则） |
| **缓存管理** | `CacheConfig.java` + `SchemaCacheManager.java` | 3 个 Caffeine 实例 + 启动预热 + 读写操作 + 失效策略 |
| 版本管理 | `FormSchemaService` + `FlowSchemaService` + `AttendanceSchemaService` 的 saveWithVersion/getVersions/publish/disable | form/flow 用 code+version 模型，attendance 用 is_current 模型 |
| 统一响应 | `Result.java` | 泛型封装 `{code, message, data}` |
| 前端项目搭建 | `package.json` + `vite.config.ts` + `tsconfig.json` + `index.html` | Vite 5 + Element Plus + vuedraggable + Sass |
| 应用导航 | `App.vue` | 深色顶栏 + 设计态/运行态 Tab 分组（6 个页面） |
| 实体类 | 12 个 Entity | 含 MySQL JSON 列的 JacksonTypeHandler 配置 |
| Mapper | 10 个 Mapper | MyBatis-Plus BaseMapper |
| 项目文档 | `README.md` | 架构图、模块说明、API 对照、分工表 |

**还需要关注**：Caffeine 缓存 Key 设计（form/flow 用 code，attendance 用 "current"）；Druid 只保留核心配置；`DataInitializer` 需幂等；JSON 列的 `autoResultMap = true` 不可遗漏。
