'use client';

interface Props {
  label: string;
  value: number;
}

export default function StatsCard({ label, value }: Props) {
  return (
    <div style={{ border: '1px solid #ccc', borderRadius: 8, padding: '1rem', minWidth: 160 }}>
      <div style={{ fontSize: '0.85rem', color: '#666' }}>{label}</div>
      <div style={{ fontSize: '2rem', fontWeight: 700 }}>{value}</div>
    </div>
  );
}
