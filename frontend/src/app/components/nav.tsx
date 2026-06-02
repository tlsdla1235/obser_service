import { Link, useLocation } from "react-router";
import { Activity, BookOpen, Github, LayoutDashboard } from "lucide-react";
import { Button } from "./ui/button";

/**
 * 앱 상단 내비게이션이다.
 * GitHub action prop은 Story 10.2의 auth context 연결 지점으로만 사용한다.
 */
export interface NavProps {
  onGithubLogin?: () => void;
  githubLoginLabel?: string;
}

export function Nav({ onGithubLogin, githubLoginLabel = "GitHub 로그인" }: NavProps) {
  const { pathname } = useLocation();
  const linkCls = (active: boolean) =>
    `inline-flex items-center gap-2 px-3 py-1.5 border ${
      active ? "border-neutral-900 text-neutral-900" : "border-transparent text-neutral-600 hover:text-neutral-900"
    }`;

  return (
    <header className="border-b border-neutral-200 bg-white">
      <div className="mx-auto max-w-7xl flex items-center justify-between px-6 h-14">
        <Link to="/" className="flex items-center gap-2 text-neutral-900">
          <Activity className="h-5 w-5" strokeWidth={1.5} />
          <span className="tracking-tight">Observation Portal</span>
        </Link>
        <nav className="flex items-center gap-1">
          <Link to="/dashboard" className={linkCls(pathname.startsWith("/dashboard"))}>
            <LayoutDashboard className="h-4 w-4" strokeWidth={1.5} />
            <span>Dashboard</span>
          </Link>
          <Link to="/docs" className={linkCls(pathname.startsWith("/docs"))}>
            <BookOpen className="h-4 w-4" strokeWidth={1.5} />
            <span>Docs</span>
          </Link>
          <Button variant="outline" size="sm" className="ml-3 gap-2 border-neutral-300" onClick={onGithubLogin}>
            <Github className="h-4 w-4" strokeWidth={1.5} />
            {githubLoginLabel}
          </Button>
        </nav>
      </div>
    </header>
  );
}
