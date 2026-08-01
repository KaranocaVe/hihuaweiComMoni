export type PollStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type ChangeState = 'NEW' | 'UNCHANGED' | 'CHANGED' | 'DROPPED' | 'ABSENT' | 'RETURNED';
export type AnomalyType =
  | 'SCORE_REGRESSION'
  | 'POSSIBLE_HIDING'
  | 'SCORE_REBOUND'
  | 'SUBMISSION_BURST'
  | 'DROPPED_FROM_BOARD'
  | 'RETURNED_TO_BOARD';

export interface PollRun {
  id: string;
  status: PollStatus;
  startedAt: string;
  completedAt?: string;
  topicCount: number;
  snapshotCount: number;
  changedCount: number;
  anomalyCount: number;
  errorMessage?: string;
}

export interface TopicSummary {
  topic: string;
  presentTeams: number;
  droppedTeams: number;
  changedTeams: number;
  anomalyCount: number;
}

export interface Summary {
  serverTime: string;
  contestId: string;
  contestName?: string;
  pollerRunning: boolean;
  pollIntervalSeconds: number;
  currentRows: number;
  currentTeams: number;
  anomaliesLast24Hours: number;
  latestSuccessfulRun?: PollRun;
  latestAttempt?: PollRun;
  topics: TopicSummary[];
  disclaimer: string;
}

export interface Signal {
  type: AnomalyType;
  severity: number;
  title: string;
}

export interface RankingRow {
  snapshotId: number;
  topic: string;
  teamName: string;
  unit?: string;
  ranking?: number;
  takeTime?: number;
  commitTimes?: number;
  lastCommitAt?: string;
  fastest: boolean;
  bestTakeTime?: number;
  rankChange?: number;
  takeTimeChangePct?: number;
  commitDelta?: number;
  changeState: ChangeState;
  observedAt: string;
  signals: Signal[];
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  pollRunId: string;
  observedAt: string;
}

export interface HistoryPoint {
  snapshotId: number;
  observedAt: string;
  present: boolean;
  ranking?: number;
  takeTime?: number;
  commitTimes?: number;
  bestTakeTime?: number;
  rankChange?: number;
  takeTimeChangePct?: number;
  commitDelta?: number;
  changeState: ChangeState;
  signals: Signal[];
}

export interface HistoryResponse {
  teamName: string;
  topic: string;
  from: string;
  to: string;
  originalPointCount: number;
  points: HistoryPoint[];
}

export interface Anomaly {
  id: number;
  snapshotId: number;
  pollRunId: string;
  topic: string;
  teamName: string;
  type: AnomalyType;
  severity: number;
  title: string;
  description: string;
  previousTakeTime?: number;
  currentTakeTime?: number;
  baselineTakeTime?: number;
  detectedAt: string;
}

export interface SimplePage<T> {
  items: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
