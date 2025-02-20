import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Navigation from './components/Navigation'

function App() {
  return (
    <BrowserRouter>
      <Navigation />
      <main style={{ marginTop: '60px' }}>
        <Routes>
          <Route 
            path="/monitor" 
            element={
              <iframe 
                src="/exchange-monitor.html" 
                style={{
                  width: '100%',
                  height: 'calc(100vh - 60px)',
                  border: 'none'
                }}
              />
            } 
          />
          <Route path="/" element={<Navigate to="/monitor" replace />} />
          <Route path="/dashboard" element={<div>Dashboard (Coming Soon)</div>} />
        </Routes>
      </main>
    </BrowserRouter>
  )
}

export default App 