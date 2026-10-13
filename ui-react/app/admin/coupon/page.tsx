"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Copy } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import Link from "next/link"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
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
} from "@/components/ui/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Calendar } from "@/components/ui/calendar";
import { format } from "date-fns";
import { z } from "zod";
import { getConfigValue } from "@/lib/config";
import { GlobalConfigItemValue } from "@/lib/api";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { useToast } from "@/hooks/use-toast";
import {
  fetchCouponSave,
  fetchCouponList as fetchCouponListApi,
  fetchCouponDelete,
  CouponListItem,
  fetchCouponUpdate,
} from "@/lib/api";
import { formatDateTime as formatDateTimeStr } from "@/lib/utils";

function configValue(option: GlobalConfigItemValue) {
  return String(option.value);
}

function configIntro(option: GlobalConfigItemValue) {
  return String(option.intro);
}

export default function CouponPage() {
  // 路由对象
  const router = useRouter();

  // 优惠券列表数据
  const [couponList, setCouponList] = useState<CouponListItem[]>([]);
  // 加载状态
  const [loading, setLoading] = useState(false);
  // 搜索条件
  const [searchParams, setSearchParams] = useState<Record<string, string>>({});
  // 分页状态
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 });
  // 新增弹窗状态
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  // 编辑弹窗状态
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  // 当前编辑的优惠券
  const [currentEditCoupon, setCurrentEditCoupon] =
    useState<CouponListItem | null>(null);
  // 删除确认状态
  const [dictToDelete, setDictToDelete] = useState<number | null>(null);
  // 日期选择状态
  const [startDate, setStartDate] = useState<Date>();
  const [endDate, setEndDate] = useState<Date>();
  // 表单状态管理
  const [form, setForm] = useState({
    couponCode: "",
    couponValue: 0.99,
    couponCount: 1,
    couponType: "0",
    scope: "999",
    startTime: new Date(),
    endTime: new Date(),
  });

  // 配置项
  const [couponTypeOptions, setCouponTypeOptions] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [scopeOptions, setScopeOptions] = useState<GlobalConfigItemValue[]>([]);
  const [vipOptions, setVipOptions] = useState<GlobalConfigItemValue[]>([]);
  // 提示框
  const { toast } = useToast();

  // 处理删除确认
  const handleDeleteConfirm = async () => {
    if (!dictToDelete) return;
    try {
      // 调用API删除优惠券
      await fetchCouponDelete(dictToDelete);
      // 关闭弹窗
      setDictToDelete(null);
      // 刷新列表
      fetchCouponList();
      // 显示成功提示
      toast({ title: "优惠券删除成功" });
    } catch (error) {
      toast({ title: "优惠券删除失败", variant: "destructive" });
      console.error("Failed to delete coupon:", error);
    }
  };

  // 获取优惠券列表
  const fetchCouponList = async () => {
    setLoading(true);
    try {
      // 调用API获取优惠券列表
      const response = await fetchCouponListApi({
        page: pagination.page,
        size: pagination.size,
        ...searchParams,
      });
      // 更新优惠券列表和分页信息
      setCouponList(response.list);
      setPagination((prev) => ({
        ...prev,
        total: response.total,
      }));
    } catch (error) {
      toast({ title: "获取优惠券列表失败", variant: "destructive" });
      console.error("Failed to fetch coupon list:", error);
    } finally {
      setLoading(false);
    }
  };

  // 获取配置项
  useEffect(() => {
    // 实际项目中这里应该从API获取配置
    getConfigValue("recharge", "CouponTypeEnum").then(setCouponTypeOptions);
    getConfigValue("recharge", "CouponScopeEnum").then(setScopeOptions);
    getConfigValue("user", "RechargeLevelEnum").then(setVipOptions);
  }, []);

  // 处理搜索
  const handleSearch = () => {
    if (pagination.page !== 1) {
      setPagination((prev) => ({ ...prev, page: 1 }));
    } else {
      fetchCouponList();
    }
  };

  // 初始加载数据
  useEffect(() => {
    fetchCouponList();
  }, [searchParams, pagination.page, pagination.size]);

  // 编辑对话框打开时加载数据
  useEffect(() => {
    if (isEditDialogOpen && currentEditCoupon) {
      setForm({
        couponCode: currentEditCoupon.couponCode ?? "",
        couponValue: Number(currentEditCoupon.couponValue),
        couponCount: currentEditCoupon.couponCount,
        couponType: currentEditCoupon.couponType.toString(),
        scope: currentEditCoupon.scope.toString(),
        startTime: new Date(currentEditCoupon.startTime),
        endTime: new Date(currentEditCoupon.endTime),
      });
    } else if (!isEditDialogOpen) {
      // 关闭对话框时重置表单
      setForm({
        couponCode: "",
        couponValue: 0,
        couponType: "0",
        couponCount: 1,
        scope: "999",
        startTime: new Date(),
        endTime: new Date(),
      });
    }
  }, [isEditDialogOpen, currentEditCoupon]);

  // 处理表单提交
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (currentEditCoupon?.couponId) {
        // 编辑
        await fetchCouponUpdate({
          couponId: currentEditCoupon?.couponId,
          couponCode: form.couponCode,
          couponValue: form.couponValue + "",
          couponCount: form.couponCount,
          couponType: Number(form.couponType),
          scope: Number(form.scope),
          startTime: new Date(form.startTime).getTime(),
          endTime: new Date(form.endTime).getTime(),
        });
      } else {
        // 调用API提交优惠券数据
        await fetchCouponSave({
          couponId: currentEditCoupon?.couponId,
          couponCode: form.couponCode,
          couponValue: form.couponValue + "",
          couponCount: form.couponCount,
          couponType: Number(form.couponType),
          scope: Number(form.scope),
          startTime: new Date(form.startTime).getTime(),
          endTime: new Date(form.endTime).getTime(),
        });
      }
      // 关闭弹窗
      if (isAddDialogOpen) {
        setIsAddDialogOpen(false);
        toast({ title: "优惠券创建成功" });
      } else if (isEditDialogOpen) {
        setIsEditDialogOpen(false);
        toast({ title: "优惠券编辑成功" });
      }
      // 刷新列表
      fetchCouponList();
      // 重置表单
      setForm({
        couponCode: "",
        couponValue: 0,
        couponType: "0",
        couponCount: 1,
        scope: "999",
        startTime: new Date(),
        endTime: new Date(),
      });
    } catch (error) {
      if (isAddDialogOpen) {
        toast({ title: "优惠券创建失败", variant: "destructive" });
      } else if (isEditDialogOpen) {
        toast({ title: "优惠券编辑失败", variant: "destructive" });
      }
      console.error("Failed to save coupon:", error);
    }
  };

  // 格式化优惠券类型
  const formatCouponType = (type: number) => {
    const item = couponTypeOptions.find((item) => Number(item.value) === type);
    return item ? item.intro : "未知";
  };

  // 格式化优惠券作用范围
  const formatScope = (scope: number) => {
    let item = scopeOptions.find((item) => Number(item.value) === scope);
    if (item) return item.intro;

    item = vipOptions.find((item) => Number(item.value) === scope);
    return item ? item.intro : "未知";
  };

  // 格式化优惠值显示
  const formatCouponValue = (value: number, type: number) => {
    return type === 0 ? `${value}元` : `${value}折`;
  };

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-6">
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <Input
                className="h-10 w-72"
                placeholder="请输入券码搜索"
                onChange={(e) =>
                  setSearchParams({
                    ...searchParams,
                    couponCode: e.target.value.trim() ? e.target.value : "",
                  })
                }
              />
              <Button onClick={handleSearch}>搜索</Button>
            </div>
            {/* 新增优惠券对话框 */}
            <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
              <DialogTrigger asChild>
                <Button>新增优惠券</Button>
              </DialogTrigger>
              <DialogContent className="w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                  <DialogTitle>新增优惠券</DialogTitle>
                </DialogHeader>
                <form
                  className="bg-white rounded-lg shadow p-6 space-y-6 mt-4"
                  onSubmit={handleSubmit}
                >
                  <div className="grid grid-cols-2 gap-4">
                    {/* <div>
                      <label className="text-sm font-medium">券码</label>
                      <Input value={form.couponCode} onChange={(e) => handleFormChange('couponCode', e.target.value)} placeholder="请输入券码" />
                    </div> */}
                    <div>
                      <label className="text-sm font-medium">优惠类型</label>
                      <Select
                        value={form.couponType}
                        onValueChange={(v) =>
                          setForm((prev) => ({ ...prev, couponType: v }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择类型" />
                        </SelectTrigger>
                        <SelectContent>
                          {couponTypeOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <label className="text-sm font-medium">
                        优惠{form.couponType == "0" ? "金额(元)" : "折扣(折)"}{" "}
                      </label>
                      <Input
                        type="number"
                        value={form.couponValue}
                        onChange={(e) => {
                          let value = e.target.value;
                          if (form.couponType == "0") {
                            // 优惠金额：最多两位小数
                            value = value.replace(
                              /^(\d+)(\.\d{0,2})\d*/,
                              "$1$2"
                            );
                          } else {
                            // 折扣优惠：最多一位小数，且0-10
                            value = value.replace(
                              /^(\d+)(\.\d{0,1})\d*/,
                              "$1$2"
                            );
                            if (parseFloat(value) > 10) value = "10";
                            if (parseFloat(value) <= 0) value = "";
                          }
                          setForm((prev) => ({
                            ...prev,
                            couponValue: Number(value),
                          }));
                        }}
                        placeholder={
                          form.couponType == "1"
                            ? "请输入优惠金额（最多两位小数）"
                            : "请输入折扣（0-10，最多一位小数）"
                        }
                      />{" "}
                    </div>
                    <div>
                      <label className="text-sm font-medium">优惠券数量</label>
                      <Input
                        type="number"
                        value={form.couponCount}
                        onChange={(e) =>
                          setForm((prev) => ({
                            ...prev,
                            couponCount: Number(e.target.value),
                          }))
                        }
                        placeholder="请输入优惠券数量"
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium">作用范围</label>
                      <Select
                        value={form.scope}
                        onValueChange={(v) =>
                          setForm((prev) => ({ ...prev, scope: v }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择作用范围" />
                        </SelectTrigger>
                        <SelectContent>
                          {scopeOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                          {vipOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="text-sm font-medium">开始时间</label>
                      <Popover>
                        <PopoverTrigger asChild>
                          <Input
                            type="text"
                            value={
                              form.startTime && !isNaN(form.startTime.getTime())
                                ? format(form.startTime, "yyyy-MM-dd")
                                : ""
                            }
                            readOnly
                            placeholder="点击选择开始时间"
                          />
                        </PopoverTrigger>
                        <PopoverContent className="p-0 w-auto">
                          <Calendar
                            mode="single"
                            selected={form.startTime}
                            onSelect={(date) => {
                              if (!date) {
                                return;
                              }
                              // 设置为当天0点
                              const startOfDay = new Date(date);
                              startOfDay.setHours(0, 0, 0, 0);
                              setForm((prev) => ({
                                ...prev,
                                startTime: startOfDay,
                              }));
                              setStartDate(startOfDay);
                            }}
                            initialFocus
                          />
                        </PopoverContent>
                      </Popover>
                    </div>
                    <div>
                      <label className="text-sm font-medium">结束时间</label>
                      <Popover>
                        <PopoverTrigger asChild>
                          <Input
                            type="text"
                            value={
                              form.endTime && !isNaN(form.endTime.getTime())
                                ? format(form.endTime, "yyyy-MM-dd")
                                : ""
                            }
                            readOnly
                            placeholder="点击选择结束时间"
                          />
                        </PopoverTrigger>
                        <PopoverContent className="p-0 w-auto">
                          <Calendar
                            mode="single"
                            selected={form.endTime}
                            onSelect={(date) => {
                              if (!date) {
                                return;
                              }
                              // 设置为当天23:59:59
                              let endOfDay = new Date(date);
                              endOfDay.setHours(23, 59, 59, 999);

                              // 如果选中的时间早于开始时间，则设置为开始时间的当天23:59:59
                              if (form.startTime && endOfDay < form.startTime) {
                                const startDate = new Date(form.startTime);
                                endOfDay = new Date(startDate);
                                endOfDay.setHours(23, 59, 59, 999);
                              }

                              setForm((prev) => ({
                                ...prev,
                                endTime: endOfDay,
                              }));
                              setEndDate(endOfDay);
                            }}
                          />
                        </PopoverContent>
                      </Popover>
                    </div>
                  </div>
                  <div className="flex justify-end space-x-3 pt-4 border-t border-gray-100">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setIsAddDialogOpen(false)}
                    >
                      取消
                    </Button>
                    <Button type="submit">提交</Button>
                  </div>
                </form>
              </DialogContent>
            </Dialog>

            {/* 编辑优惠券对话框 */}
            <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
              <DialogContent className="w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                  <DialogTitle>编辑优惠券</DialogTitle>
                </DialogHeader>
                <form
                  className="bg-white rounded-lg shadow p-6 space-y-6 mt-4"
                  onSubmit={handleSubmit}
                >
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="text-sm font-medium">ID</label>
                      <Input
                        value={currentEditCoupon?.couponId || ""}
                        readOnly
                        disabled
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium">券码</label>
                      <Input value={form.couponCode} readOnly disabled />
                    </div>
                    <div>
                      <label className="text-sm font-medium">优惠类型</label>
                      <Select
                        value={form.couponType}
                        onValueChange={(v) =>
                          setForm((prev) => ({ ...prev, couponType: v }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择类型" />
                        </SelectTrigger>
                        <SelectContent>
                          {couponTypeOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <label className="text-sm font-medium">
                        优惠{form.couponType == "0" ? "金额(元)" : "折扣(折)"}{" "}
                      </label>
                      <Input
                        type="number"
                        value={form.couponValue}
                        onChange={(e) => {
                          let value = e.target.value;
                          if (form.couponType == "0") {
                            // 优惠金额：最多两位小数
                            value = value.replace(
                              /^(\d+)(\.\d{0,2})\d*/,
                              "$1$2"
                            );
                          } else {
                            // 折扣优惠：最多一位小数，且0-10
                            value = value.replace(
                              /^(\d+)(\.\d{0,1})\d*/,
                              "$1$2"
                            );
                            if (parseFloat(value) > 10) value = "10";
                            if (parseFloat(value) <= 0) value = "";
                          }
                          setForm((prev) => ({
                            ...prev,
                            couponValue: Number(value),
                          }));
                        }}
                        placeholder={
                          form.couponType == "1"
                            ? "请输入优惠金额（最多两位小数）"
                            : "请输入折扣（0-10，最多一位小数）"
                        }
                      />{" "}
                    </div>
                    <div>
                      <label className="text-sm font-medium">优惠券数量</label>
                      <Input
                        type="number"
                        value={form.couponCount}
                        onChange={(e) =>
                          setForm((prev) => ({
                            ...prev,
                            couponCount: Number(e.target.value),
                          }))
                        }
                        placeholder="请输入优惠券数量"
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium">作用范围</label>
                      <Select
                        value={form.scope}
                        onValueChange={(v) =>
                          setForm((prev) => ({ ...prev, scope: v }))
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="选择作用范围" />
                        </SelectTrigger>
                        <SelectContent>
                          {scopeOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                          {vipOptions.map((option) => (
                            <SelectItem key={configValue(option)} value={configValue(option)}>
                              {configIntro(option)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="text-sm font-medium">开始时间</label>
                      <Popover>
                        <PopoverTrigger asChild>
                          <Input
                            type="text"
                            value={
                              form.startTime && !isNaN(form.startTime.getTime())
                                ? format(form.startTime, "yyyy-MM-dd")
                                : ""
                            }
                            readOnly
                            placeholder="点击选择开始时间"
                          />
                        </PopoverTrigger>
                        <PopoverContent className="p-0 w-auto">
                          <Calendar
                            mode="single"
                            selected={form.startTime}
                            onSelect={(date) => {
                              if (!date) {
                                return;
                              }
                              // 设置为当天0点
                              const startOfDay = new Date(date);
                              startOfDay.setHours(0, 0, 0, 0);
                              setForm((prev) => ({
                                ...prev,
                                startTime: startOfDay,
                              }));
                              setStartDate(startOfDay);
                            }}
                            initialFocus
                          />
                        </PopoverContent>
                      </Popover>
                    </div>
                    <div>
                      <label className="text-sm font-medium">结束时间</label>
                      <Popover>
                        <PopoverTrigger asChild>
                          <Input
                            type="text"
                            value={
                              form.endTime && !isNaN(form.endTime.getTime())
                                ? format(form.endTime, "yyyy-MM-dd")
                                : ""
                            }
                            readOnly
                            placeholder="点击选择结束时间"
                          />
                        </PopoverTrigger>
                        <PopoverContent className="p-0 w-auto">
                          <Calendar
                            mode="single"
                            selected={form.endTime}
                            onSelect={(date) => {
                              if (!date) {
                                return;
                              }
                              // 设置为当天23:59:59
                              let endOfDay = new Date(date);
                              endOfDay.setHours(23, 59, 59, 999);

                              // 如果选中的时间早于开始时间，则设置为开始时间的当天23:59:59
                              if (form.startTime && endOfDay < form.startTime) {
                                const startDate = new Date(form.startTime);
                                endOfDay = new Date(startDate);
                                endOfDay.setHours(23, 59, 59, 999);
                              }

                              setForm((prev) => ({
                                ...prev,
                                endTime: endOfDay,
                              }));
                              setEndDate(endOfDay);
                            }}
                          />
                        </PopoverContent>
                      </Popover>
                    </div>
                  </div>
                  <div className="flex justify-end space-x-3 pt-4 border-t border-gray-100">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setIsEditDialogOpen(false)}
                    >
                      取消
                    </Button>
                    <Button type="submit">提交</Button>
                  </div>
                </form>
              </DialogContent>
            </Dialog>
          </div>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-blue-50">
                <TableRow>
                  <TableHead className="w-1/24 text-blue-600">ID</TableHead>
                  <TableHead className="w-1/12 text-blue-600">券码</TableHead>
                  <TableHead className="w-1/12 text-blue-600">优惠值</TableHead>
                  <TableHead className="w-1/12 text-blue-600">类型</TableHead>
                  <TableHead className="w-1/12 text-blue-600">数量</TableHead>
                  <TableHead className="w-1/12 text-blue-600">已使用</TableHead>
                  <TableHead className="w-1/12 text-blue-600">作用范围</TableHead>
                  <TableHead className="w-1/8 text-blue-600">开始时间</TableHead>
                  <TableHead className="w-1/8 text-blue-600">结束时间</TableHead>
                  <TableHead className="w-1/12 text-blue-600">编辑</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {couponList.map((coupon) => (
                  <TableRow key={coupon.couponId}>
                    <TableCell>{coupon.couponId}</TableCell>
                    <TableCell className="flex items-center space-x-2">
                      {coupon.couponCode}
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          navigator.clipboard
                            .writeText(coupon.couponCode ?? "")
                            .then(() => {
                              toast({
                                title: "成功",
                                description: "券码已复制到剪贴板",
                              });
                            })
                            .catch((err) => {
                              toast({
                                title: "失败",
                                description: "复制失败: " + err.message,
                                variant: "destructive",
                              });
                            });
                        }}
                      >
                        <Copy className="h-4 w-4" />
                      </Button>
                    </TableCell>
                    <TableCell>
                      {formatCouponValue(
                        Number(coupon.couponValue),
                        coupon.couponType
                      )}
                    </TableCell>
                    <TableCell>{formatCouponType(coupon.couponType)}</TableCell>
                    <TableCell>{coupon.couponCount}</TableCell>
                    <TableCell>
                      {coupon.useCount ? (
                        <Link
                          href={`/admin/coupon/detail?code=${coupon.couponCode}`}
                          className="text-blue-600 hover:underline"
                        >
                          {coupon.useCount}
                        </Link>
                      ) : (
                        "未使用"
                      )}
                    </TableCell>
                    <TableCell>{formatScope(coupon.scope)}</TableCell>
                    <TableCell>{formatDateTimeStr(coupon.startTime)}</TableCell>
                    <TableCell>{formatDateTimeStr(coupon.endTime)}</TableCell>
                    <TableCell className="flex space-x-2">
                      <Button
                        variant="outline"
                        size="xs"
                        onClick={() => {
                          toast({
                            title: "成功",
                            description: "待实现一个生成优惠券的推广图的功能",
                            variant: "orange",
                          });
                        }}
                      >
                        分享
                      </Button>
                      <Button
                        variant="secondary"
                        size="xs"
                        onClick={() => {
                          setCurrentEditCoupon(coupon);
                          setIsEditDialogOpen(true);
                        }}
                      >
                        编辑
                      </Button>
                      <Button
                        variant="destructive"
                        size="xs"
                        onClick={() => coupon.couponId && setDictToDelete(coupon.couponId)}
                      >
                        删除
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <AlertDialog
              open={!!dictToDelete}
              onOpenChange={(isOpen) => !isOpen && setDictToDelete(null)}
            >
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>确定要删除吗？</AlertDialogTitle>
                  <AlertDialogDescription>
                    此操作无法撤销。这将永久删除该优惠券记录。
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>取消</AlertDialogCancel>
                  <AlertDialogAction onClick={handleDeleteConfirm}>
                    确定
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
          {couponList.length === 0 && (
            <div className="flex items-center justify-center py-12 text-gray-500">
              暂无优惠券数据
            </div>
          )}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-content-tertiary">
              共 <span className="font-semibold text-content-primary">{pagination.total}</span> 张优惠券
            </div>
            {pagination.total > 0 && <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (pagination.page > 1) {
                        setPagination({
                          ...pagination,
                          page: pagination.page - 1,
                        });
                      }
                    }}
                    className={
                      pagination.page <= 1
                        ? "pointer-events-none opacity-50"
                        : ""
                    }
                  />
                </PaginationItem>
                <PaginationItem>
                  <span className="text-sm text-muted-foreground">
                    第 {pagination.page} /
                    {Math.ceil(pagination.total / pagination.size)} 页
                  </span>
                </PaginationItem>
                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (
                        pagination.page * pagination.size <
                        pagination.total
                      ) {
                        setPagination({
                          ...pagination,
                          page: pagination.page + 1,
                        });
                      }
                    }}
                    className={
                      pagination.page * pagination.size >= pagination.total
                        ? "pointer-events-none opacity-50"
                        : ""
                    }
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>}
          </div>
        </div>
      </div>
    </div>
  );
}
