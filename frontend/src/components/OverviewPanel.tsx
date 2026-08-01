import { Card, Table, Tag } from '@douyinfe/semi-ui';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import type { Summary, TopicSummary } from '../types';
import { StatCard } from './StatCard';

interface OverviewPanelProps {
  summary: Summary;
  onTopicSelect: (topic: string) => void;
}

export function OverviewPanel({ summary, onTopicSelect }: OverviewPanelProps) {
  const option: EChartsOption = {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: '#5f6673' } },
    grid: { left: 44, right: 20, top: 42, bottom: 30 },
    xAxis: {
      type: 'category',
      data: summary.topics.map((item) => item.topic),
      axisTick: { show: false },
      axisLabel: { color: '#5f6673', interval: 0 },
      axisLine: { lineStyle: { color: '#e5e6eb' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#86909c' },
      splitLine: { lineStyle: { color: '#f0f1f2' } },
    },
    series: [
      {
        name: '在榜队伍',
        type: 'bar',
        data: summary.topics.map((item) => item.presentTeams),
        itemStyle: { color: '#3370ff', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 32,
      },
      {
        name: '本轮变化',
        type: 'bar',
        data: summary.topics.map((item) => item.changedTeams),
        itemStyle: { color: '#ff8800', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 32,
      },
    ],
  };

  const columns = [
    {
      title: '赛题',
      dataIndex: 'topic',
      render: (value: string) => (
        <button className="topic-link" onClick={() => onTopicSelect(value)}>{value}</button>
      ),
    },
    { title: '在榜', dataIndex: 'presentTeams', width: 80 },
    { title: '变化', dataIndex: 'changedTeams', width: 80 },
    { title: '掉榜', dataIndex: 'droppedTeams', width: 80 },
    {
      title: '异常',
      dataIndex: 'anomalyCount',
      width: 90,
      render: (value: number) => value > 0 ? <Tag color="orange">{value}</Tag> : '—',
    },
  ];

  return (
    <div className="panel-stack">
      <div className="stat-grid">
        <StatCard label="在榜记录" value={summary.currentRows} />
        <StatCard label="独立队伍" value={summary.currentTeams} />
        <StatCard label="24 小时异常" value={summary.anomaliesLast24Hours} />
        <StatCard label="采集间隔" value={`${summary.pollIntervalSeconds / 60} 分钟`} />
      </div>

      <div className="overview-grid">
        <Card className="content-card" title="赛题分布">
          {summary.topics.length > 0 ? <ReactECharts option={option} style={{ height: 320 }} /> : null}
        </Card>
        <Card className="content-card" title="赛题状态">
          <Table<TopicSummary>
            columns={columns}
            dataSource={summary.topics}
            pagination={false}
            rowKey="topic"
          />
        </Card>
      </div>
    </div>
  );
}
