"use client";

import { OPEN_JOB_ASSISTANT_EVENT } from "@/lib/chat-events";
import { useEffect, useState } from "react";
import { MessageCircle, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ChatDialog } from "./ChatDialog";
import { useLoginUser } from "@/hooks/useLoginUser";

interface FloatingChatButtonProps {
  hasUnread?: boolean;
}

export function FloatingChatButton({ hasUnread }: FloatingChatButtonProps) {
  const { userInfo } = useLoginUser();
  const [open, setOpen] = useState<boolean>(false);
  useEffect(() => {
    const openAssistant = () => setOpen(true);
    window.addEventListener(OPEN_JOB_ASSISTANT_EVENT, openAssistant);
    return () => window.removeEventListener(OPEN_JOB_ASSISTANT_EVENT, openAssistant);
  }, []);

  return (
    <>
      {open && <ChatDialog key={userInfo?.userId ?? "anonymous"} onClose={() => setOpen(false)} />}

      <div className="fixed bottom-6 right-6 z-50">
        <Button
          aria-label={open ? "收起求职助手" : "打开求职助手"}
          onClick={() => setOpen(!open)}
          className="h-14 w-14 rounded-full shadow-lg bg-blue-600 hover:bg-blue-700 text-white"
          size="icon"
        >
          {open ? (
            <X className="h-6 w-6" />
          ) : (
            <MessageCircle className="h-6 w-6" />
          )}
        </Button>

        {hasUnread && !open && (
          <Badge
            variant="destructive"
            className="absolute -top-1 -right-1 h-5 w-5 rounded-full p-0 flex items-center justify-center text-xs"
          >
            1
          </Badge>
        )}
      </div>
    </>
  );
}
