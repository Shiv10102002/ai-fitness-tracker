import { Box, Card, CardContent, Divider, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router';
import { getActivityDetail } from '../services/api';

/** Shows the AI recommendation for one activity. The API returns the Recommendation document. */
const ActivityDetail = () => {
  const { id } = useParams();
  const [recommendation, setRecommendation] = useState(null);
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    let cancelled = false;
    getActivityDetail(id)
      .then((response) => {
        if (cancelled) return;
        setRecommendation(response.data);
        setStatus('ready');
      })
      .catch((error) => {
        if (cancelled) return;
        // 404 means the AI service has not processed this activity yet (or it is not yours).
        setStatus(error.response?.status === 404 ? 'pending' : 'error');
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (status === 'loading') return <Typography>Loading...</Typography>;
  if (status === 'pending') return <Typography>Your AI recommendation is still being generated. Check back in a moment.</Typography>;
  if (status === 'error') return <Typography color="error">Could not load this recommendation.</Typography>;

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 2 }}>
      <Card>
        <CardContent>
          <Typography variant="h5" gutterBottom>AI Recommendation — {recommendation.activityType}</Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Generated {new Date(recommendation.createdAt).toLocaleString()}
          </Typography>

          <Typography variant="h6">Analysis</Typography>
          <Typography sx={{ whiteSpace: 'pre-line', mb: 2 }}>{recommendation.recommendation}</Typography>

          <Section title="Improvements" items={recommendation.improvements} />
          <Section title="Suggestions" items={recommendation.suggestions} />
          <Section title="Safety Guidelines" items={recommendation.safety} />
        </CardContent>
      </Card>
    </Box>
  )
}

const Section = ({ title, items }) => (
  <>
    <Divider sx={{ my: 2 }} />
    <Typography variant="h6">{title}</Typography>
    {items?.map((item, index) => (
      <Typography key={index} sx={{ mb: 1 }}>• {item}</Typography>
    ))}
  </>
);

export default ActivityDetail
