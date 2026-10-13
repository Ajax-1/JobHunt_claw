import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import {
  ChannelConfig,
  fetchDingTalkList,
  fetchDingTalkBind,
  fetchFeiShuList,
  fetchFeiShuBind,
  fetchWeChatBindQrCode,
  fetchWeChatList,
  WeChatAccount,
  getUserDetail,
  updateUserDetail,
} from "@/lib/api";

interface UserInfo {
  id?: number;
  nickname?: string;
  avatar?: string;
  dingDingId?: string;
  feiShuId?: string;
  wxId?: string;
}

export default function ChannelSection() {
  const { toast } = useToast();
  const [activeTab, setActiveTab] = useState("dingtalk");
  
  const [dingtalkList, setDingtalkList] = useState<ChannelConfig[]>([]);
  const [feishuList, setFeishuList] = useState<ChannelConfig[]>([]);
  const [wechatList, setWechatList] = useState<WeChatAccount[]>([]);
  const [userInfo, setUserInfo] = useState<UserInfo>({});
  
  const [dingtalkForm, setDingtalkForm] = useState<ChannelConfig>({
    appId: "",
    appSecret: "",
    mode: "WEBHOOK",
    scope: "OWNER",
    botName: "",
    aiCardId: "",
  });
  const [feishuForm, setFeishuForm] = useState<ChannelConfig>({
    appId: "",
    appSecret: "",
    mode: "WEBHOOK",
    scope: "OWNER",
    botName: "",
  });
  const [editingDingtalkBot, setEditingDingtalkBot] = useState<ChannelConfig | null>(null);
  const [editingFeishuBot, setEditingFeishuBot] = useState<ChannelConfig | null>(null);
  const [dingtalkOpenIdForm, setDingtalkOpenIdForm] = useState({
    openId: "",
  });
  const [feishuOpenIdForm, setFeishuOpenIdForm] = useState({
    openId: "",
  });
  
  const [dingtalkDialogOpen, setDingtalkDialogOpen] = useState(false);
  const [feishuDialogOpen, setFeishuDialogOpen] = useState(false);
  const [dingtalkUserBindDialogOpen, setDingtalkUserBindDialogOpen] = useState(false);
  const [feishuUserBindDialogOpen, setFeishuUserBindDialogOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadChannelData();
  }, []);

  const loadChannelData = async () => {
    try {
      const [dingtalk, feishu, wechat, user] = await Promise.all([
        fetchDingTalkList(),
        fetchFeiShuList(),
        fetchWeChatList(),
        getUserDetail(),
      ]);
      setDingtalkList(dingtalk);
      setFeishuList(feishu);
      setWechatList(wechat);
      setUserInfo(user || {});
    } catch (error) {
      console.error("加载渠道数据失败:", error);
    }
  };

  const handleDingtalkBind = async () => {
    if (!dingtalkForm.appId || !dingtalkForm.appSecret) {
      toast({
        title: "请填写完整信息",
        description: "AppID 和 AppSecret 不能为空",
        variant: "destructive",
      });
      return;
    }
    
    setLoading(true);
    try {
      await fetchDingTalkBind(dingtalkForm);
      toast({
        title: editingDingtalkBot ? "更新成功" : "绑定成功",
        description: editingDingtalkBot ? "钉钉机器人更新成功" : "钉钉机器人绑定成功",
      });
      await loadChannelData();
      setDingtalkDialogOpen(false);
      setDingtalkForm({ appId: "", appSecret: "", mode: "WEBHOOK", scope: "OWNER", botName: "", aiCardId: "" });
      setEditingDingtalkBot(null);
    } catch (error: any) {
      toast({
        title: editingDingtalkBot ? "更新失败" : "绑定失败",
        description: error.message || (editingDingtalkBot ? "更新失败，请稍后重试" : "绑定失败，请稍后重试"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleFeiShuBind = async () => {
    if (!feishuForm.appId || !feishuForm.appSecret) {
      toast({
        title: "请填写完整信息",
        description: "AppID 和 AppSecret 不能为空",
        variant: "destructive",
      });
      return;
    }
    
    setLoading(true);
    try {
      await fetchFeiShuBind(feishuForm);
      toast({
        title: editingFeishuBot ? "更新成功" : "绑定成功",
        description: editingFeishuBot ? "飞书机器人更新成功" : "飞书机器人绑定成功",
      });
      await loadChannelData();
      setFeishuDialogOpen(false);
      setFeishuForm({ appId: "", appSecret: "", mode: "WEBHOOK", scope: "OWNER", botName: "" });
      setEditingFeishuBot(null);
    } catch (error: any) {
      toast({
        title: editingFeishuBot ? "更新失败" : "绑定失败",
        description: error.message || (editingFeishuBot ? "更新失败，请稍后重试" : "绑定失败，请稍后重试"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDingtalkUserBind = async () => {
    if (!dingtalkOpenIdForm.openId) {
      toast({
        title: "请填写完整信息",
        description: "钉钉 OpenID 不能为空",
        variant: "destructive",
      });
      return;
    }
    
    setLoading(true);
    try {
      await updateUserDetail({
        dingDingId: dingtalkOpenIdForm.openId
      });
      toast({
        title: "绑定成功",
        description: "钉钉 OpenID 绑定成功",
      });
      setDingtalkUserBindDialogOpen(false);
      setDingtalkOpenIdForm({ openId: "" });
      loadChannelData();
    } catch (error: any) {
      toast({
        title: "绑定失败",
        description: error.message || "绑定失败，请稍后重试",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleFeishuUserBind = async () => {
    if (!feishuOpenIdForm.openId) {
      toast({
        title: "请填写完整信息",
        description: "飞书 OpenID 不能为空",
        variant: "destructive",
      });
      return;
    }
    
    setLoading(true);
    try {
      await updateUserDetail({
        feiShuId: feishuOpenIdForm.openId
      });
      toast({
        title: "绑定成功",
        description: "飞书 OpenID 绑定成功",
      });
      setFeishuUserBindDialogOpen(false);
      setFeishuOpenIdForm({ openId: "" });
      loadChannelData();
    } catch (error: any) {
      toast({
        title: "绑定失败",
        description: error.message || "绑定失败，请稍后重试",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleWechatBind = async () => {
    try {
      const result = await fetchWeChatBindQrCode();
      const qrUrl = result.qrUrl || result.qrCode;
      if (qrUrl) {
        window.open(qrUrl, '', 'width=500,height=600,scrollbars=yes,resizable=yes');
      } else {
        toast({
          title: "获取二维码失败",
          description: "未获取到有效的二维码链接",
          variant: "destructive",
        });
      }
    } catch (error: any) {
      toast({
        title: "获取二维码失败",
        description: error.message,
        variant: "destructive",
      });
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="font-bold text-lg mb-6">渠道配置</div>
      
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid w-full grid-cols-3">
          <TabsTrigger value="dingtalk">钉钉机器人</TabsTrigger>
          <TabsTrigger value="feishu">飞书机器人</TabsTrigger>
          <TabsTrigger value="wechat">微信 ClawBot</TabsTrigger>
        </TabsList>
        
        <TabsContent value="dingtalk" className="mt-6">
          <Card>
            <CardHeader>
              <CardTitle>钉钉机器人</CardTitle>
              <CardDescription>
                配置钉钉机器人，通过钉钉接收职位推送通知
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-muted-foreground">
                    已绑定机器人: {dingtalkList.length} 个
                  </span>
                  <Dialog open={dingtalkDialogOpen} onOpenChange={setDingtalkDialogOpen}>
                    <DialogTrigger asChild>
                      <Button onClick={() => {
                        setDingtalkForm({
                          appId: "",
                          appSecret: "",
                          mode: "WEBHOOK",
                          scope: "OWNER",
                          botName: "",
                          aiCardId: "",
                        });
                        setEditingDingtalkBot(null);
                      }}>添加机器人</Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>{editingDingtalkBot ? "编辑钉钉机器人" : "添加钉钉机器人"}</DialogTitle>
                      </DialogHeader>
                      <div className="space-y-4 py-4">
                        <div>
                          <label className="text-sm font-medium">AppID</label>
                          <Input
                            value={dingtalkForm.appId}
                            onChange={(e) => setDingtalkForm({ ...dingtalkForm, appId: e.target.value })}
                            placeholder="请输入钉钉 AppID"
                            className="mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">AppSecret</label>
                          <Input
                            value={dingtalkForm.appSecret}
                            onChange={(e) => setDingtalkForm({ ...dingtalkForm, appSecret: e.target.value })}
                            placeholder="请输入钉钉 AppSecret"
                            className="mt-1"
                            type="password"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">机器人昵称</label>
                          <Input
                            value={dingtalkForm.botName}
                            onChange={(e) => setDingtalkForm({ ...dingtalkForm, botName: e.target.value })}
                            placeholder="请输入机器人昵称"
                            className="mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">AI卡片模板ID</label>
                          <Input
                            value={dingtalkForm.aiCardId}
                            onChange={(e) => setDingtalkForm({ ...dingtalkForm, aiCardId: e.target.value })}
                            placeholder="用于流式返回的卡片模板ID"
                            className="mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">Scope（权限范围）</label>
                          <Select
                            value={dingtalkForm.scope}
                            onValueChange={(value) => setDingtalkForm({ ...dingtalkForm, scope: value })}
                          >
                            <SelectTrigger className="mt-1">
                              <SelectValue placeholder="请选择权限范围" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="OWNER">机器人的归属者的聊天渠道</SelectItem>
                              <SelectItem value="LOGIN">用户登录的聊天渠道</SelectItem>
                              <SelectItem value="VIP">VIP用户可以享受的聊天渠道</SelectItem>
                              <SelectItem value="PUBLIC">所有人都可以接入的聊天渠道</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                        <Button onClick={handleDingtalkBind} disabled={loading} className="w-full">
                          {loading ? "绑定中..." : (editingDingtalkBot ? "更新" : "确认绑定")}
                        </Button>
                      </div>
                    </DialogContent>
                  </Dialog>
                </div>
                
                {dingtalkList.length > 0 ? (
                  <div className="space-y-2">
                    {dingtalkList.map((item, index) => (
                      <div 
                        key={index} 
                        className="flex items-center justify-between p-3 border rounded-lg cursor-pointer hover:bg-muted/50 transition-colors"
                        onClick={() => {
                          setDingtalkForm({
                            appId: item.appId || "",
                            appSecret: item.appSecret || "",
                            mode: item.mode || "WEBHOOK",
                            scope: item.scope || "OWNER",
                            botName: item.botName || "",
                            aiCardId: item.aiCardId || "",
                          });
                          setEditingDingtalkBot(item);
                          setDingtalkDialogOpen(true);
                        }}
                      >
                        <div>
                          <div className="font-medium">{item.botName || item.appId}</div>
                          <div className="text-sm text-muted-foreground">
                            AppID: {item.appId} <br/> 模式: {item.mode} <br/> Scope: {item.scope}
                          </div>
                          {item.aiCardId && (
                            <div className="text-sm text-muted-foreground">
                              AI卡片ID: {item.aiCardId}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-muted-foreground">
                    暂无绑定的钉钉机器人
                  </div>
                )}
                
                <div className="border-t pt-4 mt-4">
                  <div className="flex justify-between items-center">
                    <div>
                      <div className="font-medium">绑定钉钉账号</div>
                      <div className="text-sm text-muted-foreground">
                        绑定您的钉钉账号，用于接收职位推送通知
                      </div>
                      {userInfo.dingDingId && (
                          <div className="text-sm text-green-600 mt-1">
                            当前绑定: {userInfo.dingDingId}
                          </div>
                        )}
                      </div>
                      <Dialog open={dingtalkUserBindDialogOpen} onOpenChange={setDingtalkUserBindDialogOpen}>
                        <DialogTrigger asChild>
                          <Button variant="outline">
                            {userInfo.dingDingId ? "修改绑定" : "绑定账号"}
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>绑定钉钉账号</DialogTitle>
                        </DialogHeader>
                        <div className="space-y-4 py-4">
                          <div>
                            <label className="text-sm font-medium">钉钉 OpenID</label>
                            <Input
                              value={dingtalkOpenIdForm.openId}
                              onChange={(e) => setDingtalkOpenIdForm({ openId: e.target.value })}
                              placeholder="请输入您的钉钉 OpenID"
                              className="mt-1"
                            />
                            <p className="text-xs text-muted-foreground mt-1">
                              OpenID 是钉钉用户在企业内的唯一标识符
                            </p>
                          </div>
                          <Button onClick={handleDingtalkUserBind} disabled={loading} className="w-full">
                            {loading ? "绑定中..." : "确认绑定"}
                          </Button>
                        </div>
                      </DialogContent>
                    </Dialog>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        
        <TabsContent value="feishu" className="mt-6">
          <Card>
            <CardHeader>
              <CardTitle>飞书机器人</CardTitle>
              <CardDescription>
                配置飞书机器人，通过飞书接收职位推送通知
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-muted-foreground">
                    已绑定机器人: {feishuList.length} 个
                  </span>
                  <Dialog open={feishuDialogOpen} onOpenChange={setFeishuDialogOpen}>
                    <DialogTrigger asChild>
                      <Button onClick={() => {
                        setFeishuForm({
                          appId: "",
                          appSecret: "",
                          mode: "WEBHOOK",
                          scope: "OWNER",
                          botName: "",
                        });
                        setEditingFeishuBot(null);
                      }}>添加机器人</Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>{editingFeishuBot ? "编辑飞书机器人" : "添加飞书机器人"}</DialogTitle>
                      </DialogHeader>
                      <div className="space-y-4 py-4">
                        <div>
                          <label className="text-sm font-medium">AppID</label>
                          <Input
                            value={feishuForm.appId}
                            onChange={(e) => setFeishuForm({ ...feishuForm, appId: e.target.value })}
                            placeholder="请输入飞书 AppID"
                            className="mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">AppSecret</label>
                          <Input
                            value={feishuForm.appSecret}
                            onChange={(e) => setFeishuForm({ ...feishuForm, appSecret: e.target.value })}
                            placeholder="请输入飞书 AppSecret"
                            className="mt-1"
                            type="password"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">机器人昵称</label>
                          <Input
                            value={feishuForm.botName}
                            onChange={(e) => setFeishuForm({ ...feishuForm, botName: e.target.value })}
                            placeholder="请输入机器人昵称"
                            className="mt-1"
                          />
                        </div>
                        <div>
                          <label className="text-sm font-medium">Scope（权限范围）</label>
                          <Select
                            value={feishuForm.scope}
                            onValueChange={(value) => setFeishuForm({ ...feishuForm, scope: value })}
                          >
                            <SelectTrigger className="mt-1">
                              <SelectValue placeholder="请选择权限范围" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="OWNER">机器人的归属者的聊天渠道</SelectItem>
                              <SelectItem value="LOGIN">用户登录的聊天渠道</SelectItem>
                              <SelectItem value="VIP">VIP用户可以享受的聊天渠道</SelectItem>
                              <SelectItem value="PUBLIC">所有人都可以接入的聊天渠道</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                        <Button onClick={handleFeiShuBind} disabled={loading} className="w-full">
                          {loading ? "绑定中..." : (editingFeishuBot ? "更新" : "确认绑定")}
                        </Button>
                      </div>
                    </DialogContent>
                  </Dialog>
                </div>
                
                {feishuList.length > 0 ? (
                  <div className="space-y-2">
                    {feishuList.map((item, index) => (
                      <div 
                        key={index} 
                        className="flex items-center justify-between p-3 border rounded-lg cursor-pointer hover:bg-muted/50 transition-colors"
                        onClick={() => {
                          setFeishuForm({
                            appId: item.appId || "",
                            appSecret: item.appSecret || "",
                            mode: item.mode || "WEBHOOK",
                            scope: item.scope || "OWNER",
                            botName: item.botName || "",
                          });
                          setEditingFeishuBot(item);
                          setFeishuDialogOpen(true);
                        }}
                      >
                        <div>
                          <div className="font-medium">{item.botName || item.appId}</div>
                          <div className="text-sm text-muted-foreground">
                            AppID: {item.appId} 
                            <br/> 模式: {item.mode}  
                            <br/> Scope: {item.scope}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-muted-foreground">
                    暂无绑定的飞书机器人
                  </div>
                )}
                
                <div className="border-t pt-4 mt-4">
                  <div className="flex justify-between items-center">
                    <div>
                      <div className="font-medium">绑定飞书账号</div>
                      <div className="text-sm text-muted-foreground">
                        绑定您的飞书账号，用于接收职位推送通知
                      </div>
                      {userInfo.feiShuId && (
                          <div className="text-sm text-green-600 mt-1">
                            当前绑定: {userInfo.feiShuId}
                          </div>
                        )}
                      </div>
                      <Dialog open={feishuUserBindDialogOpen} onOpenChange={setFeishuUserBindDialogOpen}>
                        <DialogTrigger asChild>
                          <Button variant="outline">
                            {userInfo.feiShuId ? "修改绑定" : "绑定账号"}
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>绑定飞书账号</DialogTitle>
                        </DialogHeader>
                        <div className="space-y-4 py-4">
                          <div>
                            <label className="text-sm font-medium">飞书 OpenID</label>
                            <Input
                              value={feishuOpenIdForm.openId}
                              onChange={(e) => setFeishuOpenIdForm({ openId: e.target.value })}
                              placeholder="请输入您的飞书 OpenID"
                              className="mt-1"
                            />
                            <p className="text-xs text-muted-foreground mt-1">
                              OpenID 是飞书用户在应用内的唯一标识符
                            </p>
                          </div>
                          <Button onClick={handleFeishuUserBind} disabled={loading} className="w-full">
                            {loading ? "绑定中..." : "确认绑定"}
                          </Button>
                        </div>
                      </DialogContent>
                    </Dialog>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        
        <TabsContent value="wechat" className="mt-6">
          <Card>
            <CardHeader>
              <CardTitle>微信 ClawBot</CardTitle>
              <CardDescription>
                绑定微信 ClawBot，通过微信接收职位推送通知
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-muted-foreground">
                    已绑定机器人: {wechatList.length} 个
                  </span>
                  <Button onClick={handleWechatBind} variant={wechatList.length > 0 ? "secondary" : "default"}>
                    {wechatList.length > 0 ? "重新绑定" : "扫码绑定"}
                  </Button>
                </div>
                
                {wechatList.length > 0 ? (
                  <div className="space-y-2">
                    {wechatList.map((item, index) => (
                      <div key={index} className="flex items-center justify-between p-3 border rounded-lg">
                        <div>
                          <div className="font-medium">微信 ClawBot</div>
                          <div className="text-sm text-muted-foreground">
                            UserID: {item.userId} <br/> AppID: {item.appId}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-muted-foreground">
                    点击"扫码绑定"按钮，将在新窗口打开绑定页面，请在打开的页面中完成微信绑定
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
