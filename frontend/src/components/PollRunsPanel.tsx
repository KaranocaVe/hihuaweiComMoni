import { useEffect, useState } from 'react';
import { Card, Spin, Table, Tag, Toast } from '@douyinfe/semi-ui';
import { api } from '../api';
import { formatTime } from '../format';
import type { PollRun, PollStatus } from '../types';

const statusLabels: Record<PollStatus, string> = {
  RUNNING: '采集中',
  SUCCESS: '成功',
  FAILED: '失败',
};

const statusColors: Record<PollStatus, 'blue' | 'green' | 'red'> = {
  RUNNING: 'blue',
  SUCCESS: 'green',
  FAILED: 'red',
};

export function PollRunsPanel() {
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<PollRun[]>([]);

  useEffect(() => {
    setLoading(true);
    api.polls()
      .then(setRuns)
      .catch((error: Error) => Toast.error(error.message))
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: PollStatus) => <Tag color={statusColors[value]}>{statusLabels[value]}</Tag>,
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 160,
      render: (value: string) => formatTime(value),
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      width: 160,
      render: (value: string | undefined) => formatTime(value),
    },
    { title: '赛题', dataIndex: 'topicCount', width: 80 },
    { title: '快照', dataIndex: 'snapshotCount', width: 100 },
    { title: '变化', dataIndex: 'changedCount', width: 100 },
    { title: '异常', dataIndex: 'anomalyCount', width: 100 },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      render: (value: string | undefined) => value || '—',
    },
  ];

  return (
    <Card className="content-card data-card">
      <Spin spinning={loading}>
        <Table columns={columns} dataSource={runs} pagination={false} rowKey="id" />
      </Spin>
    </Card>
  );
}
