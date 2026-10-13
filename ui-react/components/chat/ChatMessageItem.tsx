"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ThinkingBlock } from "./ThinkingBlock";
import { ToolCallBlock } from "./ToolCallBlock";
import type { ChatMessage } from "@/hooks/useWebSocket";

interface ChatMessageItemProps {
  message: ChatMessage;
}

export function ChatMessageItem({ message }: ChatMessageItemProps) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`min-w-0 max-w-[85%] rounded-xl px-4 py-2 ${
          isUser
            ? "bg-blue-600 text-white"
            : "bg-gray-100 text-gray-900"
        }`}
      >
        {/* 思考过程 */}
        {message.thinking && (
          <ThinkingBlock content={message.thinking} />
        )}

        {/* 工具调用 */}
        {message.tool && <ToolCallBlock tool={message.tool} />}

        {/* 工具结果 */}
        {message.toolResult && (
          <ToolCallBlock tool={message.toolResult} isResult />
        )}

        {/* 消息内容 */}
        {message.content && (
          <div className="break-words [overflow-wrap:anywhere] text-sm leading-relaxed">
            {isUser ? <span className="whitespace-pre-wrap">{message.content}</span> : (
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={{
                p: ({ children }) => <p className="mb-2 last:mb-0 whitespace-pre-wrap">{children}</p>,
                h1: ({ children }) => <h3 className="font-semibold text-base my-2">{children}</h3>,
                h2: ({ children }) => <h3 className="font-semibold text-base my-2">{children}</h3>,
                h3: ({ children }) => <h3 className="font-semibold my-2">{children}</h3>,
                ul: ({ children }) => <ul className="list-disc pl-5 space-y-1 my-2">{children}</ul>,
                ol: ({ children }) => <ol className="list-decimal pl-5 space-y-1 my-2">{children}</ol>,
                a: ({ href, children }) => <a href={href} target="_blank" rel="noopener noreferrer" className="text-blue-600 underline">{children}</a>,
                table: ({ children }) => <div className="overflow-x-auto my-2"><table className="min-w-[460px] text-xs border-collapse">{children}</table></div>,
                th: ({ children }) => <th className="border border-slate-300 p-2 text-left whitespace-nowrap">{children}</th>,
                td: ({ children }) => <td className="border border-slate-300 p-2 align-top">{children}</td>,
                pre: ({ children }) => <pre className="overflow-x-auto rounded bg-slate-200 p-2 my-2">{children}</pre>,
              }}>{message.content.replace(/\[(?:SOUL|IDENTITY|USER_IDENTITY)_COLLECTION_COMPLETE\]/g, "")}</ReactMarkdown>
            )}
          </div>
        )}

        {/* 流式加载指示器 */}
        {message.streaming && !message.content && (
          <div className="flex items-center gap-1 text-gray-400">
            <span className="animate-bounce">●</span>
            <span className="animate-bounce" style={{ animationDelay: "0.1s" }}>●</span>
            <span className="animate-bounce" style={{ animationDelay: "0.2s" }}>●</span>
          </div>
        )}

        {/* 时间戳 */}
        <div
          className={`text-xs mt-1 ${
            isUser ? "text-blue-200" : "text-gray-400"
          }`}
        >
          {new Date(message.timestamp).toLocaleTimeString("zh-CN", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </div>
      </div>
    </div>
  );
}
