，2# CMDB 微服务分层模型设计（业务模块 / 应用 / 集群 / 实例）

> 状态：设计稿（未实施）
> 涉及模块：admin/cmdbAdmin（t_master_node、t_service_group、t_env、t_service_cluster、t_service_instance）、admin/archadmin（t_service_dependency）
> 核心思路：领域/子系统/业务模块/应用/集群/实例分层建模，每层只维护自己的属性；业务模块复用现有主数据，不新建表
> 最后更新：2026-11-18

## 1. 分层与视图

模型不是一个固定深度的树，而是按视图分三段。每段只画自己需要的层级：

```
公司蓝图（2 层）     领域 → 子系统
子系统设计（2 层）   模块（展示单元）→ 应用
应用详情（3 层）     应用 → 集群 → 实例
```

| 视图 | 层级 | 用途 | 数据范围 |
| --- | --- | --- | --- |
| 公司蓝图 | 领域 → 子系统 | 公司级总览 | 不加载模块、应用 |
| 子系统设计 | 模块 → 应用 | 子系统内部结构 | 按模块折叠应用 |
| 应用详情 | 应用 → 集群 → 实例 | 部署与运行态 | 单应用下钻 |

节点类型：

| 节点 | 出现在 | 说明 |
| --- | --- | --- |
| 领域 | 公司蓝图 | 顶层业务域 |
| 子系统 | 公司蓝图 | 系统边界 |
| 模块 | 子系统设计 | 展示单元，可折叠，可为空 |
| 应用 | 子系统设计、应用详情 | 可独立构建、部署的微服务 |
| 集群 | 应用详情 | 某环境的部署单元，对应一个注册名 |
| 实例 | 应用详情 | 进程 / 容器副本，自动同步 |

模块的折叠规则：

- **1:1（默认）**：应用数量可控时，模块与微服务一一对应。不建模块节点，模块名直接用应用名，图上只出现应用。
- **1:N（数量失控时）**：多个微服务共用一个模块名，图上只画模块节点并标注微服务数量，下钻才展开应用。
- 模块是展示分组，不是部署单元：不承载注册名、环境、实例。
- 模块不等于团队：一个模块可能由多个团队维护，团队归属仍记录在负责人字段。

其他边界：

- 应用层不关心环境；同一应用的 dev / stg / prd 都指向同一条应用记录。
- 集群层不重复维护负责人、代码库和模块，这些都从应用继承。
- 实例层不允许人工新增，只做展示和状态标记。

## 2. 数据模型

### 2.1 模块层：复用 `t_master_node`，按需建

现有表已支持"领域 / 子系统 / 模块"三级（`node_type` 区分、`parent_id` 挂父级），
[BizModuleController](file:///Users/zlding/workspace/dms21/admin/cmdbAdmin/src/main/java/com/dms21/cmdbadmin/controller/BizModuleController.java) 已能维护模块。**不新建表，也不要求每个应用都有模块。**

| 字段 | 列名 | 说明 |
| --- | --- | --- |
| 节点 ID | `id` | 主键 |
| 所属子系统 | `parent_id` | 指向 `node_type=子系统` 的节点 |
| 节点类型 | `node_type` | 固定 `模块` |
| 模块名称 | `full_name` | 同一子系统内唯一；1:N 时作为图上标签 |
| 说明 | `description` | 模块边界、职责 |
| 扩展 | `ext` | 负责人、团队、架构图颜色等 |

使用规则：

| 场景 | `module_id` | 图上节点 | 下钻 |
| --- | --- | --- | --- |
| 模块与微服务 1:1 | 空 | 应用（用应用名） | 直接到集群 |
| 模块与微服务 1:N | 指向模块节点 | 模块名 + `N 个微服务` | 展开到应用列表 |

- 1:1 时不建模块记录，避免"一条微服务配一条模块"的冗余数据。
- 1:N 时才建模块，同一模块下的微服务在图上共用模块名称；应用自己的中文名称仍保留在应用层和详情页。
- 一个应用只挂一个主模块，不做多对多。
- 模块当前没有编码（`code` 为空）。服务数量到几百后建议补一个子系统内唯一编码，供架构图和接口稳定引用；没有编码时用 `id`。

### 2.2 应用层：沿用 `t_service_group`

不新建应用表，避免一次性迁移；语义收敛为"应用 + 中间件"。

| 字段 | 列名 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- | --- |
| 应用编码 | `service_name` | varchar(128) | 人工 | 唯一，如 `order-center`。列名保持不变，依赖关系与 `/api/nodes` 可零改动 |
| 中文名称 | `service_name_cn` | varchar(128) | 人工 | 如 订单中心 |
| 中文说明 | `description` | varchar(512) | 人工 | 职责、边界 |
| 所属业务模块 | `module_id` | bigint | 人工 | **新增**，关联 `t_master_node` 中 `node_type=模块`；为空表示自身即模块（1:1） |
| 所属子系统 | `subsystem_id` | bigint | 人工/派生 | 冗余保留，取自模块的 `parent_id`，便于直接按子系统统计和鉴权 |
| 类别 | `category` / `sub_category` | varchar | 人工 | 业务服务 / 中间件 |
| 代码库地址 | `repo_url` | varchar(500) | 人工 | **新增**，如 `git@gitlab.dms21.com:trade/order-center.git` |
| 默认分支 | `repo_branch` | varchar(128) | 人工 | **新增**，如 `main` / `release` |
| 技术负责人 | `tech_owner` | varchar(64) | 人工 | |
| 业务负责人 | `biz_owner` | varchar(64) | 人工 | |
| 运维负责人 | `ops_owner` | varchar(64) | 人工 | |
| 是否固定 | `confirmed` | boolean | 人工 | 确认后同步不覆盖 |
| 扩展 | `ext` | varchar(4000) | 人工 | 应用等级、文档地址、流水线地址等放这里，避免频繁加列 |

约束：`service_name` 唯一。

中间件（`category != 业务服务`）停在应用层，不建集群与实例。

### 2.3 集群层：新增 `t_service_cluster`

| 字段 | 列名 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- | --- |
| 所属应用 | `app_id` | bigint | 人工 | 关联 `t_service_group.id`；待认领时为空 |
| 注册名 | `registry_name` | varchar(128) | Eureka 发现 + 人工确认 | `spring.application.name`，**全局唯一** |
| 集群名 | `cluster_name` | varchar(64) | 人工 | `dev1`、`dev2`、`prd`、`gray` |
| 环境 | `env_id` | bigint | 人工 | 关联 `t_env.id`，必须与应用同一子系统 |
| 机房 | `idc` | varchar(64) | 人工 | 如 `SH-IDC-A`；初期自由文本，后续可接主数据 |
| 容器集群 | `k8s_cluster` | varchar(128) | 人工 | 如 `k8s-prod-sh-01`；虚拟机部署时留空 |
| 命名空间 | `namespace` | varchar(128) | 人工 | K8s namespace，如 `trade-prod` |
| 部署方式 | `deploy_type` | varchar(32) | 人工 | 容器 / 虚拟机 / 物理机 |
| 期望副本数 | `replicas` | int | 人工 | 用于和实际实例数对比 |
| 访问入口 | `entry_url` | varchar(300) | 人工 | 域名或网关地址，可空 |
| 集群状态 | `status` | varchar(16) | 人工 | 在运 / 停用 |
| 认领状态 | `claim_status` | varchar(16) | 同步 + 人工 | 待认领 / 已绑定 |
| 扩展 | `ext` | varchar(4000) | 人工 | 灰度标识、配置中心 namespace 等 |
| 创建 / 更新时间 | | datetime | 自动 | |

约束：

- `uk_cluster_registry (registry_name)`：一个注册名只能对应一个集群。
- `uk_cluster_app_env_name (app_id, env_id, cluster_name)`：同应用同环境内集群名唯一。
- 待认领记录 `app_id/env_id` 为空，H2 唯一索引对 null 不生效，Service 层补充校验。

### 2.4 实例层：新增 `t_service_instance`

| 字段 | 列名 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- | --- |
| 所属集群 | `cluster_id` | bigint | 同步 | |
| 实例 ID | `instance_id` | varchar(200) | Eureka | 通常 `host:app:port` |
| 主机名 | `host` | varchar(128) | Eureka | |
| IP | `ip` | varchar(64) | Eureka | |
| 端口 | `port` | int | Eureka | |
| 版本 | `version` | varchar(64) | metadata | 没有则空 |
| 状态 | `status` | varchar(16) | Eureka | UP / DOWN / OUT_OF_SERVICE / OFFLINE |
| 节点 / Pod | `node_name` | varchar(128) | metadata | 容器场景可填 |
| 元数据 | `metadata` | varchar(4000) | Eureka | JSON 原文 |
| 首次发现 | `first_seen` | datetime | 同步 | |
| 最后心跳 | `last_seen` | datetime | 同步 | |

约束：`uk_instance_cluster (cluster_id, instance_id)`。

生命周期：同步 upsert；快照中消失置 `OFFLINE`；超过保留期（默认 7 天，可配置）再清理。

### 2.5 表关系

| 表 | 关系 |
| --- | --- |
| `t_master_node` | 领域 → 子系统 → 模块；应用挂 `module_id`，`subsystem_id` 冗余自模块父级；集群的环境必须属于该子系统 |
| `t_env` | 集群按 `env_id` 关联环境 |
| `t_resource` | 现阶段不强制关联；后续可把机房、容器集群做成资源主数据 |
| `t_service_dependency` | 仍以应用编码为节点键，多个环境自动收敛为一条边 |

## 3. 分层编辑页面

菜单仍然只有「微服务」一个入口，集群和实例是应用下面的下级页面。

| 路由 | 页面 | 编辑内容 |
| --- | --- | --- |
| `GET /admin/service` | 应用列表 | 应用编码、中文名称、业务模块、子系统、负责人、集群数、实例数、状态；操作：编辑 / 集群 / 删除 |
| `GET /admin/service/form?id=` | 应用编辑 | 应用编码（只读）、中文名称、中文说明、业务模块（下拉）、子系统（随模块派生）、代码库地址、默认分支、三类负责人、确认固定 |
| `GET /admin/service/{id}/clusters` | 集群列表 | 该应用全部集群：环境、集群名、注册名、机房、容器集群、实例数、状态；操作：编辑 / 删除 / 新增集群 |
| `GET /admin/cluster/form?appId=&id=` | 集群编辑 | 注册名（只读）、集群名、环境、机房、容器集群、命名空间、部署方式、期望副本数、访问入口、状态；页面底部为只读实例列表 |
| 集群详情内的实例区 | 实例（只读） | host:port、版本、状态、最后心跳；不提供新增/编辑 |

这样每层只出现自己的属性，应用层不会出现"环境"，集群层不会重复出现"负责人"。

### 3.2 大图渲染：按视图折叠

对应三种视图，各自有明确的查询边界：

1. **公司蓝图**：只查领域和子系统，永不加载模块和应用；边上标注子系统间依赖条数。
2. **子系统设计**：取该子系统下所有应用，按 `module_id` 分成展示单元。
   - 没有 `module_id` 的应用，每个自成一个单元，直接画应用节点。
   - 有 `module_id` 的按模块合并成一个单元，画模块节点并标注 `N 个微服务`。
   - 单元里只有 1 个应用时仍然画应用，不画模块，避免出现"同名两层"。
3. **应用详情**：单个应用内展示集群和实例，不涉及其他应用。

数量保护：

- 单屏节点建议 ≤ 60；超过时把无模块的应用折叠成"未分组(N)"虚拟节点。
- 超过 150 个节点禁用全量力导向布局，改分层布局或列表。
- 聚合在服务端完成：`GET /api/graph?view=company|subsystem&id=` 直接返回折叠后的节点和边。
- 边保留明细：`t_service_dependency` 仍存应用级关系，聚合只发生在展示层，展开时按需查询。

这样 1:1 时图上没有多余层级，1:N 时又能把几百个微服务压到十几个节点，且不需要为了画图强制补齐模块数据。

## 4. Eureka 同步

```
for registryName in eureka.getServices():
    cluster = findClusterByRegistry(registryName)
    if cluster == null:
        cluster = createCluster(
            registryName = registryName,
            appId       = null,
            envId       = null,
            claimStatus = 待认领
        )

    instances = eureka.getInstances(registryName)
    for inst in instances:
        upsertInstance(cluster.id, inst)

    markMissingInstancesOffline(cluster.id, seenIds)
```

硬边界：

- 应用层任何字段都不写。
- 已绑定集群的 `app_id/env_id/cluster_name/idc/k8s_cluster` 等人工字段不覆盖。
- 不删除记录，只做状态标记。

待认领流程：列表顶部提示未知注册名；人工选择"绑定到已有应用 + 环境 + 集群名"或"新建应用"。绑定后 `claim_status=已绑定`。

## 5. 属性取值方式

| 属性 | 取值方式 | 说明 |
| --- | --- | --- |
| 环境 | 下拉，来源 `t_env`（按应用子系统过滤） | 保证环境与子系统一致 |
| 机房 | 初期自由文本，`datalist` 提示已有值 | 等主数据积累后再做独立表 |
| 容器集群 | 初期自由文本，`datalist` 提示已有值 | 后续可接资源清单 |
| 部署方式 | 固定选项：容器 / 虚拟机 / 物理机 | |
| 版本 | Eureka metadata `version` | 不手工维护，和 `t_version` 各自独立 |
| 注册名 | Eureka 同步，编辑时只读 | 防止改坏发现关系 |

## 6. 接口影响

- `GET /api/nodes`：继续输出应用层节点，`serviceName` = 应用编码，字段含义不变。
- 新增内部接口（免登录）：
  - `GET /api/apps/{appCode}/clusters`
  - `GET /api/clusters/{id}/instances`
- `t_service_dependency` 的 `source_name/target_name` 值不变；迁移后同一应用的多环境边自动合并。

## 7. 迁移

当前业务服务注册名不带环境后缀（`service-a`、`service-b`、`service-admin`、`user-admin`），迁移量小。

1. 建表：`ddl-auto: update` 自动新增两张表及两个应用字段。
2. 每条业务服务应用生成一条集群：`registry_name = service_name`、`cluster_name = default`、`env_id` 空、`claim_status = 待认领`。
3. 人工补应用编码（去掉环境后缀）、环境、机房、容器集群；业务模块按需补，1:1 场景留空即可。
4. 历史上同一应用被拆成多行时（如 `order-center-dev1` / `order-center-stg1`）：
   保留一行作为应用，其余行降级为集群并重绑，负责人与子系统取并集后删除多余应用行。
5. 依赖关系值不变；合并后需要人工确认没有重复边。

业务模块只在需要折叠时补；批量导入默认留空，图上按 1:1 展示，不会出现空洞节点。

## 8. 实施阶段

| 阶段 | 内容 |
| --- | --- |
| 零 | 分组能力：应用加 `module_id`（可空），编辑页加模块下拉；子系统视图按 1:1 / 1:N 折叠 |
| 一 | 应用层：加 `repo_url`、`repo_branch`，编辑页与列表展示 |
| 二 | 集群层：建表、集群列表与编辑页、应用列表加集群数 |
| 三 | 实例层：建表、同步改造、集群编辑页只读实例区、实例数展示 |
| 四 | 待认领绑定、历史应用合并、依赖边去重 |

同步改造必须和第三阶段一起上线，否则新环境注册仍会新增应用行。

## 9. 待确认

1. 代码库地址是一个应用一个仓库，还是可能多个（前后端 / 公共库）？当前按单仓库 + `ext` 扩展设计。
2. 机房、容器集群是先自由文本，还是要从 `t_resource` 资源清单里选？
3. 实例是否需要保留历史（落库 + OFFLINE 标记），还是只实时展示、不落库？
4. 生产是否也注册在同一个 Eureka？如果多注册中心隔离，环境来源要改成注册中心维度。
5. 业务模块是否统一补编码？服务量到几百后，重名和改名会影响架构图与接口引用。
6. 1:N 折叠时，模块名与微服务名前缀是否需要强制对齐，便于批量归组和搜索？
7. 是否需要"把应用一键提升为模块"的快捷操作？当前建议不做，避免产生冗余模块记录。
