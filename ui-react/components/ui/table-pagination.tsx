"use client"

import { ChevronLeft, ChevronRight, MoreHorizontal } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { cn } from "@/lib/utils"

type PageItem = number | "left-ellipsis" | "right-ellipsis"

interface TablePaginationProps {
  page: number
  pageSize: number
  total: number
  currentCount: number
  loading?: boolean
  totalLabel?: string
  pageSizeOptions?: number[]
  onPageChange: (page: number) => void
  onPageSizeChange: (pageSize: number) => void
}

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

function clampPage(page: number, totalPages: number) {
  return Math.min(totalPages, Math.max(1, page))
}

function buildPageItems(page: number, totalPages: number): PageItem[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1)
  }

  if (page <= 4) {
    return [1, 2, 3, 4, 5, "right-ellipsis", totalPages]
  }

  if (page >= totalPages - 3) {
    return [1, "left-ellipsis", totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages]
  }

  return [1, "left-ellipsis", page - 1, page, page + 1, "right-ellipsis", totalPages]
}

export function TablePagination({
  page,
  pageSize,
  total,
  currentCount,
  loading = false,
  totalLabel = "条任务",
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  onPageChange,
  onPageSizeChange,
}: TablePaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const currentPage = clampPage(page, totalPages)
  const pageItems = buildPageItems(currentPage, totalPages)

  const handleJump = (value: string) => {
    const nextPage = Number(value)
    if (!Number.isInteger(nextPage) || nextPage < 1) {
      return
    }
    onPageChange(clampPage(nextPage, totalPages))
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm text-content-tertiary">
        <span>
          共 {total} {totalLabel}，当前显示 {currentCount} 条
        </span>
        <span className="text-slate-300">|</span>
        <span>每页</span>
        <Select value={String(pageSize)} onValueChange={(value) => onPageSizeChange(Number(value))} disabled={loading}>
          <SelectTrigger className="h-8 w-20 bg-white px-2">
            <SelectValue />
          </SelectTrigger>
          <SelectContent align="start">
            {pageSizeOptions.map((option) => (
              <SelectItem key={option} value={String(option)}>
                {option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span>条</span>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button
          size="sm"
          variant="outline"
          className="h-8 gap-1 px-2.5"
          disabled={currentPage === 1 || loading}
          onClick={() => onPageChange(currentPage - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
          上一页
        </Button>

        <div className="flex items-center gap-1">
          {pageItems.map((item) => {
            if (typeof item !== "number") {
              return (
                <span key={item} className="flex h-8 w-8 items-center justify-center text-content-tertiary">
                  <MoreHorizontal className="h-4 w-4" />
                </span>
              )
            }

            const isActive = item === currentPage
            return (
              <Button
                key={item}
                size="icon"
                variant={isActive ? "default" : "outline"}
                className={cn("h-8 w-8", isActive && "pointer-events-none")}
                disabled={loading}
                onClick={() => onPageChange(item)}
              >
                {item}
              </Button>
            )
          })}
        </div>

        <Button
          size="sm"
          variant="outline"
          className="h-8 gap-1 px-2.5"
          disabled={currentPage >= totalPages || loading}
          onClick={() => onPageChange(currentPage + 1)}
        >
          下一页
          <ChevronRight className="h-4 w-4" />
        </Button>

        <div className="flex items-center gap-1 text-sm text-content-tertiary">
          <span>跳至</span>
          <Input
            type="number"
            min={1}
            max={totalPages}
            className="h-8 w-16 bg-white px-2 text-center"
            disabled={loading}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                handleJump(event.currentTarget.value)
                event.currentTarget.value = ""
              }
            }}
            onBlur={(event) => {
              handleJump(event.currentTarget.value)
              event.currentTarget.value = ""
            }}
          />
          <span>页</span>
        </div>
      </div>
    </div>
  )
}
