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
import MyTokens from './pages/Currency/MyTokens';
import AdminDashboard from './pages/Admin/AdminDashboard';
import Landing from './pages/Landing';
import Layout from './layout/Layout';
import GitCredentials from './pages/Profile/GitCredentials';
import AllCompanies from './components/AllCompanies';
import MergeConflictResolver from '../src/Merge/MergeConflictResolver'; // adjust path if different
import ConfirmPurchase from './pages/Currency/ConfirmPurchase';
import BuyCoins from './pages/Currency/BuyCoins';
import PayPalSuccess from './pages/paypal/PayPalSuccess';
import PayPalCancel from './pages/paypal/PayPalCancel'
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

        {/* Protected + Layout-wrapped routes */}
		<Route path="/companies/all" element={<AllCompanies />} />
		<Route path="/coin-market/success" element={<ConfirmPurchase />} />
		
		<Route
		  path="/profile/git"
		  element={
			<PrivateRoute>
				<GitCredentials />
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

<Route path="/buy-coins" element={<BuyCoins />} />
<Route path="/paypal/success" element={<PayPalSuccess />} />
<Route path="/paypal/cancel" element={<PayPalCancel />} />
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
          path="/tokens"
          element={
            <PrivateRoute>
                <MyTokens />
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
