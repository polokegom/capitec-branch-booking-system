data "aws_caller_identity" "current" {}

locals {
  name                      = "${var.project}-${var.environment}"
  service_discovery_domain  = "${local.name}.internal"
  public_base_url           = var.domain_name != "" && var.alb_enable_https ? "https://${var.domain_name}" : "http://${module.alb.alb_dns_name}"
  backend_internal_url      = "http://backend.${local.service_discovery_domain}:8080"
  fusionauth_internal_url   = "http://fusionauth.${local.service_discovery_domain}:9011"
  email_assets_bucket_name  = var.email_assets_bucket_name != "" ? var.email_assets_bucket_name : "${local.name}-${data.aws_caller_identity.current.account_id}-email-assets"
  email_assets_base_url     = module.email_assets.base_url
  backend_task_role_name    = var.environment == "prod" ? "capitec-booking-system" : "${local.name}-system"
  rds_ca_bundle_path        = "/tmp/certificates/aws-rds-ca-bundle.pem"
  fusionauth_application_id = var.fusionauth_application_id
  fusionauth_tenant_id      = "30663132-6464-6665-3032-326466613934"

  backend_mail_environment = {
    MAIL_HOST                  = module.ses.smtp_host
    MAIL_PORT                  = tostring(module.ses.smtp_port)
    MAIL_FROM                  = module.ses.from_address
    MAIL_START_TLS             = "REQUIRED"
    MAIL_LOGIN                 = "REQUIRED"
    MAIL_AUTH_METHODS          = "PLAIN LOGIN"
    MAIL_SES_CONFIGURATION_SET = module.ses.configuration_set_name
  }
}

module "network" {
  source = "./modules/network"

  project     = var.project
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
  az_count    = var.az_count
}

resource "aws_service_discovery_private_dns_namespace" "internal" {
  name        = local.service_discovery_domain
  description = "Private service discovery namespace for ${local.name} ECS services"
  vpc         = module.network.vpc_id
}

module "ecr" {
  source = "./modules/ecr"

  project     = var.project
  environment = var.environment
}

module "alb" {
  source = "./modules/alb"

  project         = var.project
  environment     = var.environment
  vpc_id          = module.network.vpc_id
  subnet_ids      = module.network.public_subnet_ids
  domain_name     = var.domain_name
  route53_zone_id = var.route53_zone_id
  enable_https    = var.alb_enable_https
}

module "email_assets" {
  source = "./modules/s3"

  project     = var.project
  environment = var.environment
  bucket_name = local.email_assets_bucket_name
  assets_path = "${path.module}/assets/email"
}

module "ecs_cluster" {
  source = "./modules/ecs-cluster"

  project     = var.project
  environment = var.environment
  vpc_id      = module.network.vpc_id
  secret_arns = [
    module.rds.secret_arn,
    aws_secretsmanager_secret.rds_ca_bundle.arn,
    aws_secretsmanager_secret.fusionauth_admin.arn,
    aws_secretsmanager_secret.fusionauth_api.arn,
    module.ses.smtp_secret_arn
  ]
}

module "ses" {
  source = "./modules/ses"

  providers = {
    aws = aws.ses
  }

  project                 = var.project
  environment             = var.environment
  domain_name             = var.ses_domain
  route53_zone_id         = var.route53_zone_id
  from_address            = var.ses_from_address
  sandbox_verified_emails = var.ses_sandbox_verified_emails
}

module "rds" {
  source = "./modules/rds"

  project           = var.project
  environment       = var.environment
  vpc_id            = module.network.vpc_id
  subnet_ids        = module.network.private_subnet_ids
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  multi_az          = var.rds_multi_az
}

resource "aws_secretsmanager_secret" "rds_ca_bundle" {
  name                    = "${local.name}/rds/ca-bundle"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "random_password" "fusionauth_admin" {
  length  = 24
  special = false
}

resource "random_uuid" "fusionauth_api_key" {}

resource "aws_secretsmanager_secret" "fusionauth_admin" {
  name                    = "${local.name}/fusionauth/admin"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "fusionauth_admin" {
  secret_id = aws_secretsmanager_secret.fusionauth_admin.id
  secret_string = jsonencode({
    username = var.owner_email
    password = random_password.fusionauth_admin.result
  })
}

resource "aws_secretsmanager_secret" "fusionauth_api" {
  name                    = "${local.name}/fusionauth/api"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "fusionauth_api" {
  secret_id = aws_secretsmanager_secret.fusionauth_api.id
  secret_string = jsonencode({
    api_key = random_uuid.fusionauth_api_key.result
  })
}

data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "backend_task_runtime" {
  statement {
    sid    = "SendEmailThroughSes"
    effect = "Allow"
    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail"
    ]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [module.ses.from_address]
    }
  }

  statement {
    sid       = "ConnectToBookingPostgres"
    effect    = "Allow"
    actions   = ["rds-db:connect"]
    resources = ["arn:aws:rds-db:${var.aws_region}:${data.aws_caller_identity.current.account_id}:dbuser:${module.rds.resource_id}/${module.rds.db_username}"]
  }

  statement {
    sid     = "ReadRuntimeSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      module.rds.secret_arn,
      aws_secretsmanager_secret.rds_ca_bundle.arn,
      module.ses.smtp_secret_arn,
      aws_secretsmanager_secret.fusionauth_api.arn
    ]
  }
}

resource "aws_iam_role" "backend_task" {
  name               = local.backend_task_role_name
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

resource "aws_iam_role_policy" "backend_task" {
  name   = "${local.backend_task_role_name}-runtime"
  role   = aws_iam_role.backend_task.id
  policy = data.aws_iam_policy_document.backend_task_runtime.json
}

module "ecs_frontend" {
  source = "./modules/ecs-service"

  project                        = var.project
  environment                    = var.environment
  service_name                   = "frontend"
  cluster_id                     = module.ecs_cluster.cluster_id
  cluster_name                   = module.ecs_cluster.cluster_name
  execution_role_arn             = module.ecs_cluster.execution_role_arn
  vpc_id                         = module.network.vpc_id
  subnet_ids                     = module.network.private_subnet_ids
  alb_security_group_id          = module.alb.alb_security_group_id
  target_group_arn               = module.alb.frontend_tg_arn
  enable_service_discovery       = true
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.internal.id

  container_image          = "${module.ecr.repository_urls["frontend"]}:${var.frontend_image_tag}"
  container_port           = 8080
  cpu                      = var.fargate_cpu
  memory                   = var.fargate_memory
  desired_count            = 1
  autoscaling_min_capacity = var.app_min_capacity
  autoscaling_max_capacity = var.app_max_capacity
  autoscaling_cpu_target   = var.app_cpu_target

  environment_vars = {
    NGINX_PORT          = "8080"
    NGINX_RESOLVER      = "169.254.169.253"
    BACKEND_UPSTREAM    = local.backend_internal_url
    FUSIONAUTH_UPSTREAM = local.fusionauth_internal_url
  }
}

module "ecs_backend" {
  source = "./modules/ecs-service"

  project                        = var.project
  environment                    = var.environment
  service_name                   = "backend"
  cluster_id                     = module.ecs_cluster.cluster_id
  cluster_name                   = module.ecs_cluster.cluster_name
  execution_role_arn             = module.ecs_cluster.execution_role_arn
  task_role_arn                  = aws_iam_role.backend_task.arn
  vpc_id                         = module.network.vpc_id
  subnet_ids                     = module.network.private_subnet_ids
  alb_security_group_id          = module.alb.alb_security_group_id
  target_group_arn               = module.alb.backend_tg_arn
  enable_service_discovery       = true
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.internal.id

  container_image          = "${module.ecr.repository_urls["backend"]}:${var.backend_image_tag}"
  container_port           = 8080
  cpu                      = var.fargate_cpu
  memory                   = var.fargate_memory
  desired_count            = 1
  autoscaling_min_capacity = var.app_min_capacity
  autoscaling_max_capacity = var.app_max_capacity
  autoscaling_cpu_target   = var.app_cpu_target

  environment_vars = merge({
    QUARKUS_HTTP_PORT                             = "8080"
    QUARKUS_PROFILE                               = "prod"
    DB_JDBC_URL                                   = "jdbc:postgresql://${module.rds.endpoint}:${module.rds.port}/${module.rds.db_name}"
    DB_REACTIVE_URL                               = "postgresql://${module.rds.endpoint}:${module.rds.port}/${module.rds.db_name}"
    DB_REACTIVE_HOSTNAME_VERIFICATION_ALGORITHM   = "HTTPS"
    DB_REACTIVE_SSL_MODE                          = "verify-full"
    DB_REACTIVE_TRUST_CERTIFICATE_PATH            = local.rds_ca_bundle_path
    DB_TRUST_CERTIFICATE_PATH                     = local.rds_ca_bundle_path
    QUARKUS_HIBERNATE_ORM_DATABASE_DEFAULT_SCHEMA = "booking"
    QUARKUS_FLYWAY_SCHEMAS                        = "booking"
    QUARKUS_FLYWAY_DEFAULT_SCHEMA                 = "booking"
    QUARKUS_FLYWAY_CREATE_SCHEMAS                 = "true"
    OIDC_AUTH_SERVER_URL                          = local.fusionauth_internal_url
    OIDC_CLIENT_ID                                = local.fusionauth_application_id
    OIDC_TOKEN_AUDIENCE                           = local.fusionauth_application_id
    OIDC_TOKEN_ISSUER                             = local.public_base_url
    FUSIONAUTH_BASE_URL                           = local.fusionauth_internal_url
    FUSIONAUTH_APPLICATION_ID                     = local.fusionauth_application_id
    FUSIONAUTH_TENANT_ID                          = local.fusionauth_tenant_id
    EMAIL_ASSETS_BASE_URL                         = local.email_assets_base_url
    OWNER_EMAIL                                   = var.owner_email
    OWNER_BOOTSTRAP                               = "true"
    CORS_ORIGINS                                  = local.public_base_url
    PUBLIC_BASE_URL                               = local.public_base_url
  }, local.backend_mail_environment)

  secrets = {
    DB_USERNAME              = "${module.rds.secret_arn}:username::"
    DB_PASSWORD              = "${module.rds.secret_arn}:password::"
    FUSIONAUTH_API_KEY       = "${aws_secretsmanager_secret.fusionauth_api.arn}:api_key::"
    QUARKUS_MAILER_USERNAME  = "${module.ses.smtp_secret_arn}:username::"
    QUARKUS_MAILER_PASSWORD  = "${module.ses.smtp_secret_arn}:password::"
    DB_TRUST_CERTIFICATE_PEM = aws_secretsmanager_secret.rds_ca_bundle.arn
  }
}

module "ecs_fusionauth" {
  source = "./modules/ecs-service"

  project                        = var.project
  environment                    = var.environment
  service_name                   = "fusionauth"
  cluster_id                     = module.ecs_cluster.cluster_id
  cluster_name                   = module.ecs_cluster.cluster_name
  execution_role_arn             = module.ecs_cluster.execution_role_arn
  vpc_id                         = module.network.vpc_id
  subnet_ids                     = module.network.private_subnet_ids
  alb_security_group_id          = module.alb.alb_security_group_id
  target_group_arn               = module.alb.fusionauth_tg_arn
  enable_service_discovery       = true
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.internal.id

  container_image = "${module.ecr.repository_urls["fusionauth"]}:${var.fusionauth_image_tag}"
  container_port  = 9011
  cpu             = 512
  memory          = 1024
  desired_count   = 1

  environment_vars = {
    DATABASE_URL                      = "jdbc:postgresql://${module.rds.endpoint}:${module.rds.port}/${module.rds.db_name}?sslmode=require"
    SEARCH_TYPE                       = "database"
    FUSIONAUTH_APP_RUNTIME_MODE       = "production"
    FUSIONAUTH_APP_MEMORY             = "768M"
    FUSIONAUTH_APP_URL                = local.public_base_url
    FUSIONAUTH_APP_KICKSTART_FILE     = "/usr/local/fusionauth/kickstart/kickstart.json"
    FUSIONAUTH_ALLOWED_ORIGIN         = local.public_base_url
    FUSIONAUTH_PUBLIC_BASE_URL        = local.public_base_url
    FUSIONAUTH_ISSUER                 = local.public_base_url
    FUSIONAUTH_REDIRECT_URL           = "${local.public_base_url}/auth/callback"
    FUSIONAUTH_LOGOUT_URL             = "${local.public_base_url}/"
    FUSIONAUTH_EMAIL_HEADER_IMAGE_URL = "${local.email_assets_base_url}/capitec_background_img_email.jpeg"
    FUSIONAUTH_APPLICATION_ID         = local.fusionauth_application_id
    FUSIONAUTH_SMTP_HOST              = module.ses.smtp_host
    FUSIONAUTH_SMTP_PORT              = tostring(module.ses.smtp_port)
    FUSIONAUTH_SMTP_SECURITY          = "TLS"
    FUSIONAUTH_MAIL_FROM              = module.ses.from_address
  }

  secrets = {
    DATABASE_ROOT_USERNAME                = "${module.rds.secret_arn}:username::"
    DATABASE_ROOT_PASSWORD                = "${module.rds.secret_arn}:password::"
    DATABASE_USERNAME                     = "${module.rds.secret_arn}:username::"
    DATABASE_PASSWORD                     = "${module.rds.secret_arn}:password::"
    FUSIONAUTH_APP_DEFAULT_ADMIN_USERNAME = "${aws_secretsmanager_secret.fusionauth_admin.arn}:username::"
    FUSIONAUTH_APP_DEFAULT_ADMIN_PASSWORD = "${aws_secretsmanager_secret.fusionauth_admin.arn}:password::"
    FUSIONAUTH_API_KEY                    = "${aws_secretsmanager_secret.fusionauth_api.arn}:api_key::"
    FUSIONAUTH_SMTP_USERNAME              = "${module.ses.smtp_secret_arn}:username::"
    FUSIONAUTH_SMTP_PASSWORD              = "${module.ses.smtp_secret_arn}:password::"
  }
}

resource "aws_security_group_rule" "backend_from_frontend" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  security_group_id        = module.ecs_backend.security_group_id
  source_security_group_id = module.ecs_frontend.security_group_id
  description              = "Backend access from frontend reverse proxy"
}

resource "aws_security_group_rule" "fusionauth_from_frontend" {
  type                     = "ingress"
  from_port                = 9011
  to_port                  = 9011
  protocol                 = "tcp"
  security_group_id        = module.ecs_fusionauth.security_group_id
  source_security_group_id = module.ecs_frontend.security_group_id
  description              = "FusionAuth access from frontend reverse proxy"
}

resource "aws_security_group_rule" "fusionauth_from_backend" {
  type                     = "ingress"
  from_port                = 9011
  to_port                  = 9011
  protocol                 = "tcp"
  security_group_id        = module.ecs_fusionauth.security_group_id
  source_security_group_id = module.ecs_backend.security_group_id
  description              = "FusionAuth API access from backend"
}

resource "aws_security_group_rule" "rds_backend_ingress" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = module.rds.security_group_id
  source_security_group_id = module.ecs_backend.security_group_id
  description              = "Postgres from backend"
}

resource "aws_security_group_rule" "rds_fusionauth_ingress" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = module.rds.security_group_id
  source_security_group_id = module.ecs_fusionauth.security_group_id
  description              = "Postgres from fusionauth"
}

