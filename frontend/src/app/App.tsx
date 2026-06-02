import { BrowserRouter, Route, Routes } from "react-router";
import { Nav } from "./components/nav";
import { Landing } from "./components/landing";
import { Dashboard } from "./components/dashboard";
import { Docs } from "./components/docs";

export default function App() {
  const handleGithubLogin = () => {
    // Story 10.2에서 auth context의 GitHub 로그인 action으로 연결한다.
  };

  return (
    <BrowserRouter>
      <div className="min-h-screen bg-white text-neutral-900">
        <Nav onGithubLogin={handleGithubLogin} />
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/docs" element={<Docs />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
