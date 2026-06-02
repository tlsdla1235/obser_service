import { useState, type ReactNode } from "react";
import { BookOpen } from "lucide-react";

const sections = [
  { id: "overview", title: "1. 무엇을 볼 수 있나요" },
  { id: "first-steps", title: "2. 처음 시작 순서" },
  { id: "credential", title: "3. 연결 키 보관" },
  { id: "starter", title: "4. Spring Boot 연결" },
  { id: "dashboard", title: "5. 첫 데이터 확인" },
  { id: "signals", title: "6. 화면 해석하기" },
  { id: "key-lifecycle", title: "7. 키를 잃었거나 노출됐을 때" },
];

function Code({ children }: { children: ReactNode }) {
  return (
    <pre className="bg-neutral-50 border border-neutral-200 p-3 text-[12px] text-neutral-800 overflow-x-auto whitespace-pre">
      {children}
    </pre>
  );
}

/**
 * 처음 사용하는 사용자가 로그인부터 starter 연결, 대시보드 해석까지 순서대로 따라갈 수 있게 안내하는 문서 화면이다.
 */
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
          <section id="overview">
            <h2 className="text-neutral-900">1. 무엇을 볼 수 있나요</h2>
            <p className="mt-3">
              Observation Portal은 Spring Boot 앱에서 보내는 실행 신호를 모아, 지금 데이터가
              들어오는지와 어디부터 확인하면 좋을지를 한 화면에서 보여줍니다. 처음에는 어려운
              용어를 외우기보다 아래처럼 이해하면 됩니다.
            </p>
            <div className="mt-5 border border-neutral-200">
              {[
                ["Project", "여러 앱을 함께 보는 작업 공간입니다. 팀, 서비스 묶음, 제품 단위로 만들면 됩니다."],
                ["Application", "연결한 Spring Boot 앱 하나입니다. 예를 들면 orders-api 같은 이름입니다."],
                ["Dashboard", "선택한 앱의 현재 상태, 느림, 오류, 먼저 볼 지점을 모아 보여주는 화면입니다."],
                ["Starter credential", "내 앱이 Portal로 데이터를 보낼 때 쓰는 연결 키입니다. 비밀번호처럼 보관합니다."],
                ["Instance", "앱이 실행 중인 서버나 프로세스 하나입니다. 로컬 실행과 운영 서버를 구분할 때 보입니다."],
                ["Snapshot / History", "이전에 저장된 상태 기록입니다. 현재 화면과 비교해 변화가 있었는지 볼 때 씁니다."],
              ].map(([term, body]) => (
                <div key={term} className="grid gap-2 border-b border-neutral-100 p-3 last:border-b-0 md:grid-cols-[180px_1fr]">
                  <div className="text-neutral-900">{term}</div>
                  <div className="text-neutral-600">{body}</div>
                </div>
              ))}
            </div>
          </section>

          <section id="first-steps">
            <h2 className="text-neutral-900">2. 처음 시작 순서</h2>
            <p className="mt-3">
              처음 사용하는 경우에는 아래 순서대로 진행하면 됩니다. 화면에서 보이는 영어 버튼명은
              그대로 함께 적었습니다.
            </p>
            <ol className="mt-4 list-decimal pl-5 space-y-2">
              <li>상단의 GitHub 로그인 버튼으로 로그인합니다.</li>
              <li>Dashboard 화면에서 Project 등록을 열고, 알아보기 쉬운 이름을 입력해 등록합니다.</li>
              <li>등록 직후 한 번만 보이는 Starter credential을 안전한 곳에 복사합니다.</li>
              <li>Spring Boot 앱에 starter dependency와 설정을 추가합니다.</li>
              <li>앱을 실행한 뒤 Dashboard에서 새 앱이 나타나는지 확인합니다.</li>
              <li>상태, 느림, 오류, 기록을 보며 어디부터 확인할지 정합니다.</li>
            </ol>
          </section>

          <section id="credential">
            <h2 className="text-neutral-900">3. 연결 키 보관</h2>
            <p className="mt-3">
              Project를 만들면 앱 연결에 필요한 키가 발급됩니다. 이 키는 생성하거나 회전한 직후
              한 번만 보입니다.
            </p>
            <div className="mt-4 border border-neutral-200 bg-neutral-50 p-4">
              <div className="text-neutral-900">추천 행동</div>
              <ul className="mt-2 list-disc pl-5 space-y-1 text-neutral-700">
                <li>표시된 키를 비밀 관리 도구, 환경 변수, CI secret처럼 안전한 곳에 저장합니다.</li>
                <li>화면의 Copy and clear를 사용했다면 복사 후 화면에서 키가 사라지는지 확인합니다.</li>
                <li>키를 채팅, 이슈, 로그, 스크린샷에 남기지 않습니다.</li>
                <li>키를 잃어버렸다면 찾으려 하지 말고 새 키를 발급합니다.</li>
              </ul>
            </div>
          </section>

          <section id="starter">
            <h2 className="text-neutral-900">4. Spring Boot 연결</h2>
            <p className="mt-3">
              Spring Boot 앱에 starter를 추가한 뒤, Portal 주소와 연결 키를 설정합니다. 아래 값에서
              <code>project-key</code>에는 방금 복사한 Starter credential을 넣습니다.
            </p>
            <p className="mt-4 text-neutral-700">Metric flush</p>
            <Code>{`observation:
  metric-flush:
    portal-base-url: http://localhost:8080
    project-key: <복사한 연결 키>
    project-id: orders-service
    application-name: orders-api
    environment: prod
    instance: orders-api-1`}</Code>
            <p className="mt-4 text-neutral-700">Heartbeat</p>
            <Code>{`observation:
  heartbeat:
    enabled: true
    portal-base-url: http://localhost:8080
    project-key: <복사한 연결 키>
    starter-version: 0.1.0-SNAPSHOT
    interval-seconds: 30`}</Code>
            <ul className="mt-4 list-disc pl-5 space-y-1">
              <li><code>portal-base-url</code>은 Portal이 실행 중인 주소입니다.</li>
              <li><code>application-name</code>은 Dashboard에서 보일 앱 이름입니다.</li>
              <li><code>environment</code>는 local, dev, prod처럼 실행 환경을 구분하는 값입니다.</li>
              <li><code>instance</code>는 실행 중인 서버나 프로세스를 알아보기 위한 이름입니다.</li>
              <li><code>project-id</code>는 키가 아닙니다. 앱 안에서 안정적으로 쓰는 식별 이름으로 둡니다.</li>
            </ul>
            <p className="mt-4 text-neutral-600">
              사용자가 직접 외워야 할 API 목록은 없습니다. 앱은 설정한 Portal 주소로 데이터를 보내고,
              화면은 로그인한 사용자 권한으로 필요한 정보를 불러옵니다.
            </p>
          </section>

          <section id="dashboard">
            <h2 className="text-neutral-900">5. 첫 데이터 확인</h2>
            <p className="mt-3">
              앱을 실행한 뒤 약 30초 정도 기다린 다음 Dashboard를 엽니다. Project를 선택하면 연결된
              앱 목록이 나타나고, 앱을 선택하면 상세 화면을 볼 수 있습니다.
            </p>
            <ol className="mt-4 list-decimal pl-5 space-y-2">
              <li>Project 목록에서 방금 만든 작업 공간을 선택합니다.</li>
              <li>Application 목록에 내 앱 이름이 나타나는지 확인합니다.</li>
              <li>Dashboard에서 마지막 데이터 수신 시각과 starter 연결 시각을 각각 확인합니다.</li>
              <li>둘 중 하나만 정상이어도 전체가 정상이라고 단정하지 말고, 비어 있는 쪽부터 확인합니다.</li>
            </ol>
          </section>

          <section id="signals">
            <h2 className="text-neutral-900">6. 화면 해석하기</h2>
            <p className="mt-3">
              Dashboard는 원인을 확정해 주는 화면이라기보다, 어디부터 확인해야 할지 순서를 좁혀 주는
              화면입니다.
            </p>
            <div className="mt-5 border border-neutral-200">
              {[
                ["데이터가 안 들어옴", "앱이 실행 중인지, Portal 주소가 맞는지, 연결 키가 설정됐는지부터 봅니다."],
                ["Starter 연결이 끊김", "앱 프로세스, 네트워크, heartbeat 설정을 확인합니다. 앱 자체가 장애라고 바로 단정하지 않습니다."],
                ["상태가 나쁨", "최근 들어온 실행 신호가 좋지 않다는 뜻입니다. 표시된 시간대와 함께 봅니다."],
                ["느림 또는 오류", "먼저 확인할 endpoint가 보이면 그 주소의 최근 요청과 오류를 살펴봅니다."],
                ["회복 관찰 중", "좋아지는 흐름을 관찰 중이라는 뜻입니다. 복구가 끝났다는 확정 표시는 아닙니다."],
                ["Snapshot / History", "과거에 저장된 순간 기록입니다. 현재 상태와 다를 수 있으니 기록 시각을 함께 확인합니다."],
              ].map(([title, body]) => (
                <div key={title} className="grid gap-2 border-b border-neutral-100 p-3 last:border-b-0 md:grid-cols-[180px_1fr]">
                  <div className="text-neutral-900">{title}</div>
                  <div className="text-neutral-600">{body}</div>
                </div>
              ))}
            </div>
          </section>

          <section id="key-lifecycle">
            <h2 className="text-neutral-900">7. 키를 잃었거나 노출됐을 때</h2>
            <p className="mt-3">
              연결 키는 다시 조회할 수 없습니다. 잃어버렸거나 노출 가능성이 있으면 Dashboard의
              Starter credential 영역에서 조치합니다.
            </p>
            <ul className="mt-3 list-disc pl-5 space-y-1">
              <li>
                계속 앱을 연결해야 하지만 키를 잃어버렸다면 <strong>Rotate</strong>로 새 키를
                발급받습니다.
              </li>
              <li>
                새 키가 보이면 바로 안전한 곳에 복사하고, Spring Boot 설정의 <code>project-key</code>를
                새 값으로 바꾼 뒤 앱을 다시 실행합니다.
              </li>
              <li>
                키가 외부에 노출됐거나 더 이상 이 Project로 데이터를 받지 않으려면 <strong>Revoke</strong>로
                폐기합니다.
              </li>
              <li>
                폐기한 뒤 다시 연결이 필요해지면 새 키를 발급하고 앱 설정을 다시 배포합니다.
              </li>
              <li>
                키 상태 화면에는 prefix와 상태만 표시됩니다. 실제 키 값은 한 번 사라지면 다시 볼 수 없습니다.
              </li>
            </ul>
          </section>
        </main>
      </div>
    </div>
  );
}
