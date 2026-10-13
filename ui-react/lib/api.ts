import axios from "axios";

// 根据环境设置BASE_URL， 利用了Next.js在构建时自动设置的环境变量，在执行deploy脚本时，由于会调用 next build ，NODE_ENV会被设置为'production'
// 本地开发时使用http://localhost:8080
// 部署时使用空字符串
const BASE_URL = process.env.NODE_ENV === 'production' ? '' : "http://localhost:8087";

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 360000,
});

export interface LlmOverview { calls:number; successRate:number; averageDurationMs:number; totalTokens:number; estimatedCost:number }
export interface LlmCall { id:string; jobClawUserId?:string; nickName?:string; channel?:string; agent?:string; operation:string; mode:string; outcome:string; durationMs:number; requestCount:number; inputTokens?:number; outputTokens?:number; totalTokens?:number; estimatedCost?:number; createTime:string }
export interface LlmRequest { id:string; invocationId:string; channel?:string; provider?:string; model?:string; modelType?:string; outcome:string; durationMs:number; inputTokens?:number; outputTokens?:number; totalTokens?:number; estimatedCost?:number; promptSample?:string; responseSample?:string; createTime:string }
export interface LlmCallDetail { invocation:LlmCall; requests:LlmRequest[] }
export async function fetchLlmOverview(admin:boolean):Promise<LlmOverview>{const r=await api.get(admin?"/api/admin/llm-monitor/overview":"/api/user/llm-usage/overview");if(r.data?.code===0)return r.data.data;throw new Error(r.data?.msg||"获取模型用量失败")}
export async function fetchLlmCalls(admin:boolean,page=1,size=20):Promise<{list:LlmCall[];total:number;page:number;size:number;hasMore:boolean}>{const r=await api.get(admin?"/api/admin/llm-monitor/calls":"/api/user/llm-usage/calls",{params:{page,size}});if(r.data?.code===0)return r.data.data;throw new Error(r.data?.msg||"获取模型调用明细失败")}
export async function fetchLlmCallDetail(admin:boolean,id:string):Promise<LlmCallDetail>{const r=await api.get(admin?"/api/admin/llm-monitor/calls/"+id:"/api/user/llm-usage/calls/"+id);if(r.data?.code===0)return r.data.data;throw new Error(r.data?.msg||"获取模型调用详情失败")}

// 全局请求拦截器，自动带上 X-OC-TOKEN
api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("oc-token");
    if (token) {
      config.headers = config.headers || {};
      config.headers["X-OC-TOKEN"] = token;
    }
  }
  return config;
});

// 响应拦截器，处理登录态失效
api.interceptors.response.use(
  (response) => {
    if (response.data.code == 100403003) {
      const message = isBlankErrorMessage(response.data.msg)
        ? "未登录或登录已过期，请重新登录"
        : response.data.msg;
      // 清除本地缓存的登录信息
      if (typeof window !== "undefined") {
        localStorage.removeItem("oc-token");
        localStorage.removeItem("oc-user");
      }
      return Promise.reject(new Error(message));
    }
    return response;
  },
  (error) => {
    if (
      error.response &&
      error.response.data &&
      error.response.data.code === 100403003
    ) {
      if (typeof window !== "undefined") {
        localStorage.removeItem("oc-token");
        localStorage.removeItem("oc-user");
      }
    }
    return Promise.reject(error);
  }
);

interface ApiResponse<T> {
  code?: number;
  msg?: string;
  message?: string;
  data?: T;
}

function isBlankErrorMessage(message?: string) {
  return !message || message.trim() === "" || message.trim() === "???";
}

function getApiResponseMessage(data: unknown) {
  if (!data || typeof data !== "object") {
    return undefined;
  }

  const record = data as Record<string, unknown>;
  const message = record.msg ?? record.message;
  if (typeof message === "string" && !isBlankErrorMessage(message)) {
    return message;
  }

  if (record.code !== undefined && record.code !== 0) {
    return `后端返回异常(code=${String(record.code)})`;
  }

  return undefined;
}

function getRequestErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const responseMessage = getApiResponseMessage(error.response?.data);
    if (responseMessage) {
      return responseMessage;
    }
    if (error.response?.status === 403) {
      return "无权限或登录态已失效，请使用管理员账号登录";
    }
    if (error.response?.status === 401) {
      return "登录态已失效，请重新登录";
    }
    if (error.response?.status) {
      return `HTTP ${error.response.status}`;
    }
    if (!isBlankErrorMessage(error.message)) {
      return error.message;
    }
    return fallback;
  }

  if (error instanceof Error && !isBlankErrorMessage(error.message)) {
    return error.message;
  }
  return fallback;
}

/**
 * 获取微信扫码登录 SSE 订阅 URL
 */
export function getWxSseUrl() {
  return `${BASE_URL}/api/wx/subscribe`;
}

/**
 * 微信扫码登录 callback
 * @param xml xml 字符串
 * @returns Promise<void>
 */
export async function postWxCallback(xml: string): Promise<void> {
  await api.post("/api/wx/callback", xml, {
    headers: { "content-type": "application/xml" },
  });
}

export interface DevLoginUser {
  userId: number;
  displayName?: string;
  avatar?: string;
  role: number;
}

export interface DevLoginResponse {
  token: string;
  user: DevLoginUser;
}

export async function devWxLogin(type: "user" | "admin"): Promise<DevLoginResponse> {
  const res = await api.get("/api/wx/dev/login", { params: { type } });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "本地登录失败");
}

export interface JobListQuery {
  sourceId?: number;
  sourceTaskId?: number;
  runnerType?: string;
  companyName?: string;
  companyType?: string;
  jobLocation?: string;
  recruitmentType?: string;
  recruitmentTarget?: string;
  position?: string;
  deliveryProgress?: string;
  state?: number;
  page?: number;
  size?: number;
}

export interface JobListResponse {
  list: any[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
  online?: number;
  locked: boolean;
}

export async function fetchJobList(
  params?: JobListQuery
): Promise<JobListResponse> {
  const res = await api.get("/api/oc/list", { params });
  if (res.data && res.data.code === 0) {
    // 将外层的在线人数写到内部
    res.data.data.online = res.data.online;
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位列表失败");
}

export async function jobDetail(id: number) {
  const res = await api.get(`/api/oc/detail?id=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位信息失败");
}

export async function fetchAdminJobList(
  params?: JobListQuery
): Promise<JobListResponse> {
  const res = await api.get("/api/admin/oc/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取岗位列表失败");
}

export async function execPublishBlogs() {
  const res = await api.get("/api/admin/oc/publish");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "发布播客失败");
}

export async function submitOcEntry(params: any) {
  const res = await api.post("/api/admin/oc/save", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "提交岗位信息失败");
}

export async function updateOcState(params: { id: number; state: number }) {
  const res = await api.get(
    `/api/admin/oc/updateState?id=${params.id}&state=${params.state}`
  );
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新岗位状态失败");
}

//  ---------------------------- gather 相关

function getSubmitPath(async: boolean) {
  if (async) {
    // 异步执行
    return "/api/admin/gather/asyncSubmit";
  } else {
    // 同步执行
    return "/api/admin/gather/submit";
  }
}

export async function submitAIEntry(params: {
  content: string;
  model?: string;
  type: string;
  file: any;
}) {
  const async = true;
  if (params.file) {
    // 传文件的方式
    const formData = new FormData();
    formData.append("file", params.file);
    if (params.model) {
      formData.append("model", params.model);
    }
    formData.append("type", params.type);
    const ans = await api.post(getSubmitPath(async), formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (ans.data && ans.data.code === 0) {
      return ans.data.data;
    } else {
      throw new Error(ans.data?.msg || "AI录入失败");
    }
  }

  const res = await api.post(getSubmitPath(async), params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "AI录入失败");
}

/**
 * 提交AI代理任务（非SSE版本）
 */
export async function submitAIAgentTaskSimple(params: {
  content: string;
  model?: string;
  type: string;
  file: any;
}) {
  const async = true;
  if (params.file) {
    // 传文件的方式
    const formData = new FormData();
    formData.append("file", params.file);
    if (params.model) {
      formData.append("model", params.model);
    }
    formData.append("type", "0");
    const ans = await api.post(getSubmitPath(async), formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    if (ans.data && ans.data.code === 0) {
      return ans.data.data;
    } else {
      throw new Error(ans.data?.msg || "AI录入失败");
    }
  }

  const res = await api.post("/api/admin/gather/agentSubmit", params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "AI录入失败");
}

/**
 * 提交AI代理任务并获取任务ID
 * @param params 请求参数
 * @param onSuccess 获取任务ID成功回调
 * @param onError 错误处理回调
 * @param controller AbortController实例，用于取消请求
 */
export function submitAIAgentTask(
  params: { content: string; model?: string; type: string; file: any },
  onSuccess: (taskId: string) => void,
  onError: (error: Error) => void,
  controller?: AbortController
) {
  // 用于文件上传的表单数据
  const formData = new FormData();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      formData.append(key, value);
    }
  });

  // 创建axios请求配置
  const config = {
    method: "post",
    url: "/api/admin/gather/agentSubmit",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
    signal: controller?.signal,
  };

  // 发送请求
  api(config)
    .then((response) => {
      const result = response?.data as ApiResponse<string | number> | undefined;
      if (!result || result.code !== 0) {
        throw new Error(getApiResponseMessage(result) || "提交任务失败");
      }
      if (result.data === undefined || result.data === null || result.data === "") {
        throw new Error("未能获取有效的任务ID");
      }
      onSuccess(String(result.data));
    })
    .catch((error) => {
      if (!controller?.signal.aborted) {
        console.error("提交任务失败:", error);
        onError(new Error(`提交任务失败: ${getRequestErrorMessage(error, "请求异常")}`));
      }
    });
}

/**
 * 通过任务ID建立SSE连接获取任务结果
 * @param taskId 任务ID
 * @param onMessage 消息处理回调
 * @param onError 错误处理回调
 * @param onComplete 完成处理回调
 * @returns 用于关闭连接的函数
 */
export function connectSSEByTaskId(
  taskId: string,
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete?: () => void
) {
  let controller: AbortController | null = new AbortController();
  let isClosed = false;

  async function startFetch() {
    try {
      // 创建请求头，包含X-OC-TOKEN
      const headers = new Headers();
      const token =
        typeof window !== "undefined" ? localStorage.getItem("oc-token") : null;
      if (token) {
        headers.append("X-OC-TOKEN", token);
      }

      // 创建fetch请求
      const url = `${BASE_URL}/api/admin/gather/autoInvoke?taskId=${encodeURIComponent(
        taskId
      )}`;
      const response = await fetch(url, {
        method: "GET",
        headers: headers,
        signal: controller?.signal,
      });

      if (!response.ok) {
        let message = `HTTP error! status: ${response.status}`;
        try {
          const data: unknown = await response.json();
          message = getApiResponseMessage(data) || message;
        } catch {
          if (response.status === 403) {
            message = "无权限或登录态已失效，请使用管理员账号登录";
          }
        }
        throw new Error(message);
      }

      if (!response.body) {
        throw new Error("ReadableStream not supported in this browser");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (!isClosed) {
        const { done, value } = await reader.read();
        if (done) {
          const tail = decoder.decode();
          if (tail) {
            buffer += tail;
          }
          if (buffer) {
            buffer = processBuffer(buffer, true);
          }
          if (onComplete && !isClosed) {
            onComplete();
          }
          close();
          break;
        }

        const directResponse = decoder.decode(value, { stream: true });
        buffer += directResponse;
        buffer = processBuffer(buffer);
      }
    } catch (error) {
      if (
        !isClosed &&
        !(error instanceof DOMException && error.name === "AbortError")
      ) {
        // console.error('SSE连接错误:', error);
        onError(
          new Error(
            `SSE连接发生错误: ${
              error instanceof Error ? error.message : String(error)
            }`
          )
        );
        close();
      }
    }
  }

  function processBuffer(nextBuffer: string, flush = false) {
    const normalized = nextBuffer.replace(/\r\n/g, "\n");
    const events = normalized.split(/\n\n+/);
    const remainder = flush ? "" : events.pop() ?? "";

    events.forEach(processEvent);
    if (flush && remainder.trim()) {
      processEvent(remainder);
    }
    return remainder;
  }

  function processEvent(event: string) {
    const dataLines: string[] = [];
    event.split("\n").forEach((rawLine) => {
      const line = rawLine.trimEnd();
      if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trimStart());
      } else if (line.trim() && !line.startsWith(":")) {
        dataLines.push(line.trim());
      }
    });

    const data = dataLines.join("\n").trim();
    if (data) {
      processMessage(data);
    }
  }

  // 处理单条消息
  function processMessage(data: string) {
    if (isClosed) return;
    try {
      onMessage(data);
    } catch (error) {
      onError(
        new Error(
          `处理SSE消息时出错: ${
            error instanceof Error ? error.message : String(error)
          }`
        )
      );
    }
  }

  // 关闭连接的函数
  function close() {
    if (isClosed) return;
    isClosed = true;
    if (controller) {
      controller.abort();
      controller = null;
    }
  }

  // 启动fetch请求
  startFetch();

  return close;
}

/**
 * 提交AI代理任务并建立SSE连接（整合版）
 * @param params 请求参数
 * @param onMessage 消息处理回调
 * @param onError 错误处理回调
 * @param onComplete 完成处理回调
 * @param controller AbortController实例，用于取消请求
 * @returns 用于关闭SSE连接的函数
 */
export function submitAIAgentTaskSSE(
  params: { content: string; model?: string; type: string; file: any },
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete?: () => void,
  controller?: AbortController
) {
  let closeSSE: (() => void) | null = null;

  // 提交任务获取taskId
  submitAIAgentTask(
    params,
    (taskId) => {
      // 使用taskId建立SSE连接
      closeSSE = connectSSEByTaskId(taskId, onMessage, onError, onComplete);
    },
    onError,
    controller
  );

  // 返回关闭函数
  return () => {
    if (closeSSE) {
      closeSSE();
    }
  };
}

export interface TaskListQuery {
  page?: number;
  size?: number;
  taskId?: number;
  sourceId?: number;
  runnerType?: string;
  model?: string;
  type?: number;
  state?: number;
}

export interface TaskListItem {
  taskId: number;
  type: number;
  model: string;
  sourceId?: number;
  sourceVersion?: number;
  runnerType?: string;
  state: number;
  content: string;
  cnt: number;
  result: string;
  processTime: string;
  createTime: string;
  updateTime: string;
}

export interface TaskListResponse {
  list: TaskListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchTaskList(
  params: TaskListQuery
): Promise<TaskListResponse> {
  const res = await api.post("/api/admin/gather/list", params, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取任务列表失败");
}

export async function reRunTask(taskId: number): Promise<boolean> {
  const res = await api.get(`/api/admin/gather/reRun?taskId=${taskId}`);
  if (res.data && res.data.code === 0) {
    return res.data.data === true;
  }
  throw new Error(res.data?.msg || "重跑任务失败");
}

export interface GatherSourceListQuery {
  page?: number;
  size?: number;
  sourceId?: number;
  type?: number;
  ownerType?: string;
  status?: string;
  keyword?: string;
}

export interface GatherSourceItem {
  sourceId: number;
  type: number;
  title: string;
  content: string;
  sourceHash: string;
  contentHash: string;
  version: number;
  model: string;
  ownerType: string;
  runnerMode: string;
  status: string;
  lastTaskId?: number;
  lastResultSummary?: string;
  lastInsertCount: number;
  lastUpdateCount: number;
  lastUnchangedCount: number;
  lastFailedCount: number;
  lastSuccessTime?: number;
  consecutiveFailCount: number;
  createTime: number;
  updateTime: number;
}

export interface GatherSourceListResponse {
  list: GatherSourceItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchGatherSourceList(
  params: GatherSourceListQuery
): Promise<GatherSourceListResponse> {
  const res = await api.get("/api/admin/gather/source/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取采集源列表失败");
}

export async function fetchGatherSourceDetail(sourceId: number): Promise<GatherSourceItem> {
  const res = await api.get("/api/admin/gather/source/detail", { params: { sourceId } });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取采集源详情失败");
}

export async function reRunGatherSource(sourceId: number): Promise<number> {
  const res = await api.get("/api/admin/gather/source/reRun", { params: { sourceId } });
  if (res.data && res.data.code === 0) {
    return Number(res.data.data);
  }
  throw new Error(res.data?.msg || "重新采集失败");
}

export interface DraftListQuery {
  page?: number;
  size?: number;
  draftIds?: string;
  sourceId?: number;
  sourceTaskId?: number;
  runnerType?: string;
  companyName?: string;
  companyType?: string;
  jobLocation?: string;
  recruitmentType?: string;
  recruitmentTarget?: string;
  position?: string;
  lastUpdatedTimeAfter?: number;
  lastUpdatedTimeBefore?: number;
  state?: number;
  toProcess?: string;
}

export interface DraftItem {
  id: number;
  companyName: string;
  companyType: string;
  companyIndustry: string;
  jobLocation: string;
  recruitmentType: string;
  recruitmentTarget: string;
  position: string;
  deliveryProgress: string;
  lastUpdatedTime: string;
  deadline: string;
  relatedLink: string;
  jobAnnouncement: string;
  internalReferralCode: string;
  remarks: string;
  state: number;
  toProcess: number;
  sourceId?: number;
  sourceTaskId?: number;
  runnerType?: string;
  createTime: string;
  updateTime: string;
}

export interface DraftListResponse {
  list: DraftItem[];
  total: number;
}

export async function fetchDraftList(
  params: DraftListQuery
): Promise<DraftListResponse> {
  const res = await api.get("/api/admin/draft/list", { params });
  if (res.data && res.data.code === 0) {
    // 兼容 data 直接为数组或为对象
    if (Array.isArray(res.data.data)) {
      return { list: res.data.data, total: res.data.data?.length };
    }
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取草稿列表失败");
}

/**
 * 批量发布草稿
 * @param ids 草稿 id 数组
 * @returns 发布结果
 */
export async function batchPublishDrafts(ids: number[]): Promise<void> {
  const res = await api.post("/api/admin/draft/toOc", ids);
  if (res.data && res.data.code === 0) {
    return;
  }
  throw new Error(res.data?.msg || "发布失败");
}

/**
 * 更新草稿
 * @param draft DraftItem
 * @returns 是否成功
 */
export async function updateDraft(draft: DraftItem): Promise<boolean> {
  const res = await api.post("/api/admin/draft/update", draft);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "草稿更新失败");
}

/**
 * 删除草稿数据
 * @param id
 * @returns
 */
export async function deleteDraft(id: number): Promise<boolean> {
  const res = await api.get("/api/admin/draft/delete?draftId=" + id);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "草稿删除失败");
}

// -------------------------- 用户相关

export interface UserListQuery {
  userId?: number;
  displayName?: string;
  role?: number;
  page?: number;
  size?: number;
}

export interface UserListItem {
  userId: number;
  displayName: string;
  avatar: string;
  wxId: string;
  role: number;
  state: number;
  email: string;
  intro: string;
  expireTime: number | null;
  createTime: number;
  updateTime: number;
}

export interface UserListResponse {
  list: UserListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchUserList(
  params?: UserListQuery
): Promise<UserListResponse> {
  const res = await api.get("/api/admin/user/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户列表失败");
}

/**
 * 更新用户角色
 * @param params 包含 userId, role, expireTime
 * @returns 是否成功
 */
export async function updateUserRole(params: {
  userId: number;
  role: number;
  expireTime: number;
}): Promise<boolean> {
  const formData = new URLSearchParams();
  formData.append("userId", String(params.userId));
  formData.append("role", String(params.role));
  formData.append("expireTime", String(params.expireTime));
  const res = await api.post("/api/admin/user/updateRole", formData, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  if (res.data && res.data.code === 0) {
    return res.data.data === true;
  }
  throw new Error(res.data?.msg || "用户角色更新失败");
}

// -------------------------- 字典相关

export interface DictListQuery {
  app?: string;
  key?: string;
  page?: number;
  size?: number;
}

export interface DictListItem {
  id: number;
  app: string;
  scope: number;
  key: string;
  value: string;
  intro: string;
  remark: string;
  state: number;
  createTime: number;
  updateTime: number;
}

export interface DictListResponse {
  list: DictListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchDictList(
  params?: DictListQuery
): Promise<DictListResponse> {
  const res = await api.get("/api/admin/dict/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取字典列表失败");
}

export interface DictSaveReq {
  id?: number;
  app: string;
  scope: number;
  key: string;
  value: string;
  intro: string;
  remark: string;
  state: number;
}

export async function saveDict(params: DictSaveReq): Promise<boolean> {
  const res = await api.post("/api/admin/dict/save", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "保存字典失败");
}

export async function updateDictState(
  id: number,
  state: number
): Promise<boolean> {
  const res = await api.post(
    "/api/admin/dict/updateState",
    {
      id: id,
      state: state,
    },
    {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    }
  );
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "更新状态失败");
}

export async function deleteDict(id: number): Promise<boolean> {
  const res = await api.get("/api/admin/dict/delete?id=" + id);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "删除失败");
}

// ---------------- 个人用户相关

export interface UserSaveReq {
  userId?: number;
  displayName?: string;
  avatar?: string;
  email?: string;
  intro?: string;
  dingDingId?: string;
  feiShuId?: string;
}

export async function execLogout() {
  const res = await api.get("/api/user/logout");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "退出登录失败");
}

export async function getUserDetail() {
  const res = await api.get("/api/user/detail");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户信息失败");
}

export interface UserModelConfig {
  name: string;
  type: string;
  multimodal?: boolean;
  billingType?: "FREE" | "PAID" | string;
}

export interface UserProviderConfig {
  provider?: string;
  displayName?: string;
  apiKey?: string;
  apiStyle?: string;
  baseUrl?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
  models?: UserModelConfig[];
}

export interface UserPreferenceModels {
  vision?: string;
  text?: string;
  image?: string;
  video?: string;
  embedding?: string;
  asr?: string;
  tts?: string;
}

export interface UserPreference {
  collector?: string;
  channels?: string[];
  models?: UserPreferenceModels;
  providers?: Record<string, UserProviderConfig>;
}

export interface UpdateProviderReq {
  provider: string;
  apiKey?: string;
  apiStyle?: string;
  baseUrl?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
}

export interface UpdateModelReq {
  name: string;
  type: string;
  multimodal?: boolean;
}

export interface UpdateUserPreferenceReq {
  collector?: string;
  channels?: string[];
  models?: UserPreferenceModels;
  provider?: UpdateProviderReq;
  deleteProvider?: boolean;
  model?: UpdateModelReq;
  deleteModel?: boolean;
}

export async function getUserPreference(): Promise<UserPreference> {
  const res = await api.get("/api/user/preference");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户偏好配置失败");
}

export async function updateUserPreference(req: UpdateUserPreferenceReq): Promise<boolean> {
  const res = await api.post("/api/user/preference/update", req);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "更新用户偏好配置失败");
}

export interface WxClawbotAccount {
  userId: string;
  appId?: string;
  appSecret?: string;
  mode?: string;
  state?: string;
}

export interface BindWxClawbotReq {
  wxClawbotUserId: string;
  appId: string;
  appSecret: string;
}

export interface AiProviderConfig {
  providerName: string;
  apiKey: string;
  baseUrl?: string;
  visionModel?: string;
  textModel?: string;
}

export interface UserModelEntry {
  preference?: UserPreference & {
    vision?: string;
    text?: string;
  };
}

export async function getWxClawbotAccounts(): Promise<WxClawbotAccount[]> {
  const accounts = await fetchWeChatList();
  return accounts.map((account) => ({
    userId: account.userId || "",
    appId: account.appId,
    appSecret: account.appSecret,
    mode: account.mode,
    state: account.state,
  }));
}

export async function bindWxClawbot(req: BindWxClawbotReq): Promise<boolean> {
  const res = await api.post("/api/wechat/clawbot/bind", req);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "绑定微信 ClawBot 失败");
}

export async function unbindWxClawbot(userId: string): Promise<boolean> {
  const res = await api.post("/api/wechat/clawbot/unbind", { userId });
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "解绑微信 ClawBot 失败");
}

export async function getUserAiProvider(): Promise<UserModelEntry> {
  const preference = await getUserPreference();
  return {
    preference: {
      ...preference,
      vision: preference.models?.vision,
      text: preference.models?.text,
    },
  };
}

export async function saveAiProvider(config: AiProviderConfig): Promise<boolean> {
  const provider: UpdateProviderReq = {
    provider: config.providerName,
    apiKey: config.apiKey,
    apiStyle: config.providerName,
    baseUrl: config.baseUrl,
  };
  const modelName = config.textModel || config.visionModel;
  const model = modelName ? { name: modelName, type: config.textModel ? "TEXT" : "VISION", multimodal: Boolean(config.visionModel) } : undefined;
  return updateUserPreference({
    provider,
    model,
    models: {
      text: config.textModel ? `${config.providerName}#${config.textModel}` : undefined,
      vision: config.visionModel ? `${config.providerName}#${config.visionModel}` : undefined,
    },
  });
}

export async function deleteAiProvider(providerName: string): Promise<boolean> {
  return updateUserPreference({
    provider: { provider: providerName },
    deleteProvider: true,
  });
}

export interface AdminLlmProviderConfig extends UserProviderConfig {
  provider?: string;
}

export interface AdminLlmProviderResponse {
  providers?: Record<string, AdminLlmProviderConfig>;
  defaults?: AdminLlmProviderDefaults;
}

export interface AdminLlmProviderDefaults {
  text?: string;
  vision?: string;
}

export interface AdminLlmProviderReq {
  provider?: string;
  originalProvider?: string;
  config: AdminLlmProviderConfig;
}

export interface AdminLlmProviderTestResponse {
  success: boolean;
  status?: "success" | "warning" | "error";
  message: string;
  provider?: string;
  apiStyle?: string;
  model?: string;
  elapsedMs?: number;
}

export async function getAdminLlmProviders(): Promise<AdminLlmProviderResponse> {
  const res = await api.get("/api/admin/env-config/llm-providers");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取供应商配置失败");
}

export async function saveAdminLlmProvider(req: AdminLlmProviderReq): Promise<boolean> {
  const res = await api.post("/api/admin/env-config/llm-providers", req);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "保存供应商配置失败");
}

export async function saveAdminLlmProviderDefaults(defaults: AdminLlmProviderDefaults): Promise<boolean> {
  const res = await api.post("/api/admin/env-config/llm-providers/defaults", defaults);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "保存默认模型失败");
}

export async function testAdminLlmProvider(req: AdminLlmProviderReq): Promise<AdminLlmProviderTestResponse> {
  const res = await api.post("/api/admin/env-config/llm-providers/test", req);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "测试供应商连接失败");
}

export async function deleteAdminLlmProvider(provider: string): Promise<boolean> {
  const res = await api.delete("/api/admin/env-config/llm-providers/" + encodeURIComponent(provider));
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "删除供应商配置失败");
}

export async function submitUserInterest(text: string | String) {
  const res = await api.get("/api/user/interest?text=" + text);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "提交个人订阅偏好失败");
}

export async function fetchUserInterestRecommend({page, size}: {
  page?: number;
  size?: number;
}) {
  const res = await api.post("/api/user/recommend", {
    page: page || 1, size: size || 10
  }, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
   if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新用户信息失败");
}

export async function updateUserDetail(params: UserSaveReq) {
  const res = await api.post("/api/user/update", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新用户信息失败");
}

export async function toPay(
  rechargeLevel: number | string | String,
  vipAmount: number | string | String,
  couponCode: string = ""
) {
  let url = `/api/recharge/toPay`;
  if (rechargeLevel != "") {
    url += `?vipLevel=${rechargeLevel}`;
  } else {
    url += `?vipPrice=${vipAmount}`;
  }
  if (couponCode) {
    url += `&couponCode=${encodeURIComponent(couponCode)}`;
  }
  const res = await api.get(url);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取支付信息失败");
}

export async function markPaying(id: any) {
  // 告诉后端已经支付成功
  const res = await api.get(`/api/recharge/paying?rechargeId=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "同步支付状态失败~ 到购买记录看看吧");
}

export async function refreshPay(id: any) {
  // 告诉后端已经支付成功
  const res = await api.get(`/api/recharge/refreshPay?rechargeId=${id}`);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "重置状态失败~");
}

export interface RechageListItem {
  payId: number;
  tradeNo: string;
  amount: string;
  level: number;
  status: number;
  payTime: number;
  transactionId: string;
}

export interface RechageListResponse {
  list: RechageListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

// 查询用户充值记录
export async function getRechargeList(): Promise<RechageListResponse> {
  const res = await api.get("/api/recharge/listRecords");
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取用户信息失败");
}

// ---------------- 全局配置

export interface GlobalConfigItem {
  app: String;
  items: GlobalConfigItemValue[];
}

export interface GlobalConfigItemValue {
  key: String;
  value: String;
  intro: String;
}

export async function getGlobalConfig(): Promise<{
  [key: string]: GlobalConfigItem;
}> {
  const res = await api.get("/api/common/dict");
  if (res.data && res.data.code === 0) {
    const data = res.data.data;
    const result: { [key: string]: GlobalConfigItem } = {};
    for (const item of data) {
      result[item.app] = item;
    }
    return result;
  }
  throw new Error(res.data?.msg || "获取全局配置失败");
}

// ----------------- 优惠券

export interface CouponListItem {
  couponId?: number;
  // 优惠券code
  couponCode?: string;
  // 优惠券类型
  couponType: number;
  // 优惠券金额/百分比
  couponValue: string;
  // 作用域
  scope: number;
  // 优惠券数量
  couponCount: number;
  // 使用数量
  useCount?: number;
  // 扩展信息
  extra?: string;
  // 开始时间
  startTime: number;
  // 结束时间
  endTime: number;
}

export interface CouponListResponse {
  list: CouponListItem[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export interface CouponListQuery {
  page?: number;
  size?: number;
  code?: string;
  type?: number;
}

export interface UserBo {
  userId: number;
  nickName: string;
  avatar: string;
  role: string;
}

export interface CouponDetail {
  coupon: string;
  payId: number;
  tradeNo: string;
  amount: string;
  level: number;
  status: number;
  payTime: number;
  transactionId: string;
  promotionAmount: string; // 优惠金额
  user: UserBo;
}

export interface CouponDetailListResponse {
  list: CouponDetail[];
  hasMore: boolean;
  page: number;
  size: number;
  total: number;
}

export async function fetchCouponList(
  params?: CouponListQuery
): Promise<CouponListResponse> {
  const res = await api.get("/api/admin/coupon/list", { params });
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取优惠券列表失败");
}

export async function fetchCouponDetail({
  coupon,
  page,
  size,
}: {
  coupon: string;
  page?: number;
  size?: number;
}): Promise<CouponDetailListResponse> {
  const res = await api.get(
    "/api/admin/coupon/useDetail?couponCode=" +
      coupon +
      "&page=" +
      page +
      "&size=" +
      size
  );
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "获取优惠券详情失败");
}

export async function fetchCouponSave(params: CouponListItem) {
  const res = await api.post("/api/admin/coupon/create", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "保存优惠券失败");
}

export async function fetchCouponDelete(id: number) {
  const res = await api.get("/api/admin/coupon/delete?couponId=" + id);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "删除优惠券失败");
}

export async function fetchCouponUpdate(params: CouponListItem) {
  const res = await api.post("/api/admin/coupon/update", params);
  if (res.data && res.data.code === 0) {
    return res.data.data;
  }
  throw new Error(res.data?.msg || "更新优惠券失败");
}

export interface ChannelConfig {
  appId?: string;
  appSecret?: string;
  mode?: string;
  state?: string;
  scope?: string;
  ownerJobClawUserId?: string;
  botName?: string;
  aiCardId?: string;
}

export async function fetchDingTalkList(): Promise<ChannelConfig[]> {
  const res = await api.get("/api/dingding/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  return [];
}

export async function fetchDingTalkBind(params: ChannelConfig): Promise<boolean> {
  const res = await api.post("/api/dingding/bind", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "绑定钉钉机器人失败");
}

export async function fetchFeiShuList(): Promise<ChannelConfig[]> {
  const res = await api.get("/api/feishu/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  return [];
}

export async function fetchFeiShuBind(params: ChannelConfig): Promise<boolean> {
  const res = await api.post("/api/feishu/bind", params);
  if (res.data && res.data.code === 0) {
    return true;
  }
  throw new Error(res.data?.msg || "绑定飞书机器人失败");
}

export async function fetchWeChatBindQrCode(): Promise<{ qrCode: string; qrUrl?: string }> {
  const res = await api.post("/api/wechat/clawbot/qrcode");
  if (res.data && res.data.code === 0) {
    return res.data.data || { qrCode: "" };
  }
  throw new Error(res.data?.msg || "获取二维码失败");
}

export async function fetchWeChatList(): Promise<WeChatAccount[]> {
  const res = await api.get("/api/wechat/clawbot/list");
  if (res.data && res.data.code === 0) {
    return res.data.data || [];
  }
  throw new Error(res.data?.msg || "获取微信绑定信息失败");
}

export interface WeChatAccount {
  appId?: string;
  appSecret?: string;
  userId?: string;
  mode?: string;
  state?: string;
}

export async function fetchWeChatBindStatus(qrCode: string): Promise<{ success: boolean; message?: string; accountId?: string }> {
  const res = await api.get("/api/wechat/clawbot/status", { params: { qrCode } });
  if (res.data && res.data.code === 0) {
    return res.data.data || { success: false };
  }
  return { success: false, message: res.data?.msg };
}
