"use client";

import { useEffect, useRef, useState } from "react";
import {
  Check,
  CircleDashed,
  FileText,
  Layers3,
  Loader2,
  Play,
  RefreshCcw,
  Rocket,
  ScanLine,
  UploadCloud,
  WandSparkles,
  X,
  type LucideIcon,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  devWxLogin,
  submitAIAgentTaskSSE,
  type GlobalConfigItemValue,
} from "@/lib/api";
import { getConfigValue } from "@/lib/config";
import { cn } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { useLoginUser } from "@/hooks/useLoginUser";

interface ProgressNode {
  id: string;
  title: string;
  description: string;
  step: number;
  icon: LucideIcon;
}

type RunState = 0 | 1 | 2;
type StepVisualState = "active" | "done" | "pending";
type ActivityTone = "info" | "running" | "success" | "error";
type RunSummaryTone = "idle" | "running" | "success" | "empty" | "failed";

interface ActivityLog {
  id: number;
  time: string;
  message: string;
  tone: ActivityTone;
}

interface AgentLogMessage {
  cmd: string;
  info?: unknown;
  agent?: string;
}

interface StoredUserInfo {
  userId: number;
  role: number;
  nickname?: string;
  avatar?: string;
  timestamp?: number;
}

interface GatherStats {
  insert: number;
  update: number;
  unchanged: number;
  actionable: number;
  total: number;
}

function getGatherStats(info: unknown): GatherStats {
  const insert = getArrayLength(info, "insertList");
  const update = getArrayLength(info, "updateList");
  const unchanged = getArrayLength(info, "unchangedList");
  return {
    insert,
    update,
    unchanged,
    actionable: insert + update,
    total: insert + update + unchanged,
  };
}

const progressNodes: ProgressNode[] = [
  {
    id: "entry",
    title: "录入任务",
    description: "提交职位线索与附件",
    step: 0,
    icon: FileText,
  },
  {
    id: "task_classify",
    title: "任务分类",
    description: "识别文本、链接或文件",
    step: 1,
    icon: Layers3,
  },
  {
    id: "task_gather",
    title: "数据提取",
    description: "抽取校招结构化字段",
    step: 2,
    icon: ScanLine,
  },
  {
    id: "draft_washer",
    title: "数据清洗",
    description: "校验并标准化草稿",
    step: 3,
    icon: WandSparkles,
  },
  {
    id: "draft_publish",
    title: "发布上线",
    description: "写入正式职位库",
    step: 4,
    icon: Rocket,
  },
];

const stepStateClass: Record<StepVisualState, string> = {
  active: "border-blue-200 bg-blue-50 text-blue-700 shadow-sm",
  done: "border-emerald-200 bg-emerald-50 text-emerald-700",
  pending: "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:bg-slate-50",
};

const iconStateClass: Record<StepVisualState, string> = {
  active: "bg-blue-600 text-white",
  done: "bg-emerald-500 text-white",
  pending: "bg-slate-100 text-slate-500",
};

const logToneClass: Record<ActivityTone, string> = {
  info: "bg-slate-400",
  running: "bg-blue-400",
  success: "bg-emerald-400",
  error: "bg-rose-400",
};

const runSummaryClass: Record<RunSummaryTone, string> = {
  idle: "border-slate-200 bg-slate-50 text-slate-600",
  running: "border-blue-200 bg-blue-50 text-blue-700",
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  empty: "border-amber-200 bg-amber-50 text-amber-700",
  failed: "border-rose-200 bg-rose-50 text-rose-700",
};

const fallbackTaskTypeLabels: Record<string, string> = {
  "1": "html文本",
  "2": "纯文本",
  "3": "http链接",
  "4": "excel文件",
  "5": "csv文件",
  "6": "图片",
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function formatPayload(info: unknown) {
  if (info === undefined || info === null || info === "") {
    return "";
  }
  if (typeof info === "string") {
    return info;
  }
  try {
    return JSON.stringify(info);
  } catch {
    return String(info);
  }
}

function getArrayLength(source: unknown, key: string) {
  if (!isRecord(source)) {
    return 0;
  }
  const value = source[key];
  return Array.isArray(value) ? value.length : 0;
}

function getNestedArrayLength(source: unknown, key: string, nestedKey: string) {
  if (!isRecord(source)) {
    return 0;
  }
  return getArrayLength(source[key], nestedKey);
}

function getTaskType(info: unknown) {
  if (!isRecord(info) || !isRecord(info.task)) {
    return undefined;
  }
  return info.task.type;
}

function getTaskTypeLabel(options: GlobalConfigItemValue[], value: unknown) {
  const matchedOption = options.find(
    (option) => String(option.value) === String(value)
  );
  return matchedOption?.intro || fallbackTaskTypeLabels[String(value)] || "未映射类型";
}

function formatFileSize(file: File) {
  const kb = file.size / 1024;
  if (kb < 1024) {
    return `${kb.toFixed(1)} KB`;
  }
  return `${(kb / 1024).toFixed(1)} MB`;
}

function getStoredUserInfo(): StoredUserInfo | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = localStorage.getItem("oc-user");
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as StoredUserInfo;
  } catch {
    return null;
  }
}

function isJwtExpired(token: string | null) {
  if (!token) {
    return true;
  }
  try {
    const payload = token.split(".")[1];
    if (!payload) {
      return true;
    }
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const decoded = JSON.parse(atob(normalized)) as { exp?: number };
    return typeof decoded.exp !== "number" || decoded.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

function isLocalhost() {
  if (typeof window === "undefined") {
    return false;
  }
  return ["localhost", "127.0.0.1", "::1"].includes(window.location.hostname);
}

export default function ProgressPage() {
  // AIDEV-NOTE: AI-GENERATED UI polish
  const [currentStep, setCurrentStep] = useState(0);
  const [taskTypeOptions, setTaskTypeOptions] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [inputValue, setInputValue] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [runState, setRunState] = useState<RunState>(0);
  const [stateInfo, setStateInfo] = useState<Record<number, string>>({});
  const [gatherStats, setGatherStats] = useState<GatherStats | null>(null);
  const [logs, setLogs] = useState<ActivityLog[]>([]);
  const [sseController, setSseController] = useState<AbortController | null>(
    null
  );
  const fileInputRef = useRef<HTMLInputElement>(null);
  const logIdRef = useRef(0);
  const { toast } = useToast();
  const { userInfo, setUserInfo } = useLoginUser();

  useEffect(() => {
    getConfigValue("gather", "GatherTargetTypeEnum").then(setTaskTypeOptions);
  }, []);

  useEffect(() => {
    const handleWindowPaste = (event: ClipboardEvent) => {
      if (runState === 1) {
        return;
      }
      const items = event.clipboardData?.items;
      if (!items) {
        return;
      }
      for (let i = 0; i < items.length; i++) {
        if (items[i].kind === "file") {
          setSelectedFile(items[i].getAsFile());
          break;
        }
      }
    };

    window.addEventListener("paste", handleWindowPaste);
    return () => window.removeEventListener("paste", handleWindowPaste);
  }, [runState]);

  useEffect(() => {
    return () => {
      if (sseController) {
        sseController.abort();
      }
    };
  }, [sseController]);

  const completedCount = progressNodes.filter((node) =>
    Boolean(stateInfo[node.step])
  ).length;
  const currentPercent = Math.round(
    (completedCount / progressNodes.length) * 100
  );
  const isRunning = runState === 1;
  const canSubmit = Boolean(inputValue.trim() || selectedFile) && !isRunning;
  const hasRunHistory = runState === 2 || logs.length > 0;
  const hasErrorLog = logs.some((log) => log.tone === "error");
  const runSummary: { tone: RunSummaryTone; label: string; description: string } = hasErrorLog
    ? {
        tone: "failed",
        label: "执行失败",
        description: "Agent 作业异常结束，请查看执行记录中的失败原因后重启。",
      }
    : isRunning
      ? {
          tone: "running",
          label: "执行中",
          description: `正在执行 ${progressNodes[currentStep]?.title || "Agent 作业"}，请等待事件流返回结果。`,
        }
        : runState === 2 && completedCount >= progressNodes.length
        ? {
            tone: "success",
            label: "成功完成",
            description: "任务已完成清洗和发布，结果已写入正式职位库。",
          }
        : runState === 2 && gatherStats && gatherStats.total > 0 && gatherStats.actionable === 0
          ? {
              tone: "empty",
              label: "已命中既有草稿",
              description: `本次提取 ${gatherStats.total} 条，全部与既有草稿一致，没有新增或更新，所以不进入清洗发布。`,
            }
          : runState === 2 && gatherStats && gatherStats.actionable > 0
            ? {
                tone: "empty",
                label: "已生成草稿，未发布",
                description: `本次新增 ${gatherStats.insert} 条、更新 ${gatherStats.update} 条，自动清洗或发布未完成，可到 Agent草稿审核。`,
              }
        : runState === 2
          ? {
              tone: "empty",
              label: "未提取到有效数据",
              description: "Agent 正常结束，但数据提取没有返回可入库草稿，请检查采集源授权、链接或输入内容。",
            }
          : {
              tone: "idle",
              label: "待启动",
              description: "提交职位线索后，Agent 会依次完成分类、提取、清洗和发布。",
            };

  const getStepVisualState = (index: number): StepVisualState => {
    if (stateInfo[index]) {
      return "done";
    }
    if (runState !== 2 && index === currentStep) {
      return "active";
    }
    return "pending";
  };

  const appendLog = (message: string, tone: ActivityTone = "info") => {
    logIdRef.current += 1;
    const id = logIdRef.current;
    const time = new Date().toLocaleTimeString();
    setLogs((prevLogs) => [
      ...prevLogs,
      {
        id,
        time,
        message,
        tone,
      },
    ]);
  };

  const ensureAdminSession = async (silent = false) => {
    const token =
      typeof window !== "undefined" ? localStorage.getItem("oc-token") : null;
    const storedUser = getStoredUserInfo();
    if (token && !isJwtExpired(token) && (userInfo?.role === 3 || storedUser?.role === 3)) {
      return;
    }

    if (!isLocalhost()) {
      throw new Error("请先使用管理员账号登录后再提交任务");
    }

    const login = await devWxLogin("admin");
    const nextUser: StoredUserInfo = {
      userId: login.user.userId,
      role: login.user.role,
      nickname: login.user.displayName,
      avatar: login.user.avatar,
      timestamp: Date.now(),
    };
    localStorage.setItem("oc-token", login.token);
    localStorage.setItem("oc-user", JSON.stringify(nextUser));
    setUserInfo(nextUser);
    if (!silent) {
      appendLog("已切换到本地管理员会话", "success");
    }
  };

  const parseAgentLog = (message: string): AgentLogMessage => {
    try {
      const parsed: unknown = JSON.parse(message);
      if (!isRecord(parsed)) {
        return { cmd: "error", info: `无法识别的消息: ${message}` };
      }
      return {
        cmd: String(parsed.cmd || "info"),
        info: parsed.info,
        agent: typeof parsed.agent === "string" ? parsed.agent : undefined,
      };
    } catch {
      return { cmd: "error", info: `解析消息失败: ${message}` };
    }
  };

  const buildStepInfo = (step: number, info: unknown) => {
    if (step === 1) {
      const taskType = getTaskType(info);
      return `任务分类为：${getTaskTypeLabel(taskTypeOptions, taskType)}`;
    }
    if (step === 2) {
      const stats = getGatherStats(info);
      return `新增 ${stats.insert} 条，更新 ${stats.update} 条，已存在 ${stats.unchanged} 条`;
    }
    if (step === 3) {
      return `完成清洗 ${getNestedArrayLength(info, "washer", "ids")} 条`;
    }
    if (step === 4) {
      return `成功发布 ${getArrayLength(info, "publish")} 条`;
    }
    return "";
  };

  const buildStartInfo = (step: number, info: unknown) => {
    if (step === 1) {
      return "准备识别任务类型";
    }
    if (step === 2) {
      return "开始抽取结构化字段";
    }
    if (step === 3) {
      const total =
        getArrayLength(info, "insertList") + getArrayLength(info, "updateList");
      return `待清洗 ${total} 条`;
    }
    if (step === 4 && isRecord(info)) {
      const ids = info.ids;
      return `待发布 ${Array.isArray(ids) ? ids.length : 0} 条`;
    }
    return "";
  };

  const addLog = (message: string) => {
    const msg = parseAgentLog(message);
    const node = progressNodes.find((item) => item.id === msg.agent);
    const payload = formatPayload(msg.info);

    if (node) {
      setCurrentStep(node.step);
    }

    if (msg.cmd === "init") {
      setRunState(1);
      appendLog(payload || "已建立 SSE 链接", "running");
      return;
    }

    if (msg.cmd === "start") {
      const startInfo = node ? buildStartInfo(node.step, msg.info) : "";
      appendLog(
        `【${node?.title || "Agent"}】开始执行 ${startInfo || payload}`,
        "running"
      );
      return;
    }

    if (msg.cmd === "end") {
      const stepInfo = node ? buildStepInfo(node.step, msg.info) : "";
      appendLog(
        `【${node?.title || "Agent"}】执行完成 ${stepInfo || payload}`,
        "success"
      );

      if (node) {
        if (node.step === 2) {
          setGatherStats(getGatherStats(msg.info));
        }
        if (stepInfo) {
          setStateInfo((prevStateInfo) => ({
            ...prevStateInfo,
            [node.step]: stepInfo,
          }));
        }
      }
      return;
    }

    if (msg.cmd === "over") {
      appendLog("【作业结束】Agent 流水线已结束", "success");
      setRunState(2);
      toast({ title: "已结束", description: "请查看本次作业判定" });
      return;
    }

    if (msg.cmd === "error") {
      appendLog(`【任务失败】${payload}`, "error");
      setRunState(0);
      setSseController(null);
      toast({
        title: "失败",
        description: `任务执行失败: ${payload}`,
        variant: "destructive",
      });
      return;
    }

    appendLog(payload || message, "info");
  };

  const addParseErrorLog = (line: string, agent: string) => {
    addLog(
      JSON.stringify({
        cmd: "error",
        info: `解析消息失败: ${line}`,
        agent,
      })
    );
  };

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      setSelectedFile(event.target.files[0]);
    }
  };

  const handleDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isRunning) {
      return;
    }
    if (event.dataTransfer.files && event.dataTransfer.files[0]) {
      setSelectedFile(event.dataTransfer.files[0]);
    }
  };

  const handlePaste = (event: React.ClipboardEvent<HTMLDivElement>) => {
    if (isRunning) {
      return;
    }
    const items = event.clipboardData?.items;
    if (!items) {
      return;
    }
    for (let i = 0; i < items.length; i++) {
      if (items[i].kind === "file") {
        setSelectedFile(items[i].getAsFile());
        break;
      }
    }
  };

  const clearSelectedFile = () => {
    setSelectedFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const setupSSEConnection = async () => {
    if (sseController) {
      sseController.abort();
    }

    const newController = new AbortController();
    setSseController(newController);
    let terminalSeen = false;

    try {
      const params = {
        type: "0",
        file: selectedFile,
        content: inputValue,
      };

      submitAIAgentTaskSSE(
        params,
        (line) => {
          const defaultAgent = progressNodes[currentStep].id;
          const closeStream = () => {
            newController.abort();
            setSseController(null);
          };

          try {
            const data: unknown = JSON.parse(line);
            if (!isRecord(data)) {
              addParseErrorLog(line, defaultAgent);
              return;
            }

            const message =
              typeof data.message === "string"
                ? data.message
                : JSON.stringify(data);

            const agentLog = parseAgentLog(message);
            addLog(message);

            if (agentLog.cmd === "over" || agentLog.cmd === "error") {
              terminalSeen = true;
              closeStream();
              return;
            }

            if (data.status === "completed") {
              terminalSeen = true;
              closeStream();
              addLog(
                `{"cmd":"over", "info": "任务成功完成", "agent": "${defaultAgent}"}`
              );
              return;
            }

            if (data.status === "failed") {
              terminalSeen = true;
              closeStream();
              addLog(
                `{"cmd":"error", "info": "任务执行失败", "agent": "${defaultAgent}"}`
              );
            }
          } catch {
            addParseErrorLog(line, defaultAgent);
          }
        },
        (error) => {
          const defaultAgent = progressNodes[currentStep].id;
          addLog(
            `{"cmd":"error", "info": "请求失败: ${error.message}", "agent": "${defaultAgent}"}`
          );
          setSseController(null);
        },
        () => {
          const defaultAgent = progressNodes[currentStep].id;
          setSseController(null);
          if (!terminalSeen) {
            addLog(
              `{"cmd":"over", "info": "SSE连接已关闭", "agent": "${defaultAgent}"}`
            );
          }
        },
        newController
      );

      addLog(
        '{"cmd":"init", "info": "建立 SSE 链接，准备接收任务进度", "agent": "task_classify"}'
      );
      toast({
        title: "成功",
        description: "已提交任务，正在接收执行进度",
      });
    } catch (error) {
      if (!newController.signal.aborted) {
        const defaultAgent = progressNodes[currentStep].id;
        addLog(
          `{"cmd":"error", "info": "SSE连接错误: ${
            error instanceof Error ? error.message : "未知错误"
          }", "agent": "${defaultAgent}"}`
        );
      }
      setSseController(null);
    }
  };

  const handleSubmitTask = async () => {
    if (!canSubmit) {
      return;
    }

    try {
      setLogs([]);
      setStateInfo({});
      setGatherStats(null);
      setCurrentStep(0);
      setRunState(1);
      logIdRef.current = 0;
      appendLog("校验管理员登录态", "running");
      await ensureAdminSession();
      setStateInfo({ 0: "已提交职位线索与附件" });
      appendLog("任务已提交，等待 Agent 调度", "running");
      await setupSSEConnection();
    } catch (error) {
      const defaultAgent = progressNodes[currentStep].id;
      addLog(
        `{"cmd":"error", "info": "提交失败: ${
          error instanceof Error ? error.message : String(error)
        }", "agent": "${defaultAgent}"}`
      );
    }
  };

  return (
    <div className="min-h-full">
      <div className="mx-auto max-w-[1480px] px-4 py-5 sm:px-6">
        <section className="mb-5 shrink-0 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <h2 className="text-base font-semibold text-slate-950">
                执行进度
              </h2>
              <p className="text-sm text-slate-500">
                采集 · 提取 · 清洗 · 发布
              </p>
            </div>
            <div className="flex items-center gap-3 text-sm text-slate-500">
              <span>
                已完成{" "}
                <span className="font-semibold text-slate-950">
                  {completedCount}
                </span>
                /{progressNodes.length}
              </span>
              <span className="h-4 w-px bg-slate-200" />
              <span>
                进度{" "}
                <span className="font-semibold text-slate-950">
                  {currentPercent}%
                </span>
              </span>
            </div>
          </div>

          <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-blue-600 transition-all duration-500"
              style={{ width: `${currentPercent}%` }}
            />
          </div>

          <div className={cn("mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border px-3 py-2 text-sm", runSummaryClass[runSummary.tone])}>
            <span className="inline-flex items-center gap-2 font-semibold">
              <span className="h-2 w-2 rounded-full bg-current" />
              {runSummary.label}
            </span>
            <span className="text-current/80">{runSummary.description}</span>
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2 min-[1100px]:grid-cols-5">
            {progressNodes.map((node, index) => {
              const visualState = getStepVisualState(index);
              const Icon = node.icon;

              return (
                <button
                  key={node.id}
                  type="button"
                  onClick={() => setCurrentStep(index)}
                  className={cn(
                    "min-h-[84px] last:col-span-2 min-[1100px]:last:col-span-1 rounded-xl border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2",
                    stepStateClass[visualState]
                  )}
                  aria-current={visualState === "active" ? "step" : undefined}
                >
                  <div className="flex items-center justify-between gap-1">
                    <div className="flex min-w-0 items-center gap-2">
                      <span
                        className={cn(
                          "flex h-9 w-9 shrink-0 items-center justify-center rounded-md",
                          iconStateClass[visualState]
                        )}
                      >
                        {visualState === "done" ? (
                          <Check className="h-4 w-4" />
                        ) : (
                          <Icon className="h-4 w-4" />
                        )}
                      </span>
                      <span className="whitespace-nowrap text-sm font-semibold leading-5 text-slate-950">
                        {node.title}
                      </span>
                    </div>
                    <span className="text-xs font-semibold text-current/70">
                      0{index + 1}
                    </span>
                  </div>
                  <p className="mt-2 text-xs leading-5 text-slate-500">
                    {node.description}
                  </p>
                  {stateInfo[index] && (
                    <p className="mt-2 line-clamp-2 whitespace-pre-line text-xs leading-5 text-emerald-700">
                      {stateInfo[index]}
                    </p>
                  )}
                </button>
              );
            })}
          </div>
        </section>

        <div className="grid items-stretch gap-5 min-[1100px]:grid-cols-[1.1fr_1fr]">
          <section className="min-h-0 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div className="min-w-0 flex-1 text-sm text-slate-500">
                新建采集 · 自动选择处理方式
              </div>
              <div className="flex flex-wrap items-center gap-3">
                {selectedFile && (
                  <Badge
                    variant="outline"
                    className="w-fit rounded-md border-blue-100 bg-blue-50 text-blue-700"
                  >
                    已附加 1 个文件
                  </Badge>
                )}
                <Button
                  type="button"
                  onClick={handleSubmitTask}
                  disabled={!canSubmit}
                  className="min-w-32 gap-2"
                >
                  {isRunning ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : hasRunHistory ? (
                    <RefreshCcw className="h-4 w-4" />
                  ) : (
                    <Play className="h-4 w-4" />
                  )}
                  {isRunning ? "执行中" : hasRunHistory ? "重启 Agent" : "启动 Agent"}
                </Button>
              </div>
            </div>

            <div className="mt-5 space-y-5">
              <div>
                <Textarea
                  rows={5}
                  value={inputValue}
                  onChange={(event) => setInputValue(event.target.value)}
                  disabled={isRunning}
                  placeholder="粘贴招聘网址，或输入需要整理的招聘原文…"
                  aria-label="招聘线索"
                  className="min-h-[190px] resize-y rounded-lg border-slate-200 bg-slate-50/70 px-4 py-3 text-sm leading-6 placeholder:text-slate-400 focus-visible:ring-blue-500"
                />
              </div>

              <div>
                <label className="text-sm font-medium text-slate-900">
                  附件
                </label>
                <div
                  className={cn(
                    "mt-2 flex min-h-[132px] items-center justify-center rounded-lg border border-dashed p-4 transition-colors",
                    isRunning
                      ? "cursor-not-allowed border-slate-200 bg-slate-50 opacity-70"
                      : "cursor-pointer border-slate-300 bg-white hover:border-blue-300 hover:bg-blue-50/40"
                  )}
                  onClick={() => {
                    if (!isRunning) {
                      fileInputRef.current?.click();
                    }
                  }}
                  onDrop={handleDrop}
                  onDragOver={handleDragOver}
                  onPaste={handlePaste}
                >
                  <input
                    type="file"
                    ref={fileInputRef}
                    className="hidden"
                    disabled={isRunning}
                    onChange={handleFileSelect}
                  />
                  {selectedFile ? (
                    <div className="flex w-full items-center justify-between gap-3 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                      <div className="flex min-w-0 items-center gap-2">
                        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-blue-100 text-blue-700">
                          <FileText className="h-5 w-5" />
                        </span>
                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium text-slate-950">
                            {selectedFile.name}
                          </div>
                          <div className="mt-0.5 text-xs text-slate-500">
                            {formatFileSize(selectedFile)}
                          </div>
                        </div>
                      </div>
                      <button
                        type="button"
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-white hover:text-slate-900"
                        onClick={(event) => {
                          event.stopPropagation();
                          clearSelectedFile();
                        }}
                        title="移除附件"
                        disabled={isRunning}
                      >
                        <X className="h-4 w-4" />
                      </button>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center text-center">
                      <span className="flex h-12 w-12 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                        <UploadCloud className="h-6 w-6" />
                      </span>
                      <div className="mt-3 text-sm font-medium text-slate-900">
                        拖入文件或点击选择
                      </div>
                      <div className="mt-1 text-xs text-slate-500">
                        CSV、Excel、图片与常见文档均可作为采集源
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>

          </section>

          <aside className="min-h-0">
            <section className="flex h-full min-h-[430px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">
                    执行记录
                  </h2>
                  <p className="mt-0.5 text-xs text-slate-400">
                    实时执行记录
                  </p>
                </div>
                <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
                  {logs.length} 条
                </span>
              </div>

              <div className="min-h-0 max-h-[520px] flex-1 overflow-y-auto px-5 py-5 [scrollbar-gutter:stable]">
                {logs.length === 0 ? (
                  <div className="flex min-h-72 flex-col items-center justify-center text-center text-slate-400">
                    <CircleDashed className="h-8 w-8" />
                    <div className="mt-3 text-sm">提交任务后，执行进度会显示在这里</div>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {logs.map((log) => (
                      <div key={log.id} className="flex gap-3">
                        <span
                          className={cn(
                            "mt-2 h-2 w-2 shrink-0 rounded-full",
                            logToneClass[log.tone]
                          )}
                        />
                        <div className="min-w-0 flex-1">
                          <div className="text-[11px] font-medium text-slate-500">
                            {log.time}
                          </div>
                          <div className="mt-0.5 break-words text-sm leading-6 text-slate-600">
                            {log.message}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
}
