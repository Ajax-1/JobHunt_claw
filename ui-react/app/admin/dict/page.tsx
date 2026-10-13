"use client"

import { useEffect, useState } from "react"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import { fetchDictList, DictListItem, saveDict, DictSaveReq, updateDictState, deleteDict } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { useToast } from "@/components/ui/use-toast"
import { Switch } from "@/components/ui/switch"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import {
    Pagination,
    PaginationContent,
    PaginationEllipsis,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious,
} from "@/components/ui/pagination"
import { Pencil, Plus, Search, Trash2 } from "lucide-react"

import { getConfigValue } from "@/lib/config"
import { GlobalConfigItemValue } from "@/lib/api"

const newDictInitValue: DictSaveReq = {
    app: "",
    key: "",
    value: "",
    intro: "",
    remark: "",
    scope: 0,
    state: 1,
}

const pageSizeOptions = [10, 20, 50]

type PageItem = number | "ellipsis"

function getPageItems(currentPage: number, totalPages: number): PageItem[] {
    if (totalPages <= 7) {
        return Array.from({ length: totalPages }, (_, index) => index + 1)
    }

    const fixedPages = [1, 2, 3, totalPages - 1, totalPages]
    const currentPages = [currentPage - 1, currentPage, currentPage + 1]
    const pages = Array.from(new Set([...fixedPages, ...currentPages]))
        .filter(page => page >= 1 && page <= totalPages)
        .sort((a, b) => a - b)

    const items: PageItem[] = []
    for (const page of pages) {
        const previous = items[items.length - 1]
        if (typeof previous === "number" && page - previous > 1) {
            items.push("ellipsis")
        }
        items.push(page)
    }

    return items
}

export default function DictPage() {
    const [dicts, setDicts] = useState<DictListItem[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [app, setApp] = useState("")
    const [key, setKey] = useState("")
    const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 })
    const [jumpPage, setJumpPage] = useState("")
    const [isSaving, setIsSaving] = useState(false)
    const { toast } = useToast()

    const [isDialogOpen, setIsDialogOpen] = useState(false)
    const [activeDict, setActiveDict] = useState<DictSaveReq>(newDictInitValue)
    const [isEditing, setIsEditing] = useState(false)
    const [dictToDelete, setDictToDelete] = useState<DictListItem | null>(null)
    const [scopeOptions, setScopeOptions] = useState<GlobalConfigItemValue[]>([]);
    const [appOptions, setAppOptions] = useState<GlobalConfigItemValue[]>([]);
    const totalPages = Math.max(1, Math.ceil(pagination.total / pagination.size))
    const pageItems = getPageItems(pagination.page, totalPages)

    useEffect(() => {
        getConfigValue('dicts', 'DictScopeEnum').then(options => {
            setScopeOptions(options);
        });
        getConfigValue('dicts', 'DictAppEnum').then(options => {
            setAppOptions(options);
        });
    }, []);

    const loadDicts = async (search: { app: string, key: string, page: number, size: number }) => {
        try {
            setLoading(true)
            const response = await fetchDictList(search)
            setDicts(response.list)
            setPagination({ page: response.page, size: response.size, total: response.total })
        } catch (err: any) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadDicts({ key, app: app == '-1' ? '' : app, page: pagination.page, size: pagination.size })
    }, [app, key, pagination.page, pagination.size])

    const handleSearch = () => {
        if (pagination.page === 1) {
            loadDicts({ app: app == '-1' ? '' : app, key, page: 1, size: pagination.size })
        } else {
            setPagination({ ...pagination, page: 1 })
        }
    }

    const handlePageChange = (page: number) => {
        const nextPage = Math.min(Math.max(page, 1), totalPages)
        if (nextPage !== pagination.page) {
            setPagination({ ...pagination, page: nextPage })
        }
    }

    const handlePageSizeChange = (size: string) => {
        setPagination({ ...pagination, page: 1, size: Number(size) })
    }

    const handleJumpPage = () => {
        const nextPage = Number(jumpPage)
        if (!Number.isFinite(nextPage)) return
        handlePageChange(nextPage)
        setJumpPage("")
    }

    const formatDate = (time: string | number | Date) => {
        const date = new Date(time)
        if (Number.isNaN(date.getTime())) return "-"
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`
    }

    const handleSave = async () => {
        try {
            setIsSaving(true)
            await saveDict(activeDict)
            toast({
                title: "成功",
                description: "字典项已保存",
            })
            setIsDialogOpen(false)
            loadDicts({ app, key, page: pagination.page, size: pagination.size }) // Refresh the list
        } catch (err: any) {
            toast({
                title: "失败",
                description: err.message,
                variant: "destructive",
            })
        } finally {
            setIsSaving(false)
        }
    }

    const handleAddNew = () => {
        setIsEditing(false)
        setActiveDict(newDictInitValue)
        setIsDialogOpen(true)
    }

    const handleEdit = (dict: DictListItem) => {
        setIsEditing(true)
        setActiveDict({ ...dict })
        setIsDialogOpen(true)
    }

    const handleDialogChange = (isOpen: boolean) => {
        setIsDialogOpen(isOpen)
        if (!isOpen) {
            setActiveDict(newDictInitValue)
            setIsEditing(false)
        }
    }

    const handleStateChange = async (id: number, newState: boolean) => {
        const state = newState ? 1 : 0
        try {
            await updateDictState(id, state)
            toast({
                title: "成功",
                description: "状态已更新",
            })
            // Optimistically update the UI
            setDicts((prevDicts) =>
                prevDicts.map((d) => (d.id === id ? { ...d, state } : d))
            )
        } catch (err: any) {
            toast({
                title: "失败",
                description: err.message,
                variant: "destructive",
            })
        }
    }

    const handleDeleteConfirm = async () => {
        if (!dictToDelete) return
        try {
            await deleteDict(dictToDelete.id)
            toast({
                title: "成功",
                description: "字典项已删除",
            })
            setDictToDelete(null)
            loadDicts({ app, key, page: pagination.page, size: pagination.size }) // Refresh the list
        } catch (err: any) {
            toast({
                title: "失败",
                description: err.message,
                variant: "destructive",
            })
        }
    }

    const renderScope = (scope: number) => {
        const option = scopeOptions.find(o => Number(o.value) === scope);
        if (option) {
            return <Badge variant="outline">{option.intro}</Badge>
        }
        return <Badge variant="outline">未知配置</Badge>
    }

    return (
        <div className="min-h-screen bg-surface-muted">
            <div className="mx-auto max-w-[1440px] px-6 py-6">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-4 rounded-lg border border-surface-border bg-white p-4 shadow-sm">
                    <div className="flex flex-wrap items-center gap-3">
                        <div>
                            <Select value={app} onValueChange={setApp}>
                                <SelectTrigger className="h-10 w-44">
                                    <SelectValue placeholder="全部 App" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="-1" key='-1'>全部 App</SelectItem>
                                    {appOptions.map(option => (
                                        <SelectItem value={option.value as string} key={option.value as string}>
                                            {option.intro}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div>
                            <Input
                                placeholder="Code / Value / 说明"
                                value={key}
                                onChange={(e) => setKey(e.target.value)}
                                className="h-10 w-72"
                            />
                        </div>
                        <Button onClick={handleSearch} className="h-10 gap-2">
                            <Search className="h-4 w-4" />
                            查询
                        </Button>
                    </div>
                    <div className="flex items-center gap-3">
                        <Button onClick={handleAddNew} className="h-10 gap-2">
                            <Plus className="h-4 w-4" />
                            添加配置
                        </Button>
                    </div>
                </div>

                <div className="overflow-hidden rounded-lg border border-surface-border bg-white shadow-sm">
                    {loading && (
                        <div className="flex h-56 items-center justify-center text-sm text-content-tertiary">加载中...</div>
                    )}
                    {error && (
                        <div className="flex h-56 items-center justify-center text-sm text-destructive">错误: {error}</div>
                    )}
                    {!loading && !error && (
                        <>
                            <Table className="min-w-[1038px] table-fixed">
                                <colgroup>
                                    <col className="w-[54px]" />
                                    <col className="w-[110px]" />
                                    <col className="w-[86px]" />
                                    <col className="w-[150px]" />
                                    <col className="w-[100px]" />
                                    <col className="w-[120px]" />
                                    <col className="w-[72px]" />
                                    <col className="w-[124px]" />
                                    <col className="w-[122px]" />
                                    <col className="w-[78px]" />
                                </colgroup>
                                <TableHeader>
                                    <TableRow className="border-blue-100 bg-blue-50 hover:bg-blue-50">
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">ID</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">App</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">作用域</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">Code</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">Value</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">说明</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">状态</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">备注</TableHead>
                                        <TableHead className="h-11 text-xs font-semibold text-blue-600">更新日期</TableHead>
                                        <TableHead className="h-11 text-right text-xs font-semibold text-blue-600">操作</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {dicts.length === 0 ? (
                                        <TableRow>
                                            <TableCell colSpan={10} className="h-48 text-center text-content-tertiary">
                                                暂无配置项
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        dicts.map((dict) => (
                                            <TableRow key={dict.id} className="hover:bg-blue-50/60">
                                                <TableCell className="font-medium text-content-tertiary">{dict.id}</TableCell>
                                                <TableCell className="font-medium text-content-primary">{dict.app}</TableCell>
                                                <TableCell className="whitespace-nowrap">{renderScope(dict.scope)}</TableCell>
                                                <TableCell className="truncate font-medium text-content-primary" title={dict.key}>{dict.key}</TableCell>
                                                <TableCell className="truncate text-content-primary" title={dict.value}>{dict.value}</TableCell>
                                                <TableCell className="truncate text-content-primary" title={dict.intro}>{dict.intro}</TableCell>
                                                <TableCell>
                                                    <Switch
                                                        checked={dict.state === 1}
                                                        onCheckedChange={(newState) => handleStateChange(dict.id, newState)}
                                                    />
                                                </TableCell>
                                                <TableCell className="whitespace-normal break-words text-content-secondary">{dict.remark || "-"}</TableCell>
                                                <TableCell className="text-content-secondary">{formatDate(dict.updateTime)}</TableCell>
                                                <TableCell>
                                                    <div className="flex justify-end gap-1">
                                                        <Button variant="ghost" size="icon" className="h-8 w-8 text-blue-600 hover:bg-blue-50 hover:text-blue-700" onClick={() => handleEdit(dict)} title="编辑">
                                                            <Pencil className="h-4 w-4" />
                                                        </Button>
                                                        <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:bg-red-50 hover:text-destructive" onClick={() => setDictToDelete(dict)} title="删除">
                                                            <Trash2 className="h-4 w-4" />
                                                        </Button>
                                                    </div>
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>

                            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-surface-border px-4 py-3">
                                <div className="flex flex-wrap items-center gap-4 text-sm text-content-tertiary">
                                    <span>共 <span className="font-semibold text-content-primary">{pagination.total}</span> 条配置</span>
                                    <div className="flex items-center gap-2">
                                    <span>每页</span>
                                    <Select value={String(pagination.size)} onValueChange={handlePageSizeChange}>
                                        <SelectTrigger className="h-9 w-20">
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {pageSizeOptions.map(size => (
                                                <SelectItem key={size} value={String(size)}>{size}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                    <span>条，第 {pagination.page} / {totalPages} 页</span>
                                    </div>
                                </div>
                                <div className="flex flex-wrap items-center gap-3">
                                    <Pagination className="w-auto">
                                        <PaginationContent>
                                            <PaginationItem>
                                                <PaginationPrevious
                                                    href="#"
                                                    onClick={(e) => {
                                                        e.preventDefault()
                                                        handlePageChange(pagination.page - 1)
                                                    }}
                                                    className={pagination.page <= 1 ? "pointer-events-none opacity-50" : ""}
                                                />
                                            </PaginationItem>
                                            {pageItems.map((page, index) => (
                                                <PaginationItem key={`${page}-${index}`}>
                                                    {page === "ellipsis" ? (
                                                        <PaginationEllipsis />
                                                    ) : (
                                                        <PaginationLink
                                                            href="#"
                                                            isActive={pagination.page === page}
                                                            onClick={(e) => {
                                                                e.preventDefault()
                                                                handlePageChange(page)
                                                            }}
                                                            className={pagination.page === page ? "border-blue-600 text-blue-600" : ""}
                                                        >
                                                            {page}
                                                        </PaginationLink>
                                                    )}
                                                </PaginationItem>
                                            ))}
                                            <PaginationItem>
                                                <PaginationNext
                                                    href="#"
                                                    onClick={(e) => {
                                                        e.preventDefault()
                                                        handlePageChange(pagination.page + 1)
                                                    }}
                                                    className={pagination.page >= totalPages ? "pointer-events-none opacity-50" : ""}
                                                />
                                            </PaginationItem>
                                        </PaginationContent>
                                    </Pagination>
                                    <div className="flex items-center gap-2 text-sm text-content-tertiary">
                                        <span>跳至</span>
                                        <Input
                                            value={jumpPage}
                                            onChange={(e) => setJumpPage(e.target.value.replace(/[^\d]/g, ""))}
                                            onKeyDown={(e) => {
                                                if (e.key === "Enter") handleJumpPage()
                                            }}
                                            className="h-9 w-16 text-center"
                                            inputMode="numeric"
                                            placeholder="页"
                                        />
                                        <Button variant="outline" size="sm" onClick={handleJumpPage}>跳转</Button>
                                    </div>
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>

            <Dialog open={isDialogOpen} onOpenChange={handleDialogChange}>
                <DialogContent className="sm:max-w-[625px]">
                    <DialogHeader>
                        <DialogTitle>{isEditing ? '编辑配置' : '添加新配置'}</DialogTitle>
                        <DialogDescription>
                            {isEditing ? '在这里修改您的字典配置项。' : '在这里添加一个新的字典配置项。'}
                        </DialogDescription>
                    </DialogHeader>
                    <div className="grid gap-4 py-4">
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="app" className="text-right">App</Label>
                            <Select
                                value={activeDict.app}
                                onValueChange={(value) => setActiveDict({ ...activeDict, app: value })}
                            >
                                <SelectTrigger className="w-40">
                                    <SelectValue placeholder="请选择App" />
                                </SelectTrigger>
                                <SelectContent>
                                    {appOptions.map(option => (
                                        <SelectItem value={option.value as string} key={option.value as string}>
                                            {option.intro}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="key" className="text-right">Code</Label>
                            <Input id="key" value={activeDict.key} onChange={(e) => setActiveDict({ ...activeDict, key: e.target.value })} className="col-span-3" />
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="value" className="text-right">Value</Label>
                            <Textarea id="value" value={activeDict.value} onChange={(e) => setActiveDict({ ...activeDict, value: e.target.value })} className="col-span-3" />
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="intro" className="text-right">说明</Label>
                            <Textarea id="intro" value={activeDict.intro} onChange={(e) => setActiveDict({ ...activeDict, intro: e.target.value })} className="col-span-3" />
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="scope" className="text-right">作用域</Label>
                            <RadioGroup
                                value={String(activeDict.scope)}
                                onValueChange={(value) => setActiveDict({ ...activeDict, scope: Number(value) })}
                                className="col-span-3 flex items-center space-x-4"
                            >
                                {scopeOptions.map(option => (
                                    <div className="flex items-center space-x-2" key={option.value as string}>
                                        <RadioGroupItem value={option.value as string} id={`scope-${option.value}`} />
                                        <Label htmlFor={`scope-${option.value}`}>{option.intro}</Label>
                                    </div>
                                ))}
                            </RadioGroup>
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="intro" className="text-right">备注</Label>
                            <Textarea id="remark" value={activeDict.remark} onChange={(e) => setActiveDict({ ...activeDict, remark: e.target.value })} className="col-span-3" />
                        </div>
                        <div className="grid grid-cols-4 items-center gap-4">
                            <Label htmlFor="state" className="text-right">状态</Label>
                            <RadioGroup
                                value={String(activeDict.state)}
                                onValueChange={(value) => setActiveDict({ ...activeDict, state: Number(value) })}
                                className="col-span-3 flex items-center space-x-4"
                            >
                                <div className="flex items-center space-x-2">
                                    <RadioGroupItem value="1" id="state-1" />
                                    <Label htmlFor="state-1">有效</Label>
                                </div>
                                <div className="flex items-center space-x-2">
                                    <RadioGroupItem value="0" id="state-0" />
                                    <Label htmlFor="state-0">未启用</Label>
                                </div>
                            </RadioGroup>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button type="submit" onClick={handleSave} disabled={isSaving}>
                            {isSaving ? '保存中...' : '保存'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
            <AlertDialog open={!!dictToDelete} onOpenChange={(isOpen) => !isOpen && setDictToDelete(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>确定要删除吗？</AlertDialogTitle>
                        <AlertDialogDescription>
                            此操作无法撤销。这将永久删除您的字典配置。
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>取消</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDeleteConfirm}>确定</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    )
} 
