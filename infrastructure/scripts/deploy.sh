set -euo pipefail

ENV="${1:?usage: $0 <env> [plan|apply|destroy]}"
ACTION="${2:-apply}"

cd "$(dirname "$0")/../terraform"

terraform init -input=false
terraform "${ACTION}" -input=false -var-file="envs/${ENV}.tfvars"
