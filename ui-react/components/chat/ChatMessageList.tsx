"use client";

import { useEffect, useRef } from "react";
import { ChatMessageItem } from "./ChatMessageItem";
import type { ChatMessage } from "@/hooks/useWebSocket";

interface ChatMessageListProps {
  messages: ChatMessage[];
}

export function ChatMessageList({ messages }: ChatMessageListProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  return (
    <div
      ref={scrollRef}
      className="h-full overflow-y-auto p-4 space-y-4"
    >
      {messages.length === 0 ? (
        <div className="h-full flex flex-col items-center justify-center text-gray-400">
          <div className="text-4xl mb-4">💬</div>
          <p className="text-sm">有什么可以帮你的？</p>
          <p className="text-xs mt-1">试试问我关于求职的问题</p>
        </div>
      ) : (
        messages.map((msg) => (
          <ChatMessageItem key={msg.id} message={msg} />
        ))
      )}
    </div>
  );
}
