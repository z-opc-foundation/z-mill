# z-mill

**资料磨坊** —— 全格式文档 → Markdown / JSON / 纯文本 / HTML。资料进、成品出。

对标 [MinerU](https://github.com/opendatalab/MinerU)，路线不同：

| | MinerU | z-mill |
|---|---|---|
| 输入格式 | 仅 PDF（重点扫描件） | **14 种**：pdf / docx / doc / xlsx / xls / pptx / ppt / rtf / odf(odt·ods·odp) / epub / txt / html（含网页 URL 直磨） |
| 运行形态 | Python + GPU + 深度模型 | **纯 Java 17，零第三方依赖，JVM 进程内嵌** |
| 部署成本 | 模型权重数 GB、CUDA 环境 | 一个 jar |
| 解析深度 | 版面检测 / OCR / 公式识别（深度模型） | 二进制格式逐记录自研解析（BIFF 记录流 / FIB piece table / CFB 容器等） |

扫描件（无文本层 PDF）不再只能外包：**视觉 OCR 走自研 AI 底座 z-llm-gateway**（OpenAI 兼容
`chat/completions` + `image_url` dataURL 直送自家网关，不引任何外部三方服务）。判定为扫描件时
（PDF + 全文文本极少 + 带内嵌页图）逐图转写、以纯文本段落替换页图；未配置 OCR 或转写失败时
诚实降级——保留原图，行为与接入前一致。

## 快速开始

### 库用法

```java
Mill mill = new Mill();                       // classpath 上 11 个读取器自动装配（DocReader SPI）

MillOutput out = mill.grind(Path.of("报告.docx"));
out.markdown();                               // Markdown
out.json();                                   // 结构化 JSON（blocks 按类型分发）
out.plainText();                              // 纯文本
out.html();                                   // HTML

// RAG 粒度：标题驱动的分块，保留标题路径，超限在块边界续切
List<MillChunker.Chunk> chunks = MillChunker.chunk(out.document(), 1200);

// 批量：目录进、成品出，逐文件隔离失败
MillBatch.Report report = MillBatch.grindAll(mill,
        Path.of("资料库"), Path.of("成品"), List.of(MillView.MARKDOWN, MillView.JSON));
```

### 命令行

```bash
mvn -pl z-mill-cli -am package
java -jar z-mill-cli/target/z-mill.jar 资料/ -o 成品/ -f md,json
```

| 参数 | 说明 |
|---|---|
| `<文件\|目录>` | 单文件磨单件；目录批量磨，输出目录镜像相对路径 |
| `-o 目录` | 成品目录；缺省时单文件写原目录、目录写 `<目录>/z-mill-out` |
| `-f md,txt,html,json` | 成品形态，可多选；缺省 md |
| `--ocr-base-url http://…` | 扫描件 OCR：z-llm-gateway（或任意 OpenAI 兼容）根地址；留空 = 关 OCR |
| `--ocr-model 模型名` | 视觉模型；缺省 `qwen-vl-max` |
| `--ocr-api-key 密钥` | 透传给网关的 Bearer token，可空 |
| `--ocr-timeout-ms 毫秒` | 单图转写超时；缺省 60000 |

退出码：`0` 成功；`1` 用法错误；`2` 有文件研磨失败或格式不支持。

### 网页磨坊

```bash
mvn -pl z-mill-web -am package
java -jar z-mill-web/target/z-mill-web.jar --port 8891
# 扫描件 OCR（自研 AI 底座 z-llm-gateway，地址含宿主端口与 context-path）：
java -jar z-mill-web/target/z-mill-web.jar --ocr-base-url http://<llm-gateway 宿主>/
# 打开 http://localhost:8891
```

拖拽（或点击、`Ctrl/⌘+V` 粘贴）上传，多文件批量；**URL 栏粘贴 http/https 地址直接抓取整页磨制**
（15s 超时、单页 10MB 上限、仅 http/https）。成品清单逐件给状态徽章（格式 / 不支持 / 失败），
查看器六个 tab：**预览**（内置安全 Markdown 渲染：表格、列表、引用、代码块；先转义再转换，
防 XSS；**内嵌图片按 base64 真实显示** + 画廊缩略图点击下载）、Markdown 源码、JSON（已美化）、
纯文本、HTML 源码、**分块**（标题路径面包屑的 RAG 资料粒度卡片）。一键复制、逐形态下载、
**打包下载 .zip**（每文件一个目录：四形态成品 + images/ 原图 + manifest.txt 台账）、
`↑`/`↓` 键盘切文件。

`POST /api/grind`（multipart，单请求上限 64MB，响应含 `images[]` base64 数组：单张 8MB /
50 张 / 总量 30MB 预算，超出标记截断）、`POST /api/grind-url`（JSON `{"url":...}`）、
`POST /api/grind-zip`（与 grind 同参，回 application/zip）可供脚本直接调用，`GET /` 出单页。

## 模块

| 模块 | 内容 |
|---|---|
| `z-mill-core` | `Mill` 门面（嗅探→读取→渲染）、`MillOutput`（四形态按需计算）、`MillJson`（零依赖 JSON 发射器，RFC 8259 转义）、`MillChunker`（标题路径分块）、`MillBatch`（批量台账：ok/unsupported/failed 逐文件隔离） |
| `z-mill-cli` | 命令行入口 `com.zifang.mill.cli.Main`；shade 出可执行胖 jar `target/z-mill.jar`（`ServicesResourceTransformer` 合并 14 个读取器的 SPI 注册表） |
| `z-mill-web` | 网页磨坊：JDK 内置 HttpServer + 内嵌单页前端（零前端依赖、无 CDN）；手写 multipart 解析；同样 shade 出 `target/z-mill-web.jar` |

解析本体不在本仓：`Mill` 是 z-util-doc 家族（13 件零依赖读取器，`com.zifang.util.doc.DocReader` SPI）之上的研磨层。格式识别、二进制解析、样式还原能力清单见 [z-util-doc](https://github.com/z-opc-foundation/z-util)。

## 能力边界（v0.1.0）

- 不做 OCR / 版面检测 / 公式识别（深度模型范畴，路线图留外部模型适配器）。
- 图片提取为字节块（json 给字节数、markdown 给引用），不解析图片内容。
- 加密文档不支持（随 z-util-doc 家族口径）。

## 构建

```bash
# 前置：z-util 1.2.13+ 已 mvn install 进本地仓库
mvn install
```

---

<!-- z-opc-foundation-sibling-registry:v1 -->
## 🔗 兄弟仓全景（z-opc-foundation 组织）

本仓隶属于 [z-opc-foundation](https://github.com/orgs/z-opc-foundation/repositories) 组织——一人公司基座的全部自研件（现存 61 仓，含私有仓；口径 2026-11-24 GitHub org API 实读）。

| 仓 | 一句话说明 |
|---|---|
| [z-agent](https://github.com/z-opc-foundation/z-agent) | 云端 Agent 平台的库与 starter——ReAct/Plan 实现 + 注册中心 + REST 网关 + 只读控制面 |
| [z-agent-kernel](https://github.com/z-opc-foundation/z-agent-kernel) | Agent 框架基座——消息/工具/记忆/provider/会话 SPI + 轻量默认实现（纯 POJO 无 Spring） |
| [z-asset](https://github.com/z-opc-foundation/z-asset) | 企业资产台账——实物设备全生命周期 + 域名/证书/许可到期提醒 |
| [z-audit](https://github.com/z-opc-foundation/z-audit) | 审计与合规留痕——事件模型 + 哈希链 + 事件出口 SPI + 留存期策略 |
| [z-biz](https://github.com/z-opc-foundation/z-biz) | 商务外壳域——客户/合同/票据/对账四聚合 |
| [z-biz-erp](https://github.com/z-opc-foundation/z-biz-erp) | 进销存 ERP——商品/客商/采购/销售/库存流水/应收应付核销（z-biz 业务线） |
| [z-biz-security](https://github.com/z-opc-foundation/z-biz-security) | 业务数据安全防护入口——场景化策略注册 + 安全线四段流水线对外编排 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | Spring Boot Starter 聚合仓 + 版本权威（火车制）——唯一消费入口 z-boot-parent |
| [z-bot](https://github.com/z-opc-foundation/z-bot) | 本地 agent 应用——终端 CLI + HTTP/SSE web 控制台 + 多通道 gateway（飞书/钉钉/webhook） |
| [z-cache](https://github.com/z-opc-foundation/z-cache) | RESP2 协议兼容的分布式内存缓存——Java 8 + Netty，Redis 客户端零修改直连 |
| [z-camuda](https://github.com/z-opc-foundation/z-camuda) | 【已退役·归档】基于 Camunda 的工作流封装层（原 z-wf 改名，与现行 z-wf 无共同历史） |
| [z-cognition](https://github.com/z-opc-foundation/z-cognition) | 业务认知域/认知舱——术语/概念/证据链 + memory/kb/ontology 渐进式模块 |
| [z-config](https://github.com/z-opc-foundation/z-config) | 分布式配置中心 + 服务注册中心（类 Nacos）——Netty 长连接推送 + React 控制台 |
| [z-ctc](https://github.com/z-opc-foundation/z-ctc) | 4A 中心——认证/授权/账户/审计，多租户 JWT + SSO 跨子域 |
| [z-dms](https://github.com/z-opc-foundation/z-dms) | 数据源管控底座——统一登记、统一建池、统一探活、变更统一推送 |
| [z-env](https://github.com/z-opc-foundation/z-env) | 本地中间件与基础设施环境——31 个 docker-compose 场景 + make 一键运维 |
| [z-etl](https://github.com/z-opc-foundation/z-etl) | ETL 引擎——DataX 式线性作业 + Flink 式 DAG 流管道（键控状态/水位/窗口） |
| [z-ext](https://github.com/z-opc-foundation/z-ext) | Java 扩展点框架——@ExtPoint/@ExtImpl 运行期选实现，五档侵入方式 |
| [z-frontend-common](https://github.com/z-opc-foundation/z-frontend-common) | 全仓共享前端基座——AppLayout/LoginPage 等前端规范的唯一承载 |
| [z-frontend-markdown](https://github.com/z-opc-foundation/z-frontend-markdown) | Markdown 通用共享组件包（npm）——MarkdownLiveEditor/NoteMarkdown/MermaidBlock 单一真源 |
| [z-frontend-render](https://github.com/z-opc-foundation/z-frontend-render) | 平台共享前端渲染引擎——后端 schema 是唯一事实源，报表与低代码共用 |
| [z-graph](https://github.com/z-opc-foundation/z-graph) | 独立图数据库——MVCC 版本链存储 + OpenCypher 子集 + Bolt 风格协议 + React 控制台 |
| [z-gw](https://github.com/z-opc-foundation/z-gw) | 自研高性能 API 网关——Netty 手写运行时，Route/Predicate/Filter 模型可嵌 Spring Boot |
| [z-indexer](https://github.com/z-opc-foundation/z-indexer) | 代码索引服务——仓库注册表 + 定时重建索引 + 符号/调用链查询 |
| [z-introduce](https://github.com/z-opc-foundation/z-introduce) | 组织仓库展示站——全仓实测元数据汇总成可搜索静态站点 |
| [z-lc](https://github.com/z-opc-foundation/z-lc) | 低代码平台——DB 驱动通用 CRUD 引擎 + 报表/模型设计框架 |
| [z-llm-agent](https://github.com/z-opc-foundation/z-llm-agent) | CLI Agent 服务化——把本机 CLI agent（CC/Hermes/Qoder 等）暴露成 OpenAI 兼容对话 API |
| [z-llm-gateway](https://github.com/z-opc-foundation/z-llm-gateway) | LLM 网关——OpenAI/Anthropic 双协议面 + 多 vendor 凭据池、限流与 token 记账 |
| [z-llm-msghub](https://github.com/z-opc-foundation/z-llm-msghub) | msgHub 中心消息路由——WebSocket 中心枢纽 + Agent 侧客户端 SDK |
| [z-mask](https://github.com/z-opc-foundation/z-mask) | 脱敏与遮蔽引擎——按敏感命中位置遮蔽，策略即数据 |
| [z-mcp](https://github.com/z-opc-foundation/z-mcp) | MCP 服务端 + 上游聚合网关——Java 8 手写协议层，Spring Boot 自动装配 |
| [z-meta](https://github.com/z-opc-foundation/z-meta) | 元数据管理平台——租户/应用/接口清单登记 + @BizModel 业务模型采集 |
| [z-mill](https://github.com/z-opc-foundation/z-mill) | 资料磨坊——全格式文档转 Markdown/JSON/纯文本/HTML（对标 MinerU） **（本仓）** |
| [z-mist](https://github.com/z-opc-foundation/z-mist) | KMS 分布式密钥/秘密管理平台——集中加密存储 + Netty 协议 + starter 嵌入 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 自研分布式消息队列——NameServer+Broker+Store，形状对标 RocketMQ、自有 Netty 协议 |
| [z-msg](https://github.com/z-opc-foundation/z-msg) | 多通道消息 + WebSocket 实时下发基础设施——短信/邮件/站内信/Webhook/IM 平台 |
| [z-observer](https://github.com/z-opc-foundation/z-observer) | 可观测性域——指标/日志/链路 + 告警引擎 |
| [z-opc](https://github.com/z-opc-foundation/z-opc) | 一人公司统一后端+统一前端——单 JVM all-in-one 承载协作/Agent/RPA/交易/资产等业务域与自研中间件 |
| [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) | 组织治理大脑——事实表、强制规范、跨仓工具集、新机器恢复钥匙（本清单权威持有处） |
| [z-opcs](https://github.com/z-opc-foundation/z-opcs) | 任务撮合/服务市场——用户付费发布诉求，持有者接单完成，金额自动瓜分 |
| [z-ops](https://github.com/z-opc-foundation/z-ops) | 个人公司运维平台——部署单元/镜像构建/WebShell/K8s 巡检 + Ops Agent 执行器 |
| [z-oss](https://github.com/z-opc-foundation/z-oss) | 对象存储服务——自持 local 后端 + aliyun/s3 对接层，starter 注入 OssTemplate |
| [z-product](https://github.com/z-opc-foundation/z-product) | 产品设计信息汇总仓（开发域 PM 工件） |
| [z-qa](https://github.com/z-opc-foundation/z-qa) | 步骤式 API 链路测试平台——用例/计划/执行/报告/质量看板（对标 TestRail·MeterSphere） |
| [z-rpa](https://github.com/z-opc-foundation/z-rpa) | RPA 产品皮——应用市场/需求中心（执行归 z-script，触发归 z-schedule） |
| [z-rpc](https://github.com/z-opc-foundation/z-rpc) | Java 8 + Netty RPC 框架——微内核 + SPI 插件化，形状参照 Dubbo/gRPC |
| [z-schedule](https://github.com/z-opc-foundation/z-schedule) | 分布式任务调度中枢（对标 XXL-Job）——选主 + 时间环排期，starter 嵌入宿主 |
| [z-script](https://github.com/z-opc-foundation/z-script) | 多 DSL 脚本平台 + Mock 平台——沙箱执行/版本灰度/可注册 MCP Tool，starter 内嵌 |
| [z-security](https://github.com/z-opc-foundation/z-security) | 数据安全域编排层——场景→策略→识别→脱敏→水印→审计 四段流水线编排 |
| [z-sense](https://github.com/z-opc-foundation/z-sense) | 敏感识别与分类分级引擎——数据安全线底座（规则目录 + 可插拔识别器 + 分类分级） |
| [z-skill](https://github.com/z-opc-foundation/z-skill) | Skill 平台聚合器——归一化各家 Skill 形状，再按各家 API 形状发回 |
| [z-surl](https://github.com/z-opc-foundation/z-surl) | 短网址服务——短码生成/解析/命中统计，starter 一行集成 |
| [z-task](https://github.com/z-opc-foundation/z-task) | 任务协作平台——项目空间/看板/任务卡片（看板范式，对标 Trello·Teambition） |
| [z-team](https://github.com/z-opc-foundation/z-team) | 数字员工协同域——全栈独立仓，自 z-opc 毕业 |
| [z-trade](https://github.com/z-opc-foundation/z-trade) | 交易域独立仓——自 z-opc reactor 迁出 |
| [z-util](https://github.com/z-opc-foundation/z-util) | 底层 Java 工具库——89 个模块 POM，Java 8/17 口径，按需引用按需升级 |
| [z-vector](https://github.com/z-opc-foundation/z-vector) | 自研 Java 向量数据库——嵌入式+可独立部署，ANN+Payload 过滤，兼容 Qdrant REST |
| [z-voice-demo](https://github.com/z-opc-foundation/z-voice-demo) | 语音链路 demo——流式识别→kernel LLM→流式 TTS→可打断 的契约验证实现 |
| [z-watermark](https://github.com/z-opc-foundation/z-watermark) | 水印引擎——显式文字/图片隐写/盲水印，三种技术成对「打+验」 |
| [z-webide](https://github.com/z-opc-foundation/z-webide) | 企业级 WebIDE 后端——Git 仓/共享目录秒变 code-server 实例，全生命周期管理 |
| [z-wf](https://github.com/z-opc-foundation/z-wf) | 自研流程引擎——不依赖 Camunda/Flowable，定义层与运行时全自研 |

**已退役仓**（迁至 [z-trash](https://github.com/z-trash) 组织，archived 只读）：

| 仓 | 说明 |
|---|---|
| [z-agent-proxy](https://github.com/z-trash/z-agent-proxy) | 三方 coding agent 适配代理（Aider/Cursor → kernel Agent SPI），2026-10-26 退役 |
| [z-kb](https://github.com/z-trash/z-kb) | 自研知识库系统（对标 LightRAG/RAGFlow），2026-11 并入 z-cognition 认知舱后退役 |
| [z-ontology](https://github.com/z-trash/z-ontology) | 本体层（TBox/ABox/RDFS 推理），并入 z-cognition 后退役 |

另有 [z-report](https://github.com/z-opc-foundation/z-report)（报表域，2026-10-11 撤销并入 z-lc（远端已删除））。

总清单权威持有处在 [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) `008_组织规范/005_兄弟仓清单.md`。
