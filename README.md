# AscendComMoni

昇腾 AI 创新大赛算子挑战赛公开榜单监测平台。系统定时采集榜单，以无损增量事件保存队伍状态变化，并提供排名、趋势、采集记录和异常信号查询。

> 异常信号仅用于辅助人工复核，不构成违规认定。

## 功能

- 每两分钟采集全部赛题和分页榜单。
- 保存当前排名、完整采集时间轴和状态变化事件。
- 展示排名、队伍历史、异常信号及最近采集状态。
- 检测成绩下降、偏离历史最佳、快速回弹、提交突增、掉榜和重新上榜；对已经处于低谷的队伍，仅在藏分幅度继续扩大或发生实质恶化时重复告警。
- 每轮采集按最近 24 小时的“首次进入明显偏离最佳成绩的区间、随后回到最佳附近、无有效成绩后的恢复”构成队伍级调查优先级；只有历史最佳耗时不超过当前赛题头部约 2 倍的队伍才进入藏分/回弹判定，避免把本身处于后位的弱成绩波动误报为藏分。高频连续提交会被降权，结果写入 `Team hiding-pattern review` 日志。
- 默认保留 90 天数据，采集频率、保留期和检测阈值均可配置。

技术栈：React、TypeScript、Spring Boot、PostgreSQL、Docker Compose。

## 快速启动

```bash
cp .env.example .env
# 修改 .env 中的 POSTGRES_PASSWORD
docker compose up -d --build
```

- 前端：<http://localhost:3000>
- 后端健康检查：<http://localhost:8080/actuator/health>

```bash
docker compose ps
docker compose logs -f backend
docker compose down
```

数据库保存在命名卷 `postgres_data` 中。`docker compose down` 不会删除数据，`docker compose down -v` 会删除数据库卷。

## 本地开发

```bash
# PostgreSQL
docker compose up -d postgres

# 后端
cd backend
SPRING_DATASOURCE_PASSWORD=ascend-change-me mvn spring-boot:run

# 前端（另一个终端）
cd frontend
npm install
npm run dev
```

本地后端默认连接 `localhost:5432/ascend_monitor`。如果修改过数据库密码，请同步调整 `SPRING_DATASOURCE_PASSWORD`；其他连接信息可通过 `SPRING_DATASOURCE_*` 覆盖。Vite 会将 `/api` 和 `/actuator` 代理到 `localhost:8080`。

## 核心接口

- `GET /api/dashboard/summary`：平台概览。
- `GET /api/rankings?topic=Concat&page=0&size=50`：当前排名。
- `GET /api/history?topic=Concat&teamName=example&hours=168`：队伍历史。
- `GET /api/anomalies?topic=Concat&hours=168&page=0&size=50`：异常事件。
- `GET /api/teams/search?q=example`：搜索队伍。
- `GET /api/polls`：最近采集记录。

## 数据存储与升级

- `poll_run`：保留每次成功或失败的采集记录。
- `ranking_current`：保存每支队伍的当前状态。
- `ranking_snapshot`：仅在状态变化时写入事件，并结合采集时间轴重建完整历史。
- 队伍级风险评分是辅助人工复核的聚合指标，不等同于违规结论；日志包含信号数、赛题数及各类信号计数，便于回溯评分来源。

Flyway 会在启动时自动执行数据库迁移。旧版本升级到 V2 后，如需立即回收重复历史行释放的磁盘空间，可在维护窗口执行以下命令；该操作会锁定表：

```bash
docker compose exec -T postgres \
  psql -U ascend -d ascend_monitor -c 'VACUUM (FULL, ANALYZE) ranking_snapshot;'
```

备份数据库：

```bash
docker compose exec -T postgres pg_dump -U ascend ascend_monitor > ascend_monitor.sql
```

## 验证

```bash
make test
docker compose config -q
docker compose build
```
