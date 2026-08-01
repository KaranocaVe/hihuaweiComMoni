# 前端设计 QA

## 对照目标

- 设计来源：`output/design-audit/02-semi-table.png`，Semi Design 官方 Table 文档页。
- 实现截图：`output/design-audit/14-desktop-final.png`，AscendComMoni 最新榜单页。
- 移动端截图：`output/design-audit/13-mobile-final.png`。
- 全屏同图对照：`output/design-audit/15-semi-vs-implementation.png`。
- 导航与数据密度局部对照：`output/design-audit/16-nav-density-comparison.png`。
- 主要实现：`frontend/src/App.tsx`、`frontend/src/styles.css`、`frontend/src/components/RankingPanel.tsx`、`frontend/src/components/OverviewPanel.tsx`、`frontend/src/components/AnomalyPanel.tsx`、`frontend/src/components/HistoryPanel.tsx`、`frontend/src/components/PollRunsPanel.tsx`。

## 归一化信息

- 官方参考：1400 × 834 px，CSS 视口约 1400 × 834，deviceScaleFactor 1。
- 桌面实现：1408 × 792 px，CSS 视口 1408 × 792，deviceScaleFactor 1。
- 同图对照：两侧均保持原始比例；桌面实现仅在底部补白到 1408 × 834，没有缩放或拉伸。
- 移动端实现：760 × 900 px，CSS 视口 760 × 900，deviceScaleFactor 1。
- 页面状态：最新榜单 / Concat / 94 条数据 / 正常采集状态。
- 参考与实现不是相同业务页面，因此对照的是 Semi 的布局、组件、视觉令牌和信息密度，而不是逐像素复制官方文档内容。

## Findings

没有剩余的 P0、P1 或 P2 问题。

- 字体与排版：使用 Inter、PingFang SC、Microsoft YaHei 的系统无衬线栈；标题、辅助信息、表头和数据行的字号与字重层级清晰，长队名和单位信息有截断策略。
- 间距与布局：220px 侧栏、64px 顶栏、24px 桌面内容边距形成稳定网格；表格工具栏、表头和行高接近 Semi 官方页面的紧凑节奏，没有宣传页式大留白。
- 颜色与令牌：背景、边框、文本、主色和状态色全部使用 Semi CSS token；没有渐变、装饰光斑和多余阴影。
- 图片与资产：该管理界面不需要图片资产；所有可见功能图标来自 `@douyinfe/semi-icons`，没有手绘 SVG、emoji 或 CSS 图形替代。
- 文案与内容：页面只保留榜单、筛选、状态和监控数据所需文字；已删除大标题宣传文案、全局警告、口号和重复状态标签。
- 交互与状态：侧栏导航、移动端导航、赛题切换、队伍搜索、刷新、队伍趋势跳转和分页使用 Semi 组件；最新榜单作为默认页。
- 响应式：760px 下侧栏切换为顶部 Select，次要表格列隐藏，表格宽度与内容容器一致，无横向溢出或单元格错位。
- 可访问性：刷新、搜索和导航控件具备语义控件及 aria-label；颜色不是异常信号的唯一表达方式。

## Full-view comparison evidence

`output/design-audit/15-semi-vs-implementation.png` 将官方参考和最终实现放在同一张图中。最终实现保留了官方 Semi 页面可见的白底、浅边框、蓝色选中态、左侧导航、紧凑工具栏和清晰内容层级，同时去除了旧版的深色 Hero、装饰卡片和冗长说明。

## Focused region comparison evidence

`output/design-audit/16-nav-density-comparison.png` 放大比较了导航、标题和数据区。图标尺寸、文字权重、选中态背景、分隔线及列表密度一致，没有发现需要继续修正的中高优先级差异。

## Comparison history

1. 基线 `output/design-audit/03-current-app.png`
   - [P1] 大面积深色 Hero、宣传式副标题、全局告警和装饰统计卡与 Semi 管理后台语言不一致。
   - 修复：重构为 `Layout + Sider + Nav + Header + Table`；删除 Hero、口号、装饰背景、全局告警和重复的正常标签。
   - 修复后证据：`output/design-audit/05-redesign-final-ranking.png`、`output/design-audit/14-desktop-final.png`。

2. 第一轮响应式 `output/design-audit/10-redesign-mobile.png`
   - [P2] 移动端隐藏表头后，对应数据单元格和 col 定义未完全同步，表格存在宽度漂移。
   - 修复：在 `frontend/src/styles.css` 中同步隐藏第 4 至第 6 列的表头、单元格和 col，并把表格宽度约束到容器。
   - 修复后证据：`output/design-audit/11-redesign-mobile-final.png`、`output/design-audit/12-redesign-mobile-passed.png`、`output/design-audit/13-mobile-final.png`；容器和表格实测均为 726px。

3. 最终对照
   - 同图检查 `output/design-audit/15-semi-vs-implementation.png` 和局部检查 `output/design-audit/16-nav-density-comparison.png` 未发现新的 P0/P1/P2。

## 运行与浏览器验证

- `npm run build`：通过；TypeScript 和 Vite 生产构建成功。
- Docker：frontend、backend、postgres 均为 healthy。
- Backend health：`UP`；frontend HTTP：200。
- 浏览器数据：Concat 当前加载 94 条真实榜单记录。
- 已测试：最新榜单、概览、异常信号、采集运行、点击 `cgmxl` 打开队伍趋势、760 × 900 响应式布局。
- 浏览器控制台：0 个 warning，0 个 error。
- 剩余测试空白：未单独执行屏幕阅读器人工朗读测试，不影响本轮视觉与核心交互验收。

## Implementation Checklist

- [x] 删除冗长宣传文案和装饰性页面元素。
- [x] 使用 Semi Layout、Nav、Table、Select、Input、Button、Tag 等组件。
- [x] 最新榜单设为默认页。
- [x] 桌面、移动端与主要交互通过浏览器验证。
- [x] 生产构建与三容器健康检查通过。

final result: passed
