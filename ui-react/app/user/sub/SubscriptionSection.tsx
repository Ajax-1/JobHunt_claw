import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { useEffect, useState } from "react";
import { submitUserInterest, fetchUserInterestRecommend } from "@/lib/api";

interface SubscriptionSectionProps {
  intro: any;
  setIntro: (value: any) => void;
  activeMenu: string;
}

export default function SubscriptionSection({
  intro,
  setIntro,
  activeMenu,
}: SubscriptionSectionProps) {
  // 推荐职位相关
  const [recommendedJobs, setRecommendedJobs] = useState<any[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    if (activeMenu == "fav") {
      console.log("显示当前订阅页面拉");
      fetchRecommendedJobs();
    }
  }, [activeMenu]);

  // 获取推荐职位
  const fetchRecommendedJobs = async () => {
    setLoadingJobs(true);
    try {
      const data = await fetchUserInterestRecommend({ page: 1, size: 10 });
      setRecommendedJobs(data?.list || []);
    } catch (error) {
      console.error("获取推荐职位失败:", error);
      toast({
        title: "获取推荐职位失败",
        description: error instanceof Error ? error.message : String(error),
        variant: "destructive",
      });
    } finally {
      setLoadingJobs(false);
    }
  };

  const handleSubmitInterest = async () => {
    if (!intro.interest) {
      toast({
        title: "失败",
        description: "请输入合法的订阅偏好哦~",
        variant: "destructive",
      });
      return;
    }
    console.log("这里的数据是：", intro.interest);
    await submitUserInterest(intro.interest)
      .then((res) => {
        setIntro(res.interest);
        toast({
          title: "成功",
          description: "个人订阅偏好更新成功",
        });
      })
      .catch((err) => {
        toast({
          title: "失败",
          description: err.message,
          variant: "destructive",
        });
      });
  };

  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-4">
        <div className="mb-2 text-lg font-medium text-gray-800">订阅偏好</div>
        <Textarea
          value={intro.interest?.interest || intro.interest}
          onChange={(e) =>
            setIntro({
              ...intro,
              interest: e.target.value.trim(),
            })
          }
          className="w-full bg-blue-50 min-h-[120px] rounded-lg border border-blue-100 p-3 focus:outline-none focus:ring-2 focus:ring-blue-300 transition-all"
          placeholder="请在这里输入你希望订阅的职业类型，例如：我是2026年毕业的大学生，希望寻找一些服务器开发、前端开发、算法相关的岗位，希望在北京、上海、深圳工作"
        />
      </div>
      <div className="flex justify-end">
        {intro.interest && typeof intro === "object" ? (
          <Button
            onClick={handleSubmitInterest}
            className="bg-orange-600 hover:bg-orange-700 text-white px-6 py-2 rounded-lg transition-colors"
          >
            更新
          </Button>
        ) : (
          <Button
            onClick={handleSubmitInterest}
            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg transition-colors"
          >
            保存
          </Button>
        )}
      </div>

      {/* 已保存的订阅信息展示 */}
      {intro.interest && typeof intro === "object" && (
        <div className="mt-6 p-4 bg-gray-50 rounded-lg border border-gray-100">
          <div className="mb-3 text-lg font-medium text-gray-800">
            根据您的偏好，下面是AI提取的关键信息：
          </div>
          <div className="space-y-2 text-sm text-gray-600">
            {intro.companyIndustry && (
              <p>
                <span className="font-medium text-gray-700">行业偏好：</span>
                {intro.companyIndustry}
              </p>
            )}
            {intro.jobLocation && (
              <p>
                <span className="font-medium text-gray-700">工作地点：</span>
                {intro.jobLocation}
              </p>
            )}
            {intro.recruitmentType && (
              <p>
                <span className="font-medium text-gray-700">职位类型：</span>
                {intro.recruitmentType}
              </p>
            )}
            {intro.recruitmentTarget && (
              <p>
                <span className="font-medium text-gray-700">招聘对象：</span>
                {intro.recruitmentTarget}
              </p>
            )}
            {intro.position && (
              <p>
                <span className="font-medium text-gray-700">目标职位：</span>
                {intro.position}
              </p>
            )}
          </div>
        </div>
      )}

      {/* 系统推荐职位 */}
      <div className="mt-8">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-medium text-gray-800">AI职位推荐</h3>
        </div>
        <div className="space-y-4">
          {loadingJobs ? (
            <div className="flex justify-center py-10">
              <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-blue-500"></div>
            </div>
          ) : recommendedJobs && recommendedJobs.length > 0 ? (
            recommendedJobs.map((item) => (
              <div className="p-4 border border-gray-100 rounded-lg hover:shadow-md transition-shadow cursor-pointer bg-white">
                <div className="flex justify-between items-start">
                  <div>
                    <h4 className="text-base font-semibold text-gray-900">
                      {item.position}
                    </h4>
                    <p className="mt-1 gap-2 text-sm text-gray-600">
                      {item.companyName} ○ {item.companyType} 🚇︎{" "}
                      {item.jobLocation}
                    </p>
                    {item.remarks && (
                      <p className="mt-1 text-sm text-gray-600">
                        备注：{item.remarks}
                      </p>
                    )}
                  </div>
                  <span className="text-sm font-medium text-blue-600 text-right">
                    <a href={item.relatedLink} target="_blank">
                      去投递
                    </a>
                    <div className="py-2 text-gray-600">
                      截止时间：{item.deadline}{" "}
                    </div>
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap gap-2">
                  <span className="px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded-full">
                    {item.recruitmentTarget}
                  </span>
                </div>
              </div>
            ))
          ) : (
            <div className="text-gray-600 font-italic text-sm">
              暂无推荐内容，到首页看看吧~
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
