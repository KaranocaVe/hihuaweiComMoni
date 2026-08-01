import type {
  Anomaly,
  HistoryResponse,
  PagedResponse,
  PollRun,
  RankingRow,
  SimplePage,
  Summary,
} from './types';

async function request<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const url = new URL(path, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      url.searchParams.set(key, String(value));
    }
  });
  const response = await fetch(url);
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message ?? `请求失败 (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  summary: () => request<Summary>('/api/dashboard/summary'),
  rankings: (topic: string, teamName: string, page: number, size: number) =>
    request<PagedResponse<RankingRow>>('/api/rankings', { topic, teamName, page, size }),
  history: (topic: string, teamName: string, hours: number) =>
    request<HistoryResponse>('/api/history', { topic, teamName, hours }),
  anomalies: (params: {
    topic?: string;
    teamName?: string;
    type?: string;
    hours: number;
    page: number;
    size: number;
  }) => request<SimplePage<Anomaly>>('/api/anomalies', params),
  polls: () => request<PollRun[]>('/api/polls'),
};
