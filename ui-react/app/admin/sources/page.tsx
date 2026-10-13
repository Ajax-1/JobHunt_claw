"use client"

import Link from "next/link"
import { useEffect, useMemo, useState } from "react"
import { useSearchParams } from "next/navigation"
import {
  Database,
  ExternalLink,
  FileSearch,
  Loader2,
  RefreshCcw,
  RotateCcw,
  Search,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  fetchGatherSourceList,
  reRunGatherSource,
  type GatherSourceItem,
  type GatherSourceListQuery,
  type GlobalConfigItemValue,
} from "@/lib/api"
import { getConfigValue } from "@/lib/config"
import { useToast } from "@/hooks/use-toast"

const PAGE_SIZE = 10
const ALL_VALUE = "-1"

const ownerLabels: Record<string, string> = {
  admin: "Admin",
  agent: "Agent",
  system: "系统",
}

const statusLabels: Record<string, string> = {
  active: "正常",
  paused: "暂停",
  archived: "归档",
  invalid: "失效",
}

const statusClass: Record<string, string> = {
  active: "border-emerald-200 bg-emerald-50 text-emerald-700",
  paused: "border-amber-200 bg-amber-50 text-amber-700",
  archived: "border-slate-200 bg-slate-50 text-slate-600",
  invalid: "border-rose-200 bg-rose-50 text-rose-700",
}

function formatDateTime(value?: number | null) {
  if (!value) {
    return "-"
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return "-"
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}

function getTypeLabel(types: GlobalConfigItemValue[], type?: number) {
  if (type === 0) {
    return "自动分类"
  }
  const match = types.find((item) => String(item.value) === String(type))
  return match?.intro ? String(match.intro) : type ? String(type) : "-"
}

function buildQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      query.set(key, String(value))
    }
  })
  return query.toString()
}

export default function GatherSourcesPage() {
  const searchParams = useSearchParams()
  const initialSourceId = searchParams.get("sourceId") || ""
  const [sources, setSources] = useState<GatherSourceItem[]>([])
  const [types, setTypes] = useState<GlobalConfigItemValue[]>([])
  const [query, setQuery] = useState<GatherSourceListQuery>({
    page: 1,
    size: PAGE_SIZE,
    status: "active",
    sourceId: initialSourceId ? Number(initialSourceId) : undefined,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [total, setTotal] = useState(0)
  const [reRunLoadingId, setReRunLoadingId] = useState<number | null>(null)
  const { toast } = useToast()

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const stats = useMemo(() => {
    return sources.reduce(
      (acc, source) => {
        acc.insert += source.lastInsertCount || 0
        acc.update += source.lastUpdateCount || 0
        acc.unchanged += source.lastUnchangedCount || 0
        acc.failed += source.lastFailedCount || 0
        return acc
      },
      { insert: 0, update: 0, unchanged: 0, failed: 0 }
    )
  }, [sources])

  useEffect(() => {
    getConfigValue("gather", "GatherTargetTypeEnum").then(setTypes)
  }, [])

  useEffect(() => {
    setQuery((current) => ({
      ...current,
      page: 1,
      sourceId: initialSourceId ? Number(initialSourceId) : undefined,
    }))
  }, [initialSourceId])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    fetchGatherSourceList(query)
      .then((res) => {
        if (!active) return
        setSources(res.list || [])
        setTotal(res.total || 0)
      })
      .catch((err: unknown) => {
        if (!active) return
        setError(err instanceof Error ? err.message : "获取采集源失败")
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [query])

  const updateQuery = (patch: Partial<GatherSourceListQuery>) => {
    setQuery((current) => ({ ...current, ...patch, page: 1 }))
  }

  const resetQuery = () => {
    setQuery({ page: 1, size: PAGE_SIZE, status: "active" })
  }

  const refresh = () => {
    setQuery((current) => ({ ...current }))
  }

  const handleReRun = async (source: GatherSourceItem) => {
    setReRunLoadingId(source.sourceId)
    try {
      const taskId = await reRunGatherSource(source.sourceId)
      toast({ title: "已创建采集任务", description: `任务 #${taskId} 已进入队列` })
      refresh()
    } catch (err: unknown) {
      toast({
        title: "重新采集失败",
        description: err instanceof Error ? err.message : "接口异常",
        variant: "destructive",
      })
    } finally {
      setReRunLoadingId(null)
    }
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-5">
        <section className="rounded-lg border border-surface-border bg-white px-5 py-4 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <h1 className="text-sm font-semibold text-slate-700">采集结果统计</h1>
            <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
              <div className="rounded-md bg-emerald-50 px-3 py-2 text-emerald-700">新增 {stats.insert}</div>
              <div className="rounded-md bg-blue-50 px-3 py-2 text-blue-700">更新 {stats.update}</div>
              <div className="rounded-md bg-slate-50 px-3 py-2 text-slate-600">无变化 {stats.unchanged}</div>
              <div className="rounded-md bg-rose-50 px-3 py-2 text-rose-700">失败 {stats.failed}</div>
            </div>
          </div>
        </section>

        <section className="mt-4 rounded-lg border border-surface-border bg-white shadow-sm">
          <div className="border-b border-surface-border p-5">
            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-surface-border bg-slate-50 p-3">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                <Input
                  placeholder="来源 ID / 关键词"
                  className="h-10 w-56 bg-white pl-9"
                  value={query.keyword || query.sourceId || ""}
                  onChange={(event) => {
                    const value = event.target.value.trim()
                    if (/^\d+$/.test(value)) {
                      updateQuery({ sourceId: Number(value), keyword: undefined })
                    } else {
                      updateQuery({ keyword: value || undefined, sourceId: undefined })
                    }
                  }}
                />
              </div>
              <Select
                value={query.type === undefined ? ALL_VALUE : String(query.type)}
                onValueChange={(value) => updateQuery({ type: value === ALL_VALUE ? undefined : Number(value) })}
              >
                <SelectTrigger className="h-10 w-40 bg-white">
                  <SelectValue placeholder="全部类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部类型</SelectItem>
                  <SelectItem value="0">自动分类</SelectItem>
                  {types.map((item) => (
                    <SelectItem key={String(item.value)} value={String(item.value)}>
                      {item.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={query.ownerType || ALL_VALUE}
                onValueChange={(value) => updateQuery({ ownerType: value === ALL_VALUE ? undefined : value })}
              >
                <SelectTrigger className="h-10 w-40 bg-white">
                  <SelectValue placeholder="全部来源" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部来源</SelectItem>
                  <SelectItem value="admin">Admin</SelectItem>
                  <SelectItem value="agent">Agent</SelectItem>
                  <SelectItem value="system">系统</SelectItem>
                </SelectContent>
              </Select>
              <Select
                value={query.status || ALL_VALUE}
                onValueChange={(value) => updateQuery({ status: value === ALL_VALUE ? undefined : value })}
              >
                <SelectTrigger className="h-10 w-36 bg-white">
                  <SelectValue placeholder="全部状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>全部状态</SelectItem>
                  <SelectItem value="active">正常</SelectItem>
                  <SelectItem value="paused">暂停</SelectItem>
                  <SelectItem value="archived">归档</SelectItem>
                  <SelectItem value="invalid">失效</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="outline" className="h-10 gap-2" onClick={resetQuery}>
                重置
              </Button>
              <Button variant="outline" className="h-10 gap-2" onClick={refresh} disabled={loading}>
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCcw className="h-4 w-4" />}
                刷新
              </Button>
            </div>
          </div>

          <div className="overflow-hidden">
            <Table>
              <TableHeader className="bg-slate-50">
                <TableRow>
                  <TableHead className="w-28">来源</TableHead>
                  <TableHead className="min-w-[340px]">内容</TableHead>
                  <TableHead className="w-32">类型</TableHead>
                  <TableHead className="w-32">归属</TableHead>
                  <TableHead className="w-40">最近结果</TableHead>
                  <TableHead className="w-40">更新时间</TableHead>
                  <TableHead className="w-72 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-40 text-center text-content-tertiary">
                      <span className="inline-flex items-center gap-2">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        加载采集源中
                      </span>
                    </TableCell>
                  </TableRow>
                ) : error ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-40 text-center text-rose-600">
                      {error}
                    </TableCell>
                  </TableRow>
                ) : sources.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-40 text-center text-content-tertiary">
                      暂无采集源
                    </TableCell>
                  </TableRow>
                ) : (
                  sources.map((source) => {
                    const tasksHref = `/admin/sources/tasks?${buildQuery({ sourceId: source.sourceId, runnerType: source.ownerType })}`
                    const draftsHref = `/admin/drafts?${buildQuery({ sourceId: source.sourceId, runnerType: source.ownerType })}`
                    const jobsHref = `/admin/jobs?${buildQuery({ sourceId: source.sourceId, runnerType: source.ownerType })}`
                    return (
                      <TableRow key={source.sourceId} className="bg-white">
                        <TableCell className="align-top">
                          <div className="font-semibold text-content-primary">#{source.sourceId}</div>
                          <Badge variant="outline" className={statusClass[source.status] || "border-slate-200 bg-slate-50 text-slate-600"}>
                            {statusLabels[source.status] || source.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="align-top">
                          <div className="line-clamp-2 break-all font-medium text-content-primary" title={source.content}>
                            {source.title || source.content || "-"}
                          </div>
                          <div className="mt-1 text-xs text-content-tertiary">hash {source.sourceHash?.slice(0, 10) || "-"}</div>
                        </TableCell>
                        <TableCell className="align-top text-content-secondary">{getTypeLabel(types, source.type)}</TableCell>
                        <TableCell className="align-top">
                          <Badge variant="outline" className="border-blue-100 bg-blue-50 text-blue-700">
                            {ownerLabels[source.ownerType] || source.ownerType}
                          </Badge>
                        </TableCell>
                        <TableCell className="align-top">
                          <div className="grid grid-cols-2 gap-1 text-xs">
                            <span className="rounded bg-emerald-50 px-2 py-1 text-emerald-700">增 {source.lastInsertCount || 0}</span>
                            <span className="rounded bg-blue-50 px-2 py-1 text-blue-700">更 {source.lastUpdateCount || 0}</span>
                            <span className="rounded bg-slate-50 px-2 py-1 text-slate-600">同 {source.lastUnchangedCount || 0}</span>
                            <span className="rounded bg-rose-50 px-2 py-1 text-rose-700">败 {source.lastFailedCount || 0}</span>
                          </div>
                        </TableCell>
                        <TableCell className="align-top text-content-secondary">
                          <div>{formatDateTime(source.updateTime)}</div>
                          <div className="mt-1 text-xs text-content-tertiary">成功 {formatDateTime(source.lastSuccessTime)}</div>
                        </TableCell>
                        <TableCell className="align-top">
                          <div className="flex flex-wrap justify-end gap-2">
                            <Button size="sm" variant="outline" className="h-8 gap-1.5" onClick={() => handleReRun(source)} disabled={reRunLoadingId === source.sourceId}>
                              {reRunLoadingId === source.sourceId ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                              重新采集
                            </Button>
                            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                              <Link href={tasksHref}>
                                <FileSearch className="h-3.5 w-3.5" />
                                任务
                              </Link>
                            </Button>
                            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                              <Link href={draftsHref}>
                                <ExternalLink className="h-3.5 w-3.5" />
                                草稿
                              </Link>
                            </Button>
                            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                              <Link href={jobsHref}>职位</Link>
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-between border-t border-surface-border px-5 py-4 text-sm text-content-tertiary">
            <span>共 {total} 个采集源，当前显示 {sources.length} 个</span>
            <div className="flex items-center gap-3">
              <Button
                variant="outline"
                size="sm"
                disabled={query.page === 1 || loading}
                onClick={() => setQuery((current) => ({ ...current, page: Math.max(1, (current.page || 1) - 1) }))}
              >
                上一页
              </Button>
              <span className="text-content-primary">{query.page || 1} / {totalPages}</span>
              <Button
                variant="outline"
                size="sm"
                disabled={(query.page || 1) >= totalPages || loading}
                onClick={() => setQuery((current) => ({ ...current, page: (current.page || 1) + 1 }))}
              >
                下一页
              </Button>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}
