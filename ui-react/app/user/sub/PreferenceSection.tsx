"use client";
import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import {
  UserPreference,
  getUserPreference,
  updateUserPreference,
  UpdateProviderReq,
  UpdateModelReq,
} from "@/lib/api";

const API_STYLE_OPTIONS = [
  { value: "openai", label: "OpenAI 兼容" },
  { value: "anthropic", label: "Anthropic" },
];

const MODEL_TYPE_OPTIONS = [
  { value: "TEXT", label: "文本模型" },
  { value: "VISION", label: "视觉理解模型" },
  { value: "IMAGE", label: "生图模型" },
  { value: "VIDEO", label: "视频模型" },
  { value: "EMBEDDING", label: "嵌入模型" },
  { value: "ASR", label: "语音识别" },
  { value: "TTS", label: "语音合成" },
];

const COLLECTOR_OPTIONS = [
  { value: "AI_BASED", label: "AI 生成问题" },
  { value: "RULE_BASED", label: "预设问题" },
];

const MODEL_PREFERENCE_TYPES = [
  { value: "vision", label: "视觉理解模型", placeholder: "选择视觉理解模型" },
  { value: "text", label: "文本模型", placeholder: "选择文本模型" },
  { value: "image", label: "生图模型", placeholder: "选择生图模型" },
  { value: "video", label: "视频模型", placeholder: "选择视频模型" },
  { value: "embedding", label: "嵌入模型", placeholder: "选择嵌入模型" },
  { value: "asr", label: "语音识别", placeholder: "选择语音识别模型" },
  { value: "tts", label: "语音合成", placeholder: "选择语音合成模型" },
];

const CHANNEL_OPTIONS = [
  { value: "dingding", label: "钉钉" },
  { value: "feishu", label: "飞书" },
  { value: "wechat-clawbot", label: "微信 ClawBot" },
];

interface ModelConfig {
  name: string;
  type: string;
  multimodal?: boolean;
}

interface OpenAIConfig {
  apiKey: string;
  baseUrl?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
}

interface ProviderConfig {
  apiKey?: string;
  baseUrl?: string;
  chatUrl?: string;
  provider?: string;
  displayName?: string;
  apiStyle?: string;
  completionsPath?: string;
  embeddingsPath?: string;
  imagesPath?: string;
  speechPath?: string;
  transcriptionPath?: string;
  models?: ModelConfig[];
}

export default function PreferenceSection() {
  const { toast } = useToast();
  const [loading, setLoading] = useState<boolean>(false);
  const [fetching, setFetching] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string>("");
  const [preference, setPreference] = useState<UserPreference>({});
  const [addApiDialogOpen, setAddApiDialogOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<string | null>(null);
  const [isEditingApi, setIsEditingApi] = useState(false);
  const [providerForm, setProviderForm] = useState<ProviderConfig>({
    apiKey: "",
    baseUrl: "",
    chatUrl: "",
    completionsPath: "",
    embeddingsPath: "",
    imagesPath: "",
    speechPath: "",
    transcriptionPath: "",
  });
  const [expandedProviders, setExpandedProviders] = useState<Set<string>>(new Set());
  const [modelDialogOpen, setModelDialogOpen] = useState(false);
  const [editingModelProvider, setEditingModelProvider] = useState<string | null>(null);
  const [modelForm, setModelForm] = useState<ModelConfig>({ name: "", type: "TEXT" });
  const [editingModelIndex, setEditingModelIndex] = useState<number>(-1);

  useEffect(() => {
    loadPreference();
  }, []);

  const loadPreference = async () => {
    setFetching(true); setLoadError("");
    try {
      const data = await getUserPreference();
      setPreference(data || {});
    } catch (error) {
      setLoadError("偏好设置暂时无法加载，请稍后重试。");
    } finally { setFetching(false); }
  };

  const getAvailableModels = (type: string): string[] => {
    const providers = getProvidersConfig();
    const models: string[] = [];
    
    Object.entries(providers).forEach(([providerName, config]) => {
      if (config.models) {
        config.models.forEach((model) => {
          const modelType = model.type?.toUpperCase();
          const modelTypeMap: Record<string, string[]> = {
            TEXT: ["text"],
            VISION: ["vision", "image"],
            IMAGE: ["image"],
            VIDEO: ["video"],
            EMBEDDING: ["embedding"],
            ASR: ["asr"],
            TTS: ["tts"],
          };
          
          const allowedTypes = modelTypeMap[modelType] || [];
          if (allowedTypes.includes(type.toLowerCase()) || 
              (type === "text" && modelType === "TEXT") ||
              (type === "vision" && modelType === "VISION")) {
            models.push(`${providerName}#${model.name}`);
          }
        });
      }
    });
    
    return models;
  };

  const handleModelPreferenceChange = async (type: string, value: string) => {
    if (!value || value === "__empty__") return;
    
    setLoading(true);
    try {
      const currentModels = preference.models ? { ...preference.models } : {};
      (currentModels as any)[type] = value;
      
      await updateUserPreference({ models: currentModels });
      setPreference({ ...preference, models: currentModels });
      toast({
        title: "更新成功",
        description: `${MODEL_PREFERENCE_TYPES.find(t => t.value === type)?.label}已更新`,
      });
    } catch (error: any) {
      toast({
        title: "更新失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCollectorChange = async (value: string) => {
    setLoading(true);
    try {
      await updateUserPreference({ collector: value });
      setPreference({ ...preference, collector: value });
      toast({
        title: "更新成功",
        description: "画像收集方式已更新",
      });
    } catch (error: any) {
      toast({
        title: "更新失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleChannelsChange = async (values: string[]) => {
    setLoading(true);
    try {
      await updateUserPreference({ channels: values });
      setPreference({ ...preference, channels: values });
      toast({
        title: "更新成功",
        description: "通知渠道优先级已更新",
      });
    } catch (error: any) {
      toast({
        title: "更新失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const getProviderLabel = (provider: string, config?: ProviderConfig) => {
    const apiStyle = config?.apiStyle || provider.replace("@", "");
    const option = API_STYLE_OPTIONS.find(o => o.value === apiStyle);
    const displayProvider = config?.displayName?.trim() || config?.provider?.replace("@", "") || provider.replace("@", "");
    
    if (apiStyle === "openai" && displayProvider) {
      return `OpenAI - ${displayProvider}`;
    }
    return option?.label || displayProvider;
  };

  const getProvidersConfig = (): Record<string, ProviderConfig> => {
    return preference.providers as Record<string, ProviderConfig> || {};
  };

  const getModelOptionLabel = (modelValue: string) => {
    const [provider, modelName] = modelValue.split("#");
    if (!provider || !modelName) {
      return modelValue;
    }
    return `${getProviderLabel(provider, getProvidersConfig()[provider])} / ${modelName}`;
  };

  const toggleProviderExpand = (provider: string) => {
    const newExpanded = new Set(expandedProviders);
    if (newExpanded.has(provider)) {
      newExpanded.delete(provider);
    } else {
      newExpanded.add(provider);
    }
    setExpandedProviders(newExpanded);
  };

  const openAddApiDialog = (provider?: string) => {
    if (provider) {
      const config = getProvidersConfig()[provider] || {};
      setEditingProvider(config.apiStyle || provider.replace("@", ""));
      setIsEditingApi(true);
      setProviderForm({
        apiKey: config.apiKey || "",
        baseUrl: config.baseUrl || "",
        chatUrl: config.chatUrl || "",
        provider: config.provider?.replace("@", "") || "",
        apiStyle: config.apiStyle || "",
        completionsPath: config.completionsPath || "",
        embeddingsPath: config.embeddingsPath || "",
        imagesPath: config.imagesPath || "",
        speechPath: config.speechPath || "",
        transcriptionPath: config.transcriptionPath || "",
      });
    } else {
      setEditingProvider(null);
      setProviderForm({
        apiKey: "",
        baseUrl: "",
        chatUrl: "",
        provider: "",
        apiStyle: "",
        completionsPath: "",
        embeddingsPath: "",
        imagesPath: "",
        speechPath: "",
        transcriptionPath: "",
      });
      setIsEditingApi(false);
    }
    setAddApiDialogOpen(true);
  };

  const handleSaveProvider = async () => {
    if (!editingProvider) {
      toast({
        title: "请选择 API 风格",
        variant: "destructive",
      });
      return;
    }

    if (!providerForm.apiKey) {
      toast({
        title: "请填写 API Key",
        variant: "destructive",
      });
      return;
    }

    if (editingProvider === "openai" && !providerForm.baseUrl) {
      toast({
        title: "请填写 Base URL",
        variant: "destructive",
      });
      return;
    }

    if (editingProvider === "openai" && providerForm.provider?.startsWith("@")) {
      toast({
        title: "OpenAI 风格不支持 @ 前缀",
        variant: "destructive",
      });
      return;
    }

    setLoading(true);
    try {
      const normalizeProviderName = (provider: string, apiStyle: string): string => {
        if (apiStyle === "openai") {
          return provider;
        }
        return provider.startsWith("@") ? provider : `@${provider}`;
      };
      
      const finalProvider = normalizeProviderName(providerForm.provider || editingProvider, editingProvider);
      
      const providerReq: UpdateProviderReq = {
        provider: finalProvider,
        apiKey: providerForm.apiKey,
        apiStyle: providerForm.apiStyle,
        baseUrl: providerForm.baseUrl,
        completionsPath: providerForm.completionsPath,
        embeddingsPath: providerForm.embeddingsPath,
        imagesPath: providerForm.imagesPath,
        speechPath: providerForm.speechPath,
        transcriptionPath: providerForm.transcriptionPath,
      };
      await updateUserPreference({ provider: providerReq });
      
      const providers = getProvidersConfig();
      const providerKey = finalProvider;
      providers[providerKey] = {
        ...providers[providerKey],
        provider: providerForm.provider,
        apiKey: providerForm.apiKey,
        apiStyle: providerForm.apiStyle,
        baseUrl: providerForm.baseUrl,
        completionsPath: providerForm.completionsPath,
        embeddingsPath: providerForm.embeddingsPath,
        imagesPath: providerForm.imagesPath,
        speechPath: providerForm.speechPath,
        transcriptionPath: providerForm.transcriptionPath,
      };
      setPreference({ ...preference, providers });
      toast({
        title: editingProvider ? "更新成功" : "添加成功",
        description: `${getProviderLabel(providerKey, providerForm)} API 已${editingProvider ? "更新" : "添加"}`,
      });
      setAddApiDialogOpen(false);
    } catch (error: any) {
      toast({
        title: "保存失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteProvider = async (provider: string) => {
    setLoading(true);
    try {
      await updateUserPreference({ 
        provider: { provider },
        deleteProvider: true 
      });
      const providers = getProvidersConfig();
      const config = providers[provider];
      delete providers[provider];
      setPreference({ ...preference, providers });
      toast({
        title: "删除成功",
        description: `${getProviderLabel(provider, config)} API 已删除`,
      });
    } catch (error: any) {
      toast({
        title: "删除失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const openModelDialog = (provider: string, model?: ModelConfig, index?: number) => {
    setEditingModelProvider(provider);
    if (model && index !== undefined) {
      setModelForm(model);
      setEditingModelIndex(index);
    } else {
      setModelForm({ name: "", type: "TEXT" });
      setEditingModelIndex(-1);
    }
    setModelDialogOpen(true);
  };

  const handleSaveModel = async () => {
    if (!editingModelProvider || !modelForm.name) {
      toast({
        title: "请填写模型名称",
        description: "模型名称不能为空",
        variant: "destructive",
      });
      return;
    }

    setLoading(true);
    try {
      const modelReq: UpdateModelReq = {
        name: modelForm.name,
        type: modelForm.type,
        multimodal: modelForm.multimodal,
      };
      
      await updateUserPreference({ 
        provider: { provider: editingModelProvider },
        model: modelReq
      });
      
      const providers = getProvidersConfig();
      const providerConfig = providers[editingModelProvider] || {};
      const models = providerConfig.models || [];

      if (editingModelIndex >= 0) {
        models[editingModelIndex] = modelForm;
      } else {
        models.push(modelForm);
      }

      providers[editingModelProvider] = {
        ...providerConfig,
        models,
      };

      setPreference({ ...preference, providers });
      toast({
        title: editingModelIndex >= 0 ? "更新成功" : "添加成功",
        description: `模型 ${modelForm.name} 已${editingModelIndex >= 0 ? "更新" : "添加"}`,
      });
      setModelDialogOpen(false);
    } catch (error: any) {
      toast({
        title: "保存失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteModel = async (provider: string, index: number) => {
    setLoading(true);
    try {
      const providers = getProvidersConfig();
      const providerConfig = providers[provider] || {};
      const models = providerConfig.models || [];
      const deleteModel = models[index];
      const deleteModelName = deleteModel?.name;
      
      if (deleteModelName) {
        await updateUserPreference({ 
          provider: { provider },
          model: { name: deleteModelName, type: deleteModel?.type || "TEXT" },
          deleteModel: true
        });
      }

      const filteredModels = models.filter((_, i) => i !== index);
      providers[provider] = {
        ...providerConfig,
        models: filteredModels,
      };

      setPreference({ ...preference, providers });
      toast({
        title: "删除成功",
        description: "模型已删除",
      });
    } catch (error: any) {
      toast({
        title: "删除失败",
        description: error.message,
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const getModelTypeLabel = (type: string) => {
    const option = MODEL_TYPE_OPTIONS.find(o => o.value === type);
    return option?.label || type;
  };

  // AI-GENERATED AIDEV-NOTE: 配置读取失败时不展示可保存的默认值。
  if (fetching) return <div className="empty-panel">正在加载偏好设置…</div>;
  if (loadError) return <div role="alert" className="empty-panel"><p>{loadError}</p><Button variant="outline" className="mt-4" onClick={loadPreference}>重新加载</Button></div>;

  return (
    <div className="max-w-4xl mx-auto">
      <div className="font-bold text-lg mb-6">偏好设置</div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">画像收集方式</CardTitle>
            <CardDescription>
              选择如何收集您的个人画像：AI 生成问题或预设问题
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Select
              value={preference.collector || "AI_BASED"}
              onValueChange={handleCollectorChange}
              disabled={loading}
            >
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder="选择收集方式" />
              </SelectTrigger>
              <SelectContent>
                {COLLECTOR_OPTIONS.map(option => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">通知渠道优先级</CardTitle>
            <CardDescription>
              按选择顺序设置通知渠道的优先级
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {CHANNEL_OPTIONS.map((channel) => {
                const selectedChannels = preference.channels || [];
                const isSelected = selectedChannels.includes(channel.value);
                
                return (
                  <div
                    key={channel.value}
                    className={`flex items-center gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${
                      isSelected ? "bg-primary/10 border-primary" : "hover:bg-muted"
                    }`}
                    onClick={() => {
                      const newChannels = isSelected
                        ? selectedChannels.filter(c => c !== channel.value)
                        : [...selectedChannels, channel.value];
                      handleChannelsChange(newChannels);
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => {}}
                      className="w-4 h-4"
                    />
                    <span className="font-medium">{channel.label}</span>
                    {isSelected && (
                      <span className="ml-auto text-sm text-muted-foreground">
                        优先级: {selectedChannels.indexOf(channel.value) + 1}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>

        <details className="rounded-xl border border-slate-200 p-4"><summary className="cursor-pointer text-sm font-medium">高级设置 · 模型与 API</summary><p className="my-3 text-xs leading-6 text-slate-500">通常可以直接使用系统默认模型；需要自定义服务时再调整以下配置。</p>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">模型偏好配置</CardTitle>
            <CardDescription>
              选择您偏好的模型，用于不同的任务场景
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {MODEL_PREFERENCE_TYPES.map((type) => {
                const currentValue = preference.models?.[type.value as keyof typeof preference.models] || "";
                const availableModels = getAvailableModels(type.value);
                
                return (
                  <div key={type.value} className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium">{type.label}</label>
                    <Select
                      value={currentValue}
                      onValueChange={(value) => handleModelPreferenceChange(type.value, value)}
                      disabled={loading || availableModels.length === 0}
                    >
                      <SelectTrigger className="flex-1">
                        <SelectValue placeholder={type.placeholder} />
                      </SelectTrigger>
                      <SelectContent>
                        {availableModels.length > 0 ? (
                          availableModels.map((model) => (
                            <SelectItem key={model} value={model}>
                              {getModelOptionLabel(model)}
                            </SelectItem>
                          ))
                        ) : (
                          <SelectItem value="__empty__" disabled>
                            暂无可用模型
                          </SelectItem>
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">API 配置</CardTitle>
            <CardDescription>
              添加和管理您的 API Key 配置
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex justify-end mb-4">
              <Button onClick={() => openAddApiDialog()}>添加 API</Button>
            </div>

            {Object.keys(getProvidersConfig()).length > 0 ? (
              <div className="space-y-2">
                {Object.entries(getProvidersConfig()).map(([provider, config]) => {
                  const isExpanded = expandedProviders.has(provider);
                  const apiKey = config.apiKey || "";
                  const hasApiKey = apiKey && !apiKey.startsWith("******");

                  return (
                    <div key={provider} className="border rounded-lg overflow-hidden">
                      <div
                        className="flex items-center justify-between p-4 cursor-pointer bg-muted/30 hover:bg-muted/50"
                        onClick={() => toggleProviderExpand(provider)}
                      >
                        <div>
                          <div className="font-medium">{getProviderLabel(provider, config)}</div>
                          <div className="text-sm text-muted-foreground">
                            {hasApiKey ? `API Key: ${apiKey.substring(0, 8)}...` : "已配置 API Key"}
                            {config.models && config.models.length > 0 && ` | ${config.models.length} 个模型`}
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              openAddApiDialog(provider);
                            }}
                          >
                            编辑
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDeleteProvider(provider);
                            }}
                          >
                            删除
                          </Button>
                          <span className="text-muted-foreground">{isExpanded ? "▼" : "▶"}</span>
                        </div>
                      </div>

                      {isExpanded && (
                        <div className="p-4 border-t bg-background">
                          <div className="flex justify-between items-center mb-3">
                            <span className="text-sm font-medium">模型列表</span>
                            <Button size="sm" onClick={() => openModelDialog(provider)}>
                              添加模型
                            </Button>
                          </div>

                          {config.models && config.models.length > 0 ? (
                            <div className="space-y-2">
                              {config.models.map((model, index) => (
                                <div
                                  key={index}
                                  className="flex items-center justify-between p-3 border rounded-lg"
                                >
                                  <div>
                                    <div className="font-medium">{model.name}</div>
                                    <div className="text-sm text-muted-foreground">
                                      类型: {getModelTypeLabel(model.type)}
                                    </div>
                                  </div>
                                  <div className="flex gap-2">
                                    <Button
                                      variant="outline"
                                      size="sm"
                                      onClick={() => openModelDialog(provider, model, index)}
                                    >
                                      编辑
                                    </Button>
                                    <Button
                                      variant="destructive"
                                      size="sm"
                                      onClick={() => handleDeleteModel(provider, index)}
                                    >
                                      删除
                                    </Button>
                                  </div>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="text-center py-4 text-muted-foreground text-sm">
                              暂无模型，请点击"添加模型"按钮添加
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                暂无 API 配置，点击"添加 API"按钮添加
              </div>
            )}
          </CardContent>
        </Card>
        </details>
      </div>

      {addApiDialogOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-background p-6 rounded-lg w-[500px] max-h-[80vh] overflow-y-auto">
            <h3 className="text-lg font-semibold mb-4">
              {editingProvider ? "编辑 API 配置" : "添加 API 配置"}
            </h3>
            
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium">API 风格</label>
                <Select
                  value={editingProvider || ""}
                  onValueChange={(value) => {
                    setEditingProvider(value);
                    setProviderForm({
                      apiKey: "",
                      baseUrl: "",
                      chatUrl: "",
                      provider: "",
                      apiStyle: value,
                      completionsPath: "",
                      embeddingsPath: "",
                      imagesPath: "",
                      speechPath: "",
                      transcriptionPath: "",
                    });
                  }}
                  disabled={isEditingApi}
                >
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="选择 API 风格" />
                  </SelectTrigger>
                  <SelectContent>
                    {API_STYLE_OPTIONS.map(option => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <label className="text-sm font-medium">API Key</label>
                <Input
                  value={providerForm.apiKey || ""}
                  onChange={(e) => setProviderForm({ ...providerForm, apiKey: e.target.value })}
                  placeholder="请输入 API Key"
                  className="mt-1"
                  type="password"
                />
              </div>

              {editingProvider === "openai" && (
                <>
                  <div>
                    <label className="text-sm font-medium">模型提供方</label>
                    <Input
                      value={providerForm.provider || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, provider: e.target.value })}
                      placeholder="如: OpenAI, Azure OpenAI, Anthropic 等"
                      className="mt-1"
                      disabled={isEditingApi}
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Base URL <span className="text-red-500">*</span></label>
                    <Input
                      value={providerForm.baseUrl || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, baseUrl: e.target.value })}
                      placeholder="如: https://api.openai.com"
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">对话完成路径 (completionsPath)</label>
                    <Input
                      value={providerForm.completionsPath || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, completionsPath: e.target.value })}
                      placeholder="如: /v1/chat/completions"
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">嵌入模型路径 (embeddingsPath)</label>
                    <Input
                      value={providerForm.embeddingsPath || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, embeddingsPath: e.target.value })}
                      placeholder="如: /v1/embeddings"
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">图片生成路径 (imagesPath)</label>
                    <Input
                      value={providerForm.imagesPath || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, imagesPath: e.target.value })}
                      placeholder="如: /v1/images/generations"
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">语音合成路径 (speechPath)</label>
                    <Input
                      value={providerForm.speechPath || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, speechPath: e.target.value })}
                      placeholder="如: /v1/audio/speech"
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">语音识别路径 (transcriptionPath)</label>
                    <Input
                      value={providerForm.transcriptionPath || ""}
                      onChange={(e) => setProviderForm({ ...providerForm, transcriptionPath: e.target.value })}
                      placeholder="如: /v1/audio/transcriptions"
                      className="mt-1"
                    />
                  </div>
                </>
              )}

              <div className="flex justify-end gap-2 pt-4">
                <Button variant="outline" onClick={() => setAddApiDialogOpen(false)}>
                  取消
                </Button>
                <Button onClick={handleSaveProvider} disabled={loading}>
                  {loading ? "保存中..." : "保存"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {modelDialogOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-background p-6 rounded-lg w-[450px]">
            <h3 className="text-lg font-semibold mb-4">
              {editingModelIndex >= 0 ? "编辑" : "添加"}模型
            </h3>
            
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium">模型名称</label>
                <Input
                  value={modelForm.name || ""}
                  onChange={(e) => setModelForm({ ...modelForm, name: e.target.value })}
                  placeholder="如: deepseek-flash, gpt-4o"
                  className="mt-1"
                />
              </div>

              <div>
                <label className="text-sm font-medium">模型类型</label>
                <Select
                  value={modelForm.type || "TEXT"}
                  onValueChange={(value) => setModelForm({ ...modelForm, type: value })}
                >
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="选择模型类型" />
                  </SelectTrigger>
                  <SelectContent>
                    {MODEL_TYPE_OPTIONS.map(option => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="flex justify-end gap-2 pt-4">
                <Button variant="outline" onClick={() => setModelDialogOpen(false)}>
                  取消
                </Button>
                <Button onClick={handleSaveModel} disabled={loading}>
                  {loading ? "保存中..." : "保存"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
