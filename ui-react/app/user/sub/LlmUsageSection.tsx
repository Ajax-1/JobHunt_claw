"use client";
import { useEffect, useState } from "react";
import { fetchLlmCalls, fetchLlmOverview, fetchLlmCallDetail, LlmCall, LlmCallDetail, LlmOverview } from "@/lib/api";

export default function LlmUsageSection() {
  const [overview, setOverview] = useState<LlmOverview | null>(null);
  const [calls, setCalls] = useState<LlmCall[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const size = 10;
  const totalPages = Math.ceil(total / size);

  const [detail, setDetail] = useState<LlmCallDetail | null>(null);

  const load = (p: number) => {
    fetchLlmOverview(false).then(setOverview);
    fetchLlmCalls(false, p, size).then(r => { setCalls(r.list); setTotal(r.total); setPage(r.page) });
  };
  useEffect(() => load(1), []);

  return <div className="space-y-6"><h2 className="text-xl font-bold">模型用量</h2>
    <div className="grid grid-cols-4 gap-4">
      <Stat label="调用次数" value={overview?.calls ?? 0} />
      <Stat label="成功率" value={`${(overview?.successRate ?? 0).toFixed(1)}%`} />
      <Stat label="平均耗时" value={`${(overview?.averageDurationMs ?? 0).toFixed(0)} ms`} />
      <Stat label="预估费用" value={`$${overview?.estimatedCost ?? 0}`} />
    </div>

    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left">
          <th className="p-2">时间</th><th>渠道</th><th>Agent</th><th>用途</th><th>状态</th><th>耗时</th><th>Token</th><th>预估费用</th><th></th>
        </tr></thead>
        <tbody>{calls.map(c => <tr key={c.id} className="border-b cursor-pointer hover:bg-gray-50" onClick={() => fetchLlmCallDetail(false, c.id).then(setDetail)}>
          <td className="p-2">{new Date(c.createTime).toLocaleString()}</td>
          <td>{c.channel || "-"}</td>
          <td>{c.agent || "-"}</td>
          <td>{c.operation}</td>
          <td>{c.outcome}</td>
          <td>{c.durationMs} ms</td>
          <td>{c.totalTokens ?? "-"}</td>
          <td>{c.estimatedCost == null ? "-" : `$${c.estimatedCost}`}</td>
          <td className="text-blue-500">详情</td>
        </tr>)}</tbody>
      </table>
    </div>

    {totalPages > 1 && <div className="flex justify-center gap-2">
      <button disabled={page <= 1} onClick={() => load(page - 1)} className="rounded border px-3 py-1 disabled:opacity-40">上一页</button>
      <span className="px-3 py-1">{page} / {totalPages}</span>
      <button disabled={page >= totalPages} onClick={() => load(page + 1)} className="rounded border px-3 py-1 disabled:opacity-40">下一页</button>
    </div>}

    {detail && <DetailModal detail={detail} onClose={() => setDetail(null)} />}
  </div>;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return <div className="rounded border bg-white p-4">
    <div className="text-gray-500">{label}</div>
    <div className="mt-2 text-2xl font-bold">{value}</div>
  </div>;
}

function DetailModal({ detail, onClose }: { detail: LlmCallDetail; onClose: () => void }) {
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
    <div className="max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl" onClick={e => e.stopPropagation()}>
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-bold">调用详情</h3>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">&times;</button>
      </div>
      <div className="space-y-3 text-sm">
        <Row label="渠道" value={detail.invocation.channel} />
        <Row label="Agent" value={detail.invocation.agent} />
        <Row label="用途" value={detail.invocation.operation} />
        <Row label="模式" value={detail.invocation.mode} />
        <Row label="状态" value={detail.invocation.outcome} />
        <Row label="耗时" value={detail.invocation.durationMs != null ? `${detail.invocation.durationMs} ms` : undefined} />
        <Row label="Token(入/出/总)" value={detail.invocation.totalTokens != null ? `${detail.invocation.inputTokens ?? "-"} / ${detail.invocation.outputTokens ?? "-"} / ${detail.invocation.totalTokens}` : undefined} />
        <Row label="预估费用" value={detail.invocation.estimatedCost != null ? `$${detail.invocation.estimatedCost}` : undefined} />
        <Row label="请求数" value={String(detail.invocation.requestCount)} />
      </div>

      {detail.requests.length > 0 && <>
        <h4 className="mt-6 mb-2 font-semibold text-sm">子请求明细</h4>
        {detail.requests.map(r => <div key={r.id} className="mb-4 rounded border p-3 space-y-2">
          <Row label="提供商" value={r.provider} />
          <Row label="模型" value={r.model} />
          <Row label="模型类型" value={r.modelType} />
          <Row label="状态" value={r.outcome} />
          <Row label="耗时" value={r.durationMs != null ? `${r.durationMs} ms` : undefined} />
          <Row label="Token(入/出/总)" value={r.totalTokens != null ? `${r.inputTokens ?? "-"} / ${r.outputTokens ?? "-"} / ${r.totalTokens}` : undefined} />
          <Row label="费用" value={r.estimatedCost != null ? `$${r.estimatedCost}` : undefined} />
          {r.promptSample && <div><div className="text-gray-500 mb-1">提示词</div><pre className="whitespace-pre-wrap rounded bg-gray-50 p-2 text-xs max-h-40 overflow-y-auto">{r.promptSample}</pre></div>}
          {r.responseSample && <div><div className="text-gray-500 mb-1">返回</div><pre className="whitespace-pre-wrap rounded bg-gray-50 p-2 text-xs max-h-40 overflow-y-auto">{r.responseSample}</pre></div>}
        </div>)}
      </>}
    </div>
  </div>;
}

function Row({ label, value }: { label: string; value?: string | null }) {
  if (value == null) return null;
  return <div className="flex"><span className="w-28 shrink-0 text-gray-500">{label}</span><span>{value}</span></div>;
}
