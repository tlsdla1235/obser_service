import { useMemo, useState } from "react";
import {
  Activity,
  AlertCircle,
  ChevronRight,
  Gauge,
  History,
  KeyRound,
  ListChecks,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Server,
} from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Separator } from "./ui/separator";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "./ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "./ui/dialog";
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
} from "./ui/alert-dialog";
import { Alert, AlertDescription, AlertTitle } from "./ui/alert";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./ui/tooltip";
import {
  applicationsByProject,
  dashboardByApplication,
  projects as projectsSeed,
} from "./dashboard-data";
import { InstancePanels, useInstanceView } from "./instance-panels";

function StatusBadge({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center border border-neutral-400 px-1.5 py-0.5 text-[11px] uppercase tracking-wider text-neutral-800">
      {children}
    </span>
  );
}

function SectionLabel({ icon: Icon, children }: { icon: any; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase tracking-wider">
      <Icon className="h-3.5 w-3.5" strokeWidth={1.5} />
      {children}
    </div>
  );
}

export function Dashboard() {
  const [projects] = useState(projectsSeed);
  const [selectedProject, setSelectedProject] = useState(projects[0].projectId);
  const apps = applicationsByProject[selectedProject] ?? [];
  const [selectedApp, setSelectedApp] = useState(apps[0]?.applicationId);
  const [projectFilter, setProjectFilter] = useState("");
  const [appFilter, setAppFilter] = useState("");
  const inst = useInstanceView();

  const activeApp = useMemo(
    () => apps.find((a) => a.applicationId === selectedApp) ?? apps[0],
    [apps, selectedApp],
  );
  const dashboard = activeApp ? dashboardByApplication[activeApp.applicationId] : undefined;

  const filteredProjects = projects.filter((p) =>
    p.name.toLowerCase().includes(projectFilter.toLowerCase()),
  );
  const filteredApps = apps.filter((a) =>
    a.name.toLowerCase().includes(appFilter.toLowerCase()),
  );

  return (
    <TooltipProvider delayDuration={150}>
      <div className="bg-neutral-50 min-h-[calc(100vh-56px)]">
        {/* App shell top bar */}
        <div className="border-b border-neutral-200 bg-white">
          <div className="mx-auto max-w-[1400px] px-6 h-12 flex items-center justify-between text-[13px]">
            <div className="flex items-center gap-2 text-neutral-600">
              <span>Projects</span>
              <ChevronRight className="h-3.5 w-3.5" strokeWidth={1.5} />
              <span className="text-neutral-900">
                {projects.find((p) => p.projectId === selectedProject)?.name}
              </span>
              {activeApp && (
                <>
                  <ChevronRight className="h-3.5 w-3.5" strokeWidth={1.5} />
                  <span className="text-neutral-900">{activeApp.name}</span>
                  <span className="text-neutral-500">· {activeApp.environment}</span>
                </>
              )}
            </div>
            <div className="flex items-center gap-3 text-neutral-600">
              <span className="text-[12px]">
                token: <span className="text-neutral-900">acct@github · exp 24m</span>
              </span>
              <Button variant="outline" size="sm" className="gap-2 border-neutral-300">
                <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> Reload
              </Button>
            </div>
          </div>
        </div>

        <div className="mx-auto max-w-[1400px] grid grid-cols-12 gap-0">
          {/* Project rail */}
          <aside className="col-span-12 lg:col-span-2 border-r border-neutral-200 bg-white min-h-[calc(100vh-104px)]">
            <div className="p-3 border-b border-neutral-200">
              <SectionLabel icon={ListChecks}>Projects</SectionLabel>
              <div className="relative mt-2">
                <Search
                  className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400"
                  strokeWidth={1.5}
                />
                <Input
                  value={projectFilter}
                  onChange={(e) => setProjectFilter(e.target.value)}
                  placeholder="검색"
                  className="h-8 pl-7 border-neutral-300 bg-white"
                />
              </div>
            </div>
            <ul>
              {filteredProjects.map((p) => {
                const active = p.projectId === selectedProject;
                return (
                  <li key={p.projectId}>
                    <button
                      onClick={() => {
                        setSelectedProject(p.projectId);
                        const first = applicationsByProject[p.projectId]?.[0];
                        setSelectedApp(first?.applicationId);
                      }}
                      className={`w-full text-left px-3 py-2.5 border-l-2 ${
                        active
                          ? "border-neutral-900 bg-neutral-50"
                          : "border-transparent hover:bg-neutral-50"
                      }`}
                    >
                      <div className="text-[13px] text-neutral-900">{p.name}</div>
                      <div className="mt-1 flex items-center gap-2 text-[11px] text-neutral-500">
                        <span>{p.applicationCount} apps</span>
                        {p.setupConnectionIssueCount > 0 && (
                          <span className="text-neutral-800">
                            · {p.setupConnectionIssueCount} issue
                          </span>
                        )}
                      </div>
                      {p.recentConcern && (
                        <div className="mt-1 text-[11px] text-neutral-600 truncate">
                          {p.recentConcern}
                        </div>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
            <div className="p-3 border-t border-neutral-200">
              <Dialog>
                <DialogTrigger asChild>
                  <Button variant="outline" size="sm" className="w-full gap-2 border-neutral-300">
                    <Plus className="h-3.5 w-3.5" strokeWidth={1.5} /> Project 등록
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>새 Project 등록</DialogTitle>
                    <DialogDescription>
                      Project 생성 후 starter credential이 1회만 표시됩니다. 다시 볼 수 없으니
                      안전한 곳에 보관하세요. 필요하면 rotate 하세요.
                    </DialogDescription>
                  </DialogHeader>
                  <div className="space-y-2">
                    <label className="text-[12px] text-neutral-600">Project name</label>
                    <Input placeholder="orders-platform" />
                  </div>
                  <DialogFooter>
                    <Button variant="outline">취소</Button>
                    <Button className="bg-neutral-900 hover:bg-neutral-800 text-white">
                      생성
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
          </aside>

          {/* Application list */}
          <aside className="col-span-12 lg:col-span-3 border-r border-neutral-200 bg-white">
            <div className="p-3 border-b border-neutral-200">
              <SectionLabel icon={Server}>Applications</SectionLabel>
              <div className="relative mt-2">
                <Search
                  className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400"
                  strokeWidth={1.5}
                />
                <Input
                  value={appFilter}
                  onChange={(e) => setAppFilter(e.target.value)}
                  placeholder="검색"
                  className="h-8 pl-7 border-neutral-300"
                />
              </div>
            </div>
            <ul>
              {filteredApps.map((a) => {
                const active = a.applicationId === activeApp?.applicationId;
                return (
                  <li key={a.applicationId}>
                    <button
                      onClick={() => setSelectedApp(a.applicationId)}
                      className={`w-full text-left p-3 border-b border-neutral-100 ${
                        active ? "bg-neutral-50" : "hover:bg-neutral-50"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div className="text-[13px] text-neutral-900">
                          {a.name}
                          <span className="text-neutral-500"> · {a.environment}</span>
                        </div>
                        <StatusBadge>{a.lifecycleBadge}</StatusBadge>
                      </div>
                      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
                        <div className="border border-neutral-200 p-1.5">
                          <SectionLabel icon={Activity}>metric data</SectionLabel>
                          <div className="text-neutral-800 mt-0.5">{a.metricData.freshnessLabel}</div>
                          <div className="text-neutral-500">{a.metricData.statusSource}</div>
                        </div>
                        <div className="border border-neutral-200 p-1.5">
                          <SectionLabel icon={Radio}>starter</SectionLabel>
                          <div className="text-neutral-800 mt-0.5">
                            {a.starterConnection.heartbeatStatus} · {a.starterConnection.freshnessLabel}
                          </div>
                          <div className="text-neutral-500 truncate">
                            {a.starterConnection.connectionMeaning}
                          </div>
                        </div>
                      </div>
                      {a.topConcern && (
                        <div className="mt-2 text-[11px] text-neutral-700 border-l-2 border-neutral-800 pl-2">
                          {a.topConcern}
                        </div>
                      )}
                    </button>
                  </li>
                );
              })}
              {filteredApps.length === 0 && (
                <li className="p-6 text-[12px] text-neutral-500">application이 없습니다.</li>
              )}
            </ul>
          </aside>

          {/* Main + right panel */}
          <main className="col-span-12 lg:col-span-7">
            {dashboard && activeApp ? (
              <div className="grid grid-cols-12">
                <div className="col-span-12 xl:col-span-8 p-5 space-y-4">
                  {/* Context rail */}
                  <div className="border border-neutral-200 bg-white p-4">
                    <div className="flex items-start justify-between gap-4 flex-wrap">
                      <div>
                        <div className="text-[12px] text-neutral-500">
                          {projects.find((p) => p.projectId === selectedProject)?.name} /{" "}
                          {dashboard.application.name}
                        </div>
                        <div className="text-neutral-900 mt-0.5">
                          {dashboard.application.name}
                          <span className="text-neutral-500">
                            {" "}
                            · {dashboard.application.environment}
                          </span>
                        </div>
                      </div>
                      <div className="text-[12px] text-neutral-600 text-right">
                        <div>
                          window <span className="text-neutral-900">
                            {dashboard.application.currentWindowEndUtc}
                          </span>
                        </div>
                        <div>
                          baseline <span className="text-neutral-700">
                            {dashboard.application.baselineWindowEndUtc}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* State strip */}
                  <div className="border border-neutral-900 bg-white p-4">
                    <div className="flex items-center justify-between">
                      <SectionLabel icon={Gauge}>Metric state</SectionLabel>
                      <StatusBadge>{dashboard.state.label}</StatusBadge>
                    </div>
                    <div className="mt-2 text-neutral-900">{dashboard.state.rationale}</div>
                    <div className="mt-1 text-[12px] text-neutral-600">
                      {dashboard.state.recommendedAction}
                    </div>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className="mt-2 text-[11px] text-neutral-500 underline decoration-dotted underline-offset-2 cursor-help inline-block">
                          freshness: {dashboard.application.freshness}
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>accepted bucket 기반 metric data freshness</TooltipContent>
                    </Tooltip>
                  </div>

                  {/* Starter connection strip — separate band */}
                  <div className="border border-neutral-300 bg-white p-4">
                    <div className="flex items-center justify-between">
                      <SectionLabel icon={Radio}>Starter connection</SectionLabel>
                      <StatusBadge>{dashboard.starterConnection.lastHeartbeatStatus}</StatusBadge>
                    </div>
                    <div className="mt-2 grid grid-cols-2 md:grid-cols-4 gap-3 text-[12px]">
                      <div>
                        <div className="text-neutral-500">source</div>
                        <div className="text-neutral-900">
                          {dashboard.starterConnection.statusSource}
                        </div>
                      </div>
                      <div>
                        <div className="text-neutral-500">last heartbeat</div>
                        <div className="text-neutral-900">
                          {dashboard.starterConnection.lastHeartbeatAt}
                        </div>
                      </div>
                      <div>
                        <div className="text-neutral-500">meaning</div>
                        <div className="text-neutral-900">
                          {dashboard.starterConnection.connectionMeaning}
                        </div>
                      </div>
                      <div>
                        <div className="text-neutral-500">state impact</div>
                        <div className="text-neutral-900">
                          {dashboard.starterConnection.stateImpact}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Recovery copy */}
                  {dashboard.recovery?.isRecovering && (
                    <Alert className="border-neutral-400">
                      <History className="h-4 w-4" strokeWidth={1.5} />
                      <AlertTitle>회복 관찰 중</AlertTitle>
                      <AlertDescription>{dashboard.recovery.note}</AlertDescription>
                    </Alert>
                  )}

                  {/* Headline metrics */}
                  <div className="grid grid-cols-3 gap-0 border border-neutral-200 bg-white">
                    <div className="p-4 border-r border-neutral-200">
                      <div className="text-[11px] uppercase tracking-wider text-neutral-500">
                        requests
                      </div>
                      <div className="text-neutral-900 mt-1">
                        {dashboard.metrics.requestCount.toLocaleString()}
                      </div>
                    </div>
                    <div className="p-4 border-r border-neutral-200">
                      <div className="text-[11px] uppercase tracking-wider text-neutral-500">
                        errors
                      </div>
                      <div className="text-neutral-900 mt-1">
                        {dashboard.metrics.errorCount.toLocaleString()}
                      </div>
                    </div>
                    <div className="p-4">
                      <div className="text-[11px] uppercase tracking-wider text-neutral-500">
                        error rate
                      </div>
                      <div className="text-neutral-900 mt-1">{dashboard.metrics.errorRate}</div>
                    </div>
                  </div>

                  {/* Source-scoped percentiles */}
                  <div className="border border-neutral-200 bg-white">
                    <div className="px-4 py-3 border-b border-neutral-200">
                      <SectionLabel icon={Gauge}>Source-scoped percentiles</SectionLabel>
                    </div>
                    {dashboard.sourceScopedPercentiles.items.length === 0 ? (
                      <div className="p-4 text-[12px] text-neutral-500">
                        현재 윈도우에 percentile point가 없습니다.
                      </div>
                    ) : (
                      <table className="w-full text-[12px]">
                        <thead>
                          <tr className="text-left text-neutral-500">
                            <th className="px-4 py-2">source</th>
                            <th className="px-4 py-2">scope</th>
                            <th className="px-4 py-2">p95</th>
                            <th className="px-4 py-2">p99</th>
                            <th className="px-4 py-2">instance</th>
                            <th className="px-4 py-2">bucket boundary</th>
                          </tr>
                        </thead>
                        <tbody>
                          {dashboard.sourceScopedPercentiles.items.map((it, i) => (
                            <tr key={i} className="border-t border-neutral-100">
                              <td className="px-4 py-2 text-neutral-800">{it.source}</td>
                              <td className="px-4 py-2 text-neutral-800">{it.scope}</td>
                              <td className="px-4 py-2 text-neutral-900">{it.p95Ms} ms</td>
                              <td className="px-4 py-2 text-neutral-900">{it.p99Ms} ms</td>
                              <td className="px-4 py-2 text-neutral-700">{it.instance}</td>
                              <td className="px-4 py-2 text-neutral-500">{it.bucketBoundary}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>

                  {/* Triage / zero insight */}
                  <div className="border border-neutral-200 bg-white">
                    <div className="px-4 py-3 border-b border-neutral-200">
                      <SectionLabel icon={AlertCircle}>Triage</SectionLabel>
                    </div>
                    {dashboard.triageCards.length === 0 ? (
                      <div className="p-4 text-[12px] text-neutral-600">
                        {dashboard.zeroInsight ?? "특이 사항 없음 · 관찰 유지"}
                      </div>
                    ) : (
                      <ul>
                        {dashboard.triageCards.map((t, i) => (
                          <li key={i} className="px-4 py-3 border-b border-neutral-100 last:border-b-0">
                            <div className="text-neutral-900 text-[13px]">{t.title}</div>
                            <div className="text-[12px] text-neutral-700 mt-0.5">{t.detail}</div>
                            <div className="text-[11px] text-neutral-500 mt-1">
                              evidence: {t.evidence}
                            </div>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  {/* Endpoint priority */}
                  <div className="border border-neutral-200 bg-white">
                    <div className="px-4 py-3 border-b border-neutral-200 flex items-center justify-between">
                      <SectionLabel icon={ListChecks}>먼저 확인할 endpoint</SectionLabel>
                      <span className="text-[11px] text-neutral-500">Next check</span>
                    </div>
                    {dashboard.endpointPriority.length === 0 ? (
                      <div className="p-4 text-[12px] text-neutral-500">
                        엔드포인트 후보가 없습니다.
                      </div>
                    ) : (
                      <ol>
                        {dashboard.endpointPriority.map((e, i) => (
                          <li
                            key={i}
                            className="px-4 py-2.5 border-b border-neutral-100 last:border-b-0 flex items-center justify-between"
                          >
                            <div className="flex items-center gap-3">
                              <span className="text-neutral-400 text-[12px] tabular-nums">
                                {String(i + 1).padStart(2, "0")}
                              </span>
                              <span className="text-neutral-900 text-[13px]">{e.route}</span>
                              <span className="text-neutral-600 text-[12px]">· {e.reason}</span>
                            </div>
                            <span className="text-neutral-500 text-[11px]">{e.lastSeen}</span>
                          </li>
                        ))}
                      </ol>
                    )}
                  </div>
                </div>

                {/* Right contextual panel */}
                <aside className="col-span-12 xl:col-span-4 border-l border-neutral-200 bg-white p-5 space-y-4">
                  {/* Credential lifecycle */}
                  <div className="border border-neutral-200">
                    <div className="px-3 py-2.5 border-b border-neutral-200 flex items-center justify-between">
                      <SectionLabel icon={KeyRound}>Starter credential</SectionLabel>
                      <StatusBadge>ACTIVE</StatusBadge>
                    </div>
                    <div className="p-3 text-[12px] space-y-1.5">
                      <div className="flex justify-between">
                        <span className="text-neutral-500">key prefix</span>
                        <span className="text-neutral-900">obs_live_***a7f3</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-neutral-500">issued</span>
                        <span className="text-neutral-800">2026-05-20T10:00:00Z</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-neutral-500">last rotated</span>
                        <span className="text-neutral-800">2026-05-30T12:00:00Z</span>
                      </div>
                    </div>
                    <div className="px-3 py-2 border-t border-neutral-200 flex gap-2">
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button variant="outline" size="sm" className="flex-1 border-neutral-300">
                            Rotate
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Credential을 회전합니까?</AlertDialogTitle>
                            <AlertDialogDescription>
                              회전 직후 새 raw credential이 1회 표시됩니다. 다시 볼 수 없으므로
                              안전한 곳에 즉시 보관하세요. 기존 key는 무효화됩니다.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>취소</AlertDialogCancel>
                            <AlertDialogAction className="bg-neutral-900 hover:bg-neutral-800">
                              회전
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button variant="outline" size="sm" className="flex-1 border-neutral-300">
                            Revoke
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Credential을 폐기합니까?</AlertDialogTitle>
                            <AlertDialogDescription>
                              폐기 즉시 starter connection은 차단됩니다. heartbeat와 bucket
                              ingest가 모두 차단됩니다.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>취소</AlertDialogCancel>
                            <AlertDialogAction className="bg-neutral-900 hover:bg-neutral-800">
                              폐기
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  </div>

                  {/* Instance handoff */}
                  <div className="border border-neutral-200">
                    <div className="px-3 py-2.5 border-b border-neutral-200">
                      <SectionLabel icon={Server}>Instances</SectionLabel>
                    </div>
                    {dashboard.instances.length === 0 ? (
                      <div className="p-3 text-[12px] text-neutral-500">
                        contributing instance가 없습니다.
                      </div>
                    ) : (
                      <ul>
                        {dashboard.instances.map((i) => (
                          <li
                            key={i.instanceId}
                            className="px-3 py-2.5 border-b border-neutral-100 last:border-b-0"
                          >
                            <div className="text-[13px] text-neutral-900">{i.name}</div>
                            <div className="text-[11px] text-neutral-500 mt-0.5">
                              {i.firstSeen} → {i.lastSeen}
                            </div>
                            <div className="text-[12px] text-neutral-700 mt-1">
                              {i.contribution}
                            </div>
                            <div className="mt-2 flex gap-3 text-[11px] text-neutral-700">
                              <button
                                onClick={() => inst.openEvidence(i.instanceId)}
                                className="underline underline-offset-2 hover:text-neutral-900"
                              >
                                evidence
                              </button>
                              <button
                                onClick={() => inst.openTrend(i.instanceId)}
                                className="underline underline-offset-2 hover:text-neutral-900"
                              >
                                snapshot trend
                              </button>
                            </div>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  {/* Snapshot history */}
                  <div className="border border-neutral-200">
                    <div className="px-3 py-2.5 border-b border-neutral-200 flex items-center justify-between">
                      <SectionLabel icon={History}>Snapshot / events</SectionLabel>
                      <Tabs defaultValue="markers">
                        <TabsList className="h-7">
                          <TabsTrigger value="markers" className="text-[11px] px-2">
                            markers
                          </TabsTrigger>
                          <TabsTrigger value="events" className="text-[11px] px-2">
                            events
                          </TabsTrigger>
                        </TabsList>
                        <TabsContent value="markers" />
                        <TabsContent value="events" />
                      </Tabs>
                    </div>
                    <div className="p-3 text-[12px] space-y-1.5">
                      <div className="flex justify-between">
                        <span className="text-neutral-500">last captured</span>
                        <span className="text-neutral-800">{dashboard.snapshot.lastCapturedAt}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-neutral-500">markers (24h)</span>
                        <span className="text-neutral-800">{dashboard.snapshot.markerCount}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-neutral-500">events (24h)</span>
                        <span className="text-neutral-800">{dashboard.snapshot.eventCount}</span>
                      </div>
                    </div>
                    <Separator />
                    <div className="p-2 text-[11px] text-neutral-500">
                      source: dashboard_snapshots · current state not recalculated
                    </div>
                  </div>
                </aside>
              </div>
            ) : (
              <div className="p-10 text-[13px] text-neutral-500">
                application을 선택하세요.
              </div>
            )}
          </main>
        </div>

        <InstancePanels
          view={inst.view}
          onClose={inst.close}
          onOpenTrend={inst.openTrend}
          onOpenEvidence={inst.openEvidence}
        />
      </div>
    </TooltipProvider>
  );
}
