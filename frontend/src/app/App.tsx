import { BrowserRouter, Route, Routes, useLocation } from "react-router";
import { Nav } from "./components/nav";
import { Landing } from "./components/landing";
import { Dashboard } from "./components/dashboard";
import { Docs } from "./components/docs";
import { AuthProvider, useAuth } from "./lib/auth";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </BrowserRouter>
  );
}

/**
 * Router 안에서 auth context를 읽어 navigation action과 화면 route를 연결한다.
 */
function AppShell() {
  const { authenticated, githubLoginLabel, loginInProgress, startGithubLogin } = useAuth();
  const { pathname } = useLocation();
  const showGlobalNav = !pathname.startsWith("/dashboard") || !authenticated;

  return (
    <>
      <div className="min-h-screen bg-white text-neutral-900">
        {showGlobalNav && (
          <Nav
            githubLoginDisabled={loginInProgress}
            githubLoginLabel={githubLoginLabel}
            onGithubLogin={startGithubLogin}
          />
        )}
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/docs" element={<Docs />} />
        </Routes>
      </div>
    </>
  );
}
