"use client"

import Link from "next/link"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import type { MouseEvent as ReactMouseEvent } from "react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  AlertCircle,
  CheckCircle2,
  ClipboardList,
  Copy,
  ExternalLink,
  FileSpreadsheet,
  FileText,
  ImageIcon,
  Link2,
  Loader2,
  Play,
  RefreshCcw,
  Search,
  UploadCloud,
  X,
  type LucideIcon,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TablePagination } from "@/components/ui/table-pagination"
import { Textarea } from "@/components/ui/textarea"
import {
  getAdminLlmProviders,
  devWxLogin,
  fetchTaskList,
  reRunTask,
  submitAIEntry,
  type AdminLlmProviderConfig,
  type GlobalConfigItemValue,
  type TaskListItem,
  type UserModelConfig,
} from "@/lib/api"
import { getConfigValue } from "@/lib/config"
import { cn } from "@/lib/utils"
import { useToast } from "@/hooks/use-toast"
import { useLoginUser } from "@/hooks/useLoginUser"

type SubmitTone = "success" | "error"

interface TaskQueryState {
  page: number
  size: number
  taskId: string
  model: string
  type: string
  state: string
  sourceId: string
  runnerType: string
}

interface SubmitStatus {
  tone: SubmitTone
  title: string
  description?: string
}

interface TypeVisual {
  icon: LucideIcon
  label: string
  className: string
}

interface LlmModelOption {
  value: string
  provider: string
  providerName: string
  modelName: string
  type: string
  billingType?: string
  label: string
  description: string
}

interface TaskDraftResult {
  parsed: boolean
  raw: string
  msg: string
  insertDraftIds: number[]
  updateDraftIds: number[]
  unchangedDraftIds: number[]
}

interface StoredUserInfo {
  userId: number
  role: number
  nickname?: string
  avatar?: string
  timestamp?: number
}

interface EntryPageProps {
  forcedTab?: "entry" | "tasks"
  forcedRunnerType?: string
  taskEmptyText?: string
  showTaskContextClear?: boolean
}

type TaskColumnKey = "id" | "type" | "model" | "state" | "input" | "result" | "updatedAt" | "action"

interface TaskColumnConfig {
  key: TaskColumnKey
  label: string
  minWidth: number
  maxWidth: number
  align?: "left" | "right"
  sticky?: boolean
}

const PAGE_SIZE = 10
const ALL_VALUE = "-1"
const FILE_TYPE_VALUES = new Set(["4", "5", "6"])
const FALLBACK_TASK_TYPE_OPTIONS: GlobalConfigItemValue[] = [
  { key: "GatherTargetTypeEnum", value: "1", intro: "html文本" },
  { key: "GatherTargetTypeEnum", value: "2", intro: "纯文本" },
  { key: "GatherTargetTypeEnum", value: "3", intro: "http链接" },
  { key: "GatherTargetTypeEnum", value: "4", intro: "excel文件" },
  { key: "GatherTargetTypeEnum", value: "5", intro: "csv文件" },
  { key: "GatherTargetTypeEnum", value: "6", intro: "图片" },
]

const DEFAULT_TASK_COLUMN_WIDTHS: Record<TaskColumnKey, number> = {
  id: 112,
  type: 150,
  model: 165,
  state: 118,
  input: 240,
  result: 190,
  updatedAt: 160,
  action: 124,
}

const TASK_TABLE_COLUMNS: TaskColumnConfig[] = [
  { key: "id", label: "ID", minWidth: 96, maxWidth: 180 },
  { key: "type", label: "类型", minWidth: 124, maxWidth: 220 },
  { key: "model", label: "模型", minWidth: 132, maxWidth: 240 },
  { key: "state", label: "状态", minWidth: 104, maxWidth: 170 },
  { key: "input", label: "输入", minWidth: 220, maxWidth: 520 },
  { key: "result", label: "草稿结果", minWidth: 160, maxWidth: 280 },
  { key: "updatedAt", label: "更新时间", minWidth: 140, maxWidth: 220 },
  { key: "action", label: "操作", minWidth: 110, maxWidth: 170, align: "right", sticky: true },
]

const STICKY_ACTION_HEAD_CLASS =
  "border-l border-slate-200 bg-slate-100/95 shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"

const STICKY_ACTION_CELL_CLASS =
  "sticky right-0 z-10 border-l border-slate-200 bg-slate-50/95 text-right shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"

const typeVisuals: Record<string, TypeVisual> = {
  "1": {
    icon: FileText,
    label: "HTML",
    className: "border-amber-200 bg-amber-50 text-amber-700",
  },
  "2": {
    icon: FileText,
    label: "文本",
    className: "border-slate-200 bg-slate-50 text-slate-700",
  },
  "3": {
    icon: Link2,
    label: "链接",
    className: "border-blue-200 bg-blue-50 text-blue-700",
  },
  "4": {
    icon: FileSpreadsheet,
    label: "Excel",
    className: "border-orange-200 bg-orange-50 text-orange-700",
  },
  "5": {
    icon: FileSpreadsheet,
    label: "CSV",
    className: "border-emerald-200 bg-emerald-50 text-emerald-700",
  },
  "6": {
    icon: ImageIcon,
    label: "图片",
    className: "border-rose-200 bg-rose-50 text-rose-700",
  },
}

const stateClass: Record<number, string> = {
  0: "border-slate-200 bg-slate-50 text-slate-600",
  1: "border-blue-200 bg-blue-50 text-blue-700",
  2: "border-emerald-200 bg-emerald-50 text-emerald-700",
  3: "border-rose-200 bg-rose-50 text-rose-700",
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    const message = error.message?.trim()
    if (message && message !== "???") {
      return message
    }
  }
  return "接口返回异常，请确认已使用管理员账号登录并检查后端服务"
}

function formatDateTimeStr(value?: string | number | null) {
  if (value === undefined || value === null || value === "") {
    return "-"
  }

  const normalized =
    typeof value === "string" && /^\d+$/.test(value) ? Number(value) : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`
}

function getOptionLabel(options: GlobalConfigItemValue[], value?: string | number | null) {
  const match = options.find((option) => String(option.value) === String(value))
  return match?.intro ? String(match.intro) : value === undefined || value === null ? "-" : String(value)
}

function getModelTypeLabel(type?: string) {
  if (type === "TEXT") return "文本"
  if (type === "VISION") return "视觉"
  return type || "-"
}

function getBillingTypeLabel(type?: string) {
  if (type === "FREE") return "免费"
  if (type === "PAID") return "付费"
  return "未标注"
}

function buildLlmModelOptions(providers?: Record<string, AdminLlmProviderConfig>) {
  if (!providers) {
    return []
  }

  return Object.entries(providers).flatMap(([provider, config]) => {
    const providerName = config.displayName?.trim() || provider
    return (config.models || [])
      .filter((model: UserModelConfig) => model.name && (model.type === "TEXT" || model.type === "VISION"))
      .map((model: UserModelConfig) => {
        const modelName = model.name.trim()
        const value = `${provider}#${modelName}`
        const modelType = model.type || "TEXT"
        return {
          value,
          provider,
          providerName,
          modelName,
          type: modelType,
          billingType: model.billingType,
          label: `${providerName} / ${modelName}`,
          description: `${provider} · ${getModelTypeLabel(modelType)} · ${getBillingTypeLabel(model.billingType)}`,
        }
      })
  })
}

function getModelOptionLabel(options: LlmModelOption[], value?: string | number | null) {
  const stringValue = value === undefined || value === null ? "" : String(value)
  const match = options.find((option) => option.value === stringValue)
  if (match) {
    return match.label
  }
  if (stringValue.includes("#")) {
    const [provider, modelName] = stringValue.split("#", 2)
    return `${provider} / ${modelName}`
  }
  return stringValue || "-"
}

function getTypeVisual(value?: string | number | null) {
  return typeVisuals[String(value)] ?? {
    icon: ClipboardList,
    label: "任务",
    className: "border-slate-200 bg-white text-slate-600",
  }
}

function GatherTypeOption({ option }: { option: GlobalConfigItemValue }) {
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

function getAcceptByType(aiType: string) {
  if (aiType === "6") return "image/*"
  if (aiType === "5") return ".csv"
  if (aiType === "4") return ".xls,.xlsx"
  return ""
}

function getInputPlaceholder(aiType: string) {
  if (aiType === "3") {
    return "粘贴招聘官网、校招公告、公众号文章等职位链接"
  }
  if (aiType === "1") {
    return "粘贴网页 HTML 或包含职位信息的富文本"
  }
  return "粘贴招聘公告、岗位 JD、内推信息或其他职位线索"
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

function DraftSummaryBadge({ label, count, tone }: { label: string; count: number; tone: "insert" | "update" | "same" }) {
  if (count === 0) {
    return null
  }

  const toneClass = tone === "insert"
    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
    : tone === "update"
      ? "border-blue-200 bg-blue-50 text-blue-700"
      : "border-slate-200 bg-slate-50 text-slate-600"

  return (
    <Badge variant="outline" className={cn("h-6 rounded-md px-2", toneClass)}>
      {label} {count}
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
    <div className="min-w-[160px] space-y-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <DraftSummaryBadge label="新增" count={result.insertDraftIds.length} tone="insert" />
        <DraftSummaryBadge label="更新" count={result.updateDraftIds.length} tone="update" />
        <DraftSummaryBadge label="无变化" count={result.unchangedDraftIds.length} tone="same" />
        {draftIds.length === 0 && (
          <span className="line-clamp-2 text-sm text-content-secondary" title={result.msg || result.raw}>
            {result.msg && result.msg !== "success" ? result.msg : "未生成草稿"}
          </span>
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

function ResizableTaskHead({
  column,
  resizeColumn,
  width,
  onResizeStart,
}: {
  column: TaskColumnConfig
  resizeColumn?: TaskColumnConfig
  width: number
  onResizeStart: (column: TaskColumnConfig, event: ReactMouseEvent<HTMLButtonElement>) => void
}) {
  const isRightAligned = column.align === "right"

  return (
    <TableHead
      style={{ width, minWidth: width, maxWidth: width }}
      className={cn(
        "relative select-none bg-slate-50 text-content-secondary",
        resizeColumn && "pl-6",
        isRightAligned && "text-right",
        column.sticky && cn("sticky right-0 z-20", STICKY_ACTION_HEAD_CLASS)
      )}
    >
      <span className="block truncate">{column.label}</span>
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

function buildTaskParams(taskQuery: TaskQueryState) {
  return {
    page: taskQuery.page,
    size: taskQuery.size,
    taskId: taskQuery.taskId ? Number(taskQuery.taskId) : undefined,
    model: taskQuery.model !== ALL_VALUE ? taskQuery.model : undefined,
    type: taskQuery.type !== ALL_VALUE ? Number(taskQuery.type) : undefined,
    state: taskQuery.state !== ALL_VALUE ? Number(taskQuery.state) : undefined,
    sourceId: taskQuery.sourceId ? Number(taskQuery.sourceId) : undefined,
    runnerType: taskQuery.runnerType || undefined,
  }
}

function getStoredUserInfo(): StoredUserInfo | null {
  if (typeof window === "undefined") {
    return null
  }

  const raw = localStorage.getItem("oc-user")
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as StoredUserInfo
  } catch {
    return null
  }
}

function isLocalhost() {
  if (typeof window === "undefined") {
    return false
  }
  return ["localhost", "127.0.0.1", "::1"].includes(window.location.hostname)
}

function EntryContent({
  forcedTab,
  forcedRunnerType,
  taskEmptyText = "暂无采集任务",
  showTaskContextClear = true,
}: EntryPageProps = {}) {
  // AIDEV-NOTE: AI-GENERATED admin entry polish
  const router = useRouter()
  const searchParams = useSearchParams()
  const requestedTab = forcedTab ?? (searchParams.get("tab") === "tasks" ? "tasks" : "entry")
  const requestedSourceId = searchParams.get("sourceId") ?? ""
  const requestedRunnerType = forcedRunnerType ?? searchParams.get("runnerType") ?? ""
  const taskRunnerType = requestedTab === "tasks" ? requestedRunnerType || "admin" : requestedRunnerType
  const [aiType, setAiType] = useState("")
  const [aiInput, setAiInput] = useState("")
  const [aiLoading, setAiLoading] = useState(false)
  const [submitStatus, setSubmitStatus] = useState<SubmitStatus | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [selectedImageUrl, setSelectedImageUrl] = useState<string | null>(null)
  const [previewImg, setPreviewImg] = useState<string | null>(null)
  const [taskQuery, setTaskQuery] = useState<TaskQueryState>({
    page: 1,
    size: PAGE_SIZE,
    taskId: "",
    model: ALL_VALUE,
    type: ALL_VALUE,
    state: ALL_VALUE,
    sourceId: requestedSourceId,
    runnerType: taskRunnerType,
  })
  const [taskList, setTaskList] = useState<TaskListItem[]>([])
  const [taskTotal, setTaskTotal] = useState(0)
  const [taskLoading, setTaskLoading] = useState(false)
  const [taskError, setTaskError] = useState<string | null>(null)
  const [reRunLoadingId, setReRunLoadingId] = useState<number | null>(null)
  const [taskColumnWidths, setTaskColumnWidths] = useState<Record<TaskColumnKey, number>>(DEFAULT_TASK_COLUMN_WIDTHS)
  const [companyTypeOptions, setCompanyTypeOptions] = useState<GlobalConfigItemValue[]>([])
  const [recruitmentTypeOptions, setRecruitmentTypeOptions] = useState<GlobalConfigItemValue[]>([])
  const [recruitmentTargetOptions, setRecruitmentTargetOptions] = useState<GlobalConfigItemValue[]>([])
  const [aiModelOptions, setAiModelOptions] = useState<LlmModelOption[]>([])
  const [taskStateOptions, setTaskStateOptions] = useState<GlobalConfigItemValue[]>([])
  const [taskTypeOptions, setTaskTypeOptions] = useState<GlobalConfigItemValue[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { toast } = useToast()
  const { setUserInfo } = useLoginUser()

  const isFileType = FILE_TYPE_VALUES.has(aiType)
  const selectedTypeVisual = useMemo(() => getTypeVisual(aiType), [aiType])
  const SelectedTypeIcon = selectedTypeVisual.icon
  const taskTableWidth = useMemo(
    () => TASK_TABLE_COLUMNS.reduce((total, column) => total + taskColumnWidths[column.key], 0),
    [taskColumnWidths]
  )
  const canSubmit =
    Boolean(aiType) &&
    (isFileType ? Boolean(selectedFile) : aiInput.trim().length > 0) &&
    !aiLoading

  useEffect(() => {
    setTaskQuery((current) => ({
      ...current,
      page: 1,
      sourceId: requestedSourceId,
      runnerType: taskRunnerType,
    }))
  }, [requestedSourceId, requestedTab, taskRunnerType])

  const ensureAdminSession = useCallback(async () => {
    if (typeof window === "undefined") {
      return
    }

    const token = localStorage.getItem("oc-token")
    const storedUser = getStoredUserInfo()
    if (token && storedUser?.role === 3) {
      return
    }

    if (!isLocalhost()) {
      throw new Error("请先使用管理员账号登录")
    }

    const login = await devWxLogin("admin")
    const nextUser: StoredUserInfo = {
      userId: login.user.userId,
      role: login.user.role,
      nickname: login.user.displayName,
      avatar: login.user.avatar,
      timestamp: Date.now(),
    }
    localStorage.setItem("oc-token", login.token)
    localStorage.setItem("oc-user", JSON.stringify(nextUser))
    setUserInfo(nextUser)
  }, [setUserInfo])

  useEffect(() => {
    let active = true

    ensureAdminSession()
      .then(() => Promise.all([
        getConfigValue("gather", "GatherTargetTypeEnum").then((value) => {
          if (active) setTaskTypeOptions(value.length > 0 ? value : FALLBACK_TASK_TYPE_OPTIONS)
        }),
        getConfigValue("gather", "GatherTaskStateEnum").then((value) => {
          if (active) setTaskStateOptions(value)
        }),
        getConfigValue("oc", "CompanyTypeEnum").then((value) => {
          if (active) setCompanyTypeOptions(value)
        }),
        getConfigValue("oc", "RecruitmentTypeEnum").then((value) => {
          if (active) setRecruitmentTypeOptions(value)
        }),
        getConfigValue("oc", "RecruitmentTargetEnum").then((value) => {
          if (active) setRecruitmentTargetOptions(value)
        }),
        getAdminLlmProviders().then((data) => {
          if (active) setAiModelOptions(buildLlmModelOptions(data.providers))
        }),
      ]))
      .catch((error: unknown) => {
        if (active) setTaskError(getErrorMessage(error))
      })

    return () => {
      active = false
    }
  }, [ensureAdminSession])

  useEffect(() => {
    if (taskTypeOptions.length > 0 && !aiType) {
      setAiType(String(taskTypeOptions[0].value))
    }
  }, [aiType, taskTypeOptions])

  useEffect(() => {
    if (selectedFile && aiType === "6" && selectedFile.type.startsWith("image/")) {
      const objectUrl = URL.createObjectURL(selectedFile)
      setSelectedImageUrl(objectUrl)
      return () => URL.revokeObjectURL(objectUrl)
    }
    setSelectedImageUrl(null)
    return undefined
  }, [aiType, selectedFile])

  useEffect(() => {
    function handlePaste(event: ClipboardEvent) {
      if (!isFileType) return

      const clipboardFile = Array.from(event.clipboardData?.items ?? [])
        .find((item) => item.kind === "file")
        ?.getAsFile()
      if (clipboardFile) {
        setSelectedFile(clipboardFile)
        return
      }

      const fallbackFile = event.clipboardData?.files?.[0]
      if (fallbackFile) {
        setSelectedFile(fallbackFile)
      }
    }

    window.addEventListener("paste", handlePaste)
    return () => window.removeEventListener("paste", handlePaste)
  }, [isFileType])

  useEffect(() => {
    if (!isFileType) {
      setSelectedFile(null)
    }
  }, [isFileType])

  useEffect(() => {
    if (requestedTab !== "tasks") return

    let active = true
    setTaskLoading(true)
    setTaskError(null)

    ensureAdminSession()
      .then(() => fetchTaskList(buildTaskParams(taskQuery)))
      .then((res) => {
        if (!active) return
        setTaskList(res.list || [])
        setTaskTotal(res.total || 0)
      })
      .catch((error: unknown) => {
        if (!active) return
        const message = getErrorMessage(error)
        setTaskError(message)
      })
      .finally(() => {
        if (active) {
          setTaskLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [requestedTab, taskQuery, toast])

  const refreshTasks = () => {
    setTaskQuery((current) => ({ ...current }))
  }

  const resetTaskFilters = () => {
    setTaskQuery({
      page: 1,
      size: PAGE_SIZE,
      taskId: "",
      model: ALL_VALUE,
      type: ALL_VALUE,
      state: ALL_VALUE,
      sourceId: requestedSourceId,
      runnerType: taskRunnerType,
    })
  }

  const handleTaskColumnResizeStart = useCallback((column: TaskColumnConfig, event: ReactMouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()

    const startX = event.clientX
    const startWidth = taskColumnWidths[column.key]
    const previousCursor = document.body.style.cursor
    const previousUserSelect = document.body.style.userSelect

    const handleMove = (moveEvent: MouseEvent) => {
      const nextWidth = Math.min(
        column.maxWidth,
        Math.max(column.minWidth, Math.round(startWidth + moveEvent.clientX - startX))
      )
      setTaskColumnWidths((current) => ({
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
  }, [taskColumnWidths])

  const handleFileChange = (file?: File | null) => {
    if (!file) return
    setSelectedFile(file)
    setSubmitStatus(null)
  }

  const handleAISubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitStatus(null)

    if (!canSubmit) {
      setSubmitStatus({
        tone: "error",
        title: isFileType ? "请先选择要解析的文件" : "请输入要采集的职位线索",
      })
      return
    }

    setAiLoading(true)
    try {
      await ensureAdminSession()
      await submitAIEntry({
        content: aiInput,
        type: aiType,
        file: isFileType ? selectedFile : null,
      })
      setSubmitStatus({
        tone: "success",
        title: "已加入采集任务队列",
        description: "任务创建后可在任务队列中查看状态并重跑。",
      })
      setAiInput("")
      setSelectedFile(null)
      setTaskQuery((current) => ({ ...current, page: 1 }))
    } catch (error: unknown) {
      setSubmitStatus({
        tone: "error",
        title: "提交失败",
        description: getErrorMessage(error),
      })
    } finally {
      setAiLoading(false)
    }
  }

  const handleReRun = async (taskId: number) => {
    setReRunLoadingId(taskId)
    try {
      await reRunTask(taskId)
      toast({ title: "任务已重新加入队列" })
      refreshTasks()
    } catch (error: unknown) {
      toast({
        title: "重跑失败",
        description: getErrorMessage(error),
        variant: "destructive",
      })
    } finally {
      setReRunLoadingId(null)
    }
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-5">
        {requestedTab === "entry" ? (
            <form onSubmit={handleAISubmit} className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
              <section className="rounded-lg border border-surface-border bg-white shadow-sm">
                <div className="border-b border-surface-border px-5 py-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h2 className="text-base font-semibold text-content-primary">新建采集</h2>
                      <p className="mt-1 text-sm text-content-tertiary">选择数据类型后提交到异步采集任务，模型由系统自动匹配。</p>
                    </div>
                    <Button type="submit" disabled={!canSubmit} className="h-10 gap-2 bg-blue-600 px-5 hover:bg-blue-700">
                      {aiLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                      {aiLoading ? "提交中" : "提交任务"}
                    </Button>
                  </div>
                </div>

                <div className="space-y-5 p-5">
                  <div className="max-w-md space-y-2">
                      <label className="text-sm font-medium text-content-secondary">采集类型</label>
                      <Select value={aiType} onValueChange={setAiType}>
                        <SelectTrigger className="h-11 bg-white">
                          <SelectValue placeholder="请选择采集类型" />
                        </SelectTrigger>
                        <SelectContent>
                          {taskTypeOptions.map((option) => (
                            <SelectItem key={String(option.value)} value={String(option.value)}>
                              <GatherTypeOption option={option} />
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-content-secondary">{isFileType ? "文件" : "输入内容"}</label>
                    {isFileType ? (
                      <div
                        className={cn(
                          "flex min-h-[300px] cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed bg-slate-50 px-6 py-8 text-center transition",
                          selectedFile
                            ? "border-blue-200 bg-blue-50/60"
                            : "border-slate-300 hover:border-blue-300 hover:bg-blue-50/40"
                        )}
                        onClick={() => fileInputRef.current?.click()}
                        onDrop={(event) => {
                          event.preventDefault()
                          handleFileChange(event.dataTransfer.files?.[0])
                        }}
                        onDragOver={(event) => event.preventDefault()}
                      >
                        <input
                          type="file"
                          ref={fileInputRef}
                          className="hidden"
                          accept={getAcceptByType(aiType)}
                          onChange={(event) => handleFileChange(event.target.files?.[0])}
                        />
                        {selectedFile ? (
                          <div className="flex w-full max-w-xl flex-col items-center gap-4">
                            {selectedImageUrl ? (
                              <img
                                src={selectedImageUrl}
                                alt="预览"
                                className="max-h-52 max-w-full rounded-md border border-surface-border bg-white object-contain shadow-sm"
                                onClick={(event) => {
                                  event.stopPropagation()
                                  setPreviewImg(selectedImageUrl)
                                }}
                              />
                            ) : (
                              <div className="flex h-20 w-20 items-center justify-center rounded-lg border border-blue-100 bg-white text-blue-600 shadow-sm">
                                <SelectedTypeIcon className="h-9 w-9" />
                              </div>
                            )}
                            <div className="max-w-full rounded-md border border-blue-100 bg-white px-4 py-3 shadow-sm">
                              <div className="flex items-center justify-center gap-2 text-sm font-medium text-content-primary">
                                <span className="truncate">{selectedFile.name}</span>
                                <Button
                                  type="button"
                                  size="icon"
                                  variant="ghost"
                                  className="h-7 w-7 text-content-tertiary hover:text-content-primary"
                                  title="清除附件"
                                  onClick={(event) => {
                                    event.stopPropagation()
                                    setSelectedFile(null)
                                  }}
                                >
                                  <X className="h-4 w-4" />
                                </Button>
                              </div>
                              <p className="mt-1 text-xs text-content-tertiary">
                                {(selectedFile.size / 1024).toFixed(1)} KB
                              </p>
                            </div>
                          </div>
                        ) : (
                          <div className="flex max-w-md flex-col items-center">
                            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-lg border border-blue-100 bg-white text-blue-600 shadow-sm">
                              <UploadCloud className="h-8 w-8" />
                            </div>
                            <div className="text-lg font-semibold text-content-primary">
                              上传{selectedTypeVisual.label}文件
                            </div>
                            <p className="mt-2 text-sm leading-6 text-content-tertiary">
                              点击选择文件，或直接拖入当前区域；图片类型也支持粘贴截图。
                            </p>
                          </div>
                        )}
                      </div>
                    ) : (
                      <Textarea
                        className="min-h-[300px] resize-y bg-white text-sm leading-6"
                        value={aiInput}
                        onChange={(event) => {
                          setAiInput(event.target.value)
                          setSubmitStatus(null)
                        }}
                        placeholder={getInputPlaceholder(aiType)}
                      />
                    )}
                  </div>

                  {submitStatus && (
                    <div
                      className={cn(
                        "flex flex-wrap items-center justify-between gap-3 rounded-lg border px-4 py-3 text-sm",
                        submitStatus.tone === "success"
                          ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                          : "border-rose-200 bg-rose-50 text-rose-800"
                      )}
                    >
                      <div className="flex items-start gap-2">
                        {submitStatus.tone === "success" ? (
                          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                        ) : (
                          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                        )}
                        <div>
                          <div className="font-medium">{submitStatus.title}</div>
                          {submitStatus.description && <div className="mt-0.5 opacity-80">{submitStatus.description}</div>}
                        </div>
                      </div>
                      {submitStatus.tone === "success" && (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="border-emerald-300 bg-white text-emerald-700 hover:bg-emerald-50"
                          onClick={() => router.push("/admin/entry?tab=tasks")}
                        >
                          查看任务队列
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              </section>

              <aside className="space-y-5">
                <section className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
                  <div className="mb-4 flex items-center gap-3">
                    <div className={cn("flex h-10 w-10 items-center justify-center rounded-md border", selectedTypeVisual.className)}>
                      <SelectedTypeIcon className="h-5 w-5" />
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-content-primary">采集配置</h3>
                      <p className="text-xs text-content-tertiary">提交前的任务元信息</p>
                    </div>
                  </div>
                  <div className="space-y-3 text-sm">
                    <div className="flex items-center justify-between gap-3 border-t border-surface-border pt-3">
                      <span className="text-content-tertiary">类型</span>
                      <span className="font-medium text-content-primary">{getOptionLabel(taskTypeOptions, aiType)}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3 border-t border-surface-border pt-3">
                      <span className="text-content-tertiary">模型策略</span>
                      <span className="font-medium text-content-primary">自动匹配</span>
                    </div>
                    <div className="flex items-center justify-between gap-3 border-t border-surface-border pt-3">
                      <span className="text-content-tertiary">附件</span>
                      <span className="max-w-[180px] truncate font-medium text-content-primary">{selectedFile?.name || "无"}</span>
                    </div>
                  </div>
                </section>

                <section className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
                  <h3 className="text-sm font-semibold text-content-primary">字段来源</h3>
                  <div className="mt-4 grid gap-2 text-sm">
                    <div className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
                      <span className="text-content-tertiary">公司类型</span>
                      <span className="font-medium text-content-primary">{companyTypeOptions.length}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
                      <span className="text-content-tertiary">招聘类型</span>
                      <span className="font-medium text-content-primary">{recruitmentTypeOptions.length}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
                      <span className="text-content-tertiary">招聘对象</span>
                      <span className="font-medium text-content-primary">{recruitmentTargetOptions.length}</span>
                    </div>
                  </div>
                </section>
              </aside>
            </form>
        ) : (
            <section className="rounded-lg border border-surface-border bg-white shadow-sm">
              <div className="border-b border-surface-border px-5 py-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                  <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                    <div className="relative">
                      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                      <Input
                        placeholder="任务 ID"
                        className="h-10 w-36 bg-white pl-9"
                        value={taskQuery.taskId}
                        onChange={(event) => setTaskQuery((current) => ({ ...current, taskId: event.target.value, page: 1 }))}
                      />
                    </div>
                    <Select value={taskQuery.model} onValueChange={(value) => setTaskQuery((current) => ({ ...current, model: value, page: 1 }))}>
                      <SelectTrigger className="h-10 w-44 bg-white">
                        <SelectValue placeholder="全部模型" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={ALL_VALUE}>全部模型</SelectItem>
                        {aiModelOptions.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Select value={taskQuery.type} onValueChange={(value) => setTaskQuery((current) => ({ ...current, type: value, page: 1 }))}>
                      <SelectTrigger className="h-10 w-36 bg-white">
                        <SelectValue placeholder="全部类型" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={ALL_VALUE}>全部类型</SelectItem>
                        {taskTypeOptions.map((option) => (
                          <SelectItem key={String(option.value)} value={String(option.value)}>
                            <GatherTypeOption option={option} />
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Select value={taskQuery.state} onValueChange={(value) => setTaskQuery((current) => ({ ...current, state: value, page: 1 }))}>
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
                  <div className="flex shrink-0 items-center gap-2">
                    <Button variant="outline" className="h-10 gap-2" onClick={resetTaskFilters}>
                      <X className="h-4 w-4" />
                      重置
                    </Button>
                    <Button variant="outline" className="h-10 gap-2" onClick={refreshTasks} disabled={taskLoading}>
                      {taskLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                      查询
                    </Button>
                  </div>
                </div>
              </div>

              <div className="space-y-4 p-5">
                {(taskQuery.sourceId || taskQuery.runnerType) && (
                  <div className="flex flex-wrap items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700">
                    {taskQuery.sourceId && <span className="font-medium">来源 #{taskQuery.sourceId}</span>}
                    {taskQuery.runnerType === "agent" && <span className="font-medium">Agent 自动作业</span>}
                    {taskQuery.runnerType === "admin" && <span className="font-medium">Admin 半自动采集</span>}
                    {showTaskContextClear && (
                      <Link href="/admin/entry?tab=tasks" className="ml-auto text-blue-700 underline-offset-4 hover:underline">
                        清除上下文
                      </Link>
                    )}
                  </div>
                )}

                <div className="overflow-x-auto rounded-lg border border-surface-border">
                  <Table className="table-fixed" style={{ width: "100%", minWidth: taskTableWidth }}>
                    <colgroup>
                      {TASK_TABLE_COLUMNS.map((column) => (
                        <col key={column.key} style={{ width: taskColumnWidths[column.key] }} />
                      ))}
                    </colgroup>
                    <TableHeader className="bg-slate-50">
                      <TableRow className="hover:bg-slate-50">
                        {TASK_TABLE_COLUMNS.map((column, index) => (
                          <ResizableTaskHead
                            key={column.key}
                            column={column}
                            resizeColumn={index > 0 && !column.sticky ? TASK_TABLE_COLUMNS[index - 1] : undefined}
                            width={taskColumnWidths[column.key]}
                            onResizeStart={handleTaskColumnResizeStart}
                          />
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {taskLoading ? (
                        <TableRow>
                          <TableCell colSpan={8} className="h-32 text-center text-content-tertiary">
                            <span className="inline-flex items-center gap-2">
                              <Loader2 className="h-4 w-4 animate-spin" />
                              加载任务中
                            </span>
                          </TableCell>
                        </TableRow>
                      ) : taskError ? (
                        <TableRow>
                          <TableCell colSpan={8} className="h-32 text-center text-rose-600">
                            {taskError}
                          </TableCell>
                        </TableRow>
                      ) : taskList.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={8} className="h-32 text-center text-content-tertiary">
                            {taskEmptyText}
                          </TableCell>
                        </TableRow>
                      ) : (
                        taskList.map((task) => {
                          const visual = getTypeVisual(task.type)
                          const TypeIcon = visual.icon
                          const fullText = task.content || ""
                          const displayText = fullText.length > 180 ? `${fullText.slice(0, 180)}...` : fullText
                          return (
                            <TableRow key={task.taskId} className="bg-white">
                              <TableCell className="font-medium text-content-primary">
                                <div>
                                  <div className="leading-6">#{task.taskId}</div>
                                  <div className="mt-1 flex items-center gap-1 whitespace-nowrap text-xs">
                                    {task.sourceId && (
                                      <Link href={`/admin/sources?sourceId=${task.sourceId}`} className="text-blue-600 hover:underline">
                                        来源 #{task.sourceId}
                                      </Link>
                                    )}
                                  </div>
                                </div>
                              </TableCell>
                              <TableCell>
                                <div className="space-y-1.5">
                                  <Badge variant="outline" className={cn("gap-1.5", visual.className)}>
                                    <TypeIcon className="h-3.5 w-3.5" />
                                    {getOptionLabel(taskTypeOptions, task.type)}
                                  </Badge>
                                  {task.runnerType && (
                                    <span className="inline-flex rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
                                      {task.runnerType === "agent" ? "Agent" : "Admin"}
                                    </span>
                                  )}
                                </div>
                              </TableCell>
                              <TableCell className="text-content-secondary">
                                <span className="line-clamp-2 break-words">{getModelOptionLabel(aiModelOptions, task.model)}</span>
                              </TableCell>
                              <TableCell>
                                <Badge variant="outline" className={cn("border-slate-200 bg-white", stateClass[task.state])}>
                                  {getOptionLabel(taskStateOptions, task.state)}
                                </Badge>
                              </TableCell>
                              <TableCell>
                                {task.type === 6 && task.content ? (
                                  <button
                                    type="button"
                                    className="block rounded-md border border-surface-border bg-white p-1 shadow-sm"
                                    onClick={() => setPreviewImg(task.content)}
                                    title="预览图片"
                                  >
                                    <img src={task.content} alt="任务图片" className="h-16 max-w-32 rounded object-contain" />
                                  </button>
                                ) : (task.type === 4 || task.type === 5) && task.content ? (
                                  <a
                                    href={task.content}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="line-clamp-2 break-all text-sm text-blue-600 hover:text-blue-700 hover:underline"
                                    title={task.content}
                                  >
                                    {task.content}
                                  </a>
                                ) : (
                                  <div className="min-w-0 text-sm text-content-secondary">
                                    <span className="whitespace-pre-wrap break-all">{displayText || "-"}</span>
                                    {fullText && (
                                      <Button
                                        size="icon"
                                        variant="ghost"
                                        className="ml-1 inline-flex h-5 w-5 align-[-3px] text-content-tertiary hover:text-content-primary"
                                        title="复制到剪贴板"
                                        onClick={async () => {
                                          try {
                                            await navigator.clipboard.writeText(fullText)
                                            toast({ title: "已复制到剪贴板" })
                                          } catch {
                                            toast({ title: "复制失败", variant: "destructive" })
                                          }
                                        }}
                                      >
                                        <Copy className="h-4 w-4" />
                                      </Button>
                                    )}
                                  </div>
                                )}
                              </TableCell>
                              <TableCell className="text-sm text-content-secondary">
                                <TaskResultView task={task} />
                              </TableCell>
                              <TableCell className="text-sm text-content-tertiary">{formatDateTimeStr(task.updateTime)}</TableCell>
                              <TableCell className={STICKY_ACTION_CELL_CLASS}>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="h-8 gap-1.5"
                                  disabled={reRunLoadingId === task.taskId}
                                  onClick={() => handleReRun(task.taskId)}
                                >
                                  {reRunLoadingId === task.taskId ? (
                                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                  ) : (
                                    <RefreshCcw className="h-3.5 w-3.5" />
                                  )}
                                  重跑
                                </Button>
                              </TableCell>
                            </TableRow>
                          )
                        })
                      )}
                    </TableBody>
                  </Table>
                </div>

                <TablePagination
                  page={taskQuery.page}
                  pageSize={taskQuery.size}
                  total={taskTotal}
                  currentCount={taskList.length}
                  loading={taskLoading}
                  totalLabel="条任务"
                  onPageChange={(page) => setTaskQuery((current) => ({ ...current, page }))}
                  onPageSizeChange={(size) => setTaskQuery((current) => ({ ...current, size, page: 1 }))}
                />
              </div>
            </section>
        )}
      </div>

      {previewImg && (
        <div
          className="fixed inset-0 z-50 flex cursor-zoom-out items-center justify-center bg-black/80 p-6"
          onClick={() => setPreviewImg(null)}
        >
          <img
            src={previewImg}
            alt="预览"
            className="max-h-full max-w-full rounded-lg bg-white object-contain shadow-xl"
            onClick={(event) => event.stopPropagation()}
          />
        </div>
      )}
    </div>
  )
}

export default function EntryPage() {
  const pathname = usePathname()
  const isAgentTasksRoute = pathname === "/admin/progress/tasks"

  return (
    <EntryContent
      forcedTab={isAgentTasksRoute ? "tasks" : undefined}
      forcedRunnerType={isAgentTasksRoute ? "agent" : undefined}
      taskEmptyText={isAgentTasksRoute ? "暂无Agent任务" : undefined}
      showTaskContextClear={isAgentTasksRoute ? false : undefined}
    />
  )
}
