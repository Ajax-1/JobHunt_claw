"use client";

import {useEffect, useMemo, useState} from "react";
import type {ReactNode} from "react";
import {AlertCircle, CheckCircle2, Edit, Loader2, PlugZap, Plus, RefreshCw, Save, Trash2} from "lucide-react";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Badge} from "@/components/ui/badge";
import {Alert, AlertDescription, AlertTitle} from "@/components/ui/alert";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import {Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle} from "@/components/ui/sheet";
import {Skeleton} from "@/components/ui/skeleton";
import {useToast} from "@/hooks/use-toast";
import {cn} from "@/lib/utils";
import {
    AdminLlmProviderConfig,
    AdminLlmProviderDefaults,
    AdminLlmProviderTestResponse,
    UserModelConfig,
    deleteAdminLlmProvider,
    getAdminLlmProviders,
    saveAdminLlmProviderDefaults,
    saveAdminLlmProvider,
    testAdminLlmProvider,
} from "@/lib/api";

const API_STYLE_OPTIONS = [
    {value: "openai", label: "OpenAI 兼容"},
    {value: "anthropic", label: "Anthropic"},
];

const MODEL_TYPE_OPTIONS = [
    {value: "TEXT", label: "文本"},
    {value: "VISION", label: "视觉"},
];

const BILLING_TYPE_OPTIONS = [
    {value: "UNKNOWN", label: "未标注"},
    {value: "FREE", label: "免费"},
    {value: "PAID", label: "付费"},
];

const emptyModel: UserModelConfig = {name: "", type: "TEXT", multimodal: false};
const emptyProvider: AdminLlmProviderConfig = {
    provider: "",
    displayName: "",
    apiStyle: "openai",
    apiKey: "",
    baseUrl: "",
    completionsPath: "/v1/chat/completions",
    models: [{...emptyModel}],
};

type ProviderRow = {
    provider: string;
    config: AdminLlmProviderConfig;
};

type DefaultModelOption = {
    value: string;
    label: string;
    type: string;
    billingType?: string;
    multimodal?: boolean;
};

type ModelError = {
    name?: string;
    type?: string;
};

type FormErrors = {
    displayName?: string;
    apiStyle?: string;
    apiKey?: string;
    baseUrl?: string;
    completionsPath?: string;
    models?: Record<number, ModelError>;
};

type ValidatedProvider = {
    displayName: string;
    models: UserModelConfig[];
};

const getModelTypeLabel = (type?: string) => MODEL_TYPE_OPTIONS.find(option => option.value === type)?.label || type || "-";
const getBillingTypeLabel = (type?: string) => BILLING_TYPE_OPTIONS.find(option => option.value === type)?.label || "未标注";
const isSupportedModelType = (type?: string) => type === "TEXT" || type === "VISION";
const isMaskedApiKey = (value?: string) => !!value && value.includes("***");
const getProviderDisplayName = (provider: string, config?: AdminLlmProviderConfig | null) => config?.displayName?.trim() || provider;

export default function AdminLlmProvidersPage() {
    const {toast} = useToast();
    const [providers, setProviders] = useState<Record<string, AdminLlmProviderConfig>>({});
    const [defaults, setDefaults] = useState<AdminLlmProviderDefaults>({});
    const [loading, setLoading] = useState<boolean>(true);
    const [loadError, setLoadError] = useState<string>("");
    const [saving, setSaving] = useState<boolean>(false);
    const [defaultSaving, setDefaultSaving] = useState<boolean>(false);
    const [testing, setTesting] = useState<boolean>(false);
    const [deleting, setDeleting] = useState<boolean>(false);
    const [sheetOpen, setSheetOpen] = useState<boolean>(false);
    const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
    const [editingProvider, setEditingProvider] = useState<string | null>(null);
    const [form, setForm] = useState<AdminLlmProviderConfig>({...emptyProvider, models: [{...emptyModel}]});
    const [formErrors, setFormErrors] = useState<FormErrors>({});
    const [testResult, setTestResult] = useState<AdminLlmProviderTestResponse | null>(null);
    const [selectedModels, setSelectedModels] = useState<Record<string, string>>({});

    const rows = useMemo<ProviderRow[]>(() => Object.entries(providers).map(([provider, config]) => ({
        provider,
        config,
    })).sort((a, b) => Number(Boolean(b.config.apiKey)) - Number(Boolean(a.config.apiKey))), [providers]);
    const defaultModelOptions = useMemo<DefaultModelOption[]>(() => buildDefaultModelOptions(providers), [providers]);

    useEffect(() => {
        loadProviders();
    }, []);

    const loadProviders = async () => {
        setLoading(true);
        setLoadError("");
        try {
            const data = await getAdminLlmProviders();
            setProviders(data.providers || {});
            setDefaults(data.defaults || {});
        } catch (error) {
            const message = error instanceof Error ? error.message : "获取供应商配置失败";
            setLoadError(message);
            toast({
                title: "加载失败",
                description: message,
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    const openCreateSheet = () => {
        setEditingProvider(null);
        setForm({...emptyProvider, models: [{...emptyModel}]});
        setFormErrors({});
        setTestResult(null);
        setSheetOpen(true);
    };

    const openEditSheet = (provider: string, config: AdminLlmProviderConfig) => {
        setEditingProvider(provider);
        const models = config.models?.filter(model => isSupportedModelType(model.type)).map(model => ({...model})) || [];
        setForm({
            ...emptyProvider,
            ...config,
            provider,
            displayName: config.displayName || provider,
            models: models.length ? models : [{...emptyModel}],
        });
        setFormErrors({});
        setTestResult(null);
        setSheetOpen(true);
    };

    const closeSheet = () => {
        if (!saving && !testing) {
            setSheetOpen(false);
        }
    };

    const updateForm = (field: keyof AdminLlmProviderConfig, value: string) => {
        setForm(prev => ({...prev, [field]: value}));
        setFormErrors(prev => ({...prev, [field]: undefined}));
        setTestResult(null);
    };

    const updateModel = (index: number, field: keyof UserModelConfig, value: string | boolean | number | undefined) => {
        setForm(prev => {
            const models = [...(prev.models || [])];
            models[index] = {...models[index], [field]: value};
            return {...prev, models};
        });
        setFormErrors(prev => ({...prev, models: {...(prev.models || {}), [index]: {}}}));
        setTestResult(null);
    };

    const addModel = () => {
        setForm(prev => ({
            ...prev,
            models: [...(prev.models || []), {...emptyModel}],
        }));
        setTestResult(null);
    };

    const removeModel = (index: number) => {
        setForm(prev => {
            const models = [...(prev.models || [])];
            if (models.length <= 1) {
                return {...prev, models: [{...emptyModel}]};
            }
            models.splice(index, 1);
            return {...prev, models};
        });
        setFormErrors({});
        setTestResult(null);
    };

    const validateForm = (): ValidatedProvider | null => {
        const errors: FormErrors = {};
        const displayName = (form.displayName || "").trim();

        if (!displayName) {
            errors.displayName = "请输入供应商";
        }

        if (!form.apiStyle?.trim()) {
            errors.apiStyle = "请选择 API 风格";
        }

        const apiKey = form.apiKey?.trim() || "";
        if (!apiKey) {
            errors.apiKey = "请输入 API Key";
        } else if (!editingProvider && isMaskedApiKey(apiKey)) {
            errors.apiKey = "新增供应商不能使用脱敏 API Key";
        }

        const baseUrl = form.baseUrl?.trim() || "";
        if (!baseUrl) {
            errors.baseUrl = "请输入 Base URL";
        } else if (!/^https?:\/\/.+/i.test(baseUrl)) {
            errors.baseUrl = "Base URL 需要以 http:// 或 https:// 开头";
        }

        const models = form.models || [];
        const modelErrors: Record<number, ModelError> = {};
        const normalizedModels = models.map(model => ({
            ...model,
            name: (model.name || "").trim(),
            type: isSupportedModelType(model.type) ? model.type : "TEXT",
            multimodal: model.type === "VISION" || !!model.multimodal,
        })).filter(model => model.name);

        models.forEach((model, index) => {
            const name = (model.name || "").trim();
            const rowError: ModelError = {};
            if (!name) {
                rowError.name = "请输入模型名";
            }
            if (!isSupportedModelType(model.type)) {
                rowError.type = "只支持文本或视觉";
            }
            if (rowError.name || rowError.type) {
                modelErrors[index] = rowError;
            }
        });

        if (!normalizedModels.length) {
            modelErrors[0] = {...(modelErrors[0] || {}), name: "至少添加一个模型"};
        }
        if (Object.keys(modelErrors).length > 0) {
            errors.models = modelErrors;
        }

        setFormErrors(errors);
        if (Object.keys(errors).length > 0) {
            return null;
        }

        return {displayName, models: normalizedModels};
    };

    const buildProviderReq = (validated: ValidatedProvider) => ({
        provider: editingProvider || form.provider?.trim() || undefined,
        originalProvider: editingProvider || undefined,
        config: {
            ...form,
            provider: editingProvider || form.provider?.trim() || undefined,
            displayName: validated.displayName,
            apiKey: form.apiKey?.trim(),
            baseUrl: form.baseUrl?.trim(),
            completionsPath: form.completionsPath?.trim(),
            models: validated.models,
        },
    });

    const handleSave = async () => {
        const validated = validateForm();
        if (!validated) {
            return;
        }

        setSaving(true);
        try {
            await saveAdminLlmProvider(buildProviderReq(validated));
            toast({title: "保存成功", description: "供应商配置已写入全局配置"});
            setSheetOpen(false);
            await loadProviders();
        } catch (error) {
            toast({
                title: "保存失败",
                description: error instanceof Error ? error.message : "保存供应商配置失败",
                variant: "destructive",
            });
        } finally {
            setSaving(false);
        }
    };

    const handleSaveDefaults = async () => {
        if (!defaults.text || !defaults.vision) {
            toast({
                title: "默认模型不完整",
                description: "请分别选择默认文本模型和默认视觉模型",
                variant: "destructive",
            });
            return;
        }

        setDefaultSaving(true);
        try {
            await saveAdminLlmProviderDefaults(defaults);
            toast({title: "保存成功", description: "默认文本模型和默认视觉模型已更新"});
            await loadProviders();
        } catch (error) {
            toast({
                title: "保存失败",
                description: error instanceof Error ? error.message : "保存默认模型失败",
                variant: "destructive",
            });
        } finally {
            setDefaultSaving(false);
        }
    };

    const handleTestConnection = async () => {
        const validated = validateForm();
        if (!validated) {
            return;
        }

        setTesting(true);
        setTestResult(null);
        try {
            const result = await testAdminLlmProvider(buildProviderReq(validated));
            setTestResult(result);
        } catch (error) {
            const message = error instanceof Error ? error.message : "测试供应商连接失败";
            setTestResult({success: false, message});
        } finally {
            setTesting(false);
        }
    };

    const confirmDelete = async () => {
        if (!deleteTarget) {
            return;
        }
        setDeleting(true);
        try {
            await deleteAdminLlmProvider(deleteTarget);
            toast({title: "删除成功", description: "数据库中的供应商配置已删除"});
            setDeleteTarget(null);
            await loadProviders();
        } catch (error) {
            toast({
                title: "删除失败",
                description: error instanceof Error ? error.message : "删除供应商配置失败",
                variant: "destructive",
            });
        } finally {
            setDeleting(false);
        }
    };

    return (
        <main className="bg-surface-muted p-4 sm:p-6">
            {!loading && !loadError && (
                <DefaultModelCard
                    defaults={defaults}
                    options={defaultModelOptions}
                    saving={defaultSaving}
                    onChange={setDefaults}
                    onSave={handleSaveDefaults}
                />
            )}

            {loading ? (
                <ProviderSkeleton/>
            ) : loadError ? (
                <Alert variant="destructive" className="bg-white">
                    <AlertCircle className="h-4 w-4"/>
                    <AlertTitle>供应商配置加载失败</AlertTitle>
                    <AlertDescription className="flex items-center justify-between gap-4">
                        <span>{loadError}</span>
                        <Button variant="outline" size="sm" onClick={loadProviders}>
                            <RefreshCw className="h-4 w-4"/>
                            重试
                        </Button>
                    </AlertDescription>
                </Alert>
            ) : (
                <div className="space-y-4"><div className="flex items-center justify-between gap-4"><p className="text-sm text-slate-500">管理已接入的模型服务与可用模型。</p><Button onClick={openCreateSheet}><Plus className="h-4 w-4"/>添加供应商</Button></div><div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                    {rows.map(({provider, config}) => (
                        <ProviderCard
                            key={provider}
                            provider={provider}
                            config={config}
                            selectedModelName={selectedModels[provider]}
                            onSelectModel={value => setSelectedModels(prev => ({...prev, [provider]: value}))}
                            onEdit={() => openEditSheet(provider, config)}
                            onDelete={() => setDeleteTarget(provider)}
                        />
                    ))}
                </div></div>
            )}

            <ProviderSheet
                open={sheetOpen}
                editingProvider={editingProvider}
                form={form}
                errors={formErrors}
                saving={saving}
                testing={testing}
                testResult={testResult}
                onOpenChange={open => (open ? setSheetOpen(true) : closeSheet())}
                onUpdateForm={updateForm}
                onUpdateModel={updateModel}
                onAddModel={addModel}
                onRemoveModel={removeModel}
                onCancel={closeSheet}
                onSave={handleSave}
                onTestConnection={handleTestConnection}
            />

            <AlertDialog open={!!deleteTarget} onOpenChange={open => !open && !deleting && setDeleteTarget(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>删除供应商配置</AlertDialogTitle>
                        <AlertDialogDescription>
                            确定删除 {deleteTarget ? getProviderDisplayName(deleteTarget, providers[deleteTarget]) : ""} 的数据库配置吗？如果 YAML 中仍有内置配置，运行时会继续使用内置配置兜底。
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
                        <AlertDialogAction onClick={confirmDelete} disabled={deleting} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
                            {deleting ? "删除中..." : "确认删除"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </main>
    );
}

function buildDefaultModelOptions(providers: Record<string, AdminLlmProviderConfig>) {
    return Object.entries(providers).flatMap(([provider, config]) => {
        const providerName = getProviderDisplayName(provider, config);
        return (config.models || [])
            .filter(model => model.name && isSupportedModelType(model.type))
            .map(model => ({
                value: `${provider}#${model.name}`,
                label: `${providerName} / ${model.name}`,
                type: model.type,
                multimodal: model.multimodal,
                billingType: model.billingType,
            }));
    });
}

function DefaultModelCard({
    defaults,
    options,
    saving,
    onChange,
    onSave,
}: {
    defaults: AdminLlmProviderDefaults;
    options: DefaultModelOption[];
    saving: boolean;
    onChange: (defaults: AdminLlmProviderDefaults) => void;
    onSave: () => void;
}) {
    const textOptions = options.filter(option => option.type === "TEXT" || option.type === "VISION");
    const visionOptions = options.filter(option => option.type === "VISION" || option.multimodal);

    return (
        <section className="mb-5 rounded-lg border bg-white px-5 py-4 shadow-sm">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                <div className="grid min-w-0 flex-1 gap-4 md:grid-cols-2">
                    <FormField label="默认文本模型">
                        <Select value={defaults.text || ""} onValueChange={value => onChange({...defaults, text: value})}>
                            <SelectTrigger>
                                <SelectValue placeholder="选择默认文本模型"/>
                            </SelectTrigger>
                            <SelectContent>
                                {textOptions.map(option => (
                                    <SelectItem key={option.value} value={option.value}>
                                        <span className="inline-flex w-full items-center gap-2">
                                            <span className="truncate">{option.label}</span>
                                            <BillingBadge billingType={option.billingType}/>
                                        </span>
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </FormField>
                    <FormField label="默认视觉模型">
                        <Select value={defaults.vision || ""} onValueChange={value => onChange({...defaults, vision: value})}>
                            <SelectTrigger>
                                <SelectValue placeholder="选择默认视觉模型"/>
                            </SelectTrigger>
                            <SelectContent>
                                {visionOptions.map(option => (
                                    <SelectItem key={option.value} value={option.value}>
                                        <span className="inline-flex w-full items-center gap-2">
                                            <span className="truncate">{option.label}</span>
                                            <BillingBadge billingType={option.billingType}/>
                                        </span>
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </FormField>
                </div>
                <Button onClick={onSave} disabled={saving || !defaults.text || !defaults.vision} className="shrink-0">
                    {saving ? <Loader2 className="h-4 w-4 animate-spin"/> : <Save className="h-4 w-4"/>}
                    {saving ? "保存中..." : "保存默认模型"}
                </Button>
            </div>
        </section>
    );
}

function ProviderCard({
    provider,
    config,
    selectedModelName,
    onSelectModel,
    onEdit,
    onDelete,
}: {
    provider: string;
    config: AdminLlmProviderConfig;
    selectedModelName?: string;
    onSelectModel: (value: string) => void;
    onEdit: () => void;
    onDelete: () => void;
}) {
    const supportedModels = config.models?.filter(item => isSupportedModelType(item.type)) || [];
    const models = supportedModels.length ? supportedModels : [{name: "未配置模型", type: "-", multimodal: false}];
    const selected = selectedModelName || models[0].name;
    const model = models.find(item => item.name === selected) || models[0];
    const displayName = getProviderDisplayName(provider, config);

    return (
        <section className="rounded-lg border bg-white px-5 py-4 shadow-sm">
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="secondary">{config.apiStyle || "-"}</Badge>
                        <Badge variant={config.apiKey ? "green" : "outline"}>
                            {config.apiKey ? "API Key 已配置" : "API Key 未配置"}
                        </Badge>
                    </div>
                    <h2 className="mt-3 text-lg font-semibold text-content-primary">{displayName}</h2>
                    <p className="mt-1 truncate font-mono text-xs text-content-secondary">{config.baseUrl || "-"}</p>
                </div>
                <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" size="icon" title="编辑供应商" onClick={onEdit}>
                        <Edit className="h-4 w-4"/>
                    </Button>
                    <Button variant="ghost" size="icon" title="删除供应商" onClick={onDelete}>
                        <Trash2 className="h-4 w-4 text-destructive"/>
                    </Button>
                </div>
            </div>

            <div className="mt-5 space-y-2">
                <Label>模型</Label>
                <Select value={model.name} onValueChange={onSelectModel}>
                    <SelectTrigger>
                        <SelectValue/>
                    </SelectTrigger>
                    <SelectContent>
                        {models.map(item => (
                            <SelectItem key={`${provider}-${item.name}`} value={item.name}>
                                <span className="inline-flex w-full items-center gap-2">
                                    <span className="truncate">{item.name}</span>
                                    <ModelTypeBadge type={item.type}/>
                                    <BillingBadge billingType={item.billingType}/>
                                </span>
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>

        </section>
    );
}

function ProviderSheet({
    open,
    editingProvider,
    form,
    errors,
    saving,
    testing,
    testResult,
    onOpenChange,
    onUpdateForm,
    onUpdateModel,
    onAddModel,
    onRemoveModel,
    onCancel,
    onSave,
    onTestConnection,
}: {
    open: boolean;
    editingProvider: string | null;
    form: AdminLlmProviderConfig;
    errors: FormErrors;
    saving: boolean;
    testing: boolean;
    testResult: AdminLlmProviderTestResponse | null;
    onOpenChange: (open: boolean) => void;
    onUpdateForm: (field: keyof AdminLlmProviderConfig, value: string) => void;
    onUpdateModel: (index: number, field: keyof UserModelConfig, value: string | boolean | number | undefined) => void;
    onAddModel: () => void;
    onRemoveModel: (index: number) => void;
    onCancel: () => void;
    onSave: () => void;
    onTestConnection: () => void;
}) {
    return (
        <Sheet open={open} onOpenChange={onOpenChange}>
            <SheetContent side="right" className="flex h-full w-[760px] max-w-[calc(100vw-32px)] flex-col gap-0 p-0 sm:max-w-[760px]">
                <SheetHeader className="border-b px-6 py-5 pr-12">
                    <SheetTitle>{editingProvider ? "编辑供应商" : "添加供应商"}</SheetTitle>
                </SheetHeader>

                <div className="flex-1 overflow-y-auto px-6 py-5">
                    <section>
                        <div className="grid grid-cols-2 gap-4">
                            <FormField label="供应商" error={errors.displayName}>
                                <Input
                                    value={form.displayName || ""}
                                    placeholder="例如：DeepSeek / 硅基流动 / 阿里百炼"
                                    onChange={event => onUpdateForm("displayName", event.target.value)}
                                />
                            </FormField>
                            <FormField label="API 风格" error={errors.apiStyle}>
                                <Select value={form.apiStyle || "openai"} onValueChange={value => onUpdateForm("apiStyle", value)}>
                                    <SelectTrigger><SelectValue/></SelectTrigger>
                                    <SelectContent>
                                        {API_STYLE_OPTIONS.map(option => (
                                            <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </FormField>
                            <FormField label="API Key" error={errors.apiKey}>
                                <Input
                                    type="password"
                                    value={form.apiKey || ""}
                                    placeholder={editingProvider ? "保留脱敏值表示不修改" : "请输入供应商 API Key"}
                                    onChange={event => onUpdateForm("apiKey", event.target.value)}
                                />
                            </FormField>
                            <FormField label="Base URL" error={errors.baseUrl}>
                                <Input
                                    value={form.baseUrl || ""}
                                    placeholder="https://api.example.com"
                                    onChange={event => onUpdateForm("baseUrl", event.target.value)}
                                />
                            </FormField>
                        </div>
                        {testResult ? <ConnectionTestResult result={testResult}/> : null}
                    </section>

                    <section className="mt-7">
                        <div className="flex justify-end">
                            <Button variant="outline" size="sm" className="h-9" onClick={onAddModel}>
                                <Plus className="h-4 w-4"/>
                                添加模型
                            </Button>
                        </div>
                        <div className="mt-3 grid grid-cols-1 gap-x-4 gap-y-3 xl:grid-cols-2">
                            {(form.models || []).map((model, index) => (
                                <div key={index} className="grid grid-cols-[minmax(0,1fr)_128px_36px] items-start gap-2">
                                    <FormField error={errors.models?.[index]?.name} className="space-y-1">
                                        <Input
                                            value={model.name}
                                            placeholder="deepseek-flash"
                                            onChange={event => onUpdateModel(index, "name", event.target.value)}
                                        />
                                        <label className="flex items-center gap-2 pt-1 text-xs text-slate-600">
                                            <input type="checkbox" checked={model.type === "VISION" || !!model.multimodal}
                                                disabled={model.type === "VISION"}
                                                onChange={event => onUpdateModel(index, "multimodal", event.target.checked)}/>
                                            支持图片理解
                                        </label>
                                    </FormField>
                                    <FormField error={errors.models?.[index]?.type} className="space-y-1">
                                        <Select value={isSupportedModelType(model.type) ? model.type : "TEXT"} onValueChange={value => onUpdateModel(index, "type", value)}>
                                            <SelectTrigger><SelectValue/></SelectTrigger>
                                            <SelectContent>
                                                {MODEL_TYPE_OPTIONS.map(option => (
                                                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    </FormField>
                                    <Button variant="ghost" size="icon" className="mt-0.5 h-9 w-9" title="删除模型" onClick={() => onRemoveModel(index)}>
                                        <Trash2 className="h-4 w-4 text-destructive"/>
                                    </Button>
                                </div>
                            ))}
                        </div>
                    </section>
                </div>

                <SheetFooter className="border-t px-6 py-4 sm:justify-between sm:space-x-0">
                    <Button variant="outline" onClick={onTestConnection} disabled={saving || testing}>
                        {testing ? <Loader2 className="h-4 w-4 animate-spin"/> : <PlugZap className="h-4 w-4"/>}
                        {testing ? "测试中..." : "测试连接"}
                    </Button>
                    <div className="flex gap-2">
                        <Button variant="outline" onClick={onCancel} disabled={saving || testing}>取消</Button>
                        <Button onClick={onSave} disabled={saving || testing}>
                            <Save className="h-4 w-4"/>
                            {saving ? "保存中..." : "保存"}
                        </Button>
                    </div>
                </SheetFooter>
            </SheetContent>
        </Sheet>
    );
}

function ConnectionTestResult({result}: { result: AdminLlmProviderTestResponse }) {
    const status = result.status || (result.success ? "success" : "error");
    const isSuccess = status === "success";
    const isWarning = status === "warning";

    return (
        <div
            className={cn(
                "mt-4 flex items-start gap-2 rounded-md border px-3 py-2 text-sm",
                isSuccess
                    ? "border-green-200 bg-green-50 text-green-800"
                    : isWarning
                        ? "border-amber-200 bg-amber-50 text-amber-800"
                        : "border-red-200 bg-red-50 text-red-700"
            )}
        >
            {isSuccess ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0"/> : <AlertCircle className="mt-0.5 h-4 w-4 shrink-0"/>}
            <div className="min-w-0">
                <div className="font-medium">{isSuccess ? "连接可用" : isWarning ? "已连到供应商" : "连接失败"}</div>
                <p className="mt-0.5 text-xs leading-5">{result.message}</p>
            </div>
        </div>
    );
}

function FormField({
    label,
    error,
    className,
    children,
}: {
    label?: string;
    error?: string;
    className?: string;
    children: ReactNode;
}) {
    return (
        <div className={className ? `space-y-2 ${className}` : "space-y-2"}>
            {label ? <Label>{label}</Label> : null}
            {children}
            {error ? <p className="text-xs text-destructive">{error}</p> : null}
        </div>
    );
}

function ProviderSkeleton() {
    return (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            {[0, 1, 2, 3].map(item => (
                <div key={item} className="rounded-lg border bg-white px-5 py-4">
                    <div className="flex justify-between gap-4">
                        <div className="space-y-3">
                            <Skeleton className="h-5 w-44"/>
                            <Skeleton className="h-6 w-32"/>
                            <Skeleton className="h-4 w-72"/>
                        </div>
                        <Skeleton className="h-10 w-20"/>
                    </div>
                    <Skeleton className="mt-5 h-10 w-full"/>
                </div>
            ))}
        </div>
    );
}

function ModelTypeBadge({type}: { type?: string }) {
    return (
        <Badge variant="outline" className="shrink-0">
            {getModelTypeLabel(type)}
        </Badge>
    );
}

function BillingBadge({billingType}: { billingType?: string }) {
    const label = getBillingTypeLabel(billingType);
    return (
        <Badge variant={billingType === "FREE" ? "green" : "outline"} className="shrink-0">
            {label}
        </Badge>
    );
}
