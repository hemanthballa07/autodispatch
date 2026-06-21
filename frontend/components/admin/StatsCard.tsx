'use client';

import { Card, CardContent } from '@/components/ui/card';

interface Props {
  label: string;
  value: number;
}

export default function StatsCard({ label, value }: Props) {
  return (
    <Card className="min-w-[160px]">
      <CardContent className="pt-4">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-1 text-3xl font-bold text-foreground">{value}</p>
      </CardContent>
    </Card>
  );
}
