"use client"
import { useState, useEffect, useMemo, useCallback, type MouseEvent as ReactMouseEvent, type ReactNode } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { ExternalLink, FileSearch, Loader2, Search, Trash2, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { DraftEvidence } from "@/components/admin/DraftEvidence"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TablePagination } from "@/components/ui/table-pagination"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { fetchDraftList, DraftItem, DraftListQuery, DraftListResponse, batchPublishDrafts, updateDraft, deleteDraft, GlobalConfigItemValue } from "@/lib/api"
import { useToast } from "@/hooks/use-toast"
import { getConfigValue } from "@/lib/config"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"

const PAGE_SIZE = 10
type DraftColumnKey =
    | "select"
    | "companyName"
    | "companyType"
    | "industry"
    | "location"
    | "recruitmentType"
    | "target"
    | "position"
    | "updatedAt"
    | "state"
    | "process"
    | "link"
    | "action"

interface DraftColumnConfig {
    key: DraftColumnKey
    label: string
    minWidth: number
    maxWidth: number
    align?: "left" | "center" | "right"
    sticky?: boolean
}

const DEFAULT_DRAFT_COLUMN_WIDTHS: Record<DraftColumnKey, number> = {
    select: 56,
    companyName: 230,
    companyType: 92,
    industry: 132,
    location: 140,
    recruitmentType: 116,
    target: 132,
    position: 220,
    updatedAt: 136,
    state: 112,
    process: 112,
    link: 144,
    action: 128,
}

const DRAFT_TABLE_COLUMNS: DraftColumnConfig[] = [
    { key: "select", label: "选择", minWidth: 48, maxWidth: 80, align: "center" },
    { key: "updatedAt", label: "招聘时间", minWidth: 120, maxWidth: 180 },
    { key: "companyName", label: "公司名称", minWidth: 180, maxWidth: 340 },
    { key: "location", label: "工作地点", minWidth: 120, maxWidth: 220 },
    { key: "position", label: "岗位", minWidth: 170, maxWidth: 340 },
    { key: "state", label: "状态", minWidth: 96, maxWidth: 150 },
    { key: "action", label: "操作", minWidth: 120, maxWidth: 176, align: "right", sticky: true },
]

const STICKY_ACTION_HEAD_CLASS =
    "border-l border-slate-200 bg-slate-100 shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"
const STICKY_ACTION_CELL_CLASS =
    "sticky right-0 z-10 border-l border-slate-200 bg-slate-50 px-3 text-right shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"

function sanitizeDraftIds(value: string | null) {
    if (!value) {
        return ""
    }

    return Array.from(
        new Set(
            value
                .split(",")
                .map((item) => item.trim())
                .filter((item) => /^\d+$/.test(item))
        )
    ).join(",")
}

function compactText(value?: string | null) {
    const text = `${value ?? ""}`.trim()
    return text || "-"
}

function isHttpUrl(value?: string | null) {
    return /^https?:\/\/\S+$/i.test(`${value ?? ""}`.trim())
}

function normalizeLinkValue(value?: string | null) {
    const text = `${value ?? ""}`.trim()
    return !text || text === "-" ? "" : text
}

function normalizeLinkKey(value: string) {
    return value.replace(/\/$/, "").toLowerCase()
}

function getDraftLinkItems(draft: DraftItem) {
    const relatedLink = normalizeLinkValue(draft.relatedLink)
    const announcement = normalizeLinkValue(draft.jobAnnouncement)

    if (!relatedLink && !announcement) {
        return []
    }
    if (relatedLink && announcement && normalizeLinkKey(relatedLink) === normalizeLinkKey(announcement)) {
        return [{ label: "链接", value: relatedLink }]
    }

    return [
        relatedLink ? { label: "投递", value: relatedLink } : null,
        announcement ? { label: "公告", value: announcement } : null,
    ].filter((item): item is { label: string; value: string } => Boolean(item))
}

function companyTypeBadgeClass(value: string) {
    const text = compactText(value)
    if (text.includes("央") || text.includes("国企")) {
        return "border-blue-200 bg-blue-50 text-blue-700"
    }
    if (text.includes("民") || text.includes("私")) {
        return "border-emerald-200 bg-emerald-50 text-emerald-700"
    }
    if (text.includes("外")) {
        return "border-violet-200 bg-violet-50 text-violet-700"
    }
    if (text.includes("事业") || text.includes("学校")) {
        return "border-amber-200 bg-amber-50 text-amber-700"
    }
    if (text.includes("银行") || text.includes("金融")) {
        return "border-cyan-200 bg-cyan-50 text-cyan-700"
    }
    return "border-slate-200 bg-slate-100 text-slate-700"
}

function recruitmentTypeBadgeClass(value: string) {
    const text = compactText(value)
    if (text.includes("提前批")) {
        return "border-sky-200 bg-sky-50 text-sky-700"
    }
    if (text.includes("秋招")) {
        return "border-indigo-200 bg-indigo-50 text-indigo-700"
    }
    if (text.includes("春招")) {
        return "border-lime-200 bg-lime-50 text-lime-700"
    }
    if (text.includes("实习")) {
        return "border-orange-200 bg-orange-50 text-orange-700"
    }
    if (text.includes("社招")) {
        return "border-purple-200 bg-purple-50 text-purple-700"
    }
    return "border-slate-200 bg-slate-100 text-slate-700"
}

// AI-GENERATED AIDEV-NOTE: 列表与编辑框展示同一持久化值，不在展示阶段推算毕业年份。
function normalizeRecruitmentTargetForDisplay(draft: DraftItem) {
    return compactText(draft.recruitmentTarget)
}

function ValueTooltip({ value, children }: { value?: string | null; children: ReactNode }) {
    return (
        <Tooltip>
            <TooltipTrigger asChild>{children}</TooltipTrigger>
            <TooltipContent className="max-w-sm whitespace-normal break-words">
                {compactText(value)}
            </TooltipContent>
        </Tooltip>
    )
}

function DraftLinksCell({ draft }: { draft: DraftItem }) {
    const items = getDraftLinkItems(draft)
    if (items.length === 0) {
        return <span className="text-content-tertiary">-</span>
    }

    return (
        <div className="flex flex-wrap gap-1.5">
            {items.map((item) => (
                isHttpUrl(item.value) ? (
                    <a
                        key={`${item.label}-${item.value}`}
                        href={item.value}
                        className="inline-flex h-7 items-center gap-1 rounded-md border border-blue-200 bg-blue-50 px-2 text-xs font-semibold text-blue-700 hover:bg-blue-100"
                        target="_blank"
                        rel="noopener noreferrer"
                        title={item.value}
                    >
                        {item.label}
                        <ExternalLink className="h-3 w-3" />
                    </a>
                ) : (
                    <ValueTooltip key={`${item.label}-${item.value}`} value={item.value}>
                        <span className="inline-flex h-7 items-center rounded-md border border-slate-200 bg-slate-50 px-2 text-xs font-semibold text-slate-700">
                            {item.label}
                        </span>
                    </ValueTooltip>
                )
            ))}
        </div>
    )
}

function ResizableDraftHead({
    column,
    resizeColumn,
    width,
    onResizeStart,
    children,
}: {
    column: DraftColumnConfig
    resizeColumn?: DraftColumnConfig
    width: number
    onResizeStart: (column: DraftColumnConfig, event: ReactMouseEvent<HTMLButtonElement>) => void
    children?: ReactNode
}) {
    const isCenterAligned = column.align === "center"
    const isRightAligned = column.align === "right"

    return (
        <TableHead
            style={{ width, minWidth: width, maxWidth: width }}
            className={cn(
                "relative select-none bg-slate-50 text-content-secondary",
                resizeColumn && "pl-6",
                isCenterAligned && "text-center",
                isRightAligned && "text-right",
                column.sticky && cn("sticky right-0 z-20", STICKY_ACTION_HEAD_CLASS)
            )}
        >
            {children ?? <span className="block truncate">{column.label}</span>}
            {resizeColumn && (
                <button
                    type="button"
                    aria-label={`调整${resizeColumn.label}列宽`}
                    title="拖动调整左侧列宽"
                    className="absolute left-0 top-0 z-30 flex h-full w-3 cursor-col-resize touch-none items-center justify-center bg-transparent outline-none transition hover:bg-blue-100/70 focus-visible:bg-blue-100 focus-visible:ring-2 focus-visible:ring-blue-500"
                    onMouseDown={(event) => onResizeStart(resizeColumn, event)}
                >
                    <span className="h-5 w-px rounded-full bg-slate-300/70 opacity-70" />
                </button>
            )}
        </TableHead>
    )
}

export default function DraftsPage() {
    const router = useRouter()
    const searchParams = useSearchParams()
    const linkedDraftIds = useMemo(() => sanitizeDraftIds(searchParams.get("draftIds")), [searchParams])
    const sourceTaskId = searchParams.get("sourceTaskId") || ""
    const sourceId = searchParams.get("sourceId") || ""
    const runnerType = searchParams.get("runnerType") || ""
    const linkedDraftIdList = useMemo(() => linkedDraftIds ? linkedDraftIds.split(",") : [], [linkedDraftIds])
    const initialFilters = useMemo<DraftListQuery>(
        () => ({
            ...(linkedDraftIds ? { draftIds: linkedDraftIds } : {}),
            ...(sourceId ? { sourceId: Number(sourceId) } : {}),
            ...(sourceTaskId ? { sourceTaskId: Number(sourceTaskId) } : {}),
            ...(runnerType ? { runnerType } : {}),
        }),
        [linkedDraftIds, runnerType, sourceId, sourceTaskId]
    )
    const [drafts, setDrafts] = useState<DraftItem[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [editingDraft, setEditingDraft] = useState<DraftItem | null>(null)
    const [isAddingNew, setIsAddingNew] = useState(false)
    const [page, setPage] = useState(1)
    const [pageSize, setPageSize] = useState(PAGE_SIZE)
    const [total, setTotal] = useState(0)
    const [filters, setFilters] = useState<DraftListQuery>(initialFilters)
    const [query, setQuery] = useState<DraftListQuery>(initialFilters)
    const [selectedIds, setSelectedIds] = useState<number[]>([])
    const [publishLoading, setPublishLoading] = useState(false)
    const [publishOneLoadingId, setPublishOneLoadingId] = useState<number | null>(null)
    const [deleteLoading, setDeleteLoading] = useState(false)
    const [batchDeleteOpen, setBatchDeleteOpen] = useState(false)
    const [draftColumnWidths, setDraftColumnWidths] = useState<Record<DraftColumnKey, number>>(DEFAULT_DRAFT_COLUMN_WIDTHS)
    const { toast } = useToast();
    const [companyTypes, setCompanyTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTypes, setRecruitmentTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTarget, setRecruitmentTarget] = useState<GlobalConfigItemValue[]>([]);
    const [processStates, setProcessStates] = useState<GlobalConfigItemValue[]>([]);
    const draftTableWidth = useMemo(
        () => DRAFT_TABLE_COLUMNS.reduce((total, column) => total + draftColumnWidths[column.key], 0),
        [draftColumnWidths]
    )
    const visibleDraftIds = useMemo(() => drafts.map((draft) => draft.id), [drafts])
    const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds])
    const selectedDrafts = useMemo(
        () => drafts.filter((draft) => selectedIdSet.has(draft.id)),
        [drafts, selectedIdSet]
    )
    const selectedPublishableIds = useMemo(
        () => selectedDrafts.filter((draft) => draft.toProcess !== 1).map((draft) => draft.id),
        [selectedDrafts]
    )
    const allVisibleSelected = visibleDraftIds.length > 0 && visibleDraftIds.every((id) => selectedIdSet.has(id))

    // AIDEV-NOTE: AI-GENERATED 查询按钮提交筛选，来源上下文切换时同步重置。
    useEffect(() => {
        setFilters(initialFilters); setQuery(initialFilters); setSelectedIds([]); setPage(1)
    }, [initialFilters])

    const fetchData = async (params: DraftListQuery = {}) => {
        setLoading(true)
        setError(null)
        try {
            const res: DraftListResponse = await fetchDraftList({ page, size: pageSize, ...query, ...params })
            setDrafts(res.list)
            setTotal(res.total)
        } catch (e: any) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
        getConfigValue('oc', 'CompanyTypeEnum').then(setCompanyTypes);
        getConfigValue('oc', 'RecruitmentTypeEnum').then(setRecruitmentTypes);
        getConfigValue('oc', 'RecruitmentTargetEnum').then(setRecruitmentTarget);
        getConfigValue('oc', 'DraftProcessEnum').then(setProcessStates);
        // eslint-disable-next-line
    }, [page, pageSize, query])

    useEffect(() => {
        setSelectedIds([])
    }, [page, pageSize, query])

    useEffect(() => {
        const nextActionWidth = drafts.some((draft) => draft.toProcess !== 1) ? 176 : 128
        setDraftColumnWidths((current) => (
            current.action === nextActionWidth ? current : { ...current, action: nextActionWidth }
        ))
    }, [drafts])

    const handleFilterChange = (key: keyof DraftListQuery, value: string) => {
        if (value == '-1') {
            value = ''
        }
        setFilters((prev) => ({ ...prev, [key]: value }))
    }

    const resetFilters = () => {
        setFilters(initialFilters)
        setQuery({...initialFilters})
        setSelectedIds([])
        setPage(1)
    }

    const handleDraftColumnResizeStart = useCallback((column: DraftColumnConfig, event: ReactMouseEvent<HTMLButtonElement>) => {
        event.preventDefault()
        event.stopPropagation()

        const startX = event.clientX
        const startWidth = draftColumnWidths[column.key]
        const previousCursor = document.body.style.cursor
        const previousUserSelect = document.body.style.userSelect

        const handleMove = (moveEvent: MouseEvent) => {
            const nextWidth = Math.min(
                column.maxWidth,
                Math.max(column.minWidth, Math.round(startWidth + moveEvent.clientX - startX))
            )
            setDraftColumnWidths((current) => ({
                ...current,
                [column.key]: nextWidth,
            }))
        }

        const handleUp = () => {
            document.body.style.cursor = previousCursor
            document.body.style.userSelect = previousUserSelect
            document.removeEventListener("mousemove", handleMove)
            document.removeEventListener("mouseup", handleUp)
        }

        document.body.style.cursor = "col-resize"
        document.body.style.userSelect = "none"
        document.addEventListener("mousemove", handleMove)
        document.addEventListener("mouseup", handleUp)
    }, [draftColumnWidths])

    const clearTaskDraftFilter = () => {
        const params = new URLSearchParams(searchParams.toString())
        params.delete("draftIds")
        params.delete("sourceTaskId")
        params.delete("sourceId")
        params.delete("runnerType")
        const query = params.toString()
        router.replace(query ? `/admin/drafts?${query}` : "/admin/drafts")
    }

    const handleDelete = async (id: number) => {
        try {
            await deleteDraft(id)
            setDrafts((current) => current.filter((draft) => draft.id !== id))
            setSelectedIds((current) => current.filter((selectedId) => selectedId !== id))
            toast({ title: "删除成功！" })
        } catch (err: any) {
            toast({ title: "删除失败！", description: err.message, variant: "destructive" })
        }
    }

    const handleBatchDelete = async () => {
        if (selectedIds.length === 0) return

        const ids = [...selectedIds]
        setDeleteLoading(true)
        try {
            const results = await Promise.allSettled(ids.map((id) => deleteDraft(id)))
            const successIds = ids.filter((_, index) => results[index].status === "fulfilled")
            const failedCount = ids.length - successIds.length

            if (successIds.length > 0) {
                const successIdSet = new Set(successIds)
                setDrafts((current) => current.filter((draft) => !successIdSet.has(draft.id)))
                setSelectedIds((current) => current.filter((id) => !successIdSet.has(id)))
            }

            if (failedCount > 0) {
                toast({
                    title: "部分草稿删除失败",
                    description: `成功删除 ${successIds.length} 条，失败 ${failedCount} 条`,
                    variant: "destructive",
                })
                return
            }

            setBatchDeleteOpen(false)
            toast({ title: "批量删除成功", description: `已删除 ${successIds.length} 条草稿` })
            await fetchData()
        } catch (err: any) {
            toast({ title: "批量删除失败", description: err?.message || "未知错误", variant: "destructive" })
        } finally {
            setDeleteLoading(false)
        }
    }

    const ifUrlValide = (url: string) => {
        const urlRegex = /^https?:\/\/.+|^$|^-$/;
        return urlRegex.test(url);
    }

    const validateUrl = (url: string) => {
        return ifUrlValide(url) ? url : "-";
    }

    const handleSave = async (draft: DraftItem): Promise<boolean> => {
        // 在保存时，需要校验链接的合法性
        const normalizedDraft = validateDraftUrls(draft) ?? draft
        const nextUpdateTime = new Date().toISOString().split("T")[0]
        setEditingDraft(normalizedDraft);

        if (isAddingNew) {
            setDrafts((current) => [
                ...current,
                { ...normalizedDraft, id: Date.now(), updateTime: nextUpdateTime },
            ])
            setIsAddingNew(false)
            setEditingDraft(null)
            return true
        } else {
            const savedDraft = { ...normalizedDraft, toProcess: 0, updateTime: nextUpdateTime }
            try {
                await updateDraft(savedDraft)
                setDrafts((current) =>
                    current.map((item) =>
                        item.id === savedDraft.id ? savedDraft : item
                    )
                )
                setEditingDraft(null)
                toast({ title: "保存成功！" })
                return true
            } catch (err: any) {
                toast({ title: "保存失败！", description: err.message, variant: "destructive" })
                return false
            }
        }
    }

    const validateDraftUrls = (draft: DraftItem | null): DraftItem | null => {
        if (!draft) return null;
        return {
            ...draft,
            relatedLink: validateUrl(draft.relatedLink),
            jobAnnouncement: validateUrl(draft.jobAnnouncement)
        };
    }

    const checkSelectAll = () => {
        return allVisibleSelected
    }

    const handleSelectAll = (checked: boolean) => {
        if (checked) {
            setSelectedIds(visibleDraftIds)
        } else {
            setSelectedIds([])
        }
    }
    const handleSelectOne = (id: number, checked: boolean) => {
        setSelectedIds(prev => checked ? Array.from(new Set([...prev, id])) : prev.filter(i => i !== id))
    }

    const handlePublishOne = async (id: number) => {
        setPublishOneLoadingId(id)
        try {
            await doPublish([id])
        } finally {
            setPublishOneLoadingId(null)
        }
    }

    const handlePublish = async () => {
        if (selectedPublishableIds.length === 0) {
            toast({ title: "没有可同步的草稿", description: "请选择状态为待更新的草稿后再同步" })
            return
        }
        setPublishLoading(true)
        try {
            const success = await doPublish(selectedPublishableIds)
            if (success) {
                setSelectedIds([])
            }
        } finally {
            setPublishLoading(false)
        }
    }

    const doPublish = async (ids: number[]): Promise<boolean> => {
        try {
            await batchPublishDrafts(ids)
            toast({ title: "发布成功！" })
            await fetchData() // 发布后刷新列表
            return true
        } catch (e: any) {
            toast({ title: "发布失败", description: e?.message || "未知错误", variant: "destructive" })
            return false
        }
    }

    return (
        <div className="min-h-screen bg-surface-muted">
            <TooltipProvider delayDuration={150}>
            <div className="mx-auto max-w-[1440px] px-6 py-5">
                <section className="rounded-lg border border-surface-border bg-white shadow-sm">
                    <div className="border-b border-surface-border px-5 py-4">
                        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                            <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                                <div className="relative">
                                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                                    <Input placeholder="公司名称" className="h-10 w-36 bg-white pl-9" value={filters.companyName || ''} onChange={e => handleFilterChange('companyName', e.target.value)} />
                                </div>
                                <Select value={filters.companyType || "-1"} onValueChange={value => handleFilterChange('companyType', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="公司类型" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部公司</SelectItem>
                                        {companyTypes.map(option => (
                                            <SelectItem key={option.intro as string} value={option.intro as string}>{option.intro}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <Select value={filters.recruitmentType || "-1"} onValueChange={value => handleFilterChange('recruitmentType', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="招聘类型" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部类型</SelectItem>
                                        {recruitmentTypes.map(type => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <Input placeholder="工作地点" className="h-10 w-32 bg-white" value={filters.jobLocation || ''} onChange={e => handleFilterChange('jobLocation', e.target.value)} />
                                <Input placeholder="岗位" className="h-10 w-32 bg-white" value={filters.position || ''} onChange={e => handleFilterChange('position', e.target.value)} />
                                <Select value={filters.toProcess || "-1"} onValueChange={value => handleFilterChange('toProcess', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="处理状态" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部状态</SelectItem>
                                        {processStates.map(option => (
                                            <SelectItem key={option.value as string} value={option.value as string}>{option.intro}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div className="flex shrink-0 items-center gap-2">
                                <Button variant="outline" className="h-10 gap-2" onClick={resetFilters}>
                                    <X className="h-4 w-4" />
                                    重置
                                </Button>
                                <Button variant="outline" className="h-10 gap-2" onClick={() => { setPage(1); setQuery({...filters}) }} disabled={loading}>
                                    {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                                    查询
                                </Button>
                                <Button
                                    variant="destructive"
                                    className="h-10 gap-2"
                                    onClick={() => setBatchDeleteOpen(true)}
                                    disabled={deleteLoading || selectedIds.length === 0}
                                >
                                    {deleteLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                                    批量删除
                                </Button>
                                <Button className="h-10" onClick={handlePublish} disabled={publishLoading || selectedPublishableIds.length === 0}>
                                    {publishLoading ? "发布中..." : "批量发布"}
                                </Button>
                            </div>
                        </div>
                    </div>

                    <div className="space-y-4 p-5">
                        {(linkedDraftIds || sourceId || runnerType) && (
                            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700">
                                <FileSearch className="h-4 w-4" />
                                <span className="font-medium">
                                    {sourceTaskId ? `来自采集任务 #${sourceTaskId}` : sourceId ? `来自采集源 #${sourceId}` : runnerType === "agent" ? "Agent 自动作业草稿" : "草稿过滤视图"}
                                </span>
                                {linkedDraftIds ? (
                                    <>
                                        <span>仅显示 {linkedDraftIdList.length} 条关联草稿</span>
                                        {linkedDraftIdList.slice(0, 8).map((id) => (
                                            <span key={id} className="rounded-md bg-white px-1.5 py-0.5 font-medium text-blue-700">
                                                #{id}
                                            </span>
                                        ))}
                                        {linkedDraftIdList.length > 8 && <span>+{linkedDraftIdList.length - 8}</span>}
                                    </>
                                ) : (
                                    <span>仅显示当前来源的草稿</span>
                                )}
                                <Button
                                    size="sm"
                                    variant="outline"
                                    className="ml-auto h-8 border-blue-200 bg-white text-blue-700 hover:bg-blue-50"
                                    onClick={clearTaskDraftFilter}
                                >
                                    <X className="h-3.5 w-3.5" />
                                    查看全部
                                </Button>
                            </div>
                        )}

                        <div className="overflow-x-auto rounded-lg border border-surface-border">
                            <Table className="table-fixed text-sm" style={{ width: "100%", minWidth: draftTableWidth }}>
                                <colgroup>
                                    {DRAFT_TABLE_COLUMNS.map((column) => (
                                        <col key={column.key} style={{ width: draftColumnWidths[column.key] }} />
                                    ))}
                                </colgroup>
                                <TableHeader className="bg-slate-50">
                                    <TableRow className="hover:bg-slate-50">
                                        {DRAFT_TABLE_COLUMNS.map((column, index) => (
                                            <ResizableDraftHead
                                                key={column.key}
                                                column={column}
                                                resizeColumn={index > 0 && !column.sticky ? DRAFT_TABLE_COLUMNS[index - 1] : undefined}
                                                width={draftColumnWidths[column.key]}
                                                onResizeStart={handleDraftColumnResizeStart}
                                            >
                                                {column.key === "select" ? (
                                                    <input
                                                        type="checkbox"
                                                        checked={checkSelectAll()}
                                                        onChange={e => handleSelectAll(e.target.checked)}
                                                    />
                                                ) : undefined}
                                            </ResizableDraftHead>
                                        ))}
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {loading ? (
                                        <TableRow><TableCell colSpan={DRAFT_TABLE_COLUMNS.length} className="h-32 text-center text-content-tertiary"><span className="inline-flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" />加载草稿中</span></TableCell></TableRow>
                                    ) : error ? (
                                        <TableRow><TableCell colSpan={DRAFT_TABLE_COLUMNS.length} className="h-32 text-center text-rose-600">{error}</TableCell></TableRow>
                                    ) : drafts?.length === 0 ? (
                                        <TableRow><TableCell colSpan={DRAFT_TABLE_COLUMNS.length} className="h-32 text-center text-content-tertiary">暂无草稿</TableCell></TableRow>
                                    ) : (
                                        drafts.map((draft) => (
                                            <TableRow key={draft.id} className="bg-white">
                                                <TableCell className="whitespace-nowrap text-center"><input type="checkbox" checked={selectedIdSet.has(draft.id)} onChange={e => handleSelectOne(draft.id, e.target.checked)} /></TableCell>
                                                <TableCell className="whitespace-nowrap text-content-tertiary">{compactText(draft.lastUpdatedTime)}</TableCell>
                                                <TableCell className="whitespace-normal break-all font-medium leading-6 text-content-primary" title={draft.companyName}>{compactText(draft.companyName)}<div className="mt-1 text-xs font-normal text-slate-400">{compactText(draft.companyType)} · {normalizeRecruitmentTargetForDisplay(draft)}</div></TableCell>
                                                <TableCell className="text-content-secondary">
                                                    <ValueTooltip value={draft.jobLocation}>
                                                        <span className="block truncate">{compactText(draft.jobLocation)}</span>
                                                    </ValueTooltip>
                                                </TableCell>
                                                <TableCell className="max-w-[360px] truncate break-all text-content-secondary" title={draft.position}>{draft.position}</TableCell>
                                                <TableCell className="whitespace-nowrap"><Badge variant={draft.state == 1 ? "default" : "outline"}>{draft.state == 1 ? '已发布' : '未发布'}</Badge><p className="mt-1 text-xs text-slate-400">{draft.toProcess == 1 ? '已处理' : '待处理'}</p></TableCell>
                                                <TableCell className={STICKY_ACTION_CELL_CLASS}>
                                                    <div className="flex justify-end gap-1">
                                                        {draft.toProcess != 1 && (
                                                            <Button size="sm" className="h-8 px-2 bg-blue-600 text-white hover:bg-blue-700" onClick={() => handlePublishOne(draft.id)} disabled={publishOneLoadingId === draft.id}>
                                                                {publishOneLoadingId === draft.id ? "发布中" : "发布"}
                                                            </Button>
                                                        )}

                                                        <Button size="sm" variant="outline" className="h-8 px-2" onClick={() => setEditingDraft(draft)}>
                                                            审核
                                                        </Button>
                                                        <Button size="sm" variant="ghost" className="h-8 px-2 text-slate-400 hover:bg-red-50 hover:text-red-600" onClick={() => handleDelete(draft.id)}>
                                                            删除
                                                        </Button>
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>
                        </div>

                        <TablePagination
                            page={page}
                            pageSize={pageSize}
                            total={total}
                            currentCount={drafts.length}
                            loading={loading}
                            totalLabel="条草稿"
                            onPageChange={setPage}
                            onPageSizeChange={(size) => {
                                setPageSize(size)
                                setPage(1)
                            }}
                        />
                    </div>
                </section>

            {/* 编辑/新增弹窗保持原样 */}
            {editingDraft && (
                <Dialog
                    open={true}
                    onOpenChange={() => {
                        setEditingDraft(null)
                        setIsAddingNew(false)
                    }}
                >
                    <DialogContent className="max-w-6xl max-h-[92vh] overflow-y-auto rounded-2xl p-6 sm:p-7">
                        <DialogHeader>
                            <DialogTitle>{isAddingNew ? "添加新草稿" : "审核招聘草稿"}</DialogTitle>
                        </DialogHeader>
                        <div className="grid items-start gap-6 py-4 lg:grid-cols-[340px_1fr]">
                            <DraftEvidence draft={drafts.find(item => item.id === editingDraft.id) || editingDraft} />
                            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 [&_label]:mb-2 [&_label]:block">
                            <div>
                                <label className="text-sm font-medium">草稿编号</label>
                                <Input value={editingDraft.id} disabled />
                            </div>
                            <div>
                                <label className="text-sm font-medium">公司名称</label>
                                <Input value={editingDraft.companyName} onChange={e => setEditingDraft({ ...editingDraft, companyName: e.target.value })} />
                            </div>
                            <div>
                                <label className={companyTypes.some(item => item.intro === editingDraft.companyType) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>公司类型</label>
                                <Select value={editingDraft?.companyType || ""} onValueChange={(v) => setEditingDraft({ ...editingDraft, companyType: v })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择公司类型" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {companyTypes.map((type) => (
                                            <SelectItem value={type.intro as string} key={type.intro as string}>{type.intro}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-sm font-medium">所属行业</label>
                                <Input value={editingDraft.companyIndustry} onChange={e => setEditingDraft({ ...editingDraft, companyIndustry: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">工作地点</label>
                                <Input value={editingDraft.jobLocation} onChange={e => setEditingDraft({ ...editingDraft, jobLocation: e.target.value })} />
                            </div>
                            <div>
                                <label className={recruitmentTypes.some(item => item.intro === editingDraft.recruitmentType) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>招聘类型</label>
                                <Select value={editingDraft.recruitmentType || ""} onValueChange={(value) => setEditingDraft({ ...editingDraft, recruitmentType: value })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择招聘类型" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {recruitmentTypes.map((type) => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className={recruitmentTarget.some(item => item.intro === editingDraft.recruitmentTarget) ? "text-sm font-medium" : "text-sm font-medium text-red-500"}>招聘对象</label>
                                <Select value={editingDraft.recruitmentTarget || ""}
                                    onValueChange={(value) => setEditingDraft({ ...editingDraft, recruitmentTarget: value })}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="请选择招聘对象" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {recruitmentTarget.map((type) => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-sm font-medium">岗位</label>
                                <Textarea rows={4} value={editingDraft.position} onChange={e => setEditingDraft({ ...editingDraft, position: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">招聘时间</label>
                                <Input value={editingDraft.lastUpdatedTime} onChange={e => setEditingDraft({ ...editingDraft, lastUpdatedTime: e.target.value })} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">投递链接</label>
                                <Input value={editingDraft.relatedLink} onChange={e => {
                                    const value = e.target.value;
                                    setEditingDraft({ ...editingDraft, relatedLink: value });
                                }} className={!ifUrlValide(editingDraft.relatedLink) ? "border-red-500" : ""} />
                            </div>
                            <div>
                                <label className="text-sm font-medium">公告链接</label>
                                <Input value={editingDraft.jobAnnouncement} onChange={e => {
                                    const value = e.target.value;
                                    setEditingDraft({ ...editingDraft, jobAnnouncement: value });
                                }} className={!ifUrlValide(editingDraft.jobAnnouncement) ? "border-red-500" : ""} />
                            </div>
                            <div className="w-full">
                                <label className="text-sm font-medium">内推码</label>
                                <Input value={editingDraft.internalReferralCode} onChange={e => setEditingDraft({ ...editingDraft, internalReferralCode: e.target.value })} />
                            </div>
                            <div className="w-full">
                                <label className="text-sm font-medium">备注</label>
                                <Textarea rows={3} value={editingDraft.remarks} onChange={e => setEditingDraft({ ...editingDraft, remarks: e.target.value })} />
                            </div>
                        </div>
                        </div><div className="sticky bottom-0 flex justify-end gap-2 border-t border-slate-100 bg-white pt-4">
                            <Button
                                variant="outline"
                                onClick={() => {
                                    setEditingDraft(null)
                                    setIsAddingNew(false)
                                }}
                            >
                                取消
                            </Button>
                            <Button variant="outline" onClick={() => handleSave(editingDraft)}>保存草稿</Button>
                            <Button className="bg-blue-600 hover:bg-blue-700 text-white" onClick={async () => {
                                // 先保存，然后再发布
                                const saved = await handleSave(editingDraft)
                                if (saved) {
                                    await handlePublishOne(editingDraft.id)
                                    setEditingDraft(null)
                                }
                            }} disabled={publishOneLoadingId === editingDraft.id}>
                                {publishOneLoadingId === editingDraft.id ? "发布中..." : "发布"}
                            </Button>
                        </div>
                    </DialogContent>
                </Dialog>
            )}
            <AlertDialog open={batchDeleteOpen} onOpenChange={(open) => !deleteLoading && setBatchDeleteOpen(open)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>批量删除草稿</AlertDialogTitle>
                        <AlertDialogDescription>
                            确定删除已选的 {selectedIds.length} 条草稿吗？删除后需要重新采集或手动录入才能恢复。
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={deleteLoading}>取消</AlertDialogCancel>
                        <AlertDialogAction
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            disabled={deleteLoading}
                            onClick={(event) => {
                                event.preventDefault()
                                handleBatchDelete()
                            }}
                        >
                            {deleteLoading ? "删除中..." : "确认删除"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
            </div>
            </TooltipProvider>
        </div>
    )
}
