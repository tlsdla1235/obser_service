import { useState } from "react";
import { BookOpen } from "lucide-react";

const sections = [
  { id: "getting-started", title: "1. 시작하기" },
  { id: "portal-setup", title: "2. Portal 설정" },
  { id: "starter", title: "3. Starter 연결" },
  { id: "allowlist", title: "4. Route allow list" },
  { id: "dashboard", title: "5. Dashboard 읽는 법" },
  { id: "api", title: "6. API Reference" },
  { id: "troubleshooting", title: "7. Troubleshooting" },
];

function Code({ children }: { children: React.ReactNode }) {
  return (
    <pre className="bg-neutral-50 border border-neutral-200 p-3 text-[12px] text-neutral-800 overflow-x-auto whitespace-pre">
      {children}
    </pre>
  );
}

export function Docs() {
  const [active, setActive] = useState(sections[0].id);

  return (
    <div className="bg-white">
      <div className="mx-auto max-w-7xl px-6 grid grid-cols-12 gap-8 py-10">
        {/* Side TOC */}
        <aside className="col-span-12 lg:col-span-3">
          <div className="sticky top-4">
            <div className="flex items-center gap-2 text-neutral-900">
              <BookOpen className="h-4 w-4" strokeWidth={1.5} />
              <span>Docs</span>
            </div>
            <ul className="mt-4 space-y-1 border-l border-neutral-200">
              {sections.map((s) => (
                <li key={s.id}>
                  <a
                    href={`#${s.id}`}
                    onClick={() => setActive(s.id)}
                    className={`block px-3 py-1.5 -ml-px border-l-2 text-[13px] ${
                      active === s.id
                        ? "border-neutral-900 text-neutral-900"
                        : "border-transparent text-neutral-600 hover:text-neutral-900"
                    }`}
                  >
                    {s.title}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </aside>

        {/* Content */}
        <main className="col-span-12 lg:col-span-9 space-y-14 text-neutral-800 text-[14px] leading-relaxed">
          <section id="getting-started">
            <h2 className="text-neutral-900">1. 시작하기</h2>
            <p className="mt-3">
              Observation Portal은 Spring Boot 애플리케이션에 starter를 붙이면 30초 단위 metric
              bucket과 heartbeat를 portal로 보내고, project / application / instance 단위의
              운영 첫 화면을 만들어 주는 starter-first observability dashboard입니다.
            </p>
            <ol className="mt-4 list-decimal pl-5 space-y-1">
              <li>Portal에 GitHub OAuth로 로그인합니다.</li>
              <li>Project를 만들고 starter credential을 1회 받아 보관합니다.</li>
              <li>Spring Boot 앱에 starter dependency를 추가하고 yaml을 설정합니다.</li>
              <li>30초 안에 첫 bucket이 portal로 들어오는지 Dashboard에서 확인합니다.</li>
            </ol>
          </section>

          <section id="portal-setup">
            <h2 className="text-neutral-900">2. Portal 설정</h2>
            <ul className="mt-3 list-disc pl-5 space-y-1">
              <li>PostgreSQL 인스턴스를 준비하고 portal의 datasource로 연결합니다.</li>
              <li>GitHub OAuth client id / secret을 환경 변수로 주입합니다.</li>
              <li>service token signing key를 안전한 secret store에 저장합니다.</li>
              <li>dashboard 접속 URL을 사용자에게 안내합니다.</li>
            </ul>
          </section>

          <section id="starter">
            <h2 className="text-neutral-900">3. Starter 연결</h2>
            <p className="mt-3">
              starter는 metric flush와 heartbeat 두 가지 channel을 별도로 사용합니다. yaml은
              아래와 같이 설정합니다.
            </p>
            <p className="mt-4 text-neutral-700">Metric flush</p>
            <Code>{`observation:
  metric-flush:
    portal-base-url: http://localhost:8080
    project-key: <starter credential>
    project-id: <stable local project identity>
    application-name: orders-api
    environment: prod
    instance: orders-api-local`}</Code>
            <p className="mt-4 text-neutral-700">Heartbeat</p>
            <Code>{`observation:
  heartbeat:
    enabled: true
    portal-base-url: http://localhost:8080
    project-key: <starter credential>
    starter-version: 0.1.0-SNAPSHOT
    interval-seconds: 30`}</Code>
            <ul className="mt-4 list-disc pl-5 space-y-1">
              <li>
                <code>project-key</code>는 <code>X-OBS-Project-Key</code> 인증 header에 사용할
                raw starter credential이며 로그, 화면, 오류 메시지에 노출하면 안 됩니다.
              </li>
              <li>
                <code>project-id</code>는 starter가 bucket ingest <code>Idempotency-Key</code>를
                만들 때 사용하는 stable local project identity입니다.
              </li>
              <li>
                <code>project-id</code>와 <code>project-key</code>는 서로 다른 값입니다.
              </li>
              <li>metric flush와 heartbeat는 서로 별도의 portal connection 설정을 갖습니다.</li>
              <li>
                heartbeat는 accepted bucket / dashboard state / snapshot / operational event를
                만들지 않습니다.
              </li>
            </ul>
          </section>

          <section id="allowlist">
            <h2 className="text-neutral-900">4. Route allow list</h2>
            <p className="mt-3">
              설정 key: <code>observation.route-attribution.allowlist</code>. 이 항목은 raw
              path가 아니라 route template만 허용합니다.
            </p>
            <Code>{`observation:
  route-attribution:
    allowlist:
      - /orders/{orderId}
      - /inventory/{sku}`}</Code>
            <ul className="mt-4 list-disc pl-5 space-y-1">
              <li>framework가 제공하는 <code>http.route</code>가 있으면 그것을 우선 사용합니다.</li>
              <li>
                allowlist는 <code>http.route</code>가 없거나 실패했을 때 raw path candidate를
                안전한 template으로 귀속시키는 fallback입니다.
              </li>
              <li>query string, absolute URL, collapse marker, 실제 ID 처럼 보이는 concrete
                segment는 허용하지 않습니다.</li>
              <li>ambiguous match는 <code>UNKNOWN</code>으로 수렴합니다.</li>
              <li><code>UNKNOWN</code> route는 endpoint priority에 노출하지 않습니다.</li>
            </ul>
          </section>

          <section id="dashboard">
            <h2 className="text-neutral-900">5. Dashboard 읽는 법</h2>
            <ul className="mt-3 list-disc pl-5 space-y-1">
              <li>
                <strong>Metric state strip</strong>과 <strong>Starter connection strip</strong>은
                별도의 band입니다. heartbeat 성공을 application health 성공으로 해석하지 않습니다.
              </li>
              <li>
                <code>triageCards=[]</code>일 때는 <code>zeroInsight</code> 문구를 보여주고 빈
                panel을 노출하지 않습니다.
              </li>
              <li>
                <code>recovery.isRecovering=true</code> 는 "회복 관찰 중"이며 "복구 완료"가
                아닙니다.
              </li>
              <li>Endpoint priority는 "먼저 확인할 endpoint"이며 root cause ranking이 아닙니다.</li>
              <li>p95 / p99는 <code>sourceScopedPercentiles.items[]</code>에서만 보여줍니다.</li>
            </ul>
          </section>

          <section id="api">
            <h2 className="text-neutral-900">6. API Reference</h2>
            <p className="mt-3">
              UI는 기존 Spring portal endpoint를 그대로 재사용합니다. Resource API는{" "}
              <code>Authorization: Bearer &lt;access_token&gt;</code>으로 호출합니다.
            </p>
            <div className="mt-4 border border-neutral-200">
              <table className="w-full text-[12px]">
                <thead className="text-left text-neutral-500">
                  <tr>
                    <th className="px-3 py-2">Method</th>
                    <th className="px-3 py-2">Endpoint</th>
                    <th className="px-3 py-2">Usage</th>
                  </tr>
                </thead>
                <tbody>
                  {[
                    ["GET", "/api/auth/github/authorize", "OAuth 시작 URL"],
                    ["POST", "/api/auth/github/callback/tokens", "access token 1회 회수"],
                    ["GET", "/api/projects", "Project Entry list"],
                    ["POST", "/api/projects", "Project registration"],
                    ["GET", "/api/projects/{projectId}/applications", "Application list"],
                    [
                      "GET",
                      "/api/projects/{projectId}/applications/{applicationId}/dashboard",
                      "Application Dashboard read model",
                    ],
                    [
                      "GET",
                      "/api/projects/{pid}/applications/{aid}/instances/{iid}/evidence",
                      "Instance evidence",
                    ],
                    [
                      "GET",
                      "/api/projects/{pid}/applications/{aid}/instances/{iid}/snapshot-trend",
                      "Snapshot trend (7d/14d, max limit 336)",
                    ],
                    [
                      "GET",
                      "/api/projects/{pid}/applications/{aid}/dashboard/snapshot-markers",
                      "Snapshot markers",
                    ],
                    [
                      "GET",
                      "/api/projects/{pid}/applications/{aid}/operational-events",
                      "Operational events",
                    ],
                    [
                      "GET",
                      "/api/projects/{projectId}/starter-credential",
                      "Credential metadata",
                    ],
                  ].map((r, i) => (
                    <tr key={i} className="border-t border-neutral-100">
                      <td className="px-3 py-2 text-neutral-700">{r[0]}</td>
                      <td className="px-3 py-2 text-neutral-900">
                        <code>{r[1]}</code>
                      </td>
                      <td className="px-3 py-2 text-neutral-700">{r[2]}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section id="troubleshooting">
            <h2 className="text-neutral-900">7. Troubleshooting</h2>
            <ul className="mt-3 list-disc pl-5 space-y-1">
              <li>
                <strong>401</strong>: GitHub 로그인이 만료됐거나 token이 없습니다. 다시
                로그인하세요. project 부재로 해석하지 않습니다.
              </li>
              <li>
                <strong>404</strong>: project / application / instance 범위가 맞지 않거나 멤버십이
                없습니다. application health로 해석하지 않습니다.
              </li>
              <li>
                <strong>insufficient sample</strong>: 현재 윈도우에 accepted bucket이 부족합니다.
                starter metric flush 설정을 점검하세요.
              </li>
              <li>
                <strong>heartbeat missing</strong>: starter heartbeat가 도착하지 않습니다.
                starter process와 네트워크 reachability를 확인하세요. 자동으로 host down이라고
                단정하지 않습니다.
              </li>
              <li>
                <strong>409 duplicate_idempotency_key</strong>: starter가 동일 idempotency key를
                재전송했습니다. starter 로그에서 retry 경로를 확인하세요.
              </li>
            </ul>
          </section>
        </main>
      </div>
    </div>
  );
}
