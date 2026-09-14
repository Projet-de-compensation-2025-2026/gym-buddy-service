FROM postgres:18.6-alpine@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2

# Preserve the official entrypoint's privilege drop without gosu's vulnerable Go runtime.
RUN apk add --no-cache su-exec \
    && rm /usr/local/bin/gosu \
    && grep -q 'exec gosu postgres' /usr/local/bin/docker-entrypoint.sh \
    && sed -i 's/exec gosu postgres /exec su-exec postgres /' /usr/local/bin/docker-entrypoint.sh \
    && ! grep -q 'exec gosu' /usr/local/bin/docker-entrypoint.sh
