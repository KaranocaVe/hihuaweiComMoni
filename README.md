# AscendComMoni

昇腾 AI 创新大赛算子挑战赛公开榜单变化监测平台。系统每两分钟读取一次公开榜单，把每个赛题、每支队伍的最后提交成绩保存到 PostgreSQL，并提供查询、趋势图、采集审计和异常信号面板。

> 平台记录的是公开可见行为。异常信号用于辅助人工复核，不构成对参赛者不正当竞争或违规行为的认定。

## 功能

- 每两分钟自动采集全部赛题、全部分页榜单。
- 保存采集批次、队伍快照、掉榜状态、变化量和异常事件。
- 查询最新排名、队名模糊搜索、分页展示。
- 展示队伍耗时、历史最佳与排名时间序列。
- 检测提交后显著降分、偏离历史最佳、快速回弹、提交突增、掉榜和重新上榜。
- 采集失败不会覆盖上一份成功快照，可查看最近 20 次运行状态。
- 默认保留 90 天数据，阈值和保留期均可配置。

## 技术栈

- 前端：React、TypeScript、Semi Design、ECharts、Vite。
- 后端：Java 21、Spring Boot、Spring Data JPA、Flyway。
- 数据库：PostgreSQL 16。
- 部署：Docker Compose、Nginx。

## 快速启动

```bash
cp .env.example .env
# 修改 .env 中的 POSTGRES_PASSWORD
docker compose up -d --build
```

打开 [http://localhost:3000](http://localhost:3000)。后端健康检查位于 [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)。容器启动约 5 秒后执行第一轮采集。

```bash
docker compose ps
docker compose logs -f backend
docker compose down
```

数据库数据保存在命名卷 `postgres_data` 中，普通 `docker compose down` 不会删除数据。只有明确执行 `docker compose down -v` 才会删除数据库卷。

## 本地开发

先启动 PostgreSQL：

```bash
docker compose up -d postgres
```

启动后端：

```bash
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ascend_monitor \
SPRING_DATASOURCE_USERNAME=ascend \
SPRING_DATASOURCE_PASSWORD=ascend-change-me \
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

Vite 会把 `/api` 和 `/actuator` 代理到 `localhost:8080`。

## 检测规则

性能榜耗时越低越好。默认规则如下：

| 信号 | 默认条件 |
| --- | --- |
| 成绩下降 | 新提交后，耗时较上一轮恶化至少 15% |
| 疑似藏分 | 新提交后，当前耗时比平台已观测历史最佳慢至少 25% |
| 成绩回弹 | 上一轮偏离最佳至少 25%，本轮改善至少 15% 且回到最佳 5% 范围内 |
| 提交突增 | 两次采集之间累计提交次数增加至少 8 次 |
| 掉榜 | 上一轮存在，本轮完整分页结果中不存在 |
| 重新上榜 | 上一轮不在榜，本轮重新出现 |

阈值通过 `.env` 中的 `DETECTION_*` 变量调整。一个快照可以同时产生多个信号，便于保留完整证据，而不是强行压成单一结论。

## 后端接口

- `GET /api/dashboard/summary`：概览和各赛题统计。
- `GET /api/rankings?topic=Concat&teamName=&page=0&size=50`：最新成功快照。
- `GET /api/history?topic=Concat&teamName=cgmxl&hours=168`：队伍趋势。
- `GET /api/anomalies?topic=Concat&hours=168&page=0&size=50`：异常事件。
- `GET /api/teams/search?q=cg`：队名搜索。
- `GET /api/polls`：最近采集运行。

## 已确认的源站 API

公共 API 基址为 `https://www.hiascend.com/ascendgateway/ascendservice`，本赛事的 `gameId` 为 `41ffbad2024e4ccfa43520c57ffa7b9e`：

- `GET /devCenter/contested/enrollment/details?gameId=...`：赛事名称和榜单类型。
- `GET /devCenter/contested/enrollment/getTopicHeader?gameId=...`：赛题列表。
- `GET /devCenter/contested/enrollment/getWorkPerformancesRankList?gameId=...&teamName=&pageNo=1&pageSize=50&topic=Concat`：性能榜分页数据。

榜单接口返回排名、队名、组织、当前耗时、提交次数和最后提交时间等字段。采集器先读取赛题列表，再逐赛题遍历全部分页；请求带赛事详情页 `Referer`，不依赖登录 Cookie。

源站接口即使发生业务错误也可能返回 HTTP 200，因此采集器会同时校验 JSON `code=200`、`success=true` 和非空 `data`。

源榜单会用 `takeTime=-1` 表示无有效性能结果；采集器会将所有非正耗时归一化为空值，避免污染历史最佳和异常检测。

## 数据规模与备份

当前五个赛题约三百余条在榜记录。两分钟一次意味着每天约写入二十多万条快照；默认 90 天保留期适合单机 PostgreSQL。长期运行可缩短保留期或将历史表改为分区表。

备份示例：

```bash
docker compose exec -T postgres pg_dump -U ascend ascend_monitor > ascend_monitor.sql
```

恢复前请先确认目标数据库和备份文件，避免覆盖错误环境。

## 验证

```bash
make test
docker compose build
docker compose up -d
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:3000/healthz
```
