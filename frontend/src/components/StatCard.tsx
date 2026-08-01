import { Card, Typography } from '@douyinfe/semi-ui';

interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
}

export function StatCard({ label, value, hint }: StatCardProps) {
  return (
    <Card className="stat-card" bodyStyle={{ padding: 20 }}>
      <Typography.Text type="tertiary" size="small">
        {label}
      </Typography.Text>
      <div className="stat-value">{value}</div>
      {hint ? (
        <Typography.Text type="tertiary" size="small">
          {hint}
        </Typography.Text>
      ) : null}
    </Card>
  );
}
