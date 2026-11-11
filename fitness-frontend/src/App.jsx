// import './App.css'
import { Button } from "@mui/material"
import { useContext } from "react";
import { AuthContext } from "react-oauth2-code-pkce";
import { BrowserRouter as Router, Navigate, Route, Routes } from "react-router"
import { useDispatch } from "react-redux";
import { setCredentials } from "./store/authSlice";
import { useEffect } from "react";
import { Box } from "@mui/material";
import ActivityForm from "./components/ActivityForm";
import ActivityList from "./components/ActivityList";
import ActivityDetail from "./components/ActivityDetail";


const ActivitiesPage = () => {
  return (
    <Box sx={{ p: 2, border: '1px dashed grey' }}>
      <ActivityForm onActivityAdded = { () => window.location.reload()}/>
      <ActivityList />
    </Box>
  );
}



function App() {

  const { token, tokenData, logIn, logOut } = useContext(AuthContext);
  const dispatch = useDispatch();

  useEffect(() => {
    if (token) {
      dispatch(setCredentials({ token, user: tokenData }));
    }
  }, [token, tokenData, dispatch]);

  return (
    <Router> 
       {!token ? (
        <Button variant="contained"
          onClick={() => {logIn();}}>
          LOGIN
        </Button>
      ) : (
         <div>
         <Box component="section" sx={{ p: 2, border: '1px dashed grey' }}>
          <Button variant="contained" onClick={logOut} >
            LOGOUT  
          </Button>
          <Routes>
            <Route path="/activities" element={<ActivitiesPage />}/>
            <Route path="/activities/:id" element={<ActivityDetail />}/>
            <Route path="/" element={token ? <Navigate to="/activities" replace/> :
                                  <div>Welcome! Please login</div>}/>
          </Routes>
        </Box>
        </div>
      )}
       
    </Router>
  )
}

export default App
