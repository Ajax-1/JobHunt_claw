"use client"

import { useEffect, useState } from "react"
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
import { useToast } from "@/hooks/use-toast"
import {
    bindWxClawbot,
    unbindWxClawbot,
    getWxClawbotAccounts,
    WxClawbotAccount,
    saveAiProvider,
    deleteAiProvider,
    getUserAiProvider,
    AiProviderConfig,
    UserModelEntry,
} from "@/lib/api"
import { Badge } from "@/components/ui/badge"
import { Trash2, Plus } from "lucide-react"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"

// 预定义的AI Provider列表
const AVAILABLE_PROVIDERS = [
    { name: "deepseek", label: "DeepSeek" },
    { name: "silicon", label: "硅基流动" },
    { name: "openai", label: "OpenAI" },
    { name: "anthropic", label: "Anthropic" },
]

// 预定义的模型列表
const AVAILABLE_MODELS: Record<string, string[]> = {
    deepseek: ["deepseek-flash", "deepseek-v4-pro"],
    silicon: ["Qwen/Qwen2.5-7B-Instruct", "PaddlePaddle/PaddleOCR-VL-1.5"],
}

export default function UserConfigPage() {
    const { toast } = useToast()

    // 微信账号状态
    const [wxAccounts, setWxAccounts] = useState<WxClawbotAccount[]>([])
    const [isBindDialogOpen, setIsBindDialogOpen] = useState(false)
    const [bindForm, setBindForm] = useState({
        wxClawbotUserId: "",
        appId: "",
        appSecret: "",
    })

    // AI Provider配置状态
    const [aiProvider, setAiProvider] = useState<UserModelEntry | null>(null)
    const [isProviderDialogOpen, setIsProviderDialogOpen] = useState(false)
    const [providerForm, setProviderForm] = useState<AiProviderConfig>({
        providerName: "deepseek",
        apiKey: "",
        baseUrl: "",
        visionModel: "",
        textModel: "",
    })

    // 加载数据
    useEffect(() => {
        loadWxAccounts()
        loadAiProvider()
    }, [])

    const loadWxAccounts = async () => {
        try {
            const accounts = await getWxClawbotAccounts()
            setWxAccounts(accounts)
        } catch (err: any) {
            toast({
                title: "错误",
                description: err.message || "加载微信账号失败",
                variant: "destructive",
            })
        }
    }

    const loadAiProvider = async () => {
        try {
            const provider = await getUserAiProvider()
            setAiProvider(provider)
        } catch (err: any) {
            console.error("加载AI Provider配置失败", err)
        }
    }

    // 绑定微信账号
    const handleBindWx = async () => {
        if (!bindForm.wxClawbotUserId || !bindForm.appId || !bindForm.appSecret) {
            toast({
                title: "错误",
                description: "请填写完整信息",
                variant: "destructive",
            })
            return
        }

        try {
            await bindWxClawbot(bindForm)
            toast({
                title: "成功",
                description: "微信账号绑定成功",
            })
            setIsBindDialogOpen(false)
            setBindForm({ wxClawbotUserId: "", appId: "", appSecret: "" })
            loadWxAccounts()
        } catch (err: any) {
            toast({
                title: "错误",
                description: err.message || "绑定失败",
                variant: "destructive",
            })
        }
    }

    // 解绑微信账号
    const handleUnbindWx = async (userId: string) => {
        if (!confirm("确定要解绑此微信账号吗?")) return

        try {
            await unbindWxClawbot(userId)
            toast({
                title: "成功",
                description: "微信账号解绑成功",
            })
            loadWxAccounts()
        } catch (err: any) {
            toast({
                title: "错误",
                description: err.message || "解绑失败",
                variant: "destructive",
            })
        }
    }

    // 保存AI Provider配置
    const handleSaveProvider = async () => {
        if (!providerForm.apiKey) {
            toast({
                title: "错误",
                description: "请填写API Key",
                variant: "destructive",
            })
            return
        }

        try {
            await saveAiProvider(providerForm)
            toast({
                title: "成功",
                description: "AI Provider配置保存成功",
            })
            setIsProviderDialogOpen(false)
            setProviderForm({
                providerName: "deepseek",
                apiKey: "",
                baseUrl: "",
                visionModel: "",
                textModel: "",
            })
            loadAiProvider()
        } catch (err: any) {
            toast({
                title: "错误",
                description: err.message || "保存失败",
                variant: "destructive",
            })
        }
    }

    // 删除AI Provider配置
    const handleDeleteProvider = async (providerName: string) => {
        if (!confirm(`确定要删除 ${providerName} 的配置吗?`)) return

        try {
            await deleteAiProvider(providerName)
            toast({
                title: "成功",
                description: "配置删除成功",
            })
            loadAiProvider()
        } catch (err: any) {
            toast({
                title: "错误",
                description: err.message || "删除失败",
                variant: "destructive",
            })
        }
    }

    // 获取已配置的providers
    const getConfiguredProviders = () => {
        if (!aiProvider?.preference?.providers) return []
        return Object.keys(aiProvider.preference.providers).map((name) => {
            const provider = AVAILABLE_PROVIDERS.find((p) => p.name === name)
            return {
                name,
                label: provider?.label || name,
                config: aiProvider.preference!.providers![name],
            }
        })
    }

    return (
        <div className="space-y-6">
            {/* 微信ClawBot账号管理 */}
            <Card>
                <CardHeader>
                    <div className="flex justify-between items-center">
                        <div>
                            <CardTitle>微信ClawBot账号管理</CardTitle>
                            <CardDescription>
                                绑定和管理您的微信ClawBot账号
                            </CardDescription>
                        </div>
                        <Button onClick={() => setIsBindDialogOpen(true)}>
                            <Plus className="w-4 h-4 mr-2" />
                            绑定账号
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {wxAccounts.length === 0 ? (
                        <div className="text-center py-8 text-gray-500">
                            暂无绑定的微信账号
                        </div>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>用户ID</TableHead>
                                    <TableHead>App ID</TableHead>
                                    <TableHead>模式</TableHead>
                                    <TableHead className="w-[100px]">操作</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {wxAccounts.map((account) => (
                                    <TableRow key={account.userId}>
                                        <TableCell className="font-mono text-sm">
                                            {account.userId}
                                        </TableCell>
                                        <TableCell className="font-mono text-sm">
                                            {account.appId}
                                        </TableCell>
                                        <TableCell>
                                            <Badge variant="outline">{account.mode}</Badge>
                                        </TableCell>
                                        <TableCell>
                                            <Button
                                                size="sm"
                                                variant="destructive"
                                                onClick={() => handleUnbindWx(account.userId)}
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {/* AI Provider配置 */}
            <Card>
                <CardHeader>
                    <div className="flex justify-between items-center">
                        <div>
                            <CardTitle>AI Provider配置</CardTitle>
                            <CardDescription>
                                配置您自己的AI模型提供商和API Key
                            </CardDescription>
                        </div>
                        <Button onClick={() => setIsProviderDialogOpen(true)}>
                            <Plus className="w-4 h-4 mr-2" />
                            添加Provider
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    {getConfiguredProviders().length === 0 ? (
                        <div className="text-center py-8 text-gray-500">
                            暂无配置的AI Provider
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {getConfiguredProviders().map((provider) => (
                                <div
                                    key={provider.name}
                                    className="flex justify-between items-center p-4 border rounded-lg"
                                >
                                    <div>
                                        <div className="font-semibold">{provider.label}</div>
                                        <div className="text-sm text-gray-500 mt-1">
                                            API Key: {provider.config.apiKey ? `${provider.config.apiKey.substring(0, 8)}***` : "未配置"}
                                        </div>
                                        {aiProvider?.preference && (
                                            <div className="text-sm text-gray-500 mt-1">
                                                {aiProvider.preference.vision && (
                                                    <span className="mr-4">
                                                        视觉: {aiProvider.preference.vision}
                                                    </span>
                                                )}
                                                {aiProvider.preference.text && (
                                                    <span>文本: {aiProvider.preference.text}</span>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                    <Button
                                        size="sm"
                                        variant="destructive"
                                        onClick={() => handleDeleteProvider(provider.name)}
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </Button>
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* 绑定微信账号对话框 */}
            <Dialog open={isBindDialogOpen} onOpenChange={setIsBindDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>绑定微信ClawBot账号</DialogTitle>
                        <DialogDescription>
                            请输入您的微信ClawBot账号信息
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div className="space-y-2">
                            <Label htmlFor="wxUserId">微信用户ID</Label>
                            <Input
                                id="wxUserId"
                                placeholder="例如: o9cq807KL9SgEouT2mDo1h0kw6LY@im.wechat"
                                value={bindForm.wxClawbotUserId}
                                onChange={(e) =>
                                    setBindForm({ ...bindForm, wxClawbotUserId: e.target.value })
                                }
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="appId">App ID</Label>
                            <Input
                                id="appId"
                                placeholder="例如: 3edc5b4270b2@im.bot"
                                value={bindForm.appId}
                                onChange={(e) =>
                                    setBindForm({ ...bindForm, appId: e.target.value })
                                }
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="appSecret">App Secret</Label>
                            <Input
                                id="appSecret"
                                type="password"
                                placeholder="请输入App Secret"
                                value={bindForm.appSecret}
                                onChange={(e) =>
                                    setBindForm({ ...bindForm, appSecret: e.target.value })
                                }
                            />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsBindDialogOpen(false)}>
                            取消
                        </Button>
                        <Button onClick={handleBindWx}>绑定</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* 添加AI Provider对话框 */}
            <Dialog open={isProviderDialogOpen} onOpenChange={setIsProviderDialogOpen}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>添加AI Provider配置</DialogTitle>
                        <DialogDescription>
                            配置您的AI模型提供商信息
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-4">
                        <div className="space-y-2">
                            <Label htmlFor="providerName">Provider</Label>
                            <Select
                                value={providerForm.providerName}
                                onValueChange={(value) =>
                                    setProviderForm({ ...providerForm, providerName: value })
                                }
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="选择Provider" />
                                </SelectTrigger>
                                <SelectContent>
                                    {AVAILABLE_PROVIDERS.map((provider) => (
                                        <SelectItem key={provider.name} value={provider.name}>
                                            {provider.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="apiKey">API Key</Label>
                            <Input
                                id="apiKey"
                                type="password"
                                placeholder="请输入API Key"
                                value={providerForm.apiKey}
                                onChange={(e) =>
                                    setProviderForm({ ...providerForm, apiKey: e.target.value })
                                }
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="visionModel">视觉模型</Label>
                            <Select
                                value={providerForm.visionModel}
                                onValueChange={(value) =>
                                    setProviderForm({ ...providerForm, visionModel: value })
                                }
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="选择视觉模型" />
                                </SelectTrigger>
                                <SelectContent>
                                    {(AVAILABLE_MODELS[providerForm.providerName] || []).map(
                                        (model) => (
                                            <SelectItem key={model} value={model}>
                                                {model}
                                            </SelectItem>
                                        )
                                    )}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="textModel">文本模型</Label>
                            <Select
                                value={providerForm.textModel}
                                onValueChange={(value) =>
                                    setProviderForm({ ...providerForm, textModel: value })
                                }
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="选择文本模型" />
                                </SelectTrigger>
                                <SelectContent>
                                    {(AVAILABLE_MODELS[providerForm.providerName] || []).map(
                                        (model) => (
                                            <SelectItem key={model} value={model}>
                                                {model}
                                            </SelectItem>
                                        )
                                    )}
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsProviderDialogOpen(false)}>
                            取消
                        </Button>
                        <Button onClick={handleSaveProvider}>保存</Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    )
}
