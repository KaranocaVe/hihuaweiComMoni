import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Input, Pagination, Select, Spin, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { api } from '../api';
import { anomalyLabels, formatDuration, formatTime, severityColor } from '../format';
import type { Anomaly, AnomalyType, SimplePage, TopicSummary } from '../types';

interface AnomalyPanelProps {
  topics: TopicSummary[];
  topic: string;
  onTopicChange: (topic: string) => void;
  onTeamSelect: (teamName: string) => void;
}

const anomalyOptions: Array<{ label: string; value: AnomalyType | '' }> = [
  { label: '全部信号', value: '' },
  { label: '疑似藏分', value: 'POSSIBLE_HIDING' },
  { label: '成绩下降', value: 'SCORE_REGRESSION' },
  { label: '成绩回弹', value: 'SCORE_REBOUND' },
  { label: '掉榜', value: 'DROPPED_FROM_BOARD' },
  { label: '重新上榜', value: 'RETURNED_TO_BOARD' },
  { label: '提交突增', value: 'SUBMISSION_BURST' },
];

export function AnomalyPanel({ topics, topic, onTopicChange, onTeamSelect }: AnomalyPanelProps) {
  const [teamName, setTeamName] = useState('');
  const [type, setType] = useState<AnomalyType | ''>('');
  const [hours, setHours] = useState(168);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<SimplePage<Anomaly> | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.anomalies({ topic, teamName, type, hours, page, size: 50 }));
    } catch (error) {
      Toast.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [hours, page, teamName, topic, type]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns = [
    {
      title: '时间',
      dataIndex: 'detectedAt',
      width: 150,
      render: (value: string) => formatTime(value),
    },
    {
      title: '队伍 / 赛题',
      dataIndex: 'teamName',
      width: 210,
      render: (value: string, record: Anomaly) => (
        <button className="team-link" onClick={() => onTeamSelect(value)}>
          <span>{value}</span>
          <small>{record.topic}</small>
        </button>
      ),
    },
    {
      title: '信号',
      dataIndex: 'type',
      width: 120,
      render: (value: AnomalyType, record: Anomaly) => (
        <Tag color={severityColor(record.severity)}>{anomalyLabels[value] ?? value}</Tag>
      ),
    },
    {
      title: '严重度',
      dataIndex: 'severity',
      width: 90,
      render: (value: number) => <strong className={value >= 80 ? 'severity-high' : 'severity-medium'}>{value}</strong>,
    },
    {
      title: '证据说明',
      dataIndex: 'description',
      render: (value: string, record: Anomaly) => (
        <div className="anomaly-description">
          <strong>{record.title}</strong>
          <span>{value}</span>
          <small>
            上轮 {formatDuration(record.previousTakeTime)} · 当前 {formatDuration(record.currentTakeTime)} · 基线 {formatDuration(record.baselineTakeTime)}
          </small>
        </div>
      ),
    },
  ];

  return (
    <Card className="content-card data-card">
      <div className="toolbar">
        <Select
          aria-label="异常赛题"
          value={topic}
          style={{ width: 170 }}
          optionList={topics.map((item) => ({ label: item.topic, value: item.topic }))}
          onChange={(value) => {
            setPage(0);
            onTopicChange(String(value));
          }}
        />
        <Select
          aria-label="异常类型"
          value={type}
          style={{ width: 150 }}
          optionList={anomalyOptions}
          onChange={(value) => {
            setPage(0);
            setType(String(value) as AnomalyType | '');
          }}
        />
        <Select
          aria-label="异常时间范围"
          value={hours}
          style={{ width: 150 }}
          optionList={[
            { label: '最近 24 小时', value: 24 },
            { label: '最近 7 天', value: 168 },
            { label: '最近 30 天', value: 720 },
          ]}
          onChange={(value) => {
            setPage(0);
            setHours(Number(value));
          }}
        />
        <Input
          aria-label="异常队伍搜索"
          prefix={<IconSearch />}
          value={teamName}
          showClear
          placeholder="筛选队伍"
          onChange={(value) => {
            setPage(0);
            setTeamName(value);
          }}
          style={{ width: 220 }}
        />
        <Button icon={<IconRefresh />} loading={loading} onClick={() => void load()}>
          刷新
        </Button>
        <Typography.Text type="tertiary" size="small" className="toolbar-spacer">
          仅供人工复核
        </Typography.Text>
      </div>
      <Spin spinning={loading}>
        <Table columns={columns} dataSource={data?.items ?? []} pagination={false} rowKey="id" />
      </Spin>
      {data && data.totalElements > 50 ? (
        <div className="pagination-row">
          <Pagination
            currentPage={page + 1}
            pageSize={50}
            total={data.totalElements}
            onPageChange={(next) => setPage(next - 1)}
          />
        </div>
      ) : null}
    </Card>
  );
}
