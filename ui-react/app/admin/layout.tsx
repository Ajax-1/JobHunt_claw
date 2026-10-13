"use client"
import React from "react"
import { usePathname, useSearchParams } from "next/navigation"
import Link from "next/link"
import {
    Activity,
    BookOpenText,
    Bot,
    BriefcaseBusiness,
    ClipboardList,
    Database,
    FilePlus2,
    Files,
    ListChecks,
    ServerCog,
    TicketPercent,
    Users,
    type LucideIcon,
} from "lucide-react"

interface AdminMenuItem {
    label: string
    path: string
    icon: LucideIcon
    match?: (pathname: string, searchParams: SearchParamsLike) => boolean
}

interface SearchParamsLike {
    get(name: string): string | null
}

const collectMenu: AdminMenuItem[] = [
    {
        label: "新建采集",
        path: "/admin/entry",
        icon: FilePlus2,
        match: (pathname, searchParams) => pathname === "/admin/entry" && searchParams.get("tab") !== "tasks",
    },
    {
        label: "采集任务",
        path: "/admin/entry?tab=tasks",
        icon: ListChecks,
        match: (pathname, searchParams) => pathname === "/admin/entry" && searchParams.get("tab") === "tasks" && searchParams.get("runnerType") !== "agent",
    },
    { label: "草稿审核", path: "/admin/drafts", icon: Files },
    { label: "职位库", path: "/admin/jobs", icon: BriefcaseBusiness },
]

const agentMenu: AdminMenuItem[] = [
    { label: "运行工作台", path: "/admin/progress", icon: Bot },
    {
        label: "Agent任务",
        path: "/admin/progress/tasks",
        icon: ListChecks,
        match: (pathname) => pathname === "/admin/progress/tasks",
    },
    {
        label: "Agent草稿",
        path: "/admin/drafts?runnerType=agent",
        icon: Files,
        match: (pathname, searchParams) => pathname === "/admin/drafts" && searchParams.get("runnerType") === "agent",
    },
    {
        label: "Agent职位",
        path: "/admin/jobs?runnerType=agent",
        icon: BriefcaseBusiness,
        match: (pathname, searchParams) => pathname === "/admin/jobs" && searchParams.get("runnerType") === "agent",
    },
]

const sourceMenu: AdminMenuItem[] = [
    { label: "来源列表", path: "/admin/sources", icon: Database },
    {
        label: "来源任务",
        path: "/admin/sources/tasks",
        icon: ClipboardList,
        match: (pathname) => pathname === "/admin/sources/tasks",
    },
]

const configMenu: AdminMenuItem[] = [
    { label: "LLM供应商", path: "/admin/llm-providers", icon: ServerCog },
    { label: "大模型监控", path: "/admin/llm-monitor", icon: Activity },
    { label: "字典管理", path: "/admin/dict", icon: BookOpenText },
    { label: "用户管理", path: "/admin/users", icon: Users },
    { label: "券码管理", path: "/admin/coupon", icon: TicketPercent },
]

function getMenu(pathname: string, searchParams: SearchParamsLike) {
    if (pathname === "/admin/progress" || pathname.startsWith("/admin/progress/") || searchParams.get("runnerType") === "agent") {
        return agentMenu
    }
    if (pathname === "/admin/sources" || pathname.startsWith("/admin/sources/") || searchParams.get("sourceId")) {
        return sourceMenu
    }
    if (pathname === "/admin/llm-providers" || pathname === "/admin/llm-monitor" || pathname === "/admin/dict" || pathname === "/admin/users" || pathname.startsWith("/admin/coupon") || pathname === "/admin/user-config") {
        return configMenu
    }
    return collectMenu
}

// AI-GENERATED AIDEV-NOTE: 导航直接展示页面入口，正文从实际操作区开始。
export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname()
    const searchParams = useSearchParams()
    const menu = getMenu(pathname, searchParams)
    return (
        <div className="flex min-h-[calc(100svh-4rem)] flex-col md:h-[calc(100svh-4rem)] md:flex-row md:overflow-hidden">
            <aside className="flex shrink-0 flex-col border-b border-slate-200/80 bg-white md:w-52 md:border-b-0 md:border-r">
                <nav aria-label="后台页面" className="flex gap-1 overflow-x-auto p-3 md:flex-col md:px-3 md:py-5">
                    {menu.map(item => {
                        const Icon = item.icon
                        const active = item.match ? item.match(pathname, searchParams) : pathname === item.path.split("?")[0]
                        return <Link key={item.path} href={item.path} aria-current={active ? "page" : undefined} className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-3 text-[13px] transition-colors ${active ? "bg-blue-50 font-semibold text-blue-700" : "text-slate-500 hover:bg-slate-50 hover:text-slate-900"}`}><Icon className="h-[17px] w-[17px]" />{item.label}{active && <span className="ml-auto hidden h-1.5 w-1.5 rounded-full bg-blue-600 md:block" />}</Link>
                    })}
                </nav>
                <div className="mt-auto hidden px-6 py-5 md:block"><Link href="/" className="text-xs text-slate-400 hover:text-blue-600">浏览用户端 ↗</Link></div>
            </aside>
            <main className="admin-workspace min-h-0 min-w-0 flex-1 overflow-y-auto">
                {children}
            </main>
        </div>
    )
}
