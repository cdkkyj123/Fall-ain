import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { EntryPage } from "./pages/EntryPage";
import { ChatPage } from "./pages/ChatPage";
import { EndingPage } from "./pages/EndingPage";

export function App() {
  return (
    <BrowserRouter>
      <div className="h-screen w-screen">
        <Routes>
          <Route path="/" element={<EntryPage />} />
          <Route path="/chat/:ucId" element={<ChatPage />} />
          <Route path="/ending/:ucId" element={<EndingPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
