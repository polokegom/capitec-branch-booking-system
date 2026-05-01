#!/usr/bin/env bash
# Build and push the application images to ECR.
# Usage: ./push-images.sh <env> [<git-tag>]
set -euo pipefail

ENV="${1:-dev}"
TAG="${2:-latest}"

cd "$(dirname "$0")/../terraform"

REGION="$(grep -E '^aws_region' "envs/${ENV}.tfvars" | awk -F'"' '{print $2}')"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo ">> Logging into ECR ${REGISTRY}"
aws ecr get-login-password --region "${REGION}" | \
  docker login --username AWS --password-stdin "${REGISTRY}"

FRONTEND_REPO="$(terraform output -raw ecr_frontend_repository_url)"
BACKEND_REPO="$(terraform output -raw ecr_backend_repository_url)"
FUSIONAUTH_REPO="$(terraform output -raw ecr_fusionauth_repository_url)"

echo ">> Building frontend"
docker build -t "${FRONTEND_REPO}:${TAG}" \
  ../../bank-appointment

echo ">> Building backend"
docker build -t "${BACKEND_REPO}:${TAG}" \
  ../../bank-appointment-backend

echo ">> Building fusionauth"
docker build -f ../../bank-appointment-backend/fusionauth/Dockerfile \
  -t "${FUSIONAUTH_REPO}:${TAG}" \
  ../../bank-appointment-backend

echo ">> Pushing"
docker push "${FRONTEND_REPO}:${TAG}"
docker push "${BACKEND_REPO}:${TAG}"
docker push "${FUSIONAUTH_REPO}:${TAG}"

echo "Done. Frontend: ${FRONTEND_REPO}:${TAG}"
echo "Backend: ${BACKEND_REPO}:${TAG}"
echo "FusionAuth: ${FUSIONAUTH_REPO}:${TAG}"
