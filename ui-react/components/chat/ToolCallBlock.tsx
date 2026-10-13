"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, Wrench, FileText } from "lucide-react";

interface ToolCallBlockProps {
  tool: string;
  isResult?: boolean;
}

export function ToolCallBlock({ tool, isResult }: ToolCallBlockProps) {
  const [expanded, setExpanded] = useState(false);

  if (!tool) return null;

  return (
    <div className="mb-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-1 text-xs text-orange-500 hover:text-orange-600"
      >
        {expanded ? (
          <ChevronDown className="w-3 h-3" />
        ) : (
          <ChevronRight className="w-3 h-3" />
        )}
        {isResult ? (
          <FileText className="w-3 h-3" />
        ) : (
          <Wrench className="w-3 h-3" />
        )}
        <span>{isResult ? "执行结果" : "工具调用"}</span>
      </button>
      {expanded && (
        <div className="mt-1 p-2 bg-orange-50 rounded text-xs text-gray-600 whitespace-pre-wrap max-h-32 overflow-y-auto">
          {tool}
        </div>
      )}
    </div>
  );
}
