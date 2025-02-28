import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import '../styles/SystemStatus.css'

interface SystemState {
  redis: boolean
  kafka: boolean
  nodeRole: string
  analysisAvailable: boolean
  totalNodes?: number
}

export default function SystemStatus() {
  const [status, setStatus] = useState<SystemState>({
    redis: false,
    kafka: false,
    nodeRole: 'unknown',
    analysisAvailable: false,
    totalNodes: undefined
  })

  const location = useLocation()

  // 현재 페이지에 따라 필요한 서비스 상태만 표시
  const shouldShowService = {
    analysis: location.pathname === '/analysis',
    monitor: location.pathname === '/monitor',
    dashboard: location.pathname === '/dashboard'
  }

  useEffect(() => {
    const checkStatus = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/v1/trading/mode/status')
        const data = await response.json()
        setStatus({
          redis: data.redisOk,
          kafka: data.kafkaOk,
          nodeRole: data.leaderOk ? 'LEADER' : 'FOLLOWER',
          analysisAvailable: data.valid,
          totalNodes: data.totalNodes
        })
      } catch (error) {
        console.error('Failed to fetch system status:', error)
      }
    }

    checkStatus()
    const interval = setInterval(checkStatus, 10000)
    return () => clearInterval(interval)
  }, [])

  // 페이지별 상태 메시지 결정
  const getStatusMessage = () => {
    if (shouldShowService.analysis) {
      const issues = []
      if (!status.redis) issues.push("Redis required")
      if (!status.kafka) issues.push("Kafka required")
      return issues.length > 0 ? `Services Unavailable (${issues.join(", ")})` : "Services Available"
    }
    
    if (shouldShowService.monitor || shouldShowService.dashboard) {
      return status.nodeRole === 'LEADER' ? "Exchange Feed Available" : "Exchange Feed Connecting..."
    }

    return ""
  }

  return (
    <div className="system-status">
      <div className={`status-item ${status.redis ? 'up' : 'down'}`}>
        Redis: {status.redis ? 'UP' : 'DOWN'}
      </div>
      <div className={`status-item ${status.kafka ? 'up' : 'down'}`}>
        Kafka: {status.kafka ? 'UP' : 'DOWN'}
      </div>
      <div className={`status-item ${status.nodeRole === 'LEADER' ? 'up' : 'warning'}`}>
        Node: {status.nodeRole} {status.totalNodes && status.totalNodes > 1 ? `(${status.totalNodes})` : ''}
      </div>
      <div className={`status-item ${
        shouldShowService.analysis ? 
          (status.redis && status.kafka ? 'up' : 'down') : 
          (status.nodeRole === 'LEADER' ? 'up' : 'warning')
      }`}>
        {getStatusMessage()}
      </div>
    </div>
  )
} 