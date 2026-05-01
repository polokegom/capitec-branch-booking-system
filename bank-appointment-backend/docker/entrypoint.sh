#!/bin/sh
set -eu

certificate_path="${DB_TRUST_CERTIFICATE_PATH:-/tmp/certificates/aws-rds-ca-bundle.pem}"

if [ -n "${DB_TRUST_CERTIFICATE_PEM:-}" ]; then
  mkdir -p "$(dirname "$certificate_path")"
  printf '%s\n' "$DB_TRUST_CERTIFICATE_PEM" > "$certificate_path"
  chmod 600 "$certificate_path"
  unset DB_TRUST_CERTIFICATE_PEM
fi

exec java $JAVA_OPTS -jar /app/quarkus-run.jar
