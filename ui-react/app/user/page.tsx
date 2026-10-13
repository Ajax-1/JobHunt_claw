"use client";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import ProfileSection from '@/app/user/sub/ProfileSection';
import VipSection from '@/app/user/sub/VipSection';
import OrdersSection from '@/app/user/sub/OrdersSection';
import SubscriptionSection from '@/app/user/sub/SubscriptionSection';
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import {
  getUserDetail,
  updateUserDetail,
  getRechargeList,
  UserSaveReq,
  submitUserInterest,
  fetchUserInterestRecommend,
} from "@/lib/api";
import ChannelSection from "@/app/user/sub/ChannelSection";
import PreferenceSection from "@/app/user/sub/PreferenceSection";
import LlmUsageSection from "@/app/user/sub/LlmUsageSection";
import { Bell, Crown, ReceiptText, Radio, SlidersHorizontal, ChartNoAxesCombined, UserRound } from "lucide-react";
import { Input } from "@/components/ui/input";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { QRCodeCanvas } from "qrcode.react";
import { useToast } from "@/hooks/use-toast";
import { getConfigValue } from "@/lib/config";
import {
  GlobalConfigItemValue,
  toPay,
  markPaying,
  refreshPay,
} from "@/lib/api";

// AI-GENERATED AIDEV-NOTE: 个人导航优先展示求职订阅，账户能力保持独立。
const MENU = [
  { key: "fav", label: "求职订阅", icon: Bell },
  { key: "vip", label: "我的会员", icon: Crown },
  { key: "orders", label: "购买记录", icon: ReceiptText },
  { key: "channel", label: "渠道配置", icon: Radio },
  { key: "preference", label: "偏好设置", icon: SlidersHorizontal },
  { key: "llm-usage", label: "模型用量", icon: ChartNoAxesCombined },
  // { key: "post", label: "内推录入", icon: "🏬" },
  { key: "profile", label: "基本资料", icon: UserRound },
];

const newUserInitValue: UserSaveReq = {
  userId: 0,
  displayName: "",
  email: "",
  intro: "",
  avatar: "",
};

export default function UserPage() {
  const { toast } = useToast();
  const [userInfo, setUserInfo] = useState<any>(null);
  const searchParams = useSearchParams();
  const [activeMenu, setActiveMenu] = useState<string>(searchParams.get("tab") === "vip" ? "vip" : "fav");
  const [form, setForm] = useState<UserSaveReq>(newUserInitValue);
  const [intro, setIntro] = useState<any>({});
  // 推荐职位相关
  const [recommendedJobs, setRecommendedJobs] = useState<any[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(false);

  // 充值相关
  const [payInfo, setPayInfo] = useState<any>(null);
  const [payDialogOpen, setPayDialogOpen] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [paying, setPaying] = useState(false);
  const [loading, setLoading] = useState(false);
  const [rechargeList, setRechargeList] = useState<any[]>([]);
  // 优惠券码弹窗相关
  const [couponDialogOpen, setCouponDialogOpen] = useState(false);
  const [couponCode, setCouponCode] = useState("");
  const [selectedVipAmount, setSelectedVipAmount] = useState<
    number | string | String
  >("");
  const [selectRechargeLevel, setSelectRechargeLevel] = useState<
    number | string | String
  >("");

  // MCP配置相关
  const [mcpConfigDialogOpen, setMcpConfigDialogOpen] = useState(false);
  const [mcpConfig, setMcpConfig] = useState("");

  // 充值业务数据
  const [rechargeOptions, setRechargeOptions] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [vipOptions, setVipOptions] = useState<GlobalConfigItemValue[]>([]);
  // 支付状态定义字典
  const [rechargeStatusOptions, setRechargeStatusOptions] = useState<
    GlobalConfigItemValue[]
  >([]);

  const getVipLevelLabel = (level: number) => {
    const item = vipOptions.find((v) => v.value == `${level}`);
    return item?.intro;
  };
  const getPayStatusText = (status: number) => {
    const item = rechargeStatusOptions.find((v) => v.value == `${status}`);
    return item?.intro;
  };

  useEffect(() => {
    getConfigValue("recharge", "vipPrice").then(setRechargeOptions);
    getConfigValue("user", "RechargeStatusEnum").then(setRechargeStatusOptions);
    getConfigValue("user", "RechargeLevelEnum").then(setVipOptions);
  }, []);

  const fetchRechargeList = async () => {
    setLoading(true);
    try {
      const response = await getRechargeList();
      console.log("发起记录查询");
      setRechargeList(response.list);
    } catch (error) {
      console.error("获取充值记录失败:", error);
    } finally {
      setLoading(false);
    }
  };

  // 处理复制MCP配置到剪贴板
  const handleCopyMcpConfig = () => {
    navigator.clipboard
      .writeText(mcpConfig)
      .then(() => {
        toast({
          title: "成功",
          description: "配置已复制到剪贴板",
        });
        setMcpConfigDialogOpen(false);
      })
      .catch((err) => {
        toast({
          title: "失败",
          description: "复制失败: " + err.message,
          variant: "destructive",
        });
      });
  };

  useEffect(() => {
    getUserDetail().then((data) => {
      // 根据用户信息，构建vip登记
      if (data.role == 3) {
        // 终身会员
        data.vipLevel = 3;
      } else if (data.role == 2) {
        // fixme 这里是一个简单的做法，根据剩余时间来判断等级
        // 是会员，根据到期时间与当前时间之间的间隔
        const period = data.expireTime - Date.now();
        if (period <= 0) {
          // 会员过期
          data.vipLevel = -1;
        } else if (period < 31 * 86400 * 1000) {
          // 小于一个月，月会员
          data.vipLevel = 0;
        } else if (period < 4 * 31 * 86400 * 1000) {
          // 季度
          data.vipLevel = 1;
        } else if (period < 3 * 366 * 31 * 86400 * 1000) {
          data.vipLevel = 2;
        } else {
          data.vipLevel = 3;
        }
      } else {
        // 不是会员
        data.vipLevel = -1;
      }

      setUserInfo(data);

      if (data.interest) {
        // 当存在用户偏好时
        setIntro(data.interest);
      } else {
        setIntro({ interest: "" });
      }

      // 完成mcp相关配置
      let mcpConfigs = {
        mcpServers: {
          JobClaw: data.config,
        },
      };
      setMcpConfig(JSON.stringify(mcpConfigs, null, 2));

      if (activeMenu === "orders") {
        console.log("当前切换为充值记录了");
        fetchRechargeList();
      }

      setForm({
        userId: data.userId || 0,
        displayName: data.displayName || "",
        avatar: data.avatar || "",
        email: data.email || "",
        intro: data.intro || "",
      });
    });
  }, [activeMenu]);

  useEffect(() => {
    if (payInfo && payInfo.prePayExpireTime) {
      const timer = setInterval(() => {
        const left = Math.max(
          0,
          Math.floor((payInfo.prePayExpireTime - Date.now()) / 1000)
        );
        setCountdown(left);
        if (left === 0) clearInterval(timer);
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [payInfo]);

  const handleSaveUserInfo = async () => {
    await updateUserDetail(form)
      .then((res) => {
        console.log("保存成功");
        toast({
          title: "成功",
          description: "个人信息更新成功",
        });
      })
      .catch((err) => {
        toast({
          title: "保存失败",
          description: err.message,
          variant: "destructive",
        });
      });
  };

  // 格式化倒计时为 mm:ss
  const formatCountdown = (sec: number) => {
    const h = Math.floor(sec / 3600)
      .toString()
      .padStart(2, "0");
    const m = Math.floor((sec % 3600) / 60)
      .toString()
      .padStart(2, "0");
    const s = (sec % 60).toString().padStart(2, "0");
    return `${h}:${m}:${s}`;
  };

  // 支付确认
  const handlePaying = async () => {
    if (!payInfo?.payId) return;
    setPaying(true);
    await markPaying(payInfo?.payId)
      .then((res) => {
        toast({
          title: "支付提醒",
          description: "支付状态变更会有一定的延时，到购买记录确认状态吧~",
        });
      })
      .catch((err) => {
        toast({
          title: "支付提醒",
          description: err.message,
          variant: "destructive",
        });
      })
      .finally(() => {
        setPaying(false);
        setPayDialogOpen(false);
      });
  };

  const handleMarkFailed = async (id: number) => {
    await refreshPay(id)
      .then((res) => {
        toast({
          title: "支付提醒",
          description: "状态刷新成功~",
        });

        if (activeMenu === "orders") {
          fetchRechargeList();
        }
      })
      .catch((err) => {
        toast({
          title: "支付提醒",
          description: err.message,
          variant: "destructive",
        });
      });
  };

  const handleFormChange = (key: string, value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
  };

  // 处理充值按钮点击 - 先显示优惠券码弹窗
  const handleRecharge = async (vipAmount: number | string | String) => {
    setSelectRechargeLevel("");
    setSelectedVipAmount(vipAmount);
    setCouponDialogOpen(true);
  };

  // 重新充值的场景
  const handleReRecharge = async (rechargeLevel: number | string | String) => {
    setSelectedVipAmount("");
    setSelectRechargeLevel(rechargeLevel);
    setCouponDialogOpen(true);
  };

  // 处理优惠券码提交
  const handleCouponSubmit = async (
    rechargeLevel?: number | string | String,
    price?: number | string | String,
    code?: string
  ) => {
    // 关闭优惠券码弹窗
    setCouponDialogOpen(false);
    if (!price) {
      price = selectedVipAmount;
    }
    if (!code) {
      code = couponCode;
    }
    if (!rechargeLevel) {
      rechargeLevel = selectRechargeLevel;
    }

    // 调用支付接口，传入优惠券码
    await toPay(selectRechargeLevel, price, code)
      .then((res) => {
        setPayInfo(res);
        setPayDialogOpen(true);
        // 重置优惠券码
        setCouponCode("");
      })
      .catch((e) => {
        toast({
          title: "唤起支付失败了~",
          description: e.message,
          variant: "destructive",
        });
      });
  };

  return (
    <div className="min-h-screen bg-surface-muted pb-10">
      <div className="bg-white border-b border-slate-200/80">
        <div className="max-w-7xl mx-auto flex items-center justify-between px-4 sm:px-8 py-6">
          <div className="flex items-center space-x-4">
            <Avatar className="w-12 h-12 border border-slate-200">
              <AvatarImage
                src={userInfo?.avatar}
                alt={userInfo?.displayName || "avatar"}
              />
              <AvatarFallback>
                {userInfo?.displayName?.[0] || "U"}
              </AvatarFallback>
            </Avatar>
            <div>
              <div className="text-xl font-bold">
                {userInfo?.displayName || (userInfo?.userId ? `用户${userInfo.userId}` : "个人中心")}
              </div>
              <div className="flex items-center space-x-2 mt-1">
                <span
                  className={`text-xs font-medium ${
                    userInfo?.role === 2 ? "text-blue-600" : "text-gray-400"
                  }`}
                >
                  {userInfo?.role === 3 ? "管理员" : userInfo?.role === 2 ? "VIP会员" : "普通用户"}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 主体区域 */}
      <div className="member-workspace mx-auto flex flex-col md:flex-row mt-6 gap-6 px-4 sm:px-8">
        {/* 左侧菜单 */}
        <div className="w-full shrink-0 md:w-52">
          <Card className="mb-4 rounded-2xl shadow-none">
            <CardContent className="p-3">
              <div className="px-3 py-2 text-xs font-medium text-slate-400">个人空间</div>
              <ul className="flex overflow-x-auto md:block">
                {MENU.map((item) => (
                  <li key={item.key} className="shrink-0">
                    <Button
                      variant={activeMenu === item.key ? "secondary" : "ghost"}
                      className="w-full justify-start mb-1"
                      onClick={() => setActiveMenu(item.key)}
                    >
                      <item.icon className="mr-2 h-4 w-4" />
                      {item.label}
                    </Button>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>

        {/* 右侧内容区 */}
        <div className="min-w-0 flex-1">
          <Card className="rounded-2xl shadow-none">
            <CardContent className="p-4 sm:p-6 min-h-[300px]">
              {activeMenu === "profile" ? (
                <ProfileSection
                  userInfo={userInfo}
                  form={form}
                  handleFormChange={handleFormChange}
                  handleSaveUserInfo={handleSaveUserInfo}
                />
              ) : activeMenu === "vip" ? (
                <VipSection
                  userInfo={userInfo}
                  vipOptions={vipOptions}
                  rechargeOptions={rechargeOptions}
                  setMcpConfigDialogOpen={setMcpConfigDialogOpen}
                  handleRecharge={handleRecharge}
                />
              ) : activeMenu === "orders" ? (
                <OrdersSection
                  loading={loading}
                  rechargeList={rechargeList}
                  getVipLevelLabel={(level: number) => getVipLevelLabel(level) || ''}
                  getPayStatusText={(level: number) => getPayStatusText(level) || ''}
                  handleMarkFailed={(payId: string) => handleMarkFailed(Number(payId))}
                  handleReRecharge={handleReRecharge}
                  handleCouponSubmit={handleCouponSubmit}
                />
              ) : activeMenu === "channel" ? (
                <ChannelSection />
              ) : activeMenu === "preference" ? (
                <PreferenceSection />
              ) : activeMenu === "llm-usage" ? (
                <LlmUsageSection />
              ) : (
                <SubscriptionSection
                    intro={intro}
                    activeMenu={activeMenu}
                    setIntro={setIntro}
                />
              )}
            </CardContent>
          </Card>

          {/* 支付弹窗 */}
          <Dialog open={payDialogOpen} onOpenChange={setPayDialogOpen}>
            <DialogContent className="max-w-xs">
              <DialogHeader>
                <DialogTitle>微信支付</DialogTitle>
              </DialogHeader>
              {payInfo && (
                <div className="flex flex-col items-center">
                  <QRCodeCanvas value={payInfo.prePayId} size={180} />
                  <div className="mt-4 text-sm">交易号：{payInfo.tradeNo}</div>
                  <div className="mt-1 text-sm">
                    充值金额：{payInfo.amount} 元
                  </div>
                  <div className="mt-1 text-sm text-red-500">
                    二维码有效期：{formatCountdown(countdown)}
                  </div>
                  {Number(payInfo.amount) == 0 ? (
                    <Button
                      className="mt-4 w-full"
                      onClick={() => setPayDialogOpen(false)}
                    >
                      支付成功
                    </Button>
                  ) : (
                    <Button className="mt-4 w-full" onClick={handlePaying}>
                      {paying ? "处理中..." : "我已支付"}
                    </Button>
                  )}
                </div>
              )}
            </DialogContent>
          </Dialog>
          {/* MCP配置弹窗 */}
          <Dialog
            open={mcpConfigDialogOpen}
            onOpenChange={setMcpConfigDialogOpen}
          >
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>MCP Server配置</DialogTitle>
              </DialogHeader>
              <div className="py-4">
                <div className="mb-4">
                  <label className="block text-sm font-medium mb-2">
                    配置信息
                  </label>
                  <textarea
                    readOnly
                    value={mcpConfig}
                    className="w-full h-64 p-3 border rounded-md font-mono text-sm bg-gray-50"
                  />
                </div>
                <div className="flex justify-end space-x-3">
                  <Button
                    variant="outline"
                    onClick={() => setMcpConfigDialogOpen(false)}
                  >
                    取消
                  </Button>
                  <Button onClick={handleCopyMcpConfig}>复制</Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          {/* 优惠券码输入弹窗 */}
          <Dialog open={couponDialogOpen} onOpenChange={setCouponDialogOpen}>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>输入优惠券码</DialogTitle>
              </DialogHeader>
              <div className="mt-4 flex flex-col gap-4">
                <Input
                  className="focus-visible:ring-blue-500"
                  placeholder="请输入优惠券码（选填）"
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value)}
                />
                <div className="flex gap-3 mt-4">
                  <Button
                    variant="outline"
                    onClick={() => {
                      setCouponCode("");
                      handleCouponSubmit();
                    }}
                    className="flex-1"
                  >
                    直接支付
                  </Button>
                  <Button
                    className="flex-1"
                    onClick={() => handleCouponSubmit()}
                  >
                    确认使用
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </div>
  );
}
