"use client"

import { useState, useEffect } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { ArrowLeft, MapPin, Calendar, Users, Building, Briefcase, Clock, ExternalLink } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { externalUrl } from "@/lib/links"
import { jobDetail } from "@/lib/api"

interface JobDetail {
    id: number
    draftId: number
    companyName: string
    companyType: string
    jobLocation: string
    recruitmentType: string
    recruitmentTarget: string
    position: string
    deliveryProgress: string
    lastUpdatedTime: string
    deadline: string
    relatedLink: string
    jobAnnouncement: string
    internalReferralCode: string
    remarks: string
    state: number
    createTime: number
    updateTime: number
}

export default function JobDetailPage() {
    const searchParams = useSearchParams()
    const router = useRouter()
    const [job, setJob] = useState<JobDetail | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const id = searchParams.get("id")
        if (!id) {
            setJob(null)
            setError("未指定职位ID")
            return
        }
        setLoading(true)
        setError(null)
        jobDetail(Number(id))
            .then((data) => setJob(data))
            .catch((err) => {
                setJob(null)
                setError(err.message || "获取职位信息失败")
            })
            .finally(() => setLoading(false))
    }, [searchParams])

    if (loading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="text-gray-500 text-lg">加载中...</div>
            </div>
        )
    }
    if (error) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="text-center">
                    <h2 className="text-2xl font-bold text-red-600 mb-4">{error}</h2>
                    <Button onClick={() => router.back()}>返回列表</Button>
                </div>
            </div>
        )
    }
    if (!job) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="text-center">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">职位不存在</h2>
                    <Button onClick={() => router.back()}>返回列表</Button>
                </div>
            </div>
        )
    }

    // AI-GENERATED AIDEV-NOTE: 招聘公告与岗位分区展示，保留原始岗位字符串和链接。
    const applyUrl = externalUrl(job.relatedLink)
    const noticeUrl = externalUrl(job.jobAnnouncement)
    const positions = job.position?.split(/[,，、;；\n]+/).map(value => value.trim()).filter(Boolean) || []
    return (
        <div className="mx-auto max-w-6xl px-4 pb-12 pt-7 sm:px-8">
            <Button variant="ghost" onClick={() => router.back()} className="mb-6 -ml-3 text-slate-500"><ArrowLeft />返回招聘列表</Button>
            <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-9">
                <div className="eyebrow">招聘详情</div>
                <div className="mt-4 flex flex-col justify-between gap-6 sm:flex-row sm:items-center"><div><h1 className="text-3xl font-semibold tracking-tight text-slate-950">{job.companyName}</h1><div className="mt-4 flex flex-wrap gap-2">{[job.companyType, job.recruitmentType, job.recruitmentTarget].filter(Boolean).map((value,i) => <span className="soft-tag" key={i}>{value}</span>)}</div></div>
                {applyUrl ? <Button asChild size="lg"><a href={applyUrl} target="_blank" rel="noopener noreferrer">前往投递<ExternalLink /></a></Button> : <Button size="lg" disabled>投递链接待补充</Button>}</div>
                <div className="mt-7 flex flex-wrap gap-x-7 gap-y-3 border-t border-slate-100 pt-5 text-sm text-slate-500"><span className="inline-flex items-start gap-2"><MapPin className="h-4 w-4 shrink-0" />{job.jobLocation || "地点待补充"}</span><span className="inline-flex items-center gap-2"><Calendar className="h-4 w-4" />{job.lastUpdatedTime || "日期待补充"} 更新</span></div>
            </section>
            <div className="mt-6 grid items-start gap-6 lg:grid-cols-[1fr_300px]">
                <div className="space-y-6"><section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><h2 className="flex items-center gap-2 text-lg font-semibold"><Briefcase className="h-5 w-5 text-blue-600" />招聘岗位</h2><p className="mt-2 text-sm text-slate-500">岗位要求与具体投递方式，请以招聘方原始公告为准。</p><div className="mt-6 flex flex-wrap gap-2">{positions.length ? positions.map((position,i) => <span key={i} className="max-w-full rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-sm leading-7 text-slate-700 [overflow-wrap:anywhere]">{position}</span>) : <p className="text-sm text-slate-400">暂无岗位描述</p>}</div></section>
                <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8"><h2 className="text-lg font-semibold">补充说明</h2><p className="mt-4 whitespace-pre-wrap break-words text-sm leading-7 text-slate-600">{job.remarks?.replace(/；?GF简历ID:[^；\s]+/g, "") || "暂无补充说明，请查看招聘公告。"}</p>{job.internalReferralCode && job.internalReferralCode !== "-" && <div className="mt-5 rounded-lg bg-blue-50 p-4 text-sm text-blue-800">内推码：<code className="font-semibold">{job.internalReferralCode}</code></div>}</section></div>
                <aside className="rounded-2xl border border-slate-200 bg-white p-6 lg:sticky lg:top-24"><div className="eyebrow">投递前确认</div><div className="mt-6 space-y-5 text-sm"><div><p className="text-xs text-slate-400">投递截止</p><p className="mt-2 font-medium">{job.deadline || "暂未注明"}</p></div><div><p className="text-xs text-slate-400">面向对象</p><p className="mt-2 font-medium">{job.recruitmentTarget || "请查看原始公告"}</p></div></div><div className="mt-6 border-t border-slate-100 pt-5">{noticeUrl && <Button variant="outline" asChild className="w-full"><a href={noticeUrl} target="_blank" rel="noopener noreferrer">查看原始公告<ExternalLink /></a></Button>}<p className="mt-3 text-xs leading-6 text-slate-400">投递将在招聘来源网站中完成，请留意对方页面的招聘进度。</p></div></aside>
            </div>
        </div>
    )
}
