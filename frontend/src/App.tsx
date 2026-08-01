import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Layout, Nav, Select, Spin, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import {
  IconAlertTriangle,
  IconGridView,
  IconHistory,
  IconLineChartStroked,
  IconListView,
  IconPulse,
  IconRefresh,
} from '@douyinfe/semi-icons';
import { api } from './api';
import { OverviewPanel } from './components/OverviewPanel';
import { RankingPanel } from './components/RankingPanel';
import { HistoryPanel } from './components/HistoryPanel';
import { AnomalyPanel } from './components/AnomalyPanel';
import { PollRunsPanel } from './components/PollRunsPanel';
import { formatTime } from './format';
import type { Summary } from './types';

const { Header, Sider, Content } = Layout;

const navigation = [
  { itemKey: 'ranking', text: '最新榜单', icon: <IconListView /> },
  { itemKey: 'overview', text: '概览', icon: <IconGridView /> },
  { itemKey: 'history', text: '队伍趋势', icon: <IconLineChartStroked /> },
  { itemKey: 'anomalies', text: '异常信号', icon: <IconAlertTriangle /> },
  { itemKey: 'polls', text: '采集运行', icon: <IconHistory /> },
];

const pageTitles: Record<string, string> = {
  ranking: '最新榜单',
  overview: '概览',
  history: '队伍趋势',
  anomalies: '异常信号',
  polls: '采集运行',
};

export default function App() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('ranking');
  const [topic, setTopic] = useState('');
  const [teamName, setTeamName] = useState('');

  const loadSummary = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const next = await api.summary();
      setSummary(next);
      setTopic((current) => current || next.topics[0]?.topic || '');
    } catch (error) {
      Toast.error((error as Error).message);
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSummary();
    const timer = window.setInterval(() => void loadSummary(true), 30_000);
    return () => window.clearInterval(timer);
  }, [loadSummary]);

  const selectTeam = (nextTeam: string) => {
    setTeamName(nextTeam);
    setActiveTab('history');
  };

  const selectTopic = (nextTopic: string) => {
    setTopic(nextTopic);
    setActiveTab('ranking');
  };

  const pageTitle = activeTab === 'history' && teamName
    ? `${teamName} · 趋势`
    : pageTitles[activeTab];

  const pageContent = useMemo(() => {
    if (!summary) return null;
    switch (activeTab) {
      case 'overview':
        return <OverviewPanel summary={summary} onTopicSelect={selectTopic} />;
      case 'history':
        return <HistoryPanel topic={topic} teamName={teamName} />;
      case 'anomalies':
        return (
          <AnomalyPanel
            topics={summary.topics}
            topic={topic}
            onTopicChange={setTopic}
            onTeamSelect={selectTeam}
          />
        );
      case 'polls':
        return <PollRunsPanel />;
      case 'ranking':
      default:
        return (
          <RankingPanel
            topics={summary.topics}
            topic={topic}
            onTopicChange={setTopic}
            onTeamSelect={selectTeam}
          />
        );
    }
  }, [activeTab, summary, teamName, topic]);

  const failed = summary?.latestAttempt?.status === 'FAILED';
  const statusText = summary?.pollerRunning ? '采集中' : failed ? '采集失败' : '正常';
  const statusColor = failed ? 'red' : summary?.pollerRunning ? 'blue' : 'green';

  return (
    <Layout className="app-shell">
      <Sider className="app-sider">
        <div className="side-brand">
          <IconPulse size="large" />
          <div>
            <strong>AscendComMoni</strong>
          </div>
        </div>
        <Nav
          className="side-nav"
          selectedKeys={[activeTab]}
          items={navigation}
          onSelect={({ itemKey }) => setActiveTab(String(itemKey))}
        />
      </Sider>

      <Layout className="main-layout">
        <Header className="app-header">
          <div className="page-heading">
            <Typography.Title heading={5}>{pageTitle}</Typography.Title>
            <Typography.Text type="tertiary" size="small">
              {summary?.contestName || '昇腾算子挑战赛'}
            </Typography.Text>
          </div>
          <div className="header-status">
            <Tag color={statusColor}>{statusText}</Tag>
            <Typography.Text type="tertiary" size="small">
              {formatTime(summary?.latestSuccessfulRun?.completedAt)}
            </Typography.Text>
            <Button
              theme="borderless"
              icon={<IconRefresh />}
              onClick={() => void loadSummary()}
              loading={loading}
              aria-label="刷新数据"
            />
          </div>
        </Header>

        <Content className="app-content">
          <Select
            className="mobile-nav"
            value={activeTab}
            optionList={navigation.map((item) => ({ label: item.text, value: item.itemKey }))}
            onChange={(value) => setActiveTab(String(value))}
            aria-label="页面导航"
          />
          <Spin spinning={loading && !summary} size="large">
            {pageContent}
          </Spin>
        </Content>
      </Layout>
    </Layout>
  );
}
