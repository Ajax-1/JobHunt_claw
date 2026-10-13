"use client"

import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { TablePagination } from "@/components/ui/table-pagination";
import { Input } from "@/components/ui/input";
import { Copy, Search, Building2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

// 模拟内推码数据
const referralCodes = [
  { company: "腾讯", code: "ZXH7FEII" },
  { company: "影石Insta360", code: "1XZH4H5" },
  { company: "合合信息", code: "ESVM9B" },
  { company: "远景能源", code: "NTAfaVW" },
  { company: "卡尔动力", code: "XGX5QK6" },
  { company: "同花顺2026届校招提前批", code: "SL5316" },
  { company: "恒玄科技", code: "IZBCV9" },
  { company: "京东", code: "C4PCB" },
  { company: "途游游戏", code: "DStzkk9e" },
];

export default function InternalReferralPage() {
  const [searchQuery, setSearchQuery] = useState("");
const [filteredCodes, setFilteredCodes] = useState(referralCodes);
const [currentPage, setCurrentPage] = useState(1);
const [itemsPerPage, setItemsPerPage] = useState<number>(10);
const { toast } = useToast();

  const handleSearch = () => {
    setCurrentPage(1);
    if (!searchQuery.trim()) {
      setFilteredCodes(referralCodes);
      return;
    }

    const filtered = referralCodes.filter(item =>
      item.company.toLowerCase().includes(searchQuery.toLowerCase())
    );
    setFilteredCodes(filtered);
  };

  const handleReset = () => {
    setCurrentPage(1);
    setSearchQuery("");
    setFilteredCodes(referralCodes);
  };

  const copyToClipboard = async (code: string) => {
    try { await navigator.clipboard.writeText(code); toast({ title: "复制成功", description: "内推码已复制，可前往招聘方页面使用。" }) }
    catch { toast({ title: "复制失败", description: "请手动选择内推码复制。", variant: "destructive" }) }
  };

  // AI-GENERATED AIDEV-NOTE: 沿用现有内推码内容，不虚构验证状态或适用批次。
  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-8 sm:py-12">
      <div className="eyebrow">内推广场</div><h1 className="mt-3 text-3xl font-semibold tracking-tight">多一个入口，多一次机会。</h1><p className="mt-3 text-sm leading-6 text-slate-500">来自社区分享的内推码，使用前请确认招聘批次与有效性。</p>
      <form onSubmit={e => { e.preventDefault(); handleSearch() }} className="mt-8 flex flex-wrap gap-3 rounded-2xl border border-slate-200 bg-white p-4"><div className="min-w-48 flex-1"><Input aria-label="搜索企业名称" placeholder="搜索企业名称" value={searchQuery} onChange={e => setSearchQuery(e.target.value)} /></div><Button type="submit"><Search />搜索</Button><Button type="button" variant="ghost" onClick={handleReset}>重置</Button></form>
      <div className="mb-4 mt-7 flex items-center justify-between"><h2 className="font-semibold">企业内推</h2><span className="text-xs text-slate-500">共 {filteredCodes.length} 条</span></div>
      <div className="grid gap-3 sm:grid-cols-2">{filteredCodes.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage).map(item => <article key={item.code} className="rounded-2xl border border-slate-200 bg-white p-5"><div className="flex items-center gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-50 text-blue-600"><Building2 className="h-5 w-5" /></span><h3 className="font-semibold">{item.company}</h3></div><div className="mt-5 flex items-center justify-between rounded-lg bg-slate-50 p-3"><code className="select-all text-sm tracking-wider text-slate-600">{item.code}</code><Button size="sm" variant="outline" onClick={() => copyToClipboard(item.code)}><Copy />复制</Button></div></article>)}</div>
      {!filteredCodes.length && <div className="empty-panel"><Search className="mx-auto mb-3 h-7 w-7 text-slate-300" /><p>暂无匹配的企业，试试其他关键词。</p></div>}
      {filteredCodes.length > 0 && <div className="mt-6"><TablePagination page={currentPage} pageSize={itemsPerPage} total={filteredCodes.length} currentCount={Math.min(itemsPerPage, Math.max(0, filteredCodes.length - (currentPage - 1) * itemsPerPage))} totalLabel="条内推码" onPageChange={setCurrentPage} onPageSizeChange={size => { setItemsPerPage(size); setCurrentPage(1) }} /></div>}
    </div>
  );
}
