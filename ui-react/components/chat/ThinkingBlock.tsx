"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

interface ThinkingBlockProps {
  content: string;
}

export function ThinkingBlock({ content }: ThinkingBlockProps) {
  const [expanded, setExpanded] = useState(false);

  if (!content) return null;

  return (
    <div className="mb-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-1 text-xs text-blue-500 hover:text-blue-600"
      >
        {expanded ? (
          <ChevronDown className="w-3 h-3" />
        ) : (
          <ChevronRight className="w-3 h-3" />
        )}
        <span>思考过程</span>
      </button>
      {expanded && (
        <div className="mt-1 p-2 bg-blue-50 rounded text-xs text-gray-600 whitespace-pre-wrap max-h-32 overflow-y-auto">
          {content}
        </div>
      )}
    </div>
  );
}
