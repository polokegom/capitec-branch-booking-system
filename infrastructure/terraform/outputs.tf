output "alb_dns_name" {
  value       = module.alb.alb_dns_name
  description = "Public ALB DNS name. Browse to http://<this>/ to load the app."
}

output "public_base_url" {
  value       = local.public_base_url
  description = "Public URL used for frontend, FusionAuth issuer, redirects, and verification email links."
}

output "email_assets_bucket_name" {
  value       = module.email_assets.bucket_name
  description = "Public S3 bucket that hosts email image assets."
}

output "email_assets_base_url" {
  value       = local.email_assets_base_url
  description = "Public base URL for email images. Use this for EMAIL_ASSETS_BASE_URL."
}

output "alb_certificate_arn" {
  value       = module.alb.certificate_arn
  description = "ACM certificate ARN for the optional public domain."
}

output "alb_certificate_validation_records" {
  value       = module.alb.certificate_validation_records
  description = "DNS CNAME records required to validate the ALB HTTPS certificate when DNS is external."
}

output "rds_endpoint" {
  value     = module.rds.endpoint
  sensitive = true
}

output "rds_secret_arn" {
  value     = module.rds.secret_arn
  sensitive = true
}

output "rds_ca_bundle_secret_arn" {
  value       = aws_secretsmanager_secret.rds_ca_bundle.arn
  description = "Secrets Manager secret that stores the RDS CA certificate bundle injected into the backend task."
}

output "fusionauth_admin_secret_arn" {
  value     = aws_secretsmanager_secret.fusionauth_admin.arn
  sensitive = true
}

output "fusionauth_api_secret_arn" {
  value     = aws_secretsmanager_secret.fusionauth_api.arn
  sensitive = true
}

output "backend_task_role_name" {
  value       = aws_iam_role.backend_task.name
  description = "Backend ECS task role. In prod this is capitec-booking-system."
}

output "internal_service_namespace" {
  value       = aws_service_discovery_private_dns_namespace.internal.name
  description = "Private Cloud Map namespace used by ECS services, e.g. backend.<namespace>."
}

output "ecr_frontend_repository_url" { value = module.ecr.repository_urls["frontend"] }
output "ecr_backend_repository_url" { value = module.ecr.repository_urls["backend"] }
output "ecr_fusionauth_repository_url" { value = module.ecr.repository_urls["fusionauth"] }

output "ses_smtp_secret_arn" {
  value     = module.ses.smtp_secret_arn
  sensitive = true
}

output "ses_smtp_host" { value = module.ses.smtp_host }
output "ses_region" { value = var.ses_region }
output "ses_from_address" { value = module.ses.from_address }
output "ses_configuration_set_name" { value = module.ses.configuration_set_name }
output "ses_feedback_topic_arn" { value = module.ses.feedback_topic_arn }
output "ses_feedback_queue_arn" { value = module.ses.feedback_queue_arn }
output "ses_feedback_queue_url" { value = module.ses.feedback_queue_url }
output "ses_domain_identity_arn" { value = module.ses.domain_identity_arn }

output "ses_domain_verification_record" {
  value       = module.ses.domain_verification_record
  description = "Add this TXT record to external DNS when route53_zone_id is empty."
}

output "ses_domain_dkim_records" {
  value       = module.ses.domain_dkim_records
  description = "Add these CNAME records to external DNS when route53_zone_id is empty."
}

output "ses_domain_spf_record" {
  value       = module.ses.domain_spf_record
  description = "Recommended SPF record. Merge with any existing SPF record for the domain."
}

output "ses_mail_from_mx_record" {
  value       = module.ses.mail_from_mx_record
  description = "Add this MX record to external DNS for the custom SES MAIL FROM domain."
}

output "ses_mail_from_spf_record" {
  value       = module.ses.mail_from_spf_record
  description = "Add this TXT record to external DNS for the custom SES MAIL FROM domain."
}

output "ses_dmarc_record" {
  value       = module.ses.dmarc_record
  description = "Recommended DMARC TXT record for the SES sender domain."
}

output "ses_sandbox_verification_pending" {
  value       = module.ses.verification_pending_emails
  description = "Each of these addresses must click the AWS verification link before SES will send to them while the account is in the sandbox."
}
