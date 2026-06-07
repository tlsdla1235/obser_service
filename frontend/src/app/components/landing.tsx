import { Link } from "react-router";
import {
  Activity,
  ArrowRight,
  BookOpen,
  Gauge,
  KeyRound,
  ListChecks,
  Radio,
  Server,
} from "lucide-react";
import { Button } from "./ui/button";
import { Separator } from "./ui/separator";

const steps = [
  {
    n: "01",
    title: "Project 등록",
    body:
      "GitHub OAuth로 로그인하고 project를 만든다. starter credential은 생성/회전 성공 직후 1회만 표시된다.",
  },
  {
    n: "02",
    title: "Starter 설정",
    body:
      "Spring Boot 앱에 starter dependency를 추가하고 portal base URL, project key, environment를 설정한다.",
  },
  {
    n: "03",
    title: "Dashboard 확인",
    body:
      "Project → Application → Dashboard로 들어가 수집 데이터 최신성과 앱 연결 신호를 분리해서 확인한다.",
  },
];

const values = [
  {
    icon: Gauge,
    title: "설치는 starter 중심으로 간단하다",
    body: "dependency 한 줄과 yaml 설정으로 30초마다 수집 데이터와 연결 신호를 portal로 보낸다.",
  },
  {
    icon: Radio,
    title: "수집 데이터와 앱 연결을 분리한다",
    body: "최근 수집 데이터와 앱 연결 신호를 하나의 상태로 뭉개지 않고 따로 보여준다.",
  },
  {
    icon: ListChecks,
    title: "서버가 계산한 상태 요약을 그대로 보여준다",
    body: "앱 상태, 응답시간 지표, 먼저 볼 엔드포인트를 UI가 임의로 다시 계산하지 않는다.",
  },
  {
    icon: Server,
    title: "작은 팀의 첫 운영 판단을 빠르게",
    body: "지금 데이터가 들어오는지, 어디부터 확인해야 하는지 한 화면에서 결정한다.",
  },
];

const questions = [
  "지금 데이터가 들어오고 있는가?",
  "앱 연결은 살아 있는가?",
  "현재 앱 상태는 무엇인가?",
  "느려졌나, 에러가 늘었나?",
  "어디부터 확인하면 되는가?",
];

export function Landing() {
  return (
    <main className="bg-white text-neutral-900">
      {/* Hero */}
      <section className="border-b border-neutral-200">
        <div className="mx-auto max-w-7xl px-6 py-16 grid lg:grid-cols-2 gap-12 items-center">
          <div>
            <div className="inline-flex items-center gap-2 border border-neutral-300 px-2.5 py-1 text-neutral-700">
              <Activity className="h-3.5 w-3.5" strokeWidth={1.5} />
              <span className="text-[12px] tracking-wide uppercase">
                starter-first observability
              </span>
            </div>
            <h1 className="mt-6 text-neutral-900" style={{ fontSize: "3rem", lineHeight: 1.1 }}>
              Observation Portal
            </h1>
            <p className="mt-5 text-neutral-700 max-w-xl">
              Spring Boot 앱에 starter를 붙이면 30초마다 수집 데이터와 연결 신호를 모아,
              지금 데이터가 들어오는지와 어디부터 확인할지 한 화면에서 보여줍니다.
            </p>
            <div className="mt-8 flex items-center gap-3">
              <Button asChild className="bg-neutral-900 hover:bg-neutral-800 text-white gap-2">
                <Link to="/dashboard">
                  Dashboard 열기 <ArrowRight className="h-4 w-4" strokeWidth={1.5} />
                </Link>
              </Button>
              <Button asChild variant="outline" className="border-neutral-300 gap-2">
                <Link to="/docs">
                  <BookOpen className="h-4 w-4" strokeWidth={1.5} /> Docs 보기
                </Link>
              </Button>
            </div>
          </div>

          {/* Product preview */}
          <div className="border border-neutral-300 bg-neutral-50 p-4">
            <div className="flex items-center justify-between border-b border-neutral-200 pb-3 mb-3">
              <div className="flex items-center gap-2 text-neutral-700">
                <Server className="h-4 w-4" strokeWidth={1.5} />
                <span className="text-[13px]">orders-api · prod</span>
              </div>
              <span className="text-[11px] uppercase tracking-wider text-neutral-500 border border-neutral-300 px-2 py-0.5">
                상태 요약
              </span>
            </div>

            <div className="grid grid-cols-2 gap-3 text-[13px]">
              <div className="border border-neutral-200 bg-white p-3">
                <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase tracking-wider">
                  <Activity className="h-3 w-3" strokeWidth={1.5} /> 수집 데이터
                </div>
                <div className="mt-1.5 text-neutral-900">최근 수집 데이터</div>
                <div className="text-neutral-500 text-[12px]">최신 · 12초 전</div>
              </div>
              <div className="border border-neutral-200 bg-white p-3">
                <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase tracking-wider">
                  <Radio className="h-3 w-3" strokeWidth={1.5} /> 앱 연결
                </div>
                <div className="mt-1.5 text-neutral-900">연결 신호 정상</div>
                <div className="text-neutral-500 text-[12px]">마지막 확인 22초 전</div>
              </div>
              <div className="border border-neutral-200 bg-white p-3 col-span-2">
                <div className="flex items-center justify-between">
                  <div className="text-neutral-500 text-[11px] uppercase tracking-wider">
                    상태
                  </div>
                  <span className="text-[11px] border border-neutral-400 px-1.5 py-0.5">
                    주의
                  </span>
                </div>
                <div className="mt-1.5 text-neutral-900">
                  /orders/{`{orderId}`} 요청에서 오류가 늘었어요
                </div>
                <div className="text-neutral-500 text-[12px]">
                  먼저 확인할 엔드포인트 · 3건
                </div>
              </div>
              <div className="border border-neutral-200 bg-white p-3">
                <div className="text-neutral-500 text-[11px] uppercase tracking-wider">근거</div>
                <div className="mt-1.5 text-neutral-900">앱이 보낸 데이터</div>
              </div>
              <div className="border border-neutral-200 bg-white p-3">
                <div className="text-neutral-500 text-[11px] uppercase tracking-wider">범위</div>
                <div className="mt-1.5 text-neutral-900">인스턴스별 최근 구간</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Questions */}
      <section className="border-b border-neutral-200">
        <div className="mx-auto max-w-7xl px-6 py-12">
          <div className="text-[12px] uppercase tracking-wider text-neutral-500">
            첫 화면에서 답을 얻는 질문
          </div>
          <ul className="mt-4 grid md:grid-cols-2 lg:grid-cols-5 gap-3">
            {questions.map((q) => (
              <li
                key={q}
                className="border border-neutral-200 px-4 py-3 text-neutral-800 text-[14px]"
              >
                {q}
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Steps */}
      <section className="border-b border-neutral-200">
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="flex items-end justify-between">
            <div>
              <div className="text-[12px] uppercase tracking-wider text-neutral-500">
                간편한 사용법
              </div>
              <h2 className="mt-2 text-neutral-900">3단계로 시작합니다</h2>
            </div>
            <Link
              to="/docs"
              className="text-[13px] text-neutral-700 underline underline-offset-4 hover:text-neutral-900"
            >
              자세한 설정은 Docs에서
            </Link>
          </div>

          <div className="mt-8 grid md:grid-cols-3 gap-0 border border-neutral-200">
            {steps.map((s, i) => (
              <div
                key={s.n}
                className={`p-6 ${
                  i < steps.length - 1 ? "md:border-r border-neutral-200" : ""
                }`}
              >
                <div className="text-neutral-400 text-[12px] tracking-widest">{s.n}</div>
                <div className="mt-2 text-neutral-900">{s.title}</div>
                <p className="mt-3 text-[13px] text-neutral-600 leading-relaxed">{s.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Values */}
      <section className="border-b border-neutral-200">
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="text-[12px] uppercase tracking-wider text-neutral-500">제품 가치</div>
          <h2 className="mt-2 text-neutral-900">운영 첫 화면을 위한 도구</h2>
          <div className="mt-8 grid md:grid-cols-2 gap-px bg-neutral-200 border border-neutral-200">
            {values.map((v) => {
              const Icon = v.icon;
              return (
                <div key={v.title} className="bg-white p-6">
                  <Icon className="h-5 w-5 text-neutral-800" strokeWidth={1.5} />
                  <div className="mt-3 text-neutral-900">{v.title}</div>
                  <p className="mt-2 text-[13px] text-neutral-600 leading-relaxed">{v.body}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section>
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="border border-neutral-900 p-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
            <div>
              <h2 className="text-neutral-900">지금 starter를 연결해 보세요</h2>
              <p className="mt-2 text-neutral-700 max-w-xl text-[14px]">
                Project를 만들면 starter credential을 1회 발급합니다. yaml 두 블록을 설정하면
                30초 안에 첫 수집 데이터가 portal로 들어옵니다.
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Button asChild className="bg-neutral-900 hover:bg-neutral-800 text-white">
                <Link to="/dashboard">Dashboard 열기</Link>
              </Button>
              <Button asChild variant="outline" className="border-neutral-400">
                <Link to="/docs">Docs</Link>
              </Button>
            </div>
          </div>
        </div>
      </section>

      <Separator className="bg-neutral-200" />
      <footer className="mx-auto max-w-7xl px-6 py-6 text-[12px] text-neutral-500">
        © Observation Portal — starter-first operational dashboard
      </footer>
    </main>
  );
}
