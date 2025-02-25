import { Link, useLocation } from 'react-router-dom'
import '../styles/Navigation.css'

export default function Navigation() {
  const location = useLocation()
  
  return (
    <header className="navigation">
      <div className="nav-content">
        <Link to="/" className="nav-logo">
          Exchange Service
        </Link>
        <div className="nav-links">
          <Link
            to="/monitor"
            className={location.pathname === '/monitor' ? 'active' : ''}
          >
            Monitor
          </Link>
          <Link
            to="/dashboard"
            className={location.pathname === '/dashboard' ? 'active' : ''}
          >
            Dashboard
          </Link>
          <Link
            to="/analysis"
            className={location.pathname === '/analysis' ? 'active' : ''}
          >
            Analysis
          </Link>
        </div>
      </div>
    </header>
  )
} 