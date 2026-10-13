"use client"
import { useEffect, useState } from "react"
import { ExternalLink, FileText, Loader2 } from "lucide-react"
import { fetchGatherSourceDetail, type DraftItem } from "@/lib/api"
import { externalUrl } from "@/lib/links"

interface DraftEvidenceProps { draft: DraftItem }
/** AI-GENERATED AIDEV-NOTE: 来源内容按纯文本渲染；数据库快照与来源原文明确区分。 */
export function DraftEvidence({ draft }: DraftEvidenceProps) {
  const [content, setContent] = useState<string>("")
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<string>("")
  useEffect(() => {
    let active = true
    setContent(""); setError(""); setLoading(false)
    if (!draft.sourceId) return
    setLoading(true)
    fetchGatherSourceDetail(draft.sourceId).then(source => {
      if (active) setContent(source.content || "")
    }).catch(() => { if (active) setError("暂时无法加载来源内容，请打开原始公告核对。") }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [draft.id, draft.sourceId])
  const url = externalUrl(draft.jobAnnouncement) || externalUrl(draft.relatedLink)
  return <aside className="rounded-xl border border-slate-200 bg-slate-50 p-5">
    <h3 className="flex items-center gap-2 text-sm font-semibold"><FileText className="h-4 w-4 text-blue-600" />核对依据</h3>
    <p className="mt-2 text-xs leading-5 text-slate-500">对照来源检查字段，确认后保存或发布。</p>
    {url && <a href={url} target="_blank" rel="noopener noreferrer" className="my-4 flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-3 text-sm text-blue-600">打开原始招聘公告<ExternalLink className="h-3.5 w-3.5" /></a>}
    <div className="mt-5 space-y-3 text-xs"><div className="font-medium text-slate-700">当前保存的信息 · #{draft.id}</div>{[["公司",draft.companyName],["招聘对象",draft.recruitmentTarget],["招聘时间",draft.lastUpdatedTime],["岗位",draft.position]].map(([label,value]) => <div key={label}><p className="mb-1 text-slate-400">{label}</p><p className="whitespace-pre-wrap break-words leading-6 text-slate-600">{value || "未填写"}</p></div>)}</div>
    <details className="mt-5 border-t border-slate-200 pt-4"><summary className="cursor-pointer text-xs font-medium text-slate-600">查看采集来源内容</summary>{loading ? <Loader2 className="my-4 h-4 w-4 animate-spin" /> : <pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-all text-xs leading-6 text-slate-500">{error || content || "这条草稿未关联可读取的来源内容，请通过招聘公告核对。"}</pre>}</details>
  </aside>
}
