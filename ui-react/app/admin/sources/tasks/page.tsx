"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import {
  ClipboardList,
  Database,
  ExternalLink,
  FileSpreadsheet,
  FileText,
  ImageIcon,
  Link2,
  Loader2,
  RotateCcw,
  Search,
  Trash2,
  X,
  type LucideIcon,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TablePagination } from "@/components/ui/table-pagination"
import {
  fetchTaskList,
  deleteTask,
  reRunTask,
  type GlobalConfigItemValue,
  type TaskListItem,
} from "@/lib/api"
import { getConfigValue } from "@/lib/config"
import { cn } from "@/lib/utils"
import { useToast } from "@/hooks/use-toast"

const PAGE_SIZE = 10
const ALL_VALUE = "-1"

interface SourceTaskQueryState {
  page: number
  size: number
  taskId: string
  sourceId: string
  runnerType: string
  type: string
  state: string
}

interface TaskDraftResult {
  parsed: boolean
  raw: string
  msg: string
  insertDraftIds: number[]
  updateDraftIds: number[]
  unchangedDraftIds: number[]
}

interface TypeVisual {
  icon: LucideIcon
  className: string
}

const typeVisuals: Record<string, TypeVisual> = {
  "1": { icon: FileText, className: "border-amber-200 bg-amber-50 text-amber-700" },
  "2": { icon: FileText, className: "border-slate-200 bg-slate-50 text-slate-700" },
  "3": { icon: Link2, className: "border-blue-200 bg-blue-50 text-blue-700" },
  "4": { icon: FileSpreadsheet, className: "border-orange-200 bg-orange-50 text-orange-700" },
  "5": { icon: FileSpreadsheet, className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  "6": { icon: ImageIcon, className: "border-rose-200 bg-rose-50 text-rose-700" },
}

const stateClass: Record<number, string> = {
  0: "border-slate-200 bg-slate-50 text-slate-600",
  1: "border-blue-200 bg-blue-50 text-blue-700",
  2: "border-emerald-200 bg-emerald-50 text-emerald-700",
  3: "border-rose-200 bg-rose-50 text-rose-700",
}

const runnerLabels: Record<string, string> = {
  admin: "Admin",
  agent: "Agent",
  system: "系统",
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return "接口返回异常，请确认已使用管理员账号登录并检查后端服务"
}

function formatDateTime(value?: string | number | null) {
  if (value === undefined || value === null || value === "") {
    return "-"
  }
  const normalized = typeof value === "string" && /^\d+$/.test(value) ? Number(value) : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}

function getOptionLabel(options: GlobalConfigItemValue[], value?: string | number | null) {
  const match = options.find((option) => String(option.value) === String(value))
  return match?.intro ? String(match.intro) : value === undefined || value === null ? "-" : String(value)
}

function getTypeVisual(value?: string | number | null) {
  return typeVisuals[String(value)] ?? {
    icon: ClipboardList,
    className: "border-slate-200 bg-white text-slate-600",
  }
}

function SourceTaskTypeOption({ option }: { option: GlobalConfigItemValue }) {
  const visual = getTypeVisual(String(option.value))
  const TypeIcon = visual.icon

  return (
    <div className="flex items-center gap-2">
      <span className={cn("flex h-6 w-6 items-center justify-center rounded-md border", visual.className)}>
        <TypeIcon className="h-3.5 w-3.5" />
      </span>
      <span>{option.intro}</span>
    </div>
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function toDraftIdList(value: unknown) {
  const source = Array.isArray(value) ? value : typeof value === "string" ? value.split(",") : []
  return Array.from(
    new Set(
      source
        .map((item) => Number(String(item).trim()))
        .filter((id) => Number.isInteger(id) && id > 0)
    )
  )
}

function parseTaskDraftResult(result?: string | null): TaskDraftResult {
  const raw = result?.trim() || ""
  if (!raw) {
    return { parsed: false, raw: "", msg: "", insertDraftIds: [], updateDraftIds: [], unchangedDraftIds: [] }
  }

  try {
    const parsed = JSON.parse(raw) as unknown
    if (!isRecord(parsed)) {
      return { parsed: false, raw, msg: "", insertDraftIds: [], updateDraftIds: [], unchangedDraftIds: [] }
    }
    return {
      parsed: true,
      raw,
      msg: typeof parsed.msg === "string" ? parsed.msg : "",
      insertDraftIds: toDraftIdList(parsed.insertDraftIds ?? parsed.insert),
      updateDraftIds: toDraftIdList(parsed.updateDraftIds ?? parsed.update),
      unchangedDraftIds: toDraftIdList(parsed.unchangedDraftIds ?? parsed.unchanged),
    }
  } catch {
    return { parsed: false, raw, msg: "", insertDraftIds: [], updateDraftIds: [], unchangedDraftIds: [] }
  }
}

function getTaskDraftIds(result: TaskDraftResult) {
  return Array.from(new Set([...result.insertDraftIds, ...result.updateDraftIds, ...result.unchangedDraftIds]))
}

function buildDraftListHref(task: TaskListItem, draftIds: number[]) {
  const params = new URLSearchParams({
    draftIds: draftIds.join(","),
    sourceTaskId: String(task.taskId),
  })
  if (task.sourceId) {
    params.set("sourceId", String(task.sourceId))
  }
  if (task.runnerType) {
    params.set("runnerType", task.runnerType)
  }
  return `/admin/drafts?${params.toString()}`
}

function DraftCountBadge({ label, ids, tone }: { label: string; ids: number[]; tone: "insert" | "update" | "same" }) {
  if (ids.length === 0) {
    return null
  }
  const toneClass = tone === "insert"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : tone === "update"
      ? "border-blue-200 bg-blue-50 text-blue-700"
      : "border-slate-200 bg-slate-50 text-slate-600"

  return (
    <Badge variant="outline" className={cn("h-6 rounded-md px-2", toneClass)}>
      {label} {ids.length}
    </Badge>
  )
}

function TaskResultView({ task }: { task: TaskListItem }) {
  const result = parseTaskDraftResult(task.result)
  const draftIds = getTaskDraftIds(result)

  if (!result.raw) {
    return <span className="text-sm text-content-muted">未返回</span>
  }

  if (!result.parsed) {
    return (
      <span className="line-clamp-2 text-sm text-content-secondary" title={result.raw}>
        {result.raw}
      </span>
    )
  }

  return (
    <div className="min-w-[240px] space-y-2">
      <div className="flex flex-wrap gap-1.5">
        <DraftCountBadge label="新增" ids={result.insertDraftIds} tone="insert" />
        <DraftCountBadge label="更新" ids={result.updateDraftIds} tone="update" />
        <DraftCountBadge label="无变化" ids={result.unchangedDraftIds} tone="same" />
        {draftIds.length === 0 && (
          <span className="text-sm text-content-secondary">{result.msg && result.msg !== "success" ? result.msg : "未生成草稿"}</span>
        )}
      </div>
      {draftIds.length > 0 && (
        <Button asChild size="xs" variant="outline" className="h-8 border-slate-200 bg-white text-slate-700 hover:bg-slate-50">
          <Link href={buildDraftListHref(task, draftIds)}>
            <ExternalLink className="h-3.5 w-3.5" />
            查看草稿
          </Link>
        </Button>
      )}
    </div>
  )
}

function buildTaskParams(query: SourceTaskQueryState) {
  return {
    page: query.page,
    size: query.size,
    taskId: query.taskId ? Number(query.taskId) : undefined,
    sourceId: query.sourceId ? Number(query.sourceId) : undefined,
    runnerType: query.runnerType || undefined,
    type: query.type !== ALL_VALUE ? Number(query.type) : undefined,
    state: query.state !== ALL_VALUE ? Number(query.state) : undefined,
  }
}

export default function SourceTasksPage() {
  const searchParams = useSearchParams()
  const requestedSourceId = searchParams.get("sourceId") ?? ""
  const requestedRunnerType = searchParams.get("runnerType") ?? ""
  const [query, setQuery] = useState<SourceTaskQueryState>({
    page: 1,
    size: PAGE_SIZE,
    taskId: "",
    sourceId: requestedSourceId,
    runnerType: requestedRunnerType,
    type: ALL_VALUE,
    state: ALL_VALUE,
  })
  const [tasks, setTasks] = useState<TaskListItem[]>([])
  const [taskTotal, setTaskTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reRunLoadingId, setReRunLoadingId] = useState<number | null>(null)
  const [deleteLoadingId, setDeleteLoadingId] = useState<number | null>(null)
  const [taskStateOptions, setTaskStateOptions] = useState<GlobalConfigItemValue[]>([])
  const [taskTypeOptions, setTaskTypeOptions] = useState<GlobalConfigItemValue[]>([])
  const { toast } = useToast()

  useEffect(() => {
    getConfigValue("gather", "GatherTargetTypeEnum").then(setTaskTypeOptions)
    getConfigValue("gather", "GatherTaskStateEnum").then(setTaskStateOptions)
  }, [])

  useEffect(() => {
    setQuery((current) => ({
      ...current,
      page: 1,
      sourceId: requestedSourceId,
      runnerType: requestedRunnerType,
    }))
  }, [requestedRunnerType, requestedSourceId])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)

    fetchTaskList(buildTaskParams(query))
      .then((res) => {
        if (!active) return
        setTasks(res.list || [])
        setTaskTotal(res.total || 0)
      })
      .catch((err: unknown) => {
        if (!active) return
        setError(getErrorMessage(err))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [query])

  const refresh = () => {
    setQuery((current) => ({ ...current }))
  }

  const resetFilters = () => {
    setQuery({
      page: 1,
      size: PAGE_SIZE,
      taskId: "",
      sourceId: "",
      runnerType: "",
      type: ALL_VALUE,
      state: ALL_VALUE,
    })
  }

  const handleReRun = async (taskId: number) => {
    setReRunLoadingId(taskId)
    try {
      await reRunTask(taskId)
      toast({ title: "任务已重新加入队列" })
      refresh()
    } catch (err: unknown) {
      toast({
        title: "重跑失败",
        description: getErrorMessage(err),
        variant: "destructive",
      })
    } finally {
      setReRunLoadingId(null)
    }
  }

  const handleDelete = async (taskId: number) => {
    if (!window.confirm("确定删除这条已结束的采集任务吗？删除后无法恢复。")) return
    setDeleteLoadingId(taskId)
    try {
      await deleteTask(taskId)
      toast({ title: "任务已删除" })
      refresh()
    } catch (err: unknown) {
      toast({ title: "删除失败", description: getErrorMessage(err), variant: "destructive" })
    } finally {
      setDeleteLoadingId(null)
    }
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-5">
        <section className="rounded-lg border border-surface-border bg-white shadow-sm">
          <div className="border-b border-surface-border px-5 py-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h1 className="text-base font-semibold text-content-primary">来源执行记录</h1>
              <div className="flex items-center gap-2">
                <Button variant="outline" className="h-10 gap-2" onClick={resetFilters}>
                  <X className="h-4 w-4" />
                  重置
                </Button>
                <Button variant="outline" className="h-10 gap-2" onClick={refresh} disabled={loading}>
                  {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                  查询
                </Button>
              </div>
            </div>
          </div>

          <div className="space-y-4 p-5">
            {(query.sourceId || query.runnerType) && (
              <div className="flex flex-wrap items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700">
                {query.sourceId && <span className="font-medium">来源 #{query.sourceId}</span>}
                {query.runnerType && <span className="font-medium">{runnerLabels[query.runnerType] || query.runnerType}</span>}
                <Link href="/admin/sources/tasks" className="ml-auto text-blue-700 underline-offset-4 hover:underline">
                  查看全部来源任务
                </Link>
              </div>
            )}

            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-surface-border bg-slate-50 p-3">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                <Input
                  placeholder="来源 ID"
                  className="h-10 w-32 bg-white pl-9"
                  value={query.sourceId}
                  onChange={(event) => setQuery((current) => ({ ...current, sourceId: event.target.value.replace(/\D/g, ""), page: 1 }))}
                />
              </div>
              <Input
                placeholder="任务 ID"
                className="h-10 w-32 bg-white"
                value={query.taskId}
                onChange={(event) => setQuery((current) => ({ ...current, taskId: event.target.value.replace(/\D/g, ""), page: 1 }))}
              />
              <Select value={query.runnerType || ALL_VALUE} onValueChange={(value) => setQuery((current) => ({ ...current, runnerType: value === ALL_VALUE ? "" : value, page: 1 }))}>
                <SelectTrigger className="h-10 w-36 bg-white">
                  <SelectValue placeholder="全部归属" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部归属</SelectItem>
                  <SelectItem value="admin">Admin</SelectItem>
                  <SelectItem value="agent">Agent</SelectItem>
                  <SelectItem value="system">系统</SelectItem>
                </SelectContent>
              </Select>
              <Select value={query.type} onValueChange={(value) => setQuery((current) => ({ ...current, type: value, page: 1 }))}>
                <SelectTrigger className="h-10 w-40 bg-white">
                  <SelectValue placeholder="全部类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部类型</SelectItem>
                  {taskTypeOptions.map((option) => (
                    <SelectItem key={String(option.value)} value={String(option.value)}>
                      <SourceTaskTypeOption option={option} />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={query.state} onValueChange={(value) => setQuery((current) => ({ ...current, state: value, page: 1 }))}>
                <SelectTrigger className="h-10 w-36 bg-white">
                  <SelectValue placeholder="全部状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部状态</SelectItem>
                  {taskStateOptions.map((option) => (
                    <SelectItem key={String(option.value)} value={String(option.value)}>
                      {option.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="overflow-hidden rounded-lg border border-surface-border">
              <Table>
                <TableHeader className="bg-slate-50">
                  <TableRow className="hover:bg-slate-50">
                    <TableHead className="w-24 text-content-secondary">任务</TableHead>
                    <TableHead className="w-28 text-content-secondary">来源</TableHead>
                    <TableHead className="w-32 text-content-secondary">类型</TableHead>
                    <TableHead className="w-32 text-content-secondary">模型</TableHead>
                    <TableHead className="w-32 text-content-secondary">状态</TableHead>
                    <TableHead className="min-w-[320px] text-content-secondary">输入</TableHead>
                    <TableHead className="min-w-[240px] text-content-secondary">草稿结果</TableHead>
                    <TableHead className="w-40 text-content-secondary">更新时间</TableHead>
                    <TableHead className="w-24 text-right text-content-secondary">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={9} className="h-32 text-center text-content-tertiary">
                        <span className="inline-flex items-center gap-2">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          加载来源任务中
                        </span>
                      </TableCell>
                    </TableRow>
                  ) : error ? (
                    <TableRow>
                      <TableCell colSpan={9} className="h-32 text-center text-rose-600">
                        {error}
                      </TableCell>
                    </TableRow>
                  ) : tasks.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} className="h-32 text-center text-content-tertiary">
                        暂无来源任务
                      </TableCell>
                    </TableRow>
                  ) : (
                    tasks.map((task) => {
                      const visual = getTypeVisual(task.type)
                      const TypeIcon = visual.icon
                      const fullText = task.content || ""
                      const displayText = fullText.length > 160 ? `${fullText.slice(0, 160)}...` : fullText
                      return (
                        <TableRow key={task.taskId} className="bg-white">
                          <TableCell className="align-top font-semibold text-content-primary">#{task.taskId}</TableCell>
                          <TableCell className="align-top">
                            {task.sourceId ? (
                              <Link href={`/admin/sources?sourceId=${task.sourceId}`} className="font-medium text-blue-600 hover:underline">
                                来源 #{task.sourceId}
                              </Link>
                            ) : (
                              <span className="text-content-tertiary">未绑定</span>
                            )}
                            {task.runnerType && (
                              <Badge variant="outline" className="mt-1 block w-fit border-slate-200 bg-slate-50 text-slate-600">
                                {runnerLabels[task.runnerType] || task.runnerType}
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="align-top">
                            <Badge variant="outline" className={cn("gap-1.5", visual.className)}>
                              <TypeIcon className="h-3.5 w-3.5" />
                              {getOptionLabel(taskTypeOptions, task.type)}
                            </Badge>
                          </TableCell>
                          <TableCell className="align-top text-content-secondary">{task.model || "-"}</TableCell>
                          <TableCell className="align-top">
                            <Badge variant="outline" className={cn("border-slate-200 bg-white", stateClass[task.state])}>
                              {getOptionLabel(taskStateOptions, task.state)}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-[420px] align-top text-sm text-content-secondary">
                            <span className="line-clamp-3 whitespace-pre-wrap break-all">{displayText || "-"}</span>
                          </TableCell>
                          <TableCell className="max-w-[300px] align-top text-sm text-content-secondary">
                            <TaskResultView task={task} />
                          </TableCell>
                          <TableCell className="align-top text-sm text-content-tertiary">{formatDateTime(task.updateTime)}</TableCell>
                          <TableCell className="align-top text-right">
                            <div className="flex justify-end gap-2">
                              <Button
                                size="sm"
                                variant="outline"
                                className="h-8 gap-1.5"
                                disabled={reRunLoadingId === task.taskId || deleteLoadingId === task.taskId}
                                onClick={() => handleReRun(task.taskId)}
                              >
                                {reRunLoadingId === task.taskId ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                                重跑
                              </Button>
                              {(task.state === 2 || task.state === 3) && <Button size="sm" variant="ghost" className="h-8 gap-1.5 text-rose-600 hover:text-rose-700" disabled={deleteLoadingId === task.taskId || reRunLoadingId === task.taskId} onClick={() => handleDelete(task.taskId)}>
                                {deleteLoadingId === task.taskId ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                                删除
                              </Button>}
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })
                  )}
                </TableBody>
              </Table>
            </div>

            <TablePagination
              page={query.page}
              pageSize={query.size}
              total={taskTotal}
              currentCount={tasks.length}
              loading={loading}
              totalLabel="条来源任务"
              onPageChange={(page) => setQuery((current) => ({ ...current, page }))}
              onPageSizeChange={(size) => setQuery((current) => ({ ...current, size, page: 1 }))}
            />
          </div>
        </section>
      </div>
    </div>
  )
}
