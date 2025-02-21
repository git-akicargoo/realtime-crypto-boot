import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import Navigation from './components/Navigation'

export default function App() {
  return (
    <Router>
      <Navigation />
      <Routes>
        <Route path="/monitor" element={
          <iframe 
            src="/exchange-monitor.html" 
            style={{
              width: '100%',
              height: 'calc(100vh - 60px)',
              border: 'none',
              marginTop: '60px'
            }}
          />
        } />
        <Route path="/dashboard" element={
          <iframe 
            src="/crypto-dashboard.html" 
            style={{
              width: '100%',
              height: 'calc(100vh - 60px)',
              border: 'none',
              marginTop: '60px'
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