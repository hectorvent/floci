package tests

import (
	"context"
	"testing"
	"time"

	"floci-sdk-test-go/internal/testutil"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/cloudwatch"
	cwtypes "github.com/aws/aws-sdk-go-v2/service/cloudwatch/types"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestCloudWatch(t *testing.T) {
	ctx := context.Background()
	svc := testutil.CloudWatchClient()
	namespace := "GoTest"

	t.Run("PutMetricData", func(t *testing.T) {
		_, err := svc.PutMetricData(ctx, &cloudwatch.PutMetricDataInput{
			Namespace: aws.String(namespace),
			MetricData: []cwtypes.MetricDatum{
				{
					MetricName: aws.String("RequestCount"),
					Value:      aws.Float64(42),
					Unit:       cwtypes.StandardUnitCount,
					Timestamp:  aws.Time(time.Now()),
				},
			},
		})
		require.NoError(t, err)
	})

	t.Run("ListMetrics", func(t *testing.T) {
		r, err := svc.ListMetrics(ctx, &cloudwatch.ListMetricsInput{Namespace: aws.String(namespace)})
		require.NoError(t, err)
		assert.NotEmpty(t, r.Metrics)
	})

	t.Run("GetMetricStatistics", func(t *testing.T) {
		now := time.Now()
		r, err := svc.GetMetricStatistics(ctx, &cloudwatch.GetMetricStatisticsInput{
			Namespace:  aws.String(namespace),
			MetricName: aws.String("RequestCount"),
			StartTime:  aws.Time(now.Add(-5 * time.Minute)),
			EndTime:    aws.Time(now.Add(time.Minute)),
			Period:     aws.Int32(60),
			Statistics: []cwtypes.Statistic{cwtypes.StatisticSum},
		})
		require.NoError(t, err)
		assert.NotEmpty(t, r.Datapoints)
	})
}
