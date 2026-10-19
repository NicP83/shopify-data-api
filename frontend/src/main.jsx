import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { installFetchAuth } from './services/adminAuth'

// Send the admin API key on every fetch to our own /api/** before anything renders
installFetchAuth()

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
