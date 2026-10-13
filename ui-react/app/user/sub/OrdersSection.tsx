import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

interface OrdersSectionProps {
  loading: boolean;
  rechargeList: any[];
  getVipLevelLabel: (level: number) => string | String;
  getPayStatusText: (status: number) => string | String;
  handleMarkFailed: (payId: string) => void;
  handleReRecharge: (level: number) => void;
  handleCouponSubmit: (
    level: number,
    amount: number,
    couponCode?: string
  ) => Promise<void>;
}

export default function OrdersSection({
  loading,
  rechargeList,
  getVipLevelLabel,
  getPayStatusText,
  handleMarkFailed,
  handleReRecharge,
  handleCouponSubmit,
}: OrdersSectionProps) {

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] text-gray-400 text-lg">
        加载中...
      </div>
    );
  }

  if (rechargeList?.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] text-gray-400 text-lg">
        暂无充值记录
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <Table className="min-w-full text-sm">
        <TableHeader>
          <TableRow className="bg-gray-100">
            <TableHead>支付ID</TableHead>
            <TableHead>交易号</TableHead>
            <TableHead>金额(元)</TableHead>
            <TableHead>会员等级</TableHead>
            <TableHead>支付状态</TableHead>
            <TableHead>支付时间</TableHead>
            <TableHead>交易ID</TableHead>
            <TableHead>优惠券</TableHead>
            <TableHead>编辑</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rechargeList.map((item) => (
            <TableRow key={item.payId} className="hover:bg-gray-50">
              <TableCell>{item.payId}</TableCell>
              <TableCell>{item.tradeNo}</TableCell>
              <TableCell>{item.amount}</TableCell>
              <TableCell>{getVipLevelLabel(item.level)}</TableCell>
              <TableCell>
                {item.status === 0 ? (
                  <Badge className="px-1 py-1 text-xs">
                    {getPayStatusText(item.status)}
                  </Badge>
                ) : item.status === 1 ? (
                  <Badge variant="orange" className="px-1 py-1 text-xs">
                    {getPayStatusText(item.status)}
                  </Badge>
                ) : item.status === 2 ? (
                  <Badge variant="green" className="px-1 py-1 text-xs">
                    {getPayStatusText(item.status)}
                  </Badge>
                ) : (
                  <Badge variant="destructive" className="px-1 py-1 text-xs">
                    {getPayStatusText(item.status)}
                  </Badge>
                )}
              </TableCell>
              <TableCell>{new Date(item.payTime).toLocaleString()}</TableCell>
              <TableCell>{item.transactionId}</TableCell>
              <TableCell>
                {item.couponCode}
                {item.couponCode ? (
                  <div>优惠:{item.promotionAmount}元</div>
                ) : null}
              </TableCell>
              <TableCell>
                {item.status === 0 && (
                  <Button
                    variant="outline"
                    size="xs"
                    onClick={async () => {
                      await handleCouponSubmit(
                        item.level,
                        Number(item.amount) + Number(item.promotionAmount),
                        item.couponCode
                      );
                    }}
                  >
                    去支付
                  </Button>
                )}
                {item.status === 1 && (
                  <Button
                    variant="secondary"
                    size="xs"
                    onClick={() => handleMarkFailed(item.payId)}
                  >
                    刷新
                  </Button>
                )}
                {item.status === 3 && (
                  <Button
                    variant="outline"
                    size="xs"
                    onClick={() => handleReRecharge(item.level)}
                  >
                    重新充值
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
