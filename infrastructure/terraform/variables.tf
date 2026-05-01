variable "project" {
  description = "Project name; used as a prefix for resource names."
  type        = string
  default     = "capitec-booking"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*$", var.environment))
    error_message = "environment must use lowercase letters, numbers, and hyphens, and start with a letter."
  }
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "eu-west-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "az_count" {
  description = "Number of AZs to use. Two is the minimum the ALB requires."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2
    error_message = "az_count must be at least 2 because the ALB requires subnets in multiple Availability Zones."
  }
}

variable "domain_name" {
  description = "Optional public DNS name (e.g. booking.example.co.za). Leave empty to use the ALB DNS."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Hosted zone ID for the domain. Leave empty when DNS is managed outside Route53."
  type        = string
  default     = ""
}

variable "alb_enable_https" {
  description = "Enable the ALB HTTPS listener after the ACM certificate for domain_name is issued."
  type        = bool
  default     = true
}

variable "frontend_image_tag" {
  description = "Docker image tag for the frontend service."
  type        = string
  default     = "latest"
}

variable "backend_image_tag" {
  description = "Docker image tag for the backend service."
  type        = string
  default     = "latest"
}

variable "fusionauth_image_tag" {
  description = "Docker image tag for the custom FusionAuth service image."
  type        = string
  default     = "latest"
}

variable "fusionauth_application_id" {
  description = "FusionAuth application/client id used by the SPA and backend."
  type        = string
  default     = "85a03867-dccf-4882-adde-1a79aeec50df"
}

variable "owner_email" {
  description = "Initial owner/admin email seeded into FusionAuth and bootstrapped by the backend."
  type        = string
  default     = "admin@capitec-booking.co.za"
}

variable "fargate_cpu" {
  description = "Fargate CPU units per task (256, 512, 1024...)."
  type        = number
  default     = 256

  validation {
    condition     = contains([256, 512, 1024, 2048, 4096], var.fargate_cpu)
    error_message = "fargate_cpu must be one of 256, 512, 1024, 2048, or 4096."
  }
}

variable "fargate_memory" {
  description = "Fargate memory (MB) per task."
  type        = number
  default     = 512

  validation {
    condition     = var.fargate_memory >= 512
    error_message = "fargate_memory must be at least 512 MB."
  }
}

variable "app_min_capacity" {
  description = "Minimum frontend/backend ECS task count."
  type        = number
  default     = 1

  validation {
    condition     = var.app_min_capacity >= 1
    error_message = "app_min_capacity must be at least 1."
  }
}

variable "app_max_capacity" {
  description = "Maximum frontend/backend ECS task count for target tracking autoscaling."
  type        = number
  default     = 4

  validation {
    condition     = var.app_max_capacity >= 1
    error_message = "app_max_capacity must be at least 1."
  }
}

variable "app_cpu_target" {
  description = "Average CPU percentage target used by ECS target tracking."
  type        = number
  default     = 60

  validation {
    condition     = var.app_cpu_target >= 10 && var.app_cpu_target <= 90
    error_message = "app_cpu_target must be between 10 and 90."
  }
}

variable "rds_instance_class" {
  description = "RDS instance class. db.t4g.micro is the cheapest Graviton-backed option."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20

  validation {
    condition     = var.rds_allocated_storage >= 20
    error_message = "rds_allocated_storage must be at least 20 GB for gp3 PostgreSQL."
  }
}

variable "rds_multi_az" {
  description = "Enable RDS multi-AZ. Off in dev for cost; on in prod."
  type        = bool
  default     = false
}

variable "ses_domain" {
  description = "Domain to verify with SES (typically the same as domain_name). Empty = sandbox-only with verified emails."
  type        = string
  default     = ""
}

variable "ses_region" {
  description = "AWS region used for SES SMTP. Keep app infrastructure in aws_region; use an SMTP-supported SES region such as eu-west-1."
  type        = string
  default     = "eu-west-1"
}

variable "ses_from_address" {
  description = "MAIL FROM address used by the backend mailer. Must belong to the verified domain or be in sandbox_verified_emails."
  type        = string
  default     = "no-reply@capitec-booking.co.za"
}

variable "ses_sandbox_verified_emails" {
  description = "Extra email addresses to verify with SES (useful while still in the sandbox). Each address gets a verification email from AWS."
  type        = list(string)
  default     = []
}

variable "email_assets_bucket_name" {
  description = "Optional globally-unique S3 bucket name for public email images. Leave empty to derive one from project/environment/account."
  type        = string
  default     = ""
}
