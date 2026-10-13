"use client"

import { ArrowDown, ArrowUpRight, Bot, BriefcaseBusiness, MessageCircle, ScanFace, Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import styles from "./RecruitmentHero.module.css"

interface RecruitmentHeroProps {
  internship: boolean
  onOpenAssistant: () => void
}

/** AI-GENERATED AIDEV-NOTE: Agent 图形说明产品能力，不模拟任务状态或匹配结果。 */
export function RecruitmentHero({ internship, onOpenAssistant }: RecruitmentHeroProps) {
  return (
    <section className={styles.hero} aria-labelledby="recruitment-heading">
      <div className={styles.copy}>
        <div className={styles.eyebrow}>
          <span className={styles.aiMark}><Sparkles size={13} /> AI 求职伙伴</span>
          <span className="text-xs text-slate-500">{internship ? "实习探索" : "校园招聘"}</span>
        </div>
        <h1 id="recruitment-heading" className={styles.title}>
          {internship ? "第一份实习，" : "下一站的机会，"}<br />
          <span>和 AI 一起发现。</span>
        </h1>
        <p className={styles.description}>从梳理求职方向，到发现合适岗位。<br className="hidden sm:block" />让JobClaw Agent，陪你把下一步想清楚。</p>
        <div className="mt-6 flex flex-wrap items-center gap-3">
          <Button onClick={onOpenAssistant} className="h-11 gap-2 rounded-xl px-5 shadow-md shadow-blue-600/15">
            <Sparkles className="h-4 w-4" />和 AI 聊聊<ArrowUpRight className="h-4 w-4" />
          </Button>
          <Button asChild variant="ghost" className="h-11 gap-2 rounded-xl px-4 text-slate-600">
            <a href="#recruitment-search">自己找机会<ArrowDown className="h-4 w-4" /></a>
          </Button>
        </div>
      </div>

      <div className={styles.scene} aria-hidden="true">
        <div className={styles.grid} />
        <div className={styles.halo} />
        <div className={styles.orbit} />
        <svg className={styles.connections} viewBox="0 0 480 320" fill="none">
          <path d="M240 158 C160 158 195 60 100 60 M240 158 C320 158 300 100 382 100 M240 158 C225 218 170 248 110 258" stroke="#a8bbec" strokeWidth="1.5" strokeDasharray="4 6" />
          <circle cx="199" cy="113" r="3" fill="#7d9cf0" />
          <circle cx="320" cy="117" r="3" fill="#a29bef" />
          <circle cx="199" cy="220" r="3" fill="#7d9cf0" />
        </svg>
        <div className={styles.core}>
          <span className={styles.coreIcon}><Bot strokeWidth={1.5} /></span>
          <span className={styles.coreName}>JobClaw</span>
          <span className={styles.coreCaption}>YOUR CAREER AGENT</span>
        </div>
        <div className={`${styles.node} ${styles.profile}`}>
          <span className={`${styles.nodeIcon} bg-blue-50 text-blue-600`}><ScanFace size={19} /></span>
          <div><strong>先认识你</strong><span>梳理经历与偏好</span></div>
        </div>
        <div className={`${styles.node} ${styles.match}`}>
          <span className={`${styles.nodeIcon} bg-violet-50 text-violet-500`}><BriefcaseBusiness size={19} /></span>
          <div><strong>再找机会</strong><span>探索适合的岗位</span></div>
        </div>
        <div className={`${styles.node} ${styles.chat}`}>
          <span className={`${styles.nodeIcon} bg-teal-50 text-teal-600`}><MessageCircle size={19} /></span>
          <div><strong>一起做准备</strong><span>聊聊你的求职困惑</span></div>
        </div>
        <Sparkles className={styles.sparkle} size={21} strokeWidth={1.4} />
        <div className={styles.sceneCaption}><span />从一个问题，开始你的下一步</div>
      </div>
    </section>
  )
}
