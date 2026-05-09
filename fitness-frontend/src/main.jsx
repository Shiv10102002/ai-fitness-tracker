import React from 'react'
import ReactDOM from 'react-dom/client'

// import './index.css'
import App from './App.jsx'
import { Provider } from 'react-redux'
import { store } from './store/store.js'
import { AuthProvider } from 'react-oauth2-code-pkce'
import { authConfig } from './config/authConfig.js'

const root = ReactDOM.createRoot(document.getElementById('root'))
root.render(
  <AuthProvider authConfig={authConfig}>
    <Provider store={store}>
      <App />
    </Provider>
  </AuthProvider>,
)
