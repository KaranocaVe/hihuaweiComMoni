import dayjs from 'dayjs';

export function formatTime(value?: string, withSeconds = true): string {
  if (!value) return '—';
  return dayjs(value).format(withSeconds ? 'MM-DD HH:mm:ss' : 'MM-DD HH:mm');
}

export function formatDuration(value?: number): string {
  if (value === undefined || value === null || value <= 0) return '—';
  return `${value.toLocaleString('zh-CN', { maximumFractionDigits: 3 })} μs`;
}

export function formatPercent(value?: number): string {
  if (value === undefined || value === null) return '—';
  const prefix = value > 0 ? '+' : '';
  return `${prefix}${value.toFixed(2)}%`;
}

export function severityColor(severity: number): 'red' | 'orange' | 'yellow' {
  if (severity >= 80) return 'red';
  if (severity >= 60) return 'orange';
  return 'yellow';
}

export const anomalyLabels: Record<string, string> = {
  SCORE_REGRESSION: '成绩下降',
  POSSIBLE_HIDING: '疑似藏分',
  SCORE_REBOUND: '成绩回弹',
  SUBMISSION_BURST: '提交突增',
  DROPPED_FROM_BOARD: '掉榜',
  RETURNED_TO_BOARD: '重新上榜',
};
