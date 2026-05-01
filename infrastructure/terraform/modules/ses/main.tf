terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

variable "project" { type = string }
variable "environment" { type = string }

variable "domain_name" {
  description = "Domain to verify with SES (e.g. capitec-booking.example.co.za). Empty = skip domain identity."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID for the domain. Empty = output DNS records for manual/external DNS setup."
  type        = string
  default     = ""
}

variable "from_address" {
  description = "MAIL FROM address the backend should use (e.g. no-reply@booking.example.co.za)."
  type        = string
}

variable "sandbox_verified_emails" {
  description = "Email addresses to verify as SES identities (for sandbox dev). Each will receive a verification link."
  type        = list(string)
  default     = []
}

locals {
  name_prefix        = "${var.project}-${var.environment}"
  use_domain         = var.domain_name != ""
  manage_route53_dns = local.use_domain && var.route53_zone_id != ""
  mail_from_domain   = local.use_domain ? "mail.${var.domain_name}" : ""
  smtp_endpoint      = "email-smtp.${data.aws_region.current.name}.amazonaws.com"
}

data "aws_region" "current" {}

resource "aws_sesv2_account_suppression_attributes" "this" {
  suppressed_reasons = ["BOUNCE", "COMPLAINT"]
}

resource "aws_sesv2_configuration_set" "transactional" {
  configuration_set_name = "${local.name_prefix}-transactional"

  delivery_options {
    tls_policy = "REQUIRE"
  }

  reputation_options {
    reputation_metrics_enabled = true
  }

  sending_options {
    sending_enabled = true
  }

  suppression_options {
    suppressed_reasons = ["BOUNCE", "COMPLAINT"]
  }
}

resource "aws_sns_topic" "ses_feedback" {
  name = "${local.name_prefix}-ses-feedback"

}

resource "aws_sqs_queue" "ses_feedback_dlq" {
  name                       = "${local.name_prefix}-ses-feedback-dlq"
  message_retention_seconds  = 1209600
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = 30

}

resource "aws_sqs_queue" "ses_feedback" {
  name                       = "${local.name_prefix}-ses-feedback"
  message_retention_seconds  = 1209600
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ses_feedback_dlq.arn
    maxReceiveCount     = 5
  })

}

data "aws_iam_policy_document" "ses_feedback_queue" {
  statement {
    sid     = "AllowSesFeedbackTopic"
    effect  = "Allow"
    actions = ["sqs:SendMessage"]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    resources = [aws_sqs_queue.ses_feedback.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.ses_feedback.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "ses_feedback" {
  queue_url = aws_sqs_queue.ses_feedback.id
  policy    = data.aws_iam_policy_document.ses_feedback_queue.json
}

resource "aws_sns_topic_subscription" "ses_feedback_queue" {
  topic_arn            = aws_sns_topic.ses_feedback.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.ses_feedback.arn
  raw_message_delivery = true

  depends_on = [aws_sqs_queue_policy.ses_feedback]
}

resource "aws_sesv2_configuration_set_event_destination" "ses_feedback" {
  configuration_set_name = aws_sesv2_configuration_set.transactional.configuration_set_name
  event_destination_name = "${local.name_prefix}-sqs-feedback"

  event_destination {
    enabled              = true
    matching_event_types = ["BOUNCE", "COMPLAINT", "REJECT"]

    sns_destination {
      topic_arn = aws_sns_topic.ses_feedback.arn
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "ses_bounce_rate" {
  alarm_name          = "${local.name_prefix}-ses-bounce-rate-high"
  alarm_description   = "SES bounce rate reached 2 percent. Review recipient quality before AWS reputation enforcement."
  namespace           = "AWS/SES"
  metric_name         = "Reputation.BounceRate"
  statistic           = "Average"
  period              = 3600
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 0.02
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

}

resource "aws_cloudwatch_metric_alarm" "ses_complaint_rate" {
  alarm_name          = "${local.name_prefix}-ses-complaint-rate-high"
  alarm_description   = "SES complaint rate reached 0.1 percent. Stop non-essential sends and review recipient consent."
  namespace           = "AWS/SES"
  metric_name         = "Reputation.ComplaintRate"
  statistic           = "Average"
  period              = 3600
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 0.001
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

}

resource "aws_ses_domain_identity" "this" {
  count  = local.use_domain ? 1 : 0
  domain = var.domain_name
}

resource "aws_route53_record" "domain_verification" {
  count   = local.manage_route53_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = "_amazonses.${var.domain_name}"
  type    = "TXT"
  ttl     = 600
  records = [aws_ses_domain_identity.this[0].verification_token]
}

resource "aws_ses_domain_dkim" "this" {
  count  = local.use_domain ? 1 : 0
  domain = aws_ses_domain_identity.this[0].domain
}

resource "aws_route53_record" "dkim" {
  count   = local.manage_route53_dns ? 3 : 0
  zone_id = var.route53_zone_id
  name    = "${aws_ses_domain_dkim.this[0].dkim_tokens[count.index]}._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = 600
  records = ["${aws_ses_domain_dkim.this[0].dkim_tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_ses_domain_identity_verification" "this" {
  count      = local.manage_route53_dns ? 1 : 0
  domain     = aws_ses_domain_identity.this[0].id
  depends_on = [aws_route53_record.domain_verification]
}

resource "aws_route53_record" "spf" {
  count   = local.manage_route53_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "TXT"
  ttl     = 600
  records = ["v=spf1 include:amazonses.com ~all"]
}

resource "aws_ses_domain_mail_from" "this" {
  count                  = local.use_domain ? 1 : 0
  domain                 = aws_ses_domain_identity.this[0].domain
  mail_from_domain       = local.mail_from_domain
  behavior_on_mx_failure = "UseDefaultValue"
}

resource "aws_route53_record" "mail_from_mx" {
  count   = local.manage_route53_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = local.mail_from_domain
  type    = "MX"
  ttl     = 600
  records = ["10 feedback-smtp.${data.aws_region.current.name}.amazonses.com"]
}

resource "aws_route53_record" "mail_from_spf" {
  count   = local.manage_route53_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = local.mail_from_domain
  type    = "TXT"
  ttl     = 600
  records = ["v=spf1 include:amazonses.com ~all"]
}

resource "aws_route53_record" "dmarc" {
  count   = local.manage_route53_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = "_dmarc.${var.domain_name}"
  type    = "TXT"
  ttl     = 600
  records = ["v=DMARC1; p=none; adkim=r; aspf=r"]
}

resource "aws_ses_email_identity" "sandbox" {
  for_each = toset(var.sandbox_verified_emails)
  email    = each.value
}

resource "aws_iam_user" "smtp" {
  name = "${local.name_prefix}-ses-smtp"
  path = "/system/"
  tags = {
    Purpose = "SES SMTP user for ${local.name_prefix} backend"
  }
}

data "aws_iam_policy_document" "smtp_send" {
  statement {
    sid    = "AllowSendEmailFromConfiguredAddress"
    effect = "Allow"
    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail"
    ]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.from_address]
    }
  }
}

resource "aws_iam_user_policy" "smtp" {
  name   = "${local.name_prefix}-ses-send"
  user   = aws_iam_user.smtp.name
  policy = data.aws_iam_policy_document.smtp_send.json
}

resource "aws_iam_access_key" "smtp" {
  user = aws_iam_user.smtp.name
}

resource "aws_secretsmanager_secret" "smtp" {
  name                    = "${local.name_prefix}/ses/smtp"
  description             = "SES SMTP username + password for the backend mailer."
  recovery_window_in_days = var.environment == "prod" ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "smtp" {
  secret_id = aws_secretsmanager_secret.smtp.id
  secret_string = jsonencode({
    username = aws_iam_access_key.smtp.id
    password = aws_iam_access_key.smtp.ses_smtp_password_v4
    host     = local.smtp_endpoint
    port     = "587"
    from     = var.from_address
  })
}

output "smtp_secret_arn" { value = aws_secretsmanager_secret.smtp.arn }
output "smtp_host" { value = local.smtp_endpoint }
output "smtp_port" { value = 587 }
output "from_address" { value = var.from_address }
output "configuration_set_name" { value = aws_sesv2_configuration_set.transactional.configuration_set_name }
output "feedback_topic_arn" { value = aws_sns_topic.ses_feedback.arn }
output "feedback_queue_arn" { value = aws_sqs_queue.ses_feedback.arn }
output "feedback_queue_url" { value = aws_sqs_queue.ses_feedback.url }

output "domain_identity_arn" {
  value       = local.use_domain ? aws_ses_domain_identity.this[0].arn : ""
  description = "ARN of the domain identity (empty if no domain was configured)."
}

output "domain_verification_record" {
  value = local.use_domain ? {
    name  = "_amazonses.${var.domain_name}"
    type  = "TXT"
    value = aws_ses_domain_identity.this[0].verification_token
    ttl   = 600
  } : null
  description = "DNS TXT record required to verify the SES domain identity."
}

output "domain_dkim_records" {
  value = local.use_domain ? [
    for token in aws_ses_domain_dkim.this[0].dkim_tokens : {
      name  = "${token}._domainkey.${var.domain_name}"
      type  = "CNAME"
      value = "${token}.dkim.amazonses.com"
      ttl   = 600
    }
  ] : []
  description = "DNS CNAME records required for SES DKIM signing."
}

output "domain_spf_record" {
  value = local.use_domain ? {
    name  = var.domain_name
    type  = "TXT"
    value = "v=spf1 include:amazonses.com ~all"
    ttl   = 600
  } : null
  description = "Recommended SPF record. Merge include:amazonses.com into an existing SPF TXT record if one already exists."
}

output "mail_from_mx_record" {
  value = local.use_domain ? {
    name     = local.mail_from_domain
    type     = "MX"
    priority = 10
    value    = "feedback-smtp.${data.aws_region.current.name}.amazonses.com"
    ttl      = 600
  } : null
  description = "DNS MX record required for the custom SES MAIL FROM domain."
}

output "mail_from_spf_record" {
  value = local.use_domain ? {
    name  = local.mail_from_domain
    type  = "TXT"
    value = "v=spf1 include:amazonses.com ~all"
    ttl   = 600
  } : null
  description = "DNS TXT record required for SPF on the custom SES MAIL FROM domain."
}

output "dmarc_record" {
  value = local.use_domain ? {
    name  = "_dmarc.${var.domain_name}"
    type  = "TXT"
    value = "v=DMARC1; p=none; adkim=r; aspf=r"
    ttl   = 600
  } : null
  description = "Recommended DMARC record for mailbox provider trust. p=none observes without rejecting mail."
}

output "verification_pending_emails" {
  value       = [for e in aws_ses_email_identity.sandbox : e.email]
  description = "Sandbox identities awaiting click-through verification."
}
