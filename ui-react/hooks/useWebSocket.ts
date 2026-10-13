"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { wsClient, WsResponseMessage, ConnectionState } from "@/lib/ws";

const STORAGE_KEY = "jobclaw_chat_history";
const MAX_MESSAGES = 50;

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  thinking?: string;
  tool?: string;
  toolResult?: string;
  timestamp: number;
  streaming?: boolean;
}

function loadHistory(storageKey: string): ChatMessage[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return [];
    const list: ChatMessage[] = JSON.parse(raw);
    return list.filter((m) => !m.streaming).slice(-MAX_MESSAGES);
  } catch {
    return [];
  }
}

function saveHistory(storageKey: string, messages: ChatMessage[]) {
  if (typeof window === "undefined") return;
  try {
    const finished = messages.filter((m) => !m.streaming).slice(-MAX_MESSAGES);
    localStorage.setItem(storageKey, JSON.stringify(finished));
  } catch { /* ignore */ }
}

export function useWebSocket(userId?: number) {
  // AI-GENERATED AIDEV-NOTE: 对话记录按用户隔离；切换账号时由父组件重新挂载。
  const storageKey = `${STORAGE_KEY}:${userId ?? "anonymous"}`;
  const [connectionState, setConnectionState] = useState<ConnectionState>("disconnected");
  const connected = connectionState === "connected";
  const [currentAgent, setCurrentAgent] = useState("default");
  const [messages, setMessages] = useState<ChatMessage[]>(() => loadHistory(storageKey));
  const [waiting, setWaiting] = useState<boolean>(false);

  // 流式消息映射：后端 msgId → 前端 assistant 消息 ID
  const rspIdMapRef = useRef<Map<string, string>>(new Map());

  useEffect(() => { saveHistory(storageKey, messages); }, [storageKey, messages]);

  useEffect(() => {
    if (!waiting) return;
    const timer = setTimeout(() => {
      setWaiting(false);
      appendAssistant("等待回复超时，请检查连接后重试。");
    }, 120000);
    return () => clearTimeout(timer);
  }, [waiting]);

  const appendAssistant = useCallback((content: string, extra?: Partial<ChatMessage>) => {
    setMessages((prev) => [
      ...prev,
      {
        id: `asst_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        role: "assistant",
        content,
        timestamp: Date.now(),
        streaming: false,
        ...extra,
      },
    ]);
  }, []);

  const handleMessage = useCallback((msg: WsResponseMessage) => {
    const backendMsgId = msg.msgId;
    if (msg.type === "text") {
      const match = msg.content?.match(/已为您切换到\s+([a-zA-Z_]+)/)
        || msg.content?.match(/当前会话绑定的 Agent：[\s\S]*?\*\*([a-zA-Z_]+)\*\*/);
      if (match) setCurrentAgent(match[1]);
      else if (msg.content?.includes("当前会话未绑定任何Agent")) setCurrentAgent("default");
      if (backendMsgId?.startsWith("state_")) return;
    }
    setWaiting(false);

    // ---- stream_done：结束流式状态 ----
    if (msg.type === "stream_done") {
      const asstId = backendMsgId ? rspIdMapRef.current.get(backendMsgId) : undefined;
      if (asstId) {
        setMessages((prev) =>
          prev.map((m) => (m.id === asstId ? { ...m, streaming: false } : m))
        );
        rspIdMapRef.current.delete(backendMsgId!);
      }
      return;
    }

    // ---- error ----
    if (msg.type === "error") {
      const asstId = backendMsgId ? rspIdMapRef.current.get(backendMsgId) : undefined;
      if (asstId) {
        setMessages((prev) => prev.map((m) => m.id === asstId ? { ...m, streaming: false } : m));
        rspIdMapRef.current.delete(backendMsgId);
      }
      appendAssistant(msg.content || "请求处理出错，请稍后重试");
      return;
    }

    // ---- text：后端同步响应（系统命令、非流式 Agent 等），直接新建消息 ----
    if (msg.type === "text") {
      appendAssistant(msg.content || "");
      return;
    }

    // ---- stream chunk ----
    if (!backendMsgId) return;

    // AI-GENERATED AIDEV-NOTE: React 严格模式会重放 updater，不能在其中修改 ref。
    let asstId = rspIdMapRef.current.get(backendMsgId);
    if (!asstId) {
      asstId = `asst_${backendMsgId}_${Date.now()}`;
      rspIdMapRef.current.set(backendMsgId, asstId);
    }
    const assistantId = asstId;
    setMessages((prev) => {
      if (!prev.some((message) => message.id === assistantId)) {
        return [
          ...prev,
          {
            id: assistantId,
            role: "assistant" as const,
            content: msg.content || "",
            thinking: msg.thinking || undefined,
            tool: msg.tool || undefined,
            toolResult: msg.toolResult || undefined,
            timestamp: Date.now(),
            streaming: true,
          },
        ];
      }

      // 后续 chunk：追加内容
      return prev.map((m) => {
        if (m.id !== assistantId) return m;
        return {
          ...m,
          content: m.content + (msg.content || ""),
          thinking: (m.thinking || "") + (msg.thinking || "") || undefined,
          tool: (m.tool || "") + (msg.tool ? msg.tool + "\n" : "") || undefined,
          toolResult: (m.toolResult || "") + (msg.toolResult ? msg.toolResult + "\n" : "") || undefined,
          streaming: true,
        };
      });
    });
  }, [appendAssistant]);

  const connect = useCallback(
    (token: string, userId: string) => {
      wsClient.onMessage(handleMessage);
      // AI-GENERATED AIDEV-NOTE: 每次重连查询真实会话，避免标题沿用过期 Agent。
      wsClient.onConnectionChange((state) => {
        setConnectionState(state);
        if (state === "connected") {
          wsClient.sendMessage({ msgId: `state_${Date.now()}`, message: "/current" });
        }
      });
      wsClient.connect(token, userId);
    },
    [handleMessage]
  );

  const disconnect = useCallback(() => {
    wsClient.disconnect();
    setConnectionState("disconnected");
    setWaiting(false);
  }, []);

  const sendMessage = useCallback(
    (message: string, files?: string[], medias?: string[]) => {
      const msgId = `msg_${Date.now()}_${Math.random().toString(36).slice(2)}`;
      wsClient.sendMessage({ msgId, message, files, medias });
      setWaiting(true);
      setMessages((msgs) => [
        ...msgs,
        { id: msgId, role: "user", content: message, timestamp: Date.now() },
      ]);
    },
    []
  );

  const clearMessages = useCallback(() => {
    setMessages([]);
    rspIdMapRef.current.clear();
  }, []);

  return { connected, connectionState, currentAgent, waiting, messages, connect, disconnect, sendMessage, clearMessages };
}
