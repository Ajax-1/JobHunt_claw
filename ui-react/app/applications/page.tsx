"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { Bookmark, CalendarDays, ExternalLink, Loader2, MapPin, Pencil, RefreshCw, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { TablePagination } from "@/components/ui/table-pagination"
import { useLoginModal } from "@/hooks/useLoginModal"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"
import { externalUrl } from "@/lib/links"
import { deleteApplication, fetchApplications, type ApplicationStatus, type JobApplication, updateApplication } from "@/lib/api"

const STATUS_OPTIONS: Array<{ value: ApplicationStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "全部进度" },
  { value: "SAVED", label: "待投递" },
  { value: "APPLIED", label: "已投递" },
  { value: "INTERVIEW", label: "面试中" },
  { value: "OFFER", label: "已获 Offer" },
  { value: "REJECTED", label: "未通过" },
  { value: "WITHDRAWN", label: "已撤回" },
]
const labelForStatus = (status: ApplicationStatus) => STATUS_OPTIONS.find(option => option.value === status)?.label ?? status
const toneForStatus = (status: ApplicationStatus) => ({ SAVED: "bg-sky-50 text-sky-700", APPLIED: "bg-blue-50 text-blue-700", INTERVIEW: "bg-violet-50 text-violet-700", OFFER: "bg-emerald-50 text-emerald-700", REJECTED: "bg-rose-50 text-rose-700", WITHDRAWN: "bg-amber-50 text-amber-700" })[status]
const formatTime = (value?: string | number) => {
  if (!value) return ""
  const date = new Date(typeof value === "number" ? value : value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString("zh-CN")
}

interface EditState {
  status: ApplicationStatus
  notes: string
  nextFollowUpDate: string
}

/** AI-GENERATED AIDEV-NOTE: 求职记录页面集中处理状态、备注和跟进日期，保持服务端记录为单一事实来源。 */
export default function ApplicationsPage() {
  const { userInfo } = useLoginUser()
  const { setLoginOpen } = useLoginModal()
  const { toast } = useToast()
  const [status, setStatus] = useState<ApplicationStatus | "ALL">("ALL")
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [rows, setRows] = useState<JobApplication[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [editing, setEditing] = useState<JobApplication | null>(null)
  const [editState, setEditState] = useState<EditState>({ status: "SAVED", notes: "", nextFollowUpDate: "" })
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    if (!userInfo) return
    setLoading(true)
    setError("")
    try {
      const data = await fetchApplications({ ...(status === "ALL" ? {} : { status }), page, size: pageSize })
      setRows(data.list || [])
      setTotal(data.total || 0)
    } catch (err) {
      setError(err instanceof Error ? err.message : "暂时无法加载求职记录")
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, status, userInfo])

  useEffect(() => { void load() }, [load])
  const openEditor = (row: JobApplication) => {
    setEditing(row)
    setEditState({ status: row.status, notes: row.notes || "", nextFollowUpDate: row.nextFollowUpDate || "" })
  }
  const saveEdit = async () => {
    if (!editing || saving) return
    if (editState.notes.length > 4000) {
      toast({ title: "备注过长", description: "备注最多 4000 个字符。", variant: "destructive" })
      return
    }
    setSaving(true)
    try {
      await updateApplication(editing.id, { status: editState.status, notes: editState.notes, nextFollowUpDate: editState.nextFollowUpDate || null })
      setEditing(null)
      toast({ title: "已保存", description: "求职进度已更新。" })
      await load()
    } catch (err) {
      toast({ title: "保存失败", description: err instanceof Error ? err.message : "请稍后重试", variant: "destructive" })
    } finally { setSaving(false) }
  }
  const remove = async (row: JobApplication) => {
    if (deletingId !== null || !window.confirm(`确定删除「${row.companyName} · ${row.position}」这条记录吗？`)) return
    setDeletingId(row.id)
    try {
      await deleteApplication(row.id)
      toast({ title: "已删除", description: "这条求职记录已移除。" })
      if (rows.length === 1 && page > 1) setPage(current => current - 1)
      else await load()
    } catch (err) {
      toast({ title: "删除失败", description: err instanceof Error ? err.message : "请稍后重试", variant: "destructive" })
    } finally { setDeletingId(null) }
  }
  const filteredLabel = useMemo(() => STATUS_OPTIONS.find(option => option.value === status)?.label, [status])

  if (!userInfo) {
    return <main className="mx-auto flex min-h-[calc(100vh-8rem)] max-w-3xl items-center justify-center px-4 py-12"><section className="w-full rounded-2xl border border-slate-200 bg-white px-6 py-14 text-center shadow-sm sm:px-12"><Bookmark className="mx-auto h-10 w-10 text-blue-500" /><h1 className="mt-5 text-2xl font-semibold text-slate-950">登录后管理你的求职进度</h1><p className="mx-auto mt-3 max-w-md text-sm leading-7 text-slate-500">把感兴趣的岗位保存下来，记录投递、面试和后续跟进，不再靠记忆管理秋招。</p><Button className="mt-7" onClick={() => setLoginOpen(true)}>登录查看我的求职</Button></section></main>
  }

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-8 sm:py-12">
      <div className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="text-sm font-medium text-blue-600">我的求职</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">把每一次投递，放在同一处跟进</h1><p className="mt-2 text-sm leading-6 text-slate-500">保存岗位后，你可以随时更新进度、添加备注和安排下一次跟进。</p></div><Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={loading ? "animate-spin" : ""} />刷新记录</Button></div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div className="flex flex-wrap gap-2" role="group" aria-label="筛选求职进度">{STATUS_OPTIONS.map(option => <button key={option.value} type="button" onClick={() => { setStatus(option.value); setPage(1) }} className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${status === option.value ? "border-blue-600 bg-blue-600 text-white" : "border-slate-200 bg-white text-slate-600 hover:border-blue-300 hover:text-blue-700"}`}>{option.label}</button>)}</div><span className="text-sm text-slate-500">{loading ? "正在加载…" : `${total} 条${filteredLabel === "全部进度" ? "记录" : filteredLabel}`}</span></div>
      {error ? <div className="empty-panel" role="alert"><p>{error}</p><Button className="mt-4" onClick={() => void load()}>重新加载</Button></div> : loading ? <div className="space-y-3" aria-label="正在加载求职记录">{[1, 2, 3].map(item => <div key={item} className="h-44 animate-pulse rounded-2xl border border-slate-100 bg-white" />)}</div> : rows.length === 0 ? <div className="empty-panel"><Bookmark className="mx-auto mb-3 h-8 w-8 text-slate-300" /><h2 className="font-medium text-slate-800">还没有求职记录</h2><p className="mt-2 text-sm leading-6">去招聘列表保存几个感兴趣的岗位，回来统一安排进度。</p><Button asChild variant="outline" className="mt-5"><Link href="/">浏览招聘机会</Link></Button></div> : <div className="space-y-3">{rows.map(row => { const link = externalUrl(row.relatedLink); return <article key={row.id} className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm sm:p-6"><div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="break-words text-lg font-semibold text-slate-950">{row.companyName || "未注明公司"}</h2><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${toneForStatus(row.status)}`}>{labelForStatus(row.status)}</span></div><p className="mt-2 break-words text-sm font-medium text-slate-700">{row.position || "岗位信息待补充"}</p><div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs text-slate-500">{row.jobLocation && <span className="inline-flex items-center gap-1.5"><MapPin className="h-3.5 w-3.5" />{row.jobLocation}</span>}{row.deadline && <span className="inline-flex items-center gap-1.5"><CalendarDays className="h-3.5 w-3.5" />截止 {row.deadline}</span>}{row.updateTime && <span>更新于 {formatTime(row.updateTime)}</span>}</div></div><div className="flex shrink-0 flex-wrap gap-2"><Button size="sm" variant="outline" onClick={() => openEditor(row)}><Pencil />编辑进度</Button>{link && <Button size="sm" variant="ghost" asChild><a href={link} target="_blank" rel="noopener noreferrer">原始链接<ExternalLink /></a></Button>}<Button size="sm" variant="ghost" className="text-slate-500 hover:text-rose-600" onClick={() => void remove(row)} disabled={deletingId === row.id}>{deletingId === row.id ? <Loader2 className="animate-spin" /> : <Trash2 />}删除</Button></div></div>{row.notes && <p className="mt-5 whitespace-pre-wrap break-words rounded-xl bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-600">{row.notes}</p>}{row.nextFollowUpDate && <p className="mt-3 text-xs font-medium text-blue-700">下次跟进：{row.nextFollowUpDate}</p>}</article> })}</div>}
      {!error && !loading && total > 0 && <div className="mt-6"><TablePagination page={page} pageSize={pageSize} total={total} currentCount={rows.length} totalLabel="条记录" onPageChange={setPage} onPageSizeChange={value => { setPageSize(value); setPage(1) }} /></div>}
      <Dialog open={Boolean(editing)} onOpenChange={open => { if (!open && !saving) setEditing(null) }}><DialogContent><DialogHeader><DialogTitle>编辑求职进度</DialogTitle><DialogDescription className="break-words">{editing?.companyName} · {editing?.position}</DialogDescription></DialogHeader><div className="space-y-5 py-2"><label className="block text-sm font-medium">当前进度<select className="mt-2 flex h-10 w-full rounded-lg border border-input bg-background px-3 text-sm" value={editState.status} onChange={event => setEditState(state => ({ ...state, status: event.target.value as ApplicationStatus }))}>{STATUS_OPTIONS.filter(option => option.value !== "ALL").map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label><label className="block text-sm font-medium">下次跟进日期<span className="ml-2 font-normal text-slate-400">可选</span><Input className="mt-2" type="date" value={editState.nextFollowUpDate} onChange={event => setEditState(state => ({ ...state, nextFollowUpDate: event.target.value }))} /></label><label className="block text-sm font-medium">备注<span className="ml-2 font-normal text-slate-400">{editState.notes.length}/4000</span><Textarea className="mt-2 min-h-32 resize-y" maxLength={4000} value={editState.notes} onChange={event => setEditState(state => ({ ...state, notes: event.target.value }))} placeholder="记录面试时间、联系人、准备事项等" /></label></div><DialogFooter><Button variant="outline" onClick={() => setEditing(null)} disabled={saving}>取消</Button><Button onClick={() => void saveEdit()} disabled={saving}>{saving && <Loader2 className="animate-spin" />}保存修改</Button></DialogFooter></DialogContent></Dialog>
    </main>
  )
}
