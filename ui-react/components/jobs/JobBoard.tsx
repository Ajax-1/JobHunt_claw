"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { ArrowUpRight, BriefcaseBusiness, Building2, ChevronDown, LayoutList, ListFilter, LockKeyhole, MapPin, Search, Table2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { TablePagination } from "@/components/ui/table-pagination"
import { fetchJobList, type GlobalConfigItemValue } from "@/lib/api"
import { getConfigValue } from "@/lib/config"
import { useLoginModal } from "@/hooks/useLoginModal"
import { RecruitmentHero } from "./RecruitmentHero"
import { OPEN_JOB_ASSISTANT_EVENT } from "@/lib/chat-events"
import { useLoginUser } from "@/hooks/useLoginUser"
import { SaveApplicationButton } from "./SaveApplicationButton"

interface Recruitment {
  id: number
  companyName: string
  companyType: string
  companyIndustry: string
  jobLocation: string
  recruitmentType: string
  recruitmentTarget: string
  position: string
  lastUpdatedTime: string
  deadline: string
}
interface Filters { companyName: string; jobLocation: string; position: string; companyType: string; recruitmentType: string; recruitmentTarget: string; companyIndustry: string }
interface JobBoardProps { internship?: boolean }
const EMPTY: Filters = { companyName: "", jobLocation: "", position: "", companyType: "", recruitmentType: "", recruitmentTarget: "", companyIndustry: "" }
const dateLabel = (value?: string) => value?.replace(/年|月/g, ".").replace(/日/g, "").split("T")[0] || "日期待补充"

/** AI-GENERATED AIDEV-NOTE: 校招与实习复用列表；编辑筛选不发请求，提交后统一分页。 */
export function JobBoard({ internship = false }: JobBoardProps) {
  const [draft, setDraft] = useState<Filters>(EMPTY)
  const [query, setQuery] = useState<Filters>(EMPTY)
  const [page, setPage] = useState<number>(1)
  const [size, setSize] = useState<number>(10)
  const [rows, setRows] = useState<Recruitment[]>([])
  const [total, setTotal] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(true)
  const [slowLoading, setSlowLoading] = useState<boolean>(false)
  const [error, setError] = useState<string>("")
  const [locked, setLocked] = useState<boolean>(false)
  const [advanced, setAdvanced] = useState<boolean>(false)
  const [view, setView] = useState<"list" | "table">("list")
  const [dicts, setDicts] = useState<GlobalConfigItemValue[][]>([[], [], []])
  const { userInfo } = useLoginUser()
  const { setLoginOpen } = useLoginModal()
  const router = useRouter()
  useEffect(() => { Promise.all(["CompanyTypeEnum", "RecruitmentTypeEnum", "RecruitmentTargetEnum"].map(key => getConfigValue("oc", key))).then(setDicts).catch(() => setDicts([[], [], []])) }, [])
  useEffect(() => {
    let active = true
    const slowTimer = window.setTimeout(() => {
      if (active) setSlowLoading(true)
    }, 8000)
    setLoading(true)
    setSlowLoading(false)
    setError("")
    const params = Object.fromEntries(Object.entries(query).filter(([, value]) => value && value !== "all"))
    fetchJobList({ ...params, ...(internship ? { recruitmentType: params.recruitmentType || "实习" } : { recruitmentTypeExcept: "实习" }), page, size })
      .then(data => { if (active) { setRows(data.list); setTotal(data.total); setLocked(data.locked) } })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : "暂时无法加载招聘信息") })
      .finally(() => { if (active) setLoading(false) })
    return () => {
      active = false
      window.clearTimeout(slowTimer)
    }
  }, [query, page, size, internship])
  const reset = () => { setDraft(EMPTY); setQuery({ ...EMPTY }); setPage(1) }
  const openMembership = () => userInfo ? router.push("/user?tab=vip") : setLoginOpen(true)
  const select = (key: "companyType" | "recruitmentType" | "recruitmentTarget", label: string, options: GlobalConfigItemValue[]) => (
    <Select value={draft[key] || "all"} onValueChange={value => setDraft(prev => ({ ...prev, [key]: value }))}>
      <SelectTrigger aria-label={label} className="bg-white"><SelectValue /></SelectTrigger>
      <SelectContent><SelectItem value="all">全部{label}</SelectItem>{options.map(option => <SelectItem key={String(option.intro)} value={String(option.intro)}>{option.intro}</SelectItem>)}</SelectContent>
    </Select>
  )
  const hasActiveFilters = Object.values(query).some(value => value && value !== "all")
  return (
    <div className="job-board mx-auto max-w-7xl px-4 py-8 sm:px-8 sm:py-12">
      <RecruitmentHero internship={internship} onOpenAssistant={() => {
        if (!userInfo) { setLoginOpen(true); return }
        window.dispatchEvent(new Event(OPEN_JOB_ASSISTANT_EVENT))
      }} />
      <form id="recruitment-search" className="scroll-mt-24 mb-7 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm sm:p-5" onSubmit={e => { e.preventDefault(); setQuery({ ...draft }); setPage(1) }}>
        <div className="mb-4 flex items-center justify-between gap-3 text-xs"><span className="flex items-center gap-2 font-medium text-slate-600"><BriefcaseBusiness className="h-4 w-4 text-blue-500" />{internship ? "探索实习机会" : "探索招聘机会"}</span><span className="text-slate-400">{loading || error ? "—" : total} 条{hasActiveFilters ? "匹配招聘" : "招聘信息"}</span></div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_auto]">
          <div className="relative"><Search className="absolute left-3 top-3 h-4 w-4 text-slate-400" /><Input aria-label="公司名称" className="pl-10" placeholder="搜索公司名称" value={draft.companyName} onChange={e => setDraft({ ...draft, companyName: e.target.value })} /></div>
          <Input aria-label="岗位名称" placeholder="岗位关键词，如 Java、产品" value={draft.position} onChange={e => setDraft({ ...draft, position: e.target.value })} />
          <Input aria-label="工作地点" placeholder="工作地点" value={draft.jobLocation} onChange={e => setDraft({ ...draft, jobLocation: e.target.value })} />
          <Button type="submit" className="w-full px-7 sm:w-auto"><Search />查询机会</Button>
        </div>
        <div className="mt-3 grid grid-cols-1 items-center gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_auto_auto]">
          {select("recruitmentTarget", "招聘对象", dicts[2])}
          {select("recruitmentType", "招聘类型", dicts[1].filter(option => String(option.intro).includes("实习") === internship))}
          <Button type="button" variant="ghost" className="w-full sm:w-auto" onClick={() => setAdvanced(!advanced)} aria-expanded={advanced}><ListFilter />更多筛选<ChevronDown className={advanced ? "rotate-180" : ""} /></Button>
          <Button type="button" variant="ghost" className="w-full sm:w-auto" onClick={reset}>重置</Button>
        </div>
        {advanced && <div className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-2">{select("companyType", "公司类型", dicts[0])}<Input aria-label="所属行业" placeholder="所属行业" value={draft.companyIndustry} onChange={e => setDraft({ ...draft, companyIndustry: e.target.value })} /></div>}
      </form>
      <div className="mb-4 flex items-center justify-between"><div className="flex items-center gap-3"><h2 className="font-semibold text-slate-900">{internship ? "实习招聘" : "招聘机会"}</h2><span className="text-xs text-slate-500">{loading ? "正在加载…" : `${total} 条结果`}</span></div><div className="flex gap-1 rounded-lg border border-slate-200 bg-white p-1"><Button type="button" size="sm" variant={view === "list" ? "secondary" : "ghost"} aria-pressed={view === "list"} onClick={() => setView("list")}><LayoutList />列表</Button><Button type="button" size="sm" variant={view === "table" ? "secondary" : "ghost"} aria-pressed={view === "table"} onClick={() => setView("table")}><Table2 />表格</Button></div></div>
      {error ? <div role="alert" className="empty-panel"><Search className="mx-auto mb-3 h-8 w-8 text-rose-300" /><h3 className="font-medium text-slate-800">招聘信息暂时加载失败</h3><p className="mt-2 break-words text-sm">{error}</p><Button className="mt-4" onClick={() => setQuery({ ...query })}>重新加载</Button></div> : loading ? <div aria-label="正在加载招聘信息" className="space-y-3"><div className="rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">{slowLoading ? "加载时间比平时长，可能是服务正在唤醒。你可以继续等待，或稍后重新加载。" : "正在加载招聘信息…"}</div>{[0,1,2].map(i => <div key={i} className="h-36 animate-pulse rounded-2xl border border-slate-100 bg-white" />)}</div> : !rows.length ? <div className="empty-panel"><Search className="mx-auto mb-3 h-8 w-8 text-slate-300" /><h3 className="font-medium text-slate-800">{hasActiveFilters ? "暂未找到匹配的招聘" : "岗位库暂时还没有招聘信息"}</h3><p className="mt-2 text-sm">{hasActiveFilters ? "试着减少筛选条件，看看更多机会。" : "管理员发布岗位后会显示在这里，也可以稍后重新加载。"}</p><div className="mt-5 flex flex-wrap justify-center gap-3"><Button variant="outline" onClick={() => setQuery({ ...query })}>重新加载</Button>{hasActiveFilters && <Button variant="ghost" onClick={reset}>清除筛选</Button>}</div></div> : <>
        {locked && <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-100 bg-blue-50 p-4"><span className="flex items-center gap-2 text-sm text-blue-800"><LockKeyhole className="h-4 w-4" />登录并开通会员后，可查看完整招聘信息。</span><Button size="sm" onClick={openMembership}>{userInfo ? "查看会员权益" : "登录查看"}</Button></div>}
        {view === "list" ? <div className="space-y-3">{rows.map(job => <article key={job.id} className="group flex flex-col gap-5 rounded-2xl border border-slate-200/80 bg-white p-5 transition-colors hover:border-blue-200 sm:flex-row sm:p-6">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-slate-100 bg-slate-50 text-lg font-semibold text-slate-600">{job.companyName?.slice(0,1) || <Building2 />}</div>
          <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-3"><h3 className="text-lg font-semibold text-slate-950">{locked ? <button onClick={openMembership}>{job.companyName}</button> : <Link href={`/job?id=${job.id}`} className="transition-colors hover:text-blue-600">{job.companyName}</Link>}</h3>{job.companyType && <span className="soft-tag">{job.companyType}</span>}<span className="soft-tag text-blue-700">{job.recruitmentType || "招聘中"}</span></div>
            <p className="mt-2.5 line-clamp-2 text-sm leading-6 text-slate-600" title={job.position}>{job.position || "岗位信息待补充"}</p>
            <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-slate-500"><span className="inline-flex max-w-full items-center gap-1.5"><MapPin className="h-3.5 w-3.5 shrink-0" /><span className="truncate">{job.jobLocation || "地点待补充"}</span></span><span>{job.recruitmentTarget || "招聘对象待补充"}</span><span>{dateLabel(job.lastUpdatedTime)} 更新</span></div>
          </div>
          <div className="flex shrink-0 items-center justify-between gap-4 sm:flex-col sm:items-end sm:justify-center"><span className="text-xs text-slate-500">{job.deadline || "截止时间待确认"}</span>{locked ? <Button variant="outline" size="sm" onClick={openMembership}>解锁详情<LockKeyhole /></Button> : <div className="flex items-center gap-2"><Button asChild variant="outline" size="sm"><Link href={`/job?id=${job.id}`}>查看招聘<ArrowUpRight /></Link></Button><SaveApplicationButton jobId={job.id} compact /></div>}</div>
        </article>)}</div> : <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white"><table className="w-full min-w-[780px] text-left text-sm"><thead className="bg-slate-50 text-xs text-slate-500"><tr>{["公司 / 招聘类型", "岗位摘要", "工作地点", "招聘对象", "更新时间", ""].map((label,i) => <th key={i} className="px-5 py-4 font-medium">{label}</th>)}</tr></thead><tbody>{rows.map(job => <tr key={job.id} className="border-t border-slate-100"><td className="px-5 py-4"><p className="font-semibold">{job.companyName}</p><p className="mt-1 text-xs text-slate-500">{job.recruitmentType}</p></td><td className="max-w-xs px-5 py-4"><p className="line-clamp-2 leading-6" title={job.position}>{job.position}</p></td><td className="max-w-40 px-5 py-4"><p className="line-clamp-2">{job.jobLocation}</p></td><td className="whitespace-nowrap px-5 py-4">{job.recruitmentTarget}</td><td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">{dateLabel(job.lastUpdatedTime)}</td><td className="px-5 py-4">{locked ? <Button variant="ghost" onClick={openMembership}>解锁</Button> : <Link href={`/job?id=${job.id}`} className="whitespace-nowrap text-blue-600">详情 ↗</Link>}</td></tr>)}</tbody></table></div>}
      </>}
      {!error && !loading && total > 0 && <div className="mt-6"><TablePagination page={page} pageSize={size} total={total} currentCount={rows.length} totalLabel="条招聘" onPageChange={setPage} onPageSizeChange={value => { setSize(value); setPage(1) }} /></div>}
    </div>
  )
}
