import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router';
import { Card, CardContent, Grid, Typography } from '@mui/material';
import { getActivities } from '../services/api';

const ActivityList = () => {
  const [activities, setActivities] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;
    getActivities()
      .then((response) => {
        if (!cancelled) setActivities(response.data);
      })
      .catch((error) => console.error(error));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Grid container spacing={2}>
      {activities.map((activity) => (
        <Grid key={activity.id} size={{ xs: 12, sm: 6, md: 4 }}>
          <Card sx={{ cursor: 'pointer' }}
            onClick={() => navigate(`/activities/${activity.id}`)}>
            <CardContent>
              <Typography variant='h6'>{activity.type}</Typography>
              <Typography>Duration: {activity.duration} min</Typography>
              <Typography>Calories: {activity.caloriesBurned}</Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  )
}

export default ActivityList
