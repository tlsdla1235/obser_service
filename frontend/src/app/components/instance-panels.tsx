import { useState } from "react";
import { ArrowLeft, History, Server } from "lucide-react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "./ui/sheet";
import { Button } from "./ui/button";

export type InstancePanelTarget = {
  evidenceLink: string;
  instanceId: string;
  instanceName: string;
};

type View =
  | { kind: "closed" }
  | { kind: "evidence"; target: InstancePanelTarget }
  | { kind: "trend"; target: InstancePanelTarget };

/**
 * Instance drawer의 열림 상태만 관리한다.
 * Story 10.3에서는 dashboard read model의 evidence link를 보존하고, 실제 evidence/trend fetch는 Story 10.4로 넘긴다.
 */
export function useInstanceView() {
  const [view, setView] = useState<View>({ kind: "closed" });
  return {
    view,
    openEvidence: (target: InstancePanelTarget) => setView({ kind: "evidence", target }),
    openTrend: (target: InstancePanelTarget) => setView({ kind: "trend", target }),
    close: () => setView({ kind: "closed" }),
  };
}

export function InstancePanels({
  view,
  onClose,
  onOpenTrend,
  onOpenEvidence,
}: {
  view: View;
  onClose: () => void;
  onOpenTrend: (target: InstancePanelTarget) => void;
  onOpenEvidence: (target: InstancePanelTarget) => void;
}) {
  const target = view.kind === "closed" ? null : view.target;

  return (
    <Sheet open={view.kind !== "closed"} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full sm:max-w-[560px] p-0 bg-white text-neutral-900">
        {view.kind === "evidence" && target && (
          <PendingInstanceView
            mode="evidence"
            target={target}
            onSwitch={() => onOpenTrend(target)}
          />
        )}
        {view.kind === "trend" && target && (
          <PendingInstanceView
            mode="trend"
            target={target}
            onSwitch={() => onOpenEvidence(target)}
          />
        )}
      </SheetContent>
    </Sheet>
  );
}

function PendingInstanceView({
  mode,
  target,
  onSwitch,
}: {
  mode: "evidence" | "trend";
  target: InstancePanelTarget;
  onSwitch: () => void;
}) {
  const isEvidence = mode === "evidence";
  return (
    <div>
      <SheetHeader className="px-5 py-4 border-b border-neutral-200">
        <div className="flex items-center justify-between gap-3">
          <SheetTitle className="flex items-center gap-2">
            {isEvidence ? (
              <Server className="h-4 w-4" strokeWidth={1.5} />
            ) : (
              <History className="h-4 w-4" strokeWidth={1.5} />
            )}
            {target.instanceName}
          </SheetTitle>
          <Button variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={onSwitch}>
            {isEvidence ? (
              <>
                <History className="h-3.5 w-3.5" strokeWidth={1.5} /> Trend
              </>
            ) : (
              <>
                <ArrowLeft className="h-3.5 w-3.5" strokeWidth={1.5} /> Evidence
              </>
            )}
          </Button>
        </div>
        <SheetDescription className="text-[12px] text-neutral-500">
          Instance detail wiring은 다음 story 범위입니다.
        </SheetDescription>
      </SheetHeader>
      <div className="p-5 space-y-4 text-[13px]">
        <div className="border border-neutral-200 bg-neutral-50 p-4">
          <div className="text-[11px] uppercase text-neutral-500">handoff link</div>
          <div className="mt-2 break-all text-neutral-900">{target.evidenceLink}</div>
        </div>
        <div className="border border-neutral-200 p-4 text-neutral-700">
          이 패널은 API를 호출하지 않고 dashboard read model의 bounded instance entry만 표시합니다.
          Evidence, trend, history 상세 화면은 Story 10.4에서 이 link를 사용해 연결합니다.
        </div>
      </div>
    </div>
  );
}
