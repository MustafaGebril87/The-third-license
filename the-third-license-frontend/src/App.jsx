import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import React from 'react';
import Marketplace from './pages/Currency/MarketplaceCoins';
import Login from './pages/Auth/Login';
import Register from './pages/Auth/Register';
import Dashboard from './pages/Dashboard/Dashboard';
import MyCompanies from './pages/Dashboard/MyCompanies';
import PushFiles from './pages/Dashboard/PushFiles';
import MyShares from './pages/Shares/MyShares';
import AdminDashboard from './pages/Admin/AdminDashboard';
import Landing from './pages/Landing';
import Layout from './layout/Layout';
import AllCompanies from './components/AllCompanies';
import MergeConflictResolver from '../src/Merge/MergeConflictResolver';
import StripeSuccess from './pages/stripe/StripeSuccess';
import StripeCancel from './pages/stripe/StripeCancel';

const PrivateRoute = ({ children }) => {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" />;
};

function App() {
  const { user } = useAuth();
  console.log("App rendered");

  return (
    <BrowserRouter>
      <Routes>

        {/* Public routes */}
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={user ? <Navigate to="/dashboard" /> : <Login />} />
        <Route path="/register" element={user ? <Navigate to="/dashboard" /> : <Register />} />

        {/* Public / semi-public */}
        <Route path="/companies/all" element={<AllCompanies />} />

        <Route
          path="/marketplace"
          element={
            <PrivateRoute>
              <Marketplace />
            </PrivateRoute>
          }
        />

        <Route
          path="/pages/Currency/MarketplaceCoins"
          element={
            <PrivateRoute>
              <Marketplace />
            </PrivateRoute>
          }
        />

        <Route
          path="/merge/files"
          element={
            <PrivateRoute>
              <MergeConflictResolver />
            </PrivateRoute>
          }
        />

        <Route
          path="/merge-conflict"
          element={
            <PrivateRoute>
              <MergeConflictResolver />
            </PrivateRoute>
          }
        />

        <Route path="/stripe/success" element={<StripeSuccess />} />
        <Route path="/stripe/cancel" element={<StripeCancel />} />

        <Route path="/dashboard" element={
          <PrivateRoute>
            <Dashboard />
          </PrivateRoute>
        } />

        <Route
          path="/companies"
          element={
            <PrivateRoute>
              <MyCompanies />
            </PrivateRoute>
          }
        />

        <Route
          path="/push"
          element={
            <PrivateRoute>
              <PushFiles />
            </PrivateRoute>
          }
        />

        <Route
          path="/shares"
          element={
            <PrivateRoute>
              <MyShares />
            </PrivateRoute>
          }
        />

        <Route
          path="/admin"
          element={
            <PrivateRoute>
              <AdminDashboard />
            </PrivateRoute>
          }
        />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
