import { Button } from "@/components/ui/button";
import {
  GlobalConfigItemValue,
} from "@/lib/api";

interface VipSectionProps {
  userInfo: any;
  vipOptions: GlobalConfigItemValue[];
  rechargeOptions: GlobalConfigItemValue[];
  setMcpConfigDialogOpen: (tag: boolean) => void;
  handleRecharge: (amount: number | string | String) => void;
}

export default function VipSection({ userInfo, vipOptions, rechargeOptions, setMcpConfigDialogOpen, handleRecharge }: VipSectionProps) {
  
  // 会员卡片样式
  const renderVipCard = () => {
    if (!userInfo) return null;
    const isVip =
      userInfo.role == 2 || userInfo.role == 3 ||
      (typeof userInfo.vipLevel === "number" && userInfo.vipLevel >= 0);
    // 如果是管理员，则表示终身会员
    const isLife = userInfo.role == 3 || userInfo.vipLevel === 3;
    if (!isVip) {
      // 非会员灰色卡片
      return (
        <div className="relative bg-slate-100 border border-slate-200 rounded-2xl text-slate-700 p-8 w-full max-w-md mx-auto mb-8 overflow-hidden">
          <div className="text-2xl font-bold mb-2 flex items-center">
            <span className="mr-2">非会员</span>
          </div>
          <div className="text-lg mt-2">{userInfo.displayName}</div>
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm opacity-80">会员ID: {userInfo.userId}</div>
            <div className="text-sm opacity-80">您还不是会员</div>
          </div>
          <div className="absolute right-6 top-6 text-4xl opacity-10">VIP</div>
        </div>
      );
    }
    // 会员卡片
    const level = isLife ? 3 : userInfo.vipLevel;
    const levelInfo = vipOptions.find((l) => l.value === `${level}`);
    return (
      <div>
        {/* 在右上角，添加一个 MCP配置的按钮 */}
        <div className="flex justify-end">
          <Button
            onClick={() => setMcpConfigDialogOpen(true)}
            className="mb-4 border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 shadow-none"
          >
            MCP配置
          </Button>
        </div>
        <div className="relative bg-slate-900 rounded-2xl text-white p-8 w-full max-w-md mx-auto mb-8 overflow-hidden">
          <div className="text-2xl font-bold mb-2 flex items-center">
            <span className="mr-2">{levelInfo?.intro || (isLife ? "终身会员" : "会员")}</span>
            {/* <span className="text-lg font-normal">{levelInfo?.intro || (isLife ? "终身会员" : "会员")}</span> */}
          </div>
          <div className="text-lg mt-2">{userInfo.displayName}</div>
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm opacity-80">会员ID: {userInfo.userId}</div>
            <div className="text-sm opacity-80">
              {isLife
                ? "永久有效"
                : `到期日: ${
                    userInfo.expireTime
                      ? new Date(userInfo.expireTime).toLocaleDateString()
                      : "-"
                  }`}
            </div>
          </div>
          <div className="absolute right-6 top-6 text-4xl opacity-20">VIP</div>
        </div>
      </div>
    );
  };

  // 充值卡片
  const renderRechargeCards = () => (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-2 gap-6 mt-4">
      {userInfo?.role != 3 &&
        rechargeOptions.map((level, index) => (
          <div
            key={`${level.value}`}
            className="relative overflow-hidden rounded-2xl border border-slate-200 w-full"
          >
            <div className="bg-white/90 backdrop-blur-sm rounded-xl p-6 flex flex-col items-center h-full">
              <div className="text-3xl font-semibold mb-2 text-slate-900">
                ￥{level.value}
              </div>
              <div className="text-gray-700 mb-4 text-center">
                {level.intro}
              </div>
              <Button
                className="w-full"
                onClick={() => handleRecharge(level.value)}
              >
                立即充值
              </Button>
            </div>
          </div>
        ))}
    </div>
  );


  return (
    <div>
      {renderVipCard()}
      {/* 只有非终身会员且已是会员，或非会员时显示充值卡片 */}
      {(typeof userInfo?.vipLevel !== "number" || userInfo.vipLevel !== 3) && renderRechargeCards()}
    </div>
  );
}