import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "./ui/dialog";
import {
  InstanceDashboardSurface,
  type InstanceDashboardTarget,
  type SnapshotInstanceDashboardTarget,
} from "./instance-dashboard-surface";

export type InstancePanelTarget = InstanceDashboardTarget;

type View =
  | { kind: "closed" }
  | { kind: "live-dashboard"; target: InstancePanelTarget }
  | { kind: "snapshot-dashboard"; target: SnapshotInstanceDashboardTarget };

/**
 * Instance 상세는 SoT mockup의 단일 wide modal로만 본다.
 * live evidence는 Instance summary 행에서, 과거(snapshot) evidence는 Snapshot/History에서 연다.
 * snapshot dashboard target은 selected Application Snapshot id를 포함해야만 열 수 있다.
 * 시계열 stored-projection trend surface는 MVP 범위 밖이라 제공하지 않는다(과거는 snapshot-mode modal로 본다).
 */
export function useInstanceView() {
  const [view, setView] = useState<View>({ kind: "closed" });
  return {
    view,
    openEvidence: (target: InstancePanelTarget) => setView({ kind: "live-dashboard", target }),
    openSnapshotDashboard: (target: SnapshotInstanceDashboardTarget) =>
      setView({ kind: "snapshot-dashboard", target }),
    close: () => setView({ kind: "closed" }),
  };
}

export function InstancePanels({
  onClose,
  view,
}: {
  onClose: () => void;
  view: View;
}) {
  const dashboardOpen = view.kind === "live-dashboard" || view.kind === "snapshot-dashboard";
  const dashboardTarget = dashboardOpen ? view.target : null;

  return (
    <Dialog open={dashboardOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[calc(100vh-2rem)] w-[min(1120px,calc(100vw-2rem))] max-w-none sm:max-w-none overflow-y-auto overscroll-contain rounded-md border-neutral-300 bg-white p-0 text-neutral-900">
        <DialogHeader className="sticky top-0 z-10 border-b border-neutral-200 bg-white/95 px-5 py-4 pr-12 backdrop-blur">
          <DialogTitle className="text-[16px] font-medium">
            {dashboardTarget?.instanceName ?? "Instance Dashboard"}
          </DialogTitle>
          <DialogDescription className="text-[12px] text-neutral-500">
            Application 판단을 대체하지 않고 같은 window의 selected instance evidence만 보여줍니다.
          </DialogDescription>
        </DialogHeader>
        <div className="p-5">
          {view.kind === "live-dashboard" && dashboardTarget && (
            <InstanceDashboardSurface mode="live" target={dashboardTarget} />
          )}
          {view.kind === "snapshot-dashboard" && dashboardTarget && "snapshotId" in dashboardTarget && (
            <InstanceDashboardSurface mode="snapshot" target={dashboardTarget} />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
