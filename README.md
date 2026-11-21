# z-mill

**资料磨坊** —— 全格式文档 → Markdown / JSON / 纯文本 / HTML。资料进、成品出。

对标 [MinerU](https://github.com/opendatalab/MinerU)，路线不同：

| | MinerU | z-mill |
|---|---|---|
| 输入格式 | 仅 PDF（重点扫描件） | **14 种**：pdf / docx / doc / xlsx / xls / pptx / ppt / rtf / odf(odt·ods·odp) / epub / txt / html（含网页 URL 直磨） |
| 运行形态 | Python + GPU + 深度模型 | **纯 Java 17，零第三方依赖，JVM 进程内嵌** |
| 部署成本 | 模型权重数 GB、CUDA 环境 | 一个 jar |
| 解析深度 | 版面检测 / OCR / 公式识别（深度模型） | 二进制格式逐记录自研解析（BIFF 记录流 / FIB piece table / CFB 容器等） |

两者是互补关系：电子档 z-mill 直接磨，扫描件留给 MinerU 类深度模型——z-mill 的路线图里有外部模型适配器接口。

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

退出码：`0` 成功；`1` 用法错误；`2` 有文件研磨失败或格式不支持。

### 网页磨坊

```bash
mvn -pl z-mill-web -am package
java -jar z-mill-web/target/z-mill-web.jar --port 8891
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
