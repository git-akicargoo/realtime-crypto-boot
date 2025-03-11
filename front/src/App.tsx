import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import Navigation from './components/Navigation'
import SystemStatus from './components/SystemStatus'
import SimulTrading from './components/SimulTrading'
import SimulTradingResults from './components/SimulTradingResults'

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
            src="/analysis-src/analysis.html"
            style={{
              width: '100%',
              height: 'calc(100vh - 84px)',
              border: 'none',
              marginTop: '84px',
              backgroundColor: 'var(--bg-primary)'
            }}
          />
        } />
        <Route path="/simul-trading/:cardId" element={
          <div style={{ 
            marginTop: '84px', 
            padding: '20px',
            backgroundColor: 'var(--bg-primary)'
          }}>
            <SimulTrading cardId={window.location.pathname.split('/').pop() || ''} />
          </div>
        } />
        <Route path="/simul-results/:cardId" element={
          <div style={{ 
            marginTop: '84px', 
            padding: '20px',
            backgroundColor: 'var(--bg-primary)'
          }}>
            <SimulTradingResults cardId={window.location.pathname.split('/').pop() || ''} />
          </div>
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