"use client"

import { useState } from "react"
import { BookmarkCheck, BookmarkPlus, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useLoginModal } from "@/hooks/useLoginModal"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"
import { saveApplication } from "@/lib/api"

interface SaveApplicationButtonProps {
  jobId: number
  compact?: boolean
}

export function SaveApplicationButton({ jobId, compact = false }: SaveApplicationButtonProps) {
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const { userInfo } = useLoginUser()
  const { setLoginOpen } = useLoginModal()
  const { toast } = useToast()

  const handleSave = async () => {
    if (!userInfo) {
      setLoginOpen(true)
      return
    }
    if (saving || saved) return
    setSaving(true)
    try {
      await saveApplication(jobId)
      setSaved(true)
      toast({ title: "已保存到我的求职", description: "可以在我的求职中更新投递进度。" })
    } catch (error) {
      toast({ title: "保存失败", description: error instanceof Error ? error.message : "请稍后重试", variant: "destructive" })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Button type="button" variant={saved ? "secondary" : "outline"} size={compact ? "sm" : "default"} onClick={handleSave} disabled={saving}>
      {saving ? <Loader2 className="animate-spin" /> : saved ? <BookmarkCheck /> : <BookmarkPlus />}
      {saving ? "保存中" : saved ? "已保存" : "保存岗位"}
    </Button>
  )
}
