"use client";

import { OPEN_JOB_ASSISTANT_EVENT } from "@/lib/chat-events";
import { useEffect, useState } from "react";
import { useLoginUser } from "@/hooks/useLoginUser";
import { useLoginModal } from "@/hooks/useLoginModal";
import { useWebSocket } from "@/hooks/useWebSocket";
import { ChatMessageList } from "./ChatMessageList";
import { ChatInput } from "./ChatInput";

const QUICK_AGENTS = [
  { id: "default", label: "通用问答", icon: "💬", adminOnly: false },
  { id: "JobRecommendAgent", label: "岗位推荐", icon: "🎯", adminOnly: false },
  { id: "JobFetchAgent", label: "岗位采集", icon: "🕷️", adminOnly: true },
  { id: "preferenceAgent", label: "偏好设置", icon: "⚙️", adminOnly: false },
];

interface ChatDialogProps {
  onClose: () => void;
}

export function ChatDialog({ onClose }: ChatDialogProps) {
  const { userInfo } = useLoginUser();
  const { setLoginOpen } = useLoginModal();
  const { connected, connectionState, currentAgent, waiting, messages, connect, disconnect, sendMessage, clearMessages } =
    useWebSocket(userInfo?.userId);
  const [minimized, setMinimized] = useState(false);

  useEffect(() => {
    const restore = () => setMinimized(false);
    window.addEventListener(OPEN_JOB_ASSISTANT_EVENT, restore);
    return () => window.removeEventListener(OPEN_JOB_ASSISTANT_EVENT, restore);
  }, []);

  useEffect(() => {
    if (userInfo) {
      const token = localStorage.getItem("oc-token");
      if (token) {
        connect(token, String(userInfo.userId));
      }
    }
    return () => { disconnect(); };
  }, [userInfo?.userId, connect, disconnect]);

  const handleSend = async (message: string, files?: File[]) => {
    if (!userInfo) return;
    let fileIds: string[] | undefined;
    let mediaIds: string[] | undefined;
    if (files && files.length > 0) {
      const { uploadFile } = await import("@/lib/ws");
      const uploaded = await Promise.all(
        files.map((f) => uploadFile(f, String(userInfo.userId)))
      );
      fileIds = uploaded.filter((_, index) => !/\.(png|jpe?g|gif|webp)$/i.test(files[index].name)).map((f) => f!.fileId);
      mediaIds = uploaded.filter((_, index) => /\.(png|jpe?g|gif|webp)$/i.test(files[index].name)).map((f) => f!.fileId);
    }
    sendMessage(message, fileIds, mediaIds);
  };

  const switchAgent = (agentId: string) => {
    if (!connected) return;
    sendMessage(`/agent ${agentId}`);
  };

  const isAdmin = userInfo?.role === 3;
  const visibleAgents = QUICK_AGENTS.filter((a) => !a.adminOnly || isAdmin);
  const agentLabel = visibleAgents.find((a) => a.id === currentAgent)?.label || currentAgent;
  const statusLabel = !userInfo ? "请先登录" : ({
    disconnected: "未连接", connecting: "连接中…", connected: "已连接",
    reconnecting: "连接已断开，正在重连…", "auth-error": "登录已失效，请重新登录",
  } as const)[connectionState];

  if (minimized) {
    return (
      <div className="fixed bottom-24 right-6 z-50">
        <button
          onClick={() => setMinimized(false)}
          className="bg-white rounded-lg shadow-lg border px-4 py-2 text-sm font-medium hover:bg-gray-50"
        >
          💬 JobClaw助手
        </button>
      </div>
    );
  }

  return (
    <div className="fixed bottom-24 right-4 sm:right-6 z-50 w-[460px] max-w-[calc(100vw-2rem)] h-[600px] max-h-[calc(100dvh-8rem)] bg-white rounded-2xl shadow-2xl shadow-slate-900/15 border border-slate-200 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="bg-slate-900 text-white px-4 py-4 flex items-center justify-between">
        <div className="flex items-center gap-2 min-w-0">
          <div className={`w-2 h-2 rounded-full shrink-0 ${connected ? "bg-emerald-400" : "bg-amber-300"}`} />
          <span className="font-medium truncate">JobClaw助手</span>
          <span className="text-xs opacity-75 bg-white/15 px-1.5 py-0.5 rounded shrink-0">
            {agentLabel}
          </span>
        </div>
        <div className="flex items-center gap-1 shrink-0">
          <button aria-label="最小化聊天" onClick={() => setMinimized(true)} className="hover:bg-white/10 rounded p-1 text-sm">
            ─
          </button>
          <button aria-label="关闭聊天" onClick={onClose} className="hover:bg-white/10 rounded p-1 text-sm">
            ✕
          </button>
        </div>
      </div>

      <div role="status" className="px-4 py-2 text-xs text-slate-600 bg-slate-50 flex items-center justify-between gap-2">
        <span>{statusLabel}</span>
        {(!userInfo || connectionState === "auth-error") && (
          <button onClick={() => setLoginOpen(true)} className="text-blue-600 font-medium">登录后开始对话</button>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-hidden">
        <ChatMessageList messages={messages} />
      </div>
      {waiting && <p role="status" className="px-4 py-2 text-xs text-slate-500">正在思考，请稍候…</p>}

      {/* Agent Tags */}
      <div className="border-t border-slate-100 px-3 py-2 flex flex-wrap gap-1.5">
        {visibleAgents.map((agent) => (
          <button
            key={agent.id}
            onClick={() => switchAgent(agent.id)}
            disabled={!connected}
            className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-medium transition-colors disabled:opacity-50 ${
              currentAgent === agent.id
                ? "bg-blue-100 text-blue-700 ring-1 ring-blue-300"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            <span className="mr-1">{agent.icon}</span>
            {agent.label}
          </button>
        ))}
      </div>

      {/* Input */}
      <ChatInput
        onSend={handleSend}
        disabled={!connected}
        placeholder={!userInfo ? "请先登录后再发送消息" : connected ? "输入消息，Enter 发送，Shift+Enter 换行" : "连接恢复后即可发送消息"}
        onClear={clearMessages}
      />
    </div>
  );
}
