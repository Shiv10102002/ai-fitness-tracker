import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const api = axios.create({
    baseURL: API_BASE_URL,
});

api.interceptors.request.use(
    (config) => {
        // Identity comes from the JWT only; the gateway sets X-User-ID itself.
        const token = localStorage.getItem("token");
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }

        return config;
    }
);

export const getActivities = ()=> api.get("/activities");

export const addActivity = (activity) => api.post("/activities", activity);

export const getActivityDetail = (id) => api.get(`/recommendation/activity/${id}`);
