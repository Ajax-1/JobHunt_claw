import { cn } from "@/lib/utils";

interface PiLogoProps {
  className?: string;
  markClassName?: string;
  wordmarkClassName?: string;
  showWordmark?: boolean;
}

export function PiLogo({
  className,
  markClassName,
  wordmarkClassName,
  showWordmark = true,
}: PiLogoProps) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <img
        src="/logo-pi.svg"
        alt="JobClaw"
        className={cn("h-9 w-9 shrink-0", markClassName)}
      />
      {showWordmark && (
        <span
          className={cn(
            "text-2xl font-bold text-blue-600",
            wordmarkClassName
          )}
        >
          JobClaw
        </span>
      )}
    </span>
  );
}
