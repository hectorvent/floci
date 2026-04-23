FROM rust:1-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config libssl-dev ca-certificates clang lld \
    && rm -rf /var/lib/apt/lists/*

# Use lld: far more memory-efficient than GNU ld when linking large Rust binaries on aarch64
ENV RUSTFLAGS="-C link-arg=-fuse-ld=lld"

WORKDIR /app

# Install cargo-nextest
RUN cargo install cargo-nextest --locked

COPY Cargo.toml Cargo.lock ./
# pre-fetch dependencies
RUN mkdir src tests && echo "" > src/lib.rs && cargo fetch && rm -rf src tests

COPY . .

ENV FLOCI_ENDPOINT=http://floci:4566
ENV AWS_ACCESS_KEY_ID=test
ENV AWS_SECRET_ACCESS_KEY=test
ENV AWS_DEFAULT_REGION=us-east-1

RUN mkdir -p /results
ENTRYPOINT ["cargo", "nextest", "run", "--profile", "ci"]