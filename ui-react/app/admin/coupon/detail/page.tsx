"use client";
import { useState, useEffect } from "react";
import { useParams } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { fetchCouponDetail, CouponDetailListResponse } from "@/lib/api";
import { formatDateTime } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { useSearchParams } from "next/navigation";
import { getConfigValue } from "@/lib/config";
import { GlobalConfigItemValue } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
export default function CouponDetailPage() {
  // 获取路由参数
  const searchParams = useSearchParams();
  // 初始加载数据
  useEffect(() => {
    const id = searchParams.get("code");
    console.log("从请求参数中获取code", id);
    setCouponId(id || "");
  }, [searchParams]);

  const [couponId, setCouponId] = useState("");

  // 用户角色字典
  const [roleOptions, setRoleOptions] = useState<GlobalConfigItemValue[]>([]);
  // 支付状态定义字典
  const [rechargeStatusOptions, setRechargeStatusOptions] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [vipOptions, setVipOptions] = useState<GlobalConfigItemValue[]>([]);
  useEffect(() => {
    getConfigValue("user", "UserRoleEnum").then(setRoleOptions);
    getConfigValue("user", "RechargeStatusEnum").then(setRechargeStatusOptions);
    getConfigValue("user", "RechargeLevelEnum").then(setVipOptions);
  }, []);

  // 优惠券使用记录数据
  const [couponDetailList, setCouponDetailList] = useState<any[]>([]);
  // 加载状态
  const [loading, setLoading] = useState(false);
  // 分页状态
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 });
  // 提示框
  const { toast } = useToast();

  const getVipLevelLabel = (level: number) => {
    const item = vipOptions.find((v) => v.value == `${level}`);
    return item?.intro;
  };
  const getPayStatusText = (status: number) => {
    const item = rechargeStatusOptions.find((v) => v.value == `${status}`);
    return item?.intro;
  };

  // 获取优惠券详情
  const fetchCouponDetailData = async () => {
    setLoading(true);
    try {
      // 调用API获取优惠券详情
      let cid = couponId;
      if (!couponId) {
        cid = searchParams.get("code") || "";
      }
      const response = await fetchCouponDetail({
        coupon: cid,
        page: pagination.page,
        size: pagination.size,
      });
      // 更新优惠券详情列表和分页信息
      setCouponDetailList(response.list);
      setPagination((prev) => ({
        ...prev,
        total: response.total,
      }));
    } catch (error) {
      toast({ title: "获取优惠券详情失败", variant: "destructive" });
      console.error("Failed to fetch coupon detail:", error);
    } finally {
      setLoading(false);
    }
  };

  // 处理分页变更
  const handlePageChange = (newPage: number) => {
    setPagination((prev) => ({
      ...prev,
      page: newPage,
    }));
  };

  // 初始加载数据
  useEffect(() => {
    fetchCouponDetailData();
  }, [pagination.page, pagination.size]);

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="full-w mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <p className="mb-4 text-sm font-medium text-slate-600">优惠券 #{couponId} · 使用记录</p>
          <div className="overflow-x-auto mb-6">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>支付ID</TableHead>
                  <TableHead>交易号</TableHead>
                  <TableHead>充值金额</TableHead>
                  <TableHead>充值级别</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>支付时间</TableHead>
                  <TableHead>三方交易号</TableHead>
                  <TableHead>优惠金额</TableHead>
                  <TableHead>用户昵称</TableHead>
                  <TableHead>用户头像</TableHead>
                  <TableHead>用户角色</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {couponDetailList.map((item, index) => (
                  <TableRow key={index}>
                    <TableCell>{item.payId}</TableCell>
                    <TableCell>{item.tradeNo}</TableCell>
                    <TableCell>{item.amount}￥</TableCell>
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
                        <Badge
                          variant="destructive"
                          className="px-1 py-1 text-xs"
                        >
                          {getPayStatusText(item.status)}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>{formatDateTime(item.payTime)}</TableCell>
                    <TableCell>{item.transactionId}</TableCell>
                    <TableCell>{item.promotionAmount}￥</TableCell>
                    <TableCell>{item.user?.nickName || "-"}</TableCell>
                    <TableCell>
                      {item.user?.avatar ? (
                        <img
                          src={item.user.avatar}
                          alt="用户头像"
                          className="w-8 h-8 rounded-full"
                        />
                      ) : (
                        "-"
                      )}
                    </TableCell>
                    <TableCell>
                      {(() => {
                        const role = roleOptions.find(
                          (r) => Number(r.value) === item.user.role
                        );
                        return (
                          <span
                            className={
                              item.user.role === 1
                                ? "text-gray-700"
                                : item.user.role === 2
                                ? "text-blue-600 font-semibold"
                                : item.user.role === 3
                                ? "text-green-600 font-semibold"
                                : "text-gray-400"
                            }
                          >
                            {role ? role.intro : "未知"}
                          </span>
                        );
                      })()}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {pagination.total > 0 && <div className="flex items-center justify-between gap-3 pt-4 text-sm text-slate-500">
            <span>共 {pagination.total} 条使用记录</span><div className="flex items-center gap-3">
            <Button variant="outline" size="sm" disabled={pagination.page <= 1} onClick={() => handlePageChange(pagination.page - 1)}>上一页</Button>
            <span>{pagination.page} / {Math.max(1, Math.ceil(pagination.total / pagination.size))}</span>
            <Button variant="outline" size="sm" disabled={pagination.page * pagination.size >= pagination.total} onClick={() => handlePageChange(pagination.page + 1)}>下一页</Button></div>
          </div>}
        </div>
      </div>
    </div>
  );
}
