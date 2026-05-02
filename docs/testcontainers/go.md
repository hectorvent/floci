# Testcontainers — Go

!!! warning "In progress"
    Go support is under active development. Track the work at
    [github.com/floci-io/testcontainers-floci-go](https://github.com/floci-io/testcontainers-floci-go).

    The page below shows the planned API. Details may change before the first release.

## Planned usage

The module will follow the standard [Testcontainers for Go](https://golang.testcontainers.org/) patterns.

```go
package myservice_test

import (
    "context"
    "testing"

    "github.com/aws/aws-sdk-go-v2/aws"
    "github.com/aws/aws-sdk-go-v2/config"
    "github.com/aws/aws-sdk-go-v2/credentials"
    "github.com/aws/aws-sdk-go-v2/service/s3"
    floci "github.com/floci-io/testcontainers-floci-go"
)

func TestS3CreateBucket(t *testing.T) {
    ctx := context.Background()

    container, err := floci.RunContainer(ctx)
    if err != nil {
        t.Fatal(err)
    }
    defer container.Terminate(ctx)

    cfg, err := config.LoadDefaultConfig(ctx,
        config.WithRegion(container.Region()),
        config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(
            container.AccessKey(), container.SecretKey(), "",
        )),
        config.WithBaseEndpoint(container.Endpoint()),
    )
    if err != nil {
        t.Fatal(err)
    }

    client := s3.NewFromConfig(cfg, func(o *s3.Options) {
        o.UsePathStyle = true
    })

    _, err = client.CreateBucket(ctx, &s3.CreateBucketInput{
        Bucket: aws.String("my-bucket"),
    })
    if err != nil {
        t.Fatal(err)
    }

    out, err := client.ListBuckets(ctx, &s3.ListBucketsInput{})
    if err != nil {
        t.Fatal(err)
    }

    found := false
    for _, b := range out.Buckets {
        if aws.ToString(b.Name) == "my-bucket" {
            found = true
            break
        }
    }
    if !found {
        t.Error("bucket not found after create")
    }
}
```

## Contribute

If you would like to help build the Go module, open an issue or pull request at
[github.com/floci-io/testcontainers-floci-go](https://github.com/floci-io/testcontainers-floci-go).
