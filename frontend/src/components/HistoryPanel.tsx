import { useEffect, useMemo, useState } from 'react';
import { Card, Empty, Select, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { api } from '../api';
import { anomalyLabels, formatDuration, formatTime, severityColor } from '../format';
import type { HistoryResponse } from '../types';

interface HistoryPanelProps {
  topic: string;
  teamName: string;
}

export function HistoryPanel({ topic, teamName }: HistoryPanelProps) {
  const [hours, setHours] = useState(168);
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<HistoryResponse | null>(null);

  useEffect(() => {
    if (!topic || !teamName) {
      setHistory(null);
      return;
    }
    setLoading(true);
    api.history(topic, teamName, hours)
      .then(setHistory)
      .catch((error: Error) => Toast.error(error.message))
      .finally(() => setLoading(false));
  }, [hours, teamName, topic]);

  const option = useMemo<EChartsOption>(() => {
    const points = history?.points ?? [];
    return {
      tooltip: {
        trigger: 'axis',
        formatter: (params: unknown) => {
          const items = params as Array<{ axisValue: string; marker: string; seriesName: string; value: number | null }>;
          if (!items.length) return '';
          return [items[0].axisValue, ...items.map((item) => `${item.marker}${item.seriesName}: ${item.value ?? '掉榜'}`)].join('<br/>');
        },
      },
      legend: { top: 0, textStyle: { color: '#667085' } },
      grid: { left: 68, right: 68, top: 45, bottom: 70 },
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 15 }],
      xAxis: {
        type: 'category',
        data: points.map((point) => formatTime(point.observedAt, false)),
        axisLabel: { color: '#667085', hideOverlap: true },
      },
      yAxis: [
        {
          type: 'value',
          name: '耗时 (μs)',
          axisLabel: { color: '#667085' },
          splitLine: { lineStyle: { color: '#eaecf0' } },
          scale: true,
        },
        {
          type: 'value',
          name: '排名',
          inverse: true,
          min: 1,
          axisLabel: { color: '#667085' },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: '当前耗时',
          type: 'line',
          smooth: false,
          connectNulls: false,
          showSymbol: false,
          data: points.map((point) => (point.present ? point.takeTime ?? null : null)),
          lineStyle: { width: 2.5, color: '#325dff' },
          itemStyle: { color: '#325dff' },
          areaStyle: { color: 'rgba(50,93,255,.08)' },
        },
        {
          name: '历史最佳',
          type: 'line',
          showSymbol: false,
          data: points.map((point) => point.bestTakeTime ?? null),
          lineStyle: { width: 1.5, type: 'dashed', color: '#12b76a' },
          itemStyle: { color: '#12b76a' },
        },
        {
          name: '排名',
          type: 'line',
          yAxisIndex: 1,
          showSymbol: false,
          data: points.map((point) => (point.present ? point.ranking ?? null : null)),
          lineStyle: { width: 1.5, color: '#7a5af8' },
          itemStyle: { color: '#7a5af8' },
        },
        {
          name: '异常点',
          type: 'scatter',
          symbolSize: 11,
          data: points.map((point, index) => point.signals.length > 0 && point.takeTime !== undefined
            ? [index, point.takeTime]
            : null).filter(Boolean),
          itemStyle: { color: '#f04438' },
        },
      ],
    };
  }, [history]);

  if (!teamName) {
    return (
      <Card className="content-card empty-card">
        <Empty title="请先从榜单选择队伍" />
      </Card>
    );
  }

  const recentSignals = history?.points.flatMap((point) => point.signals.map((signal) => ({ ...signal, at: point.observedAt }))).slice(-8).reverse() ?? [];

  return (
    <Card className="content-card history-card">
      <div className="toolbar">
        <Select
          aria-label="趋势时间范围"
          value={hours}
          style={{ width: 150 }}
          optionList={[
            { label: '最近 24 小时', value: 24 },
            { label: '最近 7 天', value: 168 },
            { label: '最近 30 天', value: 720 },
          ]}
          onChange={(value) => setHours(Number(value))}
        />
        <Typography.Text type="tertiary" size="small">
          {topic} · {history?.originalPointCount ?? 0} 个观测点
        </Typography.Text>
      </div>
      <Spin spinning={loading}>
        {history && history.points.length > 0 ? (
          <>
            <ReactECharts option={option} style={{ height: 460 }} />
            <div className="history-summary">
              <div>
                <span>最新耗时</span>
                <strong>{formatDuration(history.points.at(-1)?.takeTime)}</strong>
              </div>
              <div>
                <span>平台观测最佳</span>
                <strong>{formatDuration(history.points.at(-1)?.bestTakeTime)}</strong>
              </div>
              <div>
                <span>异常事件</span>
                <strong>{recentSignals.length}</strong>
              </div>
            </div>
            {recentSignals.length > 0 ? (
              <div className="signal-strip">
                {recentSignals.map((signal, index) => (
                  <Tag key={`${signal.type}-${signal.at}-${index}`} color={severityColor(signal.severity)}>
                    {formatTime(signal.at)} · {anomalyLabels[signal.type] ?? signal.title}
                  </Tag>
                ))}
              </div>
            ) : null}
          </>
        ) : (
          <Empty title="该时间范围内暂无历史观测" />
        )}
      </Spin>
    </Card>
  );
}
