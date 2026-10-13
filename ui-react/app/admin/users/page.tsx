"use client"
import { useEffect, useState } from "react";
import { fetchUserList, updateUserRole, UserListItem, UserListQuery } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getConfigValue } from "@/lib/config";
import { GlobalConfigItemValue } from "@/lib/api";
import { useToast } from "@/hooks/use-toast"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"

function formatDateTime(ts?: number) {
  if (!ts) return "-";
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}

export default function UsersPage() {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [query, setQuery] = useState<UserListQuery>({ page: 1, size: 10 });
  const [total, setTotal] = useState(0);
  const [editingUser, setEditingUser] = useState<UserListItem | null>(null);
  const [editRole, setEditRole] = useState<number>(1);
  const [editExpire, setEditExpire] = useState<string>("");
  const [roleOptions, setRoleOptions] = useState<GlobalConfigItemValue[]>([]);
  const { toast } = useToast();

  useEffect(() => {
    fetchUserList(query).then(res => {
      setUsers(res.list);
      setTotal(res.total);
    });
  }, [query]);

  useEffect(() => {
    getConfigValue('user', 'UserRoleEnum').then(options => {
      setRoleOptions(options);
    });
  }, []);

  const totalPages = Math.ceil(total / (query.size || 10)) || 1;

  const openEdit = (user: UserListItem) => {
    setEditingUser(user);
    setEditRole(user.role);
    setEditExpire(user.expireTime ? new Date(user.expireTime).toISOString().slice(0, 10) : "");
  };

  const handleSave = async () => {
    if (!editingUser) return;
    let expireTime = 0;
    if (editRole === 2) {
      if (!editExpire) {
        alert("VIP用户必须指定到期日");
        return;
      }
      expireTime = new Date(editExpire).getTime();
    }
    await updateUserRole({ userId: editingUser.userId, role: editRole, expireTime })
      .then(res => {
        toast({ title: "更新成功", description: "用户角色已更新", variant: "default" })
        setEditingUser(null);
        setQuery({ ...query }); // 触发刷新
      }).catch(err => {
        toast({ title: "更新失败", description: err.message, variant: "destructive" });
      })
  };

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto max-w-[1440px] px-6 py-6">
        {/* 搜索条件 */}
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-surface-border bg-white p-4 shadow-sm">
          <Input placeholder="用户ID" className="w-36" value={query.userId || ""} onChange={e => setQuery(q => ({ ...q, userId: e.target.value ? Number(e.target.value) : undefined, page: 1 }))} />
          <Input placeholder="昵称" className="w-36" value={query.displayName || ""} onChange={e => setQuery(q => ({ ...q, displayName: e.target.value, page: 1 }))} />
          <Select value={query.role ? String(query.role) : ""} onValueChange={v => setQuery(q => ({ ...q, role: v ? Number(v) : undefined, page: 1 }))}>
            <SelectTrigger className="w-36">
              <SelectValue placeholder="全部角色" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="0">全部角色</SelectItem>
              {roleOptions.map(option => (
                <SelectItem value={option.value as string} key={option.value as string}>{option.intro}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button className="h-10 px-6" onClick={() => setQuery(q => ({ ...q, page: 1 }))}>查询</Button>
        </div>

        {/* 用户表格 */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <Table className="min-w-full text-sm">
            <TableHeader>
              <TableRow className="bg-blue-50">
                <TableHead className="w-[130px] text-blue-600">用户编号</TableHead>
                <TableHead className="w-16 text-blue-600">头像</TableHead>
                <TableHead className="text-blue-600">昵称</TableHead>
                <TableHead className="text-blue-600">角色</TableHead>
                <TableHead className="text-blue-600">邮箱</TableHead>
                <TableHead className="text-blue-600">会员到期日</TableHead>
                <TableHead className="text-blue-600">加入时间</TableHead>
                <TableHead className="w-24 text-blue-600">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users?.length === 0 ? (
                <TableRow><TableCell colSpan={6} className="text-center text-gray-400">暂无数据</TableCell></TableRow>
              ) : users.map(user => (
                <TableRow key={user.userId} className="hover:bg-gray-50">
                  <TableCell>
                    {user.userId}
                  </TableCell>
                  <TableCell>
                    <img src={user.avatar} alt="avatar" className="w-8 h-8 rounded-full border" />
                  </TableCell>
                  <TableCell>{user.displayName}</TableCell>
                  <TableCell>
                    {(() => {
                      const role = roleOptions.find(r => Number(r.value) === user.role);
                      return (
                        <span className={
                          user.role === 1 ? "text-gray-700" :
                            user.role === 2 ? "text-blue-600 font-semibold" :
                              user.role === 3 ? "text-green-600 font-semibold" : "text-gray-400"
                        }>
                          {role ? role.intro : "未知"}
                        </span>
                      );
                    })()}
                  </TableCell>
                  <TableCell>{user.email ? user.email : '-'}</TableCell>
                  <TableCell>
                    {user.expireTime ? new Date(user.expireTime).toLocaleDateString() : "-"}
                  </TableCell>
                  <TableCell>
                    {formatDateTime(user.createTime)}
                  </TableCell>
                  <TableCell>
                    <Button size="sm" variant="outline" onClick={() => openEdit(user)}>
                      编辑
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        {/* 分页 - 右下角 */}
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm text-content-tertiary">
            共 <span className="font-semibold text-content-primary">{total}</span> 个用户
          </div>
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  href="#"
                  onClick={() => setQuery(q => ({ ...q, page: (q.page || 1) - 1 }))}
                  className={query.page === 1 ? "pointer-events-none opacity-50" : ""}
                />
              </PaginationItem>
              <PaginationItem>
                <span className="text-sm text-muted-foreground">
                  第 {query.page} / {totalPages} 页
                </span>
              </PaginationItem>
              <PaginationItem>
                <PaginationNext
                  href="#"
                  onClick={() => setQuery(q => ({ ...q, page: (q.page || 1) + 1 }))}
                  className={query.page === totalPages ? "pointer-events-none opacity-50" : ""}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
        {/* 编辑弹窗 */}
        {editingUser && (
          <Dialog open={true} onOpenChange={() => setEditingUser(null)}>
            <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle>编辑用户角色</DialogTitle>
              </DialogHeader>
              <div className="grid gap-4 py-4">
                <div>
                  <label className="text-sm font-medium">昵称</label>
                  <Input value={editingUser.displayName} disabled />
                </div>
                <div>
                  <label className="text-sm font-medium">角色</label>
                  <Select value={String(editRole)} onValueChange={v => setEditRole(Number(v))}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {roleOptions.map(option => (
                        <SelectItem value={option.value as string} key={option.value as string}>{option.intro}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {editRole === 2 && (
                  <div>
                    <label className="text-sm font-medium">会员到期日</label>
                    <Input type="date" value={editExpire} onChange={e => setEditExpire(e.target.value)} />
                  </div>
                )}
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setEditingUser(null)}>取消</Button>
                <Button onClick={handleSave}>保存</Button>
              </div>
            </DialogContent>
          </Dialog>
        )}
      </div>
    </div>
  );
}
