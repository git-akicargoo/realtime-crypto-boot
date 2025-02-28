import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import Navigation from './components/Navigation'
import SystemStatus from './components/SystemStatus'

export default function App() {
  return (
    <Router>
      <Navigation />
      <SystemStatus />
      <Routes>
        <Route path="/monitor" element={
          <iframe 
            src="/exchange-monitor.html" 
            style={{
              width: '100%',
              height: 'calc(100vh - 84px)',
              border: 'none',
              marginTop: '84px'
            }}
          />
        } />
        <Route path="/dashboard" element={
          <iframe 
            src="/crypto-dashboard.html" 
            style={{
              width: '100%',
              height: 'calc(100vh - 84px)',
              border: 'none',
              marginTop: '84px'
            }}
          />
        } />
        <Route path="/analysis" element={
          <iframe 
            src="/analysis.html" 
            style={{
              width: '100%',
              height: 'calc(100vh - 84px)',
              border: 'none',
              marginTop: '84px'
            }}
          />
        } />
        <Route path="/" element={
          <div style={{ 
            marginTop: '60px', 
            padding: '20px',
            color: 'var(--text-primary)'
          }}>
            Welcome to Exchange Service
          </div>
        } />
      </Routes>
    </Router>
  )
} 