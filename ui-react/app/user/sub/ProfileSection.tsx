import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

interface ProfileSectionProps {
  userInfo: any;
  form: any;
  handleFormChange: (field: string, value: string) => void;
  handleSaveUserInfo: () => void;
}

export default function ProfileSection({ userInfo, form, handleFormChange, handleSaveUserInfo }: ProfileSectionProps) {
  return (
    <div className="max-w-3xl mx-auto">
      <div className="font-bold text-lg mb-6">个人基本信息</div>
      <div className="flex items-start gap-8 mb-6">
        <Avatar className="w-20 h-20 border-4 border-white shadow">
          <AvatarImage src={userInfo?.avatar} alt={userInfo?.displayName || "avatar"} />
          <AvatarFallback>{userInfo?.displayName?.[0] || "U"}</AvatarFallback>
        </Avatar>
        <div className="flex-1 grid grid-cols-2 gap-6">
          <div>
            <div className="mb-1 text-sm text-gray-600">账号ID</div>
            <Input value={form.userId} disabled className="bg-blue-50" />
          </div>
          <div>
            <div className="mb-1 text-sm text-gray-600">昵称</div>
            <Input value={form.displayName} className="bg-blue-50" />
          </div>
          <div>
            <div className="mb-1 text-sm text-gray-600">邮箱</div>
            <Input
              value={form.email}
              onChange={(e) => handleFormChange("email", e.target.value)}
              className="bg-blue-50"
            />
          </div>
        </div>
      </div>
      <div className="mb-6">
        <div className="mb-1 text-sm text-gray-600">介绍</div>
        <Textarea
          value={form.intro}
          onChange={(e) => handleFormChange("intro", e.target.value)}
          className="bg-blue-50 min-h-[100px]"
          placeholder="请输入个人介绍："
        />
      </div>
      <div className="flex justify-end">
        <Button onClick={handleSaveUserInfo}>保存个人信息</Button>
      </div>
    </div>
  );
}