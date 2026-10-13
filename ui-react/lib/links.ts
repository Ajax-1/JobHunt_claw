/** AI-GENERATED AIDEV-NOTE: 招聘来源仅允许打开 HTTP(S)，避免将采集文本当脚本链接。 */
export function externalUrl(value?: string | null): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value)
    return ["https:", "http:"].includes(url.protocol) ? url.href : undefined
  } catch { return undefined }
}
