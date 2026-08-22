import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Input, Pagination, Select, Spin, Table, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { api } from '../api';
import { anomalyLabels, formatDuration, formatTime, severityColor } from '../format';
import type { Anomaly, AnomalyType, HidingReview, SimplePage, TopicSummary } from '../types';

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

const reviewLevelLabel = {
  HIGH: '优先复核',
  WATCH: '建议关注',
  LOW: '证据较弱',
} as const;

function reviewLevelColor(level: HidingReview['level']): 'red' | 'orange' | 'blue' {
  if (level === 'HIGH') return 'red';
  if (level === 'WATCH') return 'orange';
  return 'blue';
}

function formatReviewHours(hours?: number): string {
  if (hours === undefined || hours === null || !Number.isFinite(hours)) return '—';
  return `${hours < 1 ? hours.toFixed(2) : hours.toFixed(1)} 小时`;
}

export function AnomalyPanel({ topics, topic, onTopicChange, onTeamSelect }: AnomalyPanelProps) {
  const [teamName, setTeamName] = useState('');
  const [type, setType] = useState<AnomalyType | ''>('');
  const [hours, setHours] = useState(168);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<SimplePage<Anomaly> | null>(null);
  const [reviews, setReviews] = useState<HidingReview[] | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [anomalies, hidingReviews] = await Promise.all([
        api.anomalies({ topic, teamName, type, hours, page, size: 50 }),
        api.hidingReviews(24),
      ]);
      setData(anomalies);
      setReviews(hidingReviews);
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
    <div className="panel-stack">
      <Card className="content-card hiding-review-card" title="藏分复核优先级">
        <div className="hiding-review-intro">
          <Typography.Text type="secondary">
            仅将“明显变差的可见成绩维持一段时间，随后恢复接近最佳成绩”的完整轨迹列入复核，且队伍历史最佳成绩须接近赛题头部；后位弱成绩的来回波动、单次下降或高频调试不构成藏分结论。
          </Typography.Text>
          <Tag color="blue">最近 24 小时</Tag>
        </div>
        <Spin spinning={loading && reviews === null}>
          {reviews?.length ? (
            <div className="hiding-review-grid">
              {reviews.map((review) => (
                <button
                  className="hiding-review-item"
                  key={review.teamName}
                  type="button"
                  onClick={() => onTeamSelect(review.teamName)}
                >
                  <div className="hiding-review-heading">
                    <strong>{review.teamName}</strong>
                    <Tag color={reviewLevelColor(review.level)}>{reviewLevelLabel[review.level]}</Tag>
                    <span>复核分 {review.score}</span>
                  </div>
                  <div className="hiding-review-metrics">
                    <span>进入差成绩 {review.hidingEntries} 次</span>
                    <span>后续恢复 {review.recoveries} 次</span>
                    <span>维持 ≥2 小时 {review.longHoldRecoveries} 次</span>
                  </div>
                  <small>
                    中位恢复 {formatReviewHours(review.medianRecoveryHours)} · 提交节奏 {review.submissionsPerHour.toFixed(2)} 轮/小时 · 活跃赛题 {review.activeTopicCount}
                  </small>
                </button>
              ))}
            </div>
          ) : reviews ? (
            <Empty description="最近 24 小时没有形成可复核的完整成绩轨迹" />
          ) : null}
        </Spin>
      </Card>

      <Card className="content-card data-card" title="原始变化信号 / 证据明细">
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
            aria-label="原始信号类型"
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
            原始信号用于追溯证据，不单独代表藏分判断
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
    </div>
  );
}
