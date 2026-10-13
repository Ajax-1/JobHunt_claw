"use client";
import {useEffect, useState} from "react";
import {fetchLlmCalls, fetchLlmOverview, fetchLlmCallDetail, LlmCall, LlmCallDetail, LlmOverview} from "@/lib/api";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { RefreshCw } from "lucide-react";

// AI-GENERATED AIDEV-NOTE: 加载失败与无调用记录分别显示，避免将未知指标展示为零。
export default function LlmMonitorPage() {
    const [o, setO] = useState<LlmOverview | null>(null);
    const [calls, setCalls] = useState<LlmCall[]>([]);
    const [page, setPage] = useState<number>(1);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string>("");
    const [total, setTotal] = useState(0);
    const size = 20;
    const totalPages = Math.ceil(total / size);
    const [detail, setDetail] = useState<LlmCallDetail | null>(null);

    const load = async (p: number) => {
        setLoading(true); setError("");
        try {
            const [overview, result] = await Promise.all([fetchLlmOverview(true), fetchLlmCalls(true, p, size)]);
            setO(overview); setCalls(result.list); setTotal(result.total); setPage(result.page);
        } catch { setError("调用记录加载失败，请重试。"); }
        finally { setLoading(false); }
    };
    const openDetail = async (id: string) => {
        try { setDetail(await fetchLlmCallDetail(true, id)); }
        catch { setError("调用详情加载失败，请重试。"); }
    };
    useEffect(() => { void load(1); }, []);
    const metrics = [
        ["累计调用", o?.calls],
        ["成功率", o && o.calls > 0 ? `${o.successRate.toFixed(1)}%` : undefined],
        ["平均耗时", o && o.calls > 0 ? `${o.averageDurationMs.toFixed(0)} ms` : undefined],
        ["预估费用", o?.estimatedCost == null ? undefined : `$${o.estimatedCost}`],
    ];
    return <main className="p-4 sm:p-6 space-y-6">
        <header className="flex items-center justify-between gap-4"><h1 className="text-base font-semibold text-slate-800">模型调用记录</h1><Button variant="outline" disabled={loading} onClick={() => load(page)}><RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`}/>刷新</Button></header>
        {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>}
        <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">{metrics.map(([k, v]) =>
            <div key={String(k)} className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="text-xs text-slate-500">{k}</div>
                <div className="mt-3 text-2xl font-semibold tabular-nums">{loading || error ? "—" : v ?? "—"}</div>
            </div>)}</div>
        <div className="rounded-2xl border border-slate-200 bg-white overflow-x-auto">
            <table className="w-full min-w-[920px] text-sm">
                <thead>
                <tr className="border-b text-left">
                    <th className="p-3">时间</th>
                    <th>用户</th>
                    <th>渠道</th>
                    <th>Agent</th>
                    <th>用途</th>
                    <th>模式</th>
                    <th>状态</th>
                    <th>请求数</th>
                    <th>耗时</th>
                    <th>Token</th>
                    <th>费用</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>{(loading || !calls.length) && <tr><td colSpan={12} className="py-16 text-center text-slate-400">{loading ? "正在加载调用记录…" : error ? "暂时无法显示记录" : "还没有模型调用记录，运行 Agent 任务后可在这里查看。"}</td></tr>}{!loading && calls.map(c => <tr className="border-b cursor-pointer hover:bg-gray-50" key={c.id} onClick={() => openDetail(c.id)}>
                    <td className="p-3">{new Date(c.createTime).toLocaleString()}</td>
                    <td>{c.nickName || c.jobClawUserId || "-"}</td>
                    <td>{c.channel || "-"}</td>
                    <td>{c.agent || "-"}</td>
                    <td>{c.operation}</td>
                    <td>{c.mode}</td>
                    <td>{c.outcome}</td>
                    <td>{c.requestCount}</td>
                    <td>{c.durationMs} ms</td>
                    <td>{c.totalTokens ?? "-"}</td>
                    <td>{c.estimatedCost == null ? "-" : `$${c.estimatedCost}`}</td>
                    <td><Button variant="ghost" size="sm" onClick={e => { e.stopPropagation(); void openDetail(c.id); }}>详情</Button></td>
                </tr>)}</tbody>
            </table>
        </div>

        {totalPages > 1 && <div className="flex justify-center gap-2">
            <button disabled={loading || page <= 1} onClick={() => load(page - 1)} className="rounded border px-3 py-1 disabled:opacity-40">上一页</button>
            <span className="px-3 py-1">{page} / {totalPages}</span>
            <button disabled={loading || page >= totalPages} onClick={() => load(page + 1)} className="rounded border px-3 py-1 disabled:opacity-40">下一页</button>
        </div>}

        {detail && <AdminDetailModal detail={detail} onClose={() => setDetail(null)} />}
    </main>;
}

function AdminDetailModal({detail, onClose}: { detail: LlmCallDetail; onClose: () => void }) {
    return <Dialog open onOpenChange={open => !open && onClose()}><DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto rounded-2xl"><DialogHeader><DialogTitle>调用详情</DialogTitle></DialogHeader>
            <div className="space-y-3 text-sm">
                <Row label="用户" value={detail.invocation.nickName || detail.invocation.jobClawUserId} />
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
    </DialogContent></Dialog>;
}

function Row({label, value}: { label: string; value?: string | null }) {
    if (value == null) return null;
    return <div className="flex"><span className="w-28 shrink-0 text-gray-500">{label}</span><span className="min-w-0 break-words">{value}</span></div>;
}
