"use client"
import { useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Loader2, Search, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { TablePagination } from "@/components/ui/table-pagination"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { JobListQuery, JobListResponse, GlobalConfigItemValue, fetchAdminJobList, submitOcEntry, updateOcState, execPublishBlogs } from "@/lib/api"
import { formatDateTime } from '@/lib/utils'
import { useToast } from "@/hooks/use-toast"
import { getConfigValue } from "@/lib/config"
import { Switch } from "@/components/ui/switch"

interface JobOffer {
    id: string | number
    companyName: string
    companyType: string
    companyIndustry: string
    jobLocation: string
    recruitmentType: string
    recruitmentTarget: string
    position: string
    applicationProgress: string
    lastUpdatedTime: string
    updateTime: string
    deadline: string
    relatedLink: string
    jobAnnouncement: string
    internalReferralCode: string
    state: number,
    remarks: string
}
const PAGE_SIZE = 10
const JOB_TABLE_MIN_WIDTH = 1280
const STICKY_ACTION_HEAD_CLASS =
    "sticky right-0 z-20 border-l border-slate-200 bg-slate-100/95 text-right shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"
const STICKY_ACTION_CELL_CLASS =
    "sticky right-0 z-10 border-l border-slate-200 bg-slate-50/95 text-right shadow-[-14px_0_22px_-18px_rgba(15,23,42,0.9)]"
export default function JobsPage() {
    const router = useRouter()
    const searchParams = useSearchParams()
    const sourceId = searchParams.get("sourceId") || ""
    const sourceTaskId = searchParams.get("sourceTaskId") || ""
    const runnerType = searchParams.get("runnerType") || ""
    const [jobOffers, setJobOffers] = useState<any[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [page, setPage] = useState(1)
    const [pageSize, setPageSize] = useState(PAGE_SIZE)
    const [total, setTotal] = useState(0)
    const [filters, setFilters] = useState<JobListQuery>({
        ...(sourceId ? { sourceId: Number(sourceId) } : {}),
        ...(sourceTaskId ? { sourceTaskId: Number(sourceTaskId) } : {}),
        ...(runnerType ? { runnerType } : {}),
    })
    const [editingOffer, setEditingOffer] = useState<JobOffer | null>(null)
    const [isAddingNew, setIsAddingNew] = useState(false)
    const [isDialogOpen, setIsDialogOpen] = useState(false)
    const [ocToDelete, setOcToDelete] = useState<JobOffer | null>(null)

    const { toast } = useToast();
    const [companyTypes, setCompanyTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTypes, setRecruitmentTypes] = useState<GlobalConfigItemValue[]>([]);
    const [recruitmentTarget, setRecruitmentTarget] = useState<GlobalConfigItemValue[]>([]);

    const handleDelete = async () => {
        if (!ocToDelete) return;
        updateOcState({ id: ocToDelete.id as number, state: -1 }).then((res) => {
            toast({
                title: "成功",
                description: "已删除",
            })
            setJobOffers(jobOffers.filter((offer) => offer.id != ocToDelete.id))
            setIsDialogOpen(false)
        }).catch(err => {
            setIsDialogOpen(false)
            toast({
                title: "失败",
                description: err.message,
                variant: "destructive",
            })
        })
    }

    const handleSave = (offer: JobOffer) => {
        submitOcEntry({ ...offer, id: offer.id }).then((res) => {
            toast({ title: '保存成功', description: '职位信息已保存' })
            setJobOffers(jobOffers.map((o) => (o.id === offer.id ? offer : o)))
            setEditingOffer(null)
        }).catch((e) => {
            toast({ title: '保存失败', description: e.message, variant: "destructive" })
        })
    }

    const handleOcStateChange = async (id: number, state: number) => {
        updateOcState({ id: id, state: state }).then((res) => {
            toast({ title: '状态更新成功', description: '职位状态已更新' })
            setJobOffers(jobOffers.map((o) => (o.id === id ? { ...o, state: state } : o)))
        }).catch((e) => {
            toast({ title: '状态更新失败', description: e.message })
        })
    }


    const fetchData = async (params: JobListQuery = {}) => {
        setLoading(true)
        setError(null)
        try {
            const res: JobListResponse = await fetchAdminJobList({ page, size: pageSize, ...filters, ...params })
            setJobOffers(res.list)
            setTotal(res.total)
        } catch (e: any) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
        getConfigValue('oc', 'CompanyTypeEnum').then(setCompanyTypes);
        getConfigValue('oc', 'RecruitmentTypeEnum').then(setRecruitmentTypes);
        getConfigValue('oc', 'RecruitmentTargetEnum').then(setRecruitmentTarget);
        // eslint-disable-next-line
    }, [page, pageSize, filters])

    useEffect(() => {
        setFilters((prev) => ({
            ...prev,
            sourceId: sourceId ? Number(sourceId) : undefined,
            sourceTaskId: sourceTaskId ? Number(sourceTaskId) : undefined,
            runnerType: runnerType || undefined,
        }))
        setPage(1)
    }, [runnerType, sourceId, sourceTaskId])

    const handleFilterChange = (key: keyof JobListQuery, value: string) => {
        if (value == '-1') {
            value = ''
        }
        setFilters((prev) => ({ ...prev, [key]: value }))
        setPage(1)
    }

    const resetFilters = () => {
        setFilters({
            ...(sourceId ? { sourceId: Number(sourceId) } : {}),
            ...(sourceTaskId ? { sourceTaskId: Number(sourceTaskId) } : {}),
            ...(runnerType ? { runnerType } : {}),
        })
        setPage(1)
    }

    const clearSourceFilter = () => {
        const params = new URLSearchParams(searchParams.toString())
        params.delete("sourceId")
        params.delete("sourceTaskId")
        params.delete("runnerType")
        const query = params.toString()
        router.replace(query ? `/admin/jobs?${query}` : "/admin/jobs")
    }

    return (
        <div className="min-h-screen bg-surface-muted">
            <div className="mx-auto max-w-[1440px] px-6 py-5">
                <section className="rounded-lg border border-surface-border bg-white shadow-sm">
                    <div className="border-b border-surface-border px-5 py-4">
                        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                            <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                                <div className="relative">
                                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                                    <Input placeholder="公司名称" className="h-10 w-36 bg-white pl-9" value={filters.companyName || ''} onChange={e => handleFilterChange('companyName', e.target.value)} />
                                </div>
                                <Select value={filters.companyType || "-1"} onValueChange={value => handleFilterChange('companyType', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="公司类型" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部公司</SelectItem>
                                        {companyTypes.map(option => (
                                            <SelectItem key={option.intro as string} value={option.intro as string}>{option.intro}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <Select value={filters.recruitmentType || "-1"} onValueChange={value => handleFilterChange('recruitmentType', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="招聘类型" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部类型</SelectItem>
                                        {recruitmentTypes.map(type => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <Select value={filters.recruitmentTarget || "-1"} onValueChange={value => handleFilterChange('recruitmentTarget', value)}>
                                    <SelectTrigger className="h-10 w-36 bg-white"><SelectValue placeholder="招聘对象" /></SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="-1">全部对象</SelectItem>
                                        {recruitmentTarget.map(type => (
                                            <SelectItem key={type.intro as string} value={type.intro as string}>
                                                {type.intro}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <Input placeholder="工作地点" className="h-10 w-32 bg-white" value={filters.jobLocation || ''} onChange={e => handleFilterChange('jobLocation', e.target.value)} />
                                <Input placeholder="岗位" className="h-10 w-36 bg-white" value={filters.position || ''} onChange={e => handleFilterChange('position', e.target.value)} />
                            </div>
                            <div className="flex shrink-0 items-center gap-2">
                                <Button variant="outline" className="h-10 gap-2" onClick={resetFilters}>
                                    <X className="h-4 w-4" />
                                    重置
                                </Button>
                                <Button variant="outline" className="h-10 gap-2" onClick={() => fetchData()} disabled={loading}>
                                    {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                                    查询
                                </Button>
                                <Button className="h-10" onClick={execPublishBlogs}>发布公众号博文</Button>
                            </div>
                        </div>
                    </div>

                    <div className="space-y-4 p-5">
                        {(sourceId || sourceTaskId || runnerType) && (
                            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm text-blue-700">
                                <span className="font-medium">{sourceTaskId ? `来自任务 #${sourceTaskId}` : sourceId ? `来自采集源 #${sourceId}` : "来源上下文"}</span>
                                {runnerType && (
                                    <Badge variant="outline" className="border-blue-200 bg-white text-blue-700">
                                        {runnerType === "agent" ? "Agent" : runnerType === "admin" ? "Admin" : "System"}
                                    </Badge>
                                )}
                                <span>按当前上下文过滤职位列表</span>
                                <Button variant="outline" size="sm" className="ml-auto h-8 border-blue-200 bg-white text-blue-700 hover:bg-blue-50" onClick={clearSourceFilter}>
                                    <X className="h-3.5 w-3.5" />
                                    查看全部
                                </Button>
                            </div>
                        )}

                        <div className="overflow-x-auto rounded-lg border border-surface-border">
                            <Table className="table-fixed text-sm" style={{ width: "100%", minWidth: JOB_TABLE_MIN_WIDTH }}>
                                <TableHeader className="bg-slate-50">
                                    <TableRow className="hover:bg-slate-50">
                                        <TableHead className="text-content-secondary">公司名称</TableHead>
                                        <TableHead className="text-content-secondary">公司类型</TableHead>
                                        <TableHead className="text-content-secondary">所属行业</TableHead>
                                        <TableHead className="text-content-secondary">工作地点</TableHead>
                                        <TableHead className="text-content-secondary">招聘类型</TableHead>
                                        <TableHead className="text-content-secondary">招聘对象</TableHead>
                                        <TableHead className="text-content-secondary">岗位</TableHead>
                                        <TableHead className="text-content-secondary">状态</TableHead>
                                        <TableHead className="text-content-secondary">编辑时间</TableHead>
                                        <TableHead className={STICKY_ACTION_HEAD_CLASS}>操作</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {loading ? (
                                        <TableRow><TableCell colSpan={10} className="h-32 text-center text-content-tertiary"><span className="inline-flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" />加载职位中</span></TableCell></TableRow>
                                    ) : error ? (
                                        <TableRow><TableCell colSpan={10} className="h-32 text-center text-rose-600">{error}</TableCell></TableRow>
                                    ) : jobOffers?.length === 0 ? (
                                        <TableRow><TableCell colSpan={10} className="h-32 text-center text-content-tertiary">暂无职位</TableCell></TableRow>
                                    ) : (
                                        jobOffers.map((offer) => (
                                            <TableRow key={String(offer.id)} className="bg-white">
                                                <TableCell className="max-w-[260px] truncate break-all font-medium text-content-primary" title={offer.companyName}>{offer.companyName}</TableCell>
                                                <TableCell className="text-content-secondary">{offer.companyType}</TableCell>
                                                <TableCell className="max-w-[260px] truncate break-all text-content-secondary" title={offer.companyIndustry}>{offer.companyIndustry}</TableCell>
                                                <TableCell className="max-w-[220px] truncate text-content-secondary" title={offer.jobLocation || offer.location}>{offer.jobLocation || offer.location}</TableCell>
                                                <TableCell className="text-content-secondary">{offer.recruitmentType}</TableCell>
                                                <TableCell className="text-content-secondary">{offer.recruitmentTarget}</TableCell>
                                                <TableCell className="max-w-[320px] truncate break-all text-content-secondary" title={offer.position}>{offer.position}</TableCell>
                                                <TableCell>
                                                    <Switch
                                                        checked={offer.state === 1}
                                                        onCheckedChange={(newState) => handleOcStateChange(offer.id, newState ? 1 : 0)}
                                                    />
                                                </TableCell>
                                                <TableCell className="text-content-tertiary">{formatDateTime(offer.updateTime || offer.lastUpdatedTime)}</TableCell>
                                                <TableCell className={STICKY_ACTION_CELL_CLASS}>
                                                    <div className="flex justify-end gap-2">
                                                        <Button size="sm" variant="outline" className="h-8" onClick={() => setEditingOffer(offer)}>
                                                            编辑
                                                        </Button>
                                                        <Button size="sm" variant="destructive" className="h-8" onClick={() => {
                                                            setOcToDelete(offer)
                                                            setIsDialogOpen(true);
                                                        }}>
                                                            删除
                                                        </Button>
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>
                        </div>

                        <TablePagination
                            page={page}
                            pageSize={pageSize}
                            total={total}
                            currentCount={jobOffers.length}
                            loading={loading}
                            totalLabel="条职位"
                            onPageChange={setPage}
                            onPageSizeChange={(size) => {
                                setPageSize(size)
                                setPage(1)
                            }}
                        />
                    </div>
                </section>

                <AlertDialog
                    open={isDialogOpen}
                    onOpenChange={(isOpen) => {
                        setIsDialogOpen(isOpen)
                        if (!isOpen) {
                            setOcToDelete(null)
                        }
                    }}
                >
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>确定要删除吗？</AlertDialogTitle>
                            <AlertDialogDescription>
                                此操作无法撤销。这将永久删除您的字典配置。
                            </AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>取消</AlertDialogCancel>
                            <AlertDialogAction onClick={handleDelete}>确定</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                {editingOffer && (
                    <Dialog
                        open={true}
                        onOpenChange={() => {
                            setEditingOffer(null)
                            setIsAddingNew(false)
                        }}
                    >
                        <DialogContent className="max-w-4xl max-h-[85vh] overflow-y-auto">
                            <DialogHeader>
                                <DialogTitle>{isAddingNew ? "添加新职位" : "编辑职位"}</DialogTitle>
                            </DialogHeader>
                            <div className="grid grid-cols-3 gap-4 py-2">
                                <div>
                                    <label className="text-sm font-medium">公司名称</label>
                                    <Input
                                        value={editingOffer.companyName}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, companyName: e.target.value })}
                                    />
                                </div>
                                <div className={companyTypes.some(item => item.intro === editingOffer.companyType) ? "" : "text-red-500"}>
                                    <label className="text-sm font-medium">公司类型</label>
                                    <Select
                                        value={editingOffer.companyType}
                                        onValueChange={(value) => setEditingOffer({ ...editingOffer, companyType: value })}
                                    >
                                        <SelectTrigger>
                                            <SelectValue placeholder="选择公司类型" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {companyTypes.map(option => (
                                                <SelectItem key={option.intro as string} value={option.intro as string}>{option.intro}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div>
                                    <label className="text-sm font-medium">所属行业</label>
                                    <Input
                                        value={editingOffer.companyIndustry}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, companyIndustry: e.target.value })}
                                    />
                                </div>
                                <div className={recruitmentTypes.some(item => item.intro === editingOffer.recruitmentType) ? "" : "text-red-500"}>
                                    <label className="text-sm font-medium">招聘类型</label>
                                    <Select
                                        value={editingOffer.recruitmentType}
                                        onValueChange={(value) => setEditingOffer({ ...editingOffer, recruitmentType: value })}
                                    >
                                        <SelectTrigger>
                                            <SelectValue placeholder="选择招聘类型" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {recruitmentTypes.map(type => (
                                                <SelectItem key={type.intro as string} value={type.intro as string}>
                                                    {type.intro}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div className={recruitmentTarget.some(item => item.intro === editingOffer.recruitmentTarget) ? "" : "text-red-500"}>
                                    <label className="text-sm font-medium">招聘对象</label>
                                    <Select
                                        value={editingOffer.recruitmentTarget}
                                        onValueChange={(value) => setEditingOffer({ ...editingOffer, recruitmentTarget: value })}
                                    >
                                        <SelectTrigger>
                                            <SelectValue placeholder="选择招聘对象" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {recruitmentTarget.map(type => (
                                                <SelectItem key={type.intro as string} value={type.intro as string}>
                                                    {type.intro}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div className={editingOffer.deadline ? '' : 'text-red-500'}>
                                    <label className="text-sm font-medium">投递截止</label>
                                    <Input
                                        value={editingOffer.deadline}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, deadline: e.target.value })}
                                    />
                                </div>
                                <div className="col-span-1">
                                    <label className="text-sm font-medium">发布时间</label>
                                    <Input
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.lastUpdatedTime}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, lastUpdatedTime: e.target.value })}
                                    />
                                </div>
                                <div className={editingOffer.position ? 'col-span-2' : 'text-red-500 col-span-2'}>
                                    <label className="text-sm font-medium">岗位描述</label>
                                    <textarea
                                        rows={2}
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.position}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, position: e.target.value })}
                                    />
                                </div>
                                <div className={editingOffer.jobLocation ? 'col-span-1' : 'text-red-500 col-span-1'}>
                                    <label className="text-sm font-medium">工作地点</label>
                                    <textarea
                                        className="w-full p-2 border rounded-md"
                                        rows={2}
                                        value={editingOffer.jobLocation}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, jobLocation: e.target.value })}
                                    />
                                </div>

                                <div className={editingOffer.relatedLink ? 'col-span-1' : 'text-red-500 col-span-1'}>
                                    <label className="text-sm font-medium">相关链接</label>
                                    <input
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.relatedLink}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, relatedLink: e.target.value })}
                                    />
                                </div>
                                <div className={editingOffer.jobAnnouncement ? 'col-span-1' : 'text-red-500 col-span-1'}>
                                    <label className="text-sm font-medium">公告链接</label>
                                    <input
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.jobAnnouncement}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, jobAnnouncement: e.target.value })}
                                    />
                                </div>
                                <div className="col-span-1">
                                    <label className="text-sm font-medium">内推码</label>
                                    <input
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.internalReferralCode}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, internalReferralCode: e.target.value })}
                                    />
                                </div>
                                <div className="col-span-3">
                                    <label className="text-sm font-medium">备注说明</label>
                                    <textarea
                                        rows={3}
                                        className="w-full p-2 border rounded-md"
                                        value={editingOffer.remarks}
                                        onChange={(e) => setEditingOffer({ ...editingOffer, remarks: e.target.value })}
                                    />
                                </div>
                            </div>
                            <div className="flex justify-end space-x-2">
                                <Button
                                    variant="outline"
                                    onClick={() => {
                                        setEditingOffer(null)
                                        setIsAddingNew(false)
                                    }}
                                >
                                    取消
                                </Button>
                                <Button onClick={() => handleSave(editingOffer)}>保存</Button>
                            </div>
                        </DialogContent>
                    </Dialog>
                )}
            </div>
        </div>
    )
}
