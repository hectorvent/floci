FROM amazon/aws-cli:latest

RUN yum install -y git jq bc >/dev/null 2>&1

WORKDIR /app

# Install bats and helpers
RUN git clone --depth 1 https://github.com/bats-core/bats-core.git /opt/bats-core \
    && git clone --depth 1 https://github.com/bats-core/bats-support.git /opt/bats-support \
    && git clone --depth 1 https://github.com/bats-core/bats-assert.git /opt/bats-assert

COPY test/ test/

ENV FLOCI_ENDPOINT=http://floci:4566
ENV BATS_LIB_PATH=/opt

RUN mkdir -p /results
ENTRYPOINT bash -c "/opt/bats-core/bin/bats --formatter junit test/ | tee /results/junit.xml"
