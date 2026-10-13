"use client";

import { useState, useRef } from "react";
import { Send, Paperclip, X, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ChatInputProps {
  onSend: (message: string, files?: File[]) => Promise<void>;
  disabled?: boolean;
  placeholder?: string;
  onClear?: () => void;
}

export function ChatInput({ onSend, disabled, placeholder = "输入消息...", onClear }: ChatInputProps) {
  const [message, setMessage] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = async () => {
    if (!message.trim() && files.length === 0) return;
    if (sending || disabled) return;

    setSending(true);
    setError(null);
    try {
      await onSend(message, files.length > 0 ? files : undefined);
      setMessage("");
      setFiles([]);
      if (textareaRef.current) {
        textareaRef.current.style.height = "auto";
      }
    } catch (error) {
      console.error("Failed to send message:", error);
      setError(error instanceof Error ? error.message : "发送失败，请稍后重试。");
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const selected = Array.from(e.target.files);
      if (selected.some((file) => {
        const image = /\.(png|jpe?g|gif|webp)$/i.test(file.name);
        const text = /\.(txt|md|csv|json|log)$/i.test(file.name);
        return (!image && !text) || file.size === 0 || file.size > (image ? 5 : 1) * 1024 * 1024;
      })) {
        setError("支持 TXT、Markdown、CSV、JSON、Log（1 MB 内）及 PNG、JPEG、GIF、WebP 图片（5 MB 内），文件不能为空。");
      } else {
        setError(null);
        setFiles((prev) => [...prev, ...selected]);
      }
      e.target.value = "";
    }
  };

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setMessage(e.target.value);
    // 自动调整高度
    e.target.style.height = "auto";
    e.target.style.height = Math.min(e.target.scrollHeight, 120) + "px";
  };

  return (
    <div className="border-t bg-white">
      {error && <p role="alert" className="px-3 pt-2 text-xs text-red-600">{error}</p>}
      {/* 文件预览 */}
      {files.length > 0 && (
        <div className="px-3 pt-2 flex flex-wrap gap-2">
          {files.map((file, index) => (
            <div
              key={index}
              className="flex items-center gap-1 bg-gray-100 rounded px-2 py-1 text-xs"
            >
              <span className="truncate max-w-[100px]">{file.name}</span>
              <button
                onClick={() => removeFile(index)}
                aria-label={`移除附件 ${file.name}`}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 输入区域 */}
      <div className="p-3 flex items-end gap-2">
        <input
          ref={fileInputRef}
          type="file"
          accept=".txt,.md,.csv,.json,.log,.png,.jpg,.jpeg,.gif,.webp"
          multiple
          className="hidden"
          onChange={handleFileChange}
        />
        <Button
          variant="ghost"
          aria-label="添加附件"
          title="添加文本（1 MB 内）或图片（5 MB 内）"
          size="icon"
          className="h-8 w-8 shrink-0"
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled || sending}
        >
          <Paperclip className="h-4 w-4" />
        </Button>

        <textarea
          ref={textareaRef}
          value={message}
          onChange={handleTextareaChange}
          onKeyDown={handleKeyDown}
          aria-label="聊天消息"
          placeholder={placeholder}
          disabled={disabled || sending}
          className="flex-1 resize-none border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 min-h-[36px] max-h-[120px]"
          rows={1}
        />

        <Button
          onClick={handleSend}
          aria-label="发送消息"
          disabled={disabled || sending || (!message.trim() && files.length === 0)}
          className="h-8 w-8 shrink-0 bg-blue-600 hover:bg-blue-700"
          size="icon"
        >
          {sending ? (
            <RotateCcw className="h-4 w-4 animate-spin" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </Button>
      </div>
    </div>
  );
}
