#!/bin/sh
# Starts as root, normalizes the bind-mounted Docker socket's group
# ownership so the unprivileged `floci` user can reach it on any host,
# then re-executes this script as `floci` via gosu. The second invocation
# falls through to exec the user's command.
#
# Why: on native Linux Docker, /var/run/docker.sock is owned by
# root:docker with mode 660 and the docker GID varies by distro. On
# Docker Desktop (macOS/Windows) the socket is typically root:root (GID
# 0). Without the group fix-up below, floci (uid 1001, group 0) can open
# the socket on Docker Desktop but not on native Linux — which breaks
# ECR, Lambda, and RDS service emulation there. Discovering the GID at
# runtime handles every host transparently.

set -eu

if [ "$(id -u)" = '0' ]; then
    if [ -S /var/run/docker.sock ]; then
        sock_gid="$(stat -c '%g' /var/run/docker.sock)"
        if [ "$sock_gid" != '0' ]; then
            group_name="$(getent group "$sock_gid" | cut -d: -f1)" || group_name=''
            if [ -z "$group_name" ]; then
                groupadd -g "$sock_gid" docker-host
                group_name='docker-host'
            fi
            usermod -aG "$group_name" floci
        fi
    fi

    # Re-own state dir for the case where a host bind-mount arrives with
    # ownership the floci user cannot write to. Ignore errors (read-only
    # mounts, unusual filesystems) so the container still starts.
    if [ -d /app/data ]; then
        chown -R floci:root /app/data 2>/dev/null || true
    fi

    exec gosu floci "$0" "$@"
fi

exec "$@"
