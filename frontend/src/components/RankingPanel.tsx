import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Select, Spin, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { api } from '../api';
import { anomalyLabels, formatDuration, formatPercent, formatTime, severityColor } from '../format';
import type { PagedResponse, RankingRow, TopicSummary } from '../types';

interface RankingPanelProps {
  topics: TopicSummary[];
  topic: string;
  onTopicChange: (topic: string) => void;
  onTeamSelect: (teamName: string) => void;
}

export function RankingPanel({ topics, topic, onTopicChange, onTeamSelect }: RankingPanelProps) {
  const [teamName, setTeamName] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<PagedResponse<RankingRow> | null>(null);

  const load = useCallback(async () => {
    if (!topic) return;
    setLoading(true);
    try {
      setData(await api.rankings(topic, teamName, page, pageSize));
    } catch (error) {
      Toast.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, teamName, topic]);

  useEffect(() => {
    void load();
  }, [load]);

  const performanceComparisonOption = useMemo<EChartsOption>(() => {
    const rows = data?.items ?? [];
    const teams = rows.map((row) => row.teamName);
    const hasManyTeams = teams.length > 15;
    return {
      color: ['#325dff', '#12b76a'],
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: unknown) => {
          const values = params as Array<{ marker: string; seriesName: string; value?: number; axisValue: string }>;
          if (!values.length) return '';
          return [values[0].axisValue, ...values.map((item) =>
            `${item.marker}${item.seriesName}: ${formatDuration(item.value)}`)].join('<br/>');
        },
      },
      legend: { top: 0, data: ['当前耗时', '平台观测最佳耗时'], textStyle: { color: '#667085' } },
      grid: { left: 142, right: hasManyTeams ? 46 : 22, top: 48, bottom: 22, containLabel: false },
      xAxis: {
        type: 'value',
        min: 0,
        name: '耗时（μs，越小越好）',
        nameTextStyle: { color: '#667085' },
        axisLabel: { color: '#667085', formatter: (value: number) => value === 0 ? '' : formatDuration(value) },
        splitLine: { lineStyle: { color: '#eaecf0' } },
      },
      yAxis: {
        type: 'category',
        inverse: true,
        data: teams,
        axisLabel: {
          color: '#344054',
          width: 126,
          overflow: 'truncate',
        },
        axisTick: { show: false },
      },
      dataZoom: hasManyTeams ? [
        { type: 'inside', yAxisIndex: 0, startValue: 0, endValue: 14 },
        { type: 'slider', yAxisIndex: 0, right: 5, width: 12, startValue: 0, endValue: 14 },
      ] : [],
      series: [
        {
          name: '当前耗时',
          type: 'bar',
          barMaxWidth: 14,
          data: rows.map((row) => row.takeTime ?? null),
          itemStyle: { borderRadius: [0, 4, 4, 0] },
        },
        {
          name: '平台观测最佳耗时',
          type: 'bar',
          barMaxWidth: 14,
          data: rows.map((row) => row.bestTakeTime ?? null),
          itemStyle: { borderRadius: [0, 4, 4, 0] },
        },
      ],
    };
  }, [data]);

  const columns = [
    {
      title: '排名',
      dataIndex: 'ranking',
      width: 80,
      render: (value: number | undefined) => <span className="rank-number">#{value ?? '—'}</span>,
    },
    {
      title: '队伍',
      dataIndex: 'teamName',
      width: 210,
      render: (value: string, record: RankingRow) => (
        <button className="team-link" onClick={() => onTeamSelect(value)}>
          <span>{value}</span>
          <small>{record.unit || '个人开发者团队'}</small>
        </button>
      ),
    },
    {
      title: '当前耗时',
      dataIndex: 'takeTime',
      width: 150,
      render: (value: number | undefined, record: RankingRow) => (
        <div>
          <strong>{formatDuration(value)}</strong>
          {record.takeTimeChangePct ? (
            <div className={record.takeTimeChangePct > 0 ? 'metric-worse' : 'metric-better'}>
              {formatPercent(record.takeTimeChangePct)}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      title: '历史最佳',
      dataIndex: 'bestTakeTime',
      width: 140,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '提交次数',
      dataIndex: 'commitTimes',
      width: 110,
      render: (value: number | undefined, record: RankingRow) => (
        <span>{value ?? '—'} {record.commitDelta ? <em className="delta-pill">+{record.commitDelta}</em> : null}</span>
      ),
    },
    {
      title: '最后提交',
      dataIndex: 'lastCommitAt',
      width: 150,
      render: (value: string | undefined) => formatTime(value),
    },
    {
      title: '变化信号',
      dataIndex: 'signals',
      render: (_value: unknown, record: RankingRow) => (
        <div className="tag-wrap">
          {record.signals.length === 0 ? <Typography.Text type="tertiary">—</Typography.Text> : record.signals.map((signal) => (
            <Tag key={signal.type} color={severityColor(signal.severity)}>
              {anomalyLabels[signal.type] ?? signal.title}
            </Tag>
          ))}
        </div>
      ),
    },
  ];

  return (
    <Card className="content-card data-card ranking-card">
      <div className="toolbar">
        <Select
          aria-label="选择赛题"
          value={topic}
          style={{ width: 180 }}
          optionList={topics.map((item) => ({ label: item.topic, value: item.topic }))}
          onChange={(value) => {
            setPage(0);
            onTopicChange(String(value));
          }}
        />
        <Input
          aria-label="搜索队伍"
          prefix={<IconSearch />}
          value={teamName}
          showClear
          placeholder="模糊搜索队名"
          onChange={(value) => {
            setPage(0);
            setTeamName(value);
          }}
          style={{ width: 260 }}
        />
        <Button icon={<IconRefresh />} onClick={() => void load()} loading={loading}>
          刷新
        </Button>
        <Typography.Text type="tertiary" size="small" className="toolbar-spacer">
          {data?.totalElements ?? 0} 条 · {formatTime(data?.observedAt)}
        </Typography.Text>
      </div>
      <Spin spinning={loading}>
        {data && data.items.length === 0 ? (
          <Empty title="没有匹配的在榜队伍" />
        ) : (
          <>
            {data && data.items.length > 0 ? (
              <section className="ranking-comparison" aria-label="当前耗时与平台观测最佳耗时对比图">
                <div className="ranking-comparison-heading">
                  <div>
                    <strong>成绩对比</strong>
                    <Typography.Text type="tertiary" size="small">
                      当前页队伍：当前耗时与平台观测到的最佳耗时（数值越小越好）
                    </Typography.Text>
                  </div>
                  {data.items.length > 15 ? <Typography.Text type="tertiary" size="small">可滚动查看全部队伍</Typography.Text> : null}
                </div>
                <ReactECharts option={performanceComparisonOption} style={{ height: 430 }} />
              </section>
            ) : null}
            <Table columns={columns} dataSource={data?.items ?? []} pagination={false} rowKey="snapshotId" />
          </>
        )}
      </Spin>
      {data && data.totalElements > pageSize ? (
        <div className="pagination-row">
          <Pagination
            currentPage={page + 1}
            pageSize={pageSize}
            total={data.totalElements}
            showSizeChanger
            pageSizeOpts={[20, 50, 100]}
            onPageChange={(next) => setPage(next - 1)}
            onPageSizeChange={(next) => {
              setPage(0);
              setPageSize(next);
            }}
          />
        </div>
      ) : null}
    </Card>
  );
}
