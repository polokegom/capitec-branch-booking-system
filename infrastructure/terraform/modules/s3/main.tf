variable "bucket_name" {
  description = "Globally unique S3 bucket name for public assets."
  type        = string
}

variable "assets_path" {
  description = "Local folder containing files to upload publicly."
  type        = string
}

variable "project" {
  type = string
}

variable "environment" {
  type = string
}

locals {
  asset_files = fileset(var.assets_path, "**/*")
  content_types = {
    ".gif"  = "image/gif"
    ".html" = "text/html"
    ".jpeg" = "image/jpeg"
    ".jpg"  = "image/jpeg"
    ".png"  = "image/png"
    ".svg"  = "image/svg+xml"
    ".webp" = "image/webp"
  }
}

resource "aws_s3_bucket" "this" {
  bucket = var.bucket_name

  tags = {
    Project     = var.project
    Environment = var.environment
    Purpose     = "s3"
  }
}

resource "aws_s3_bucket_ownership_controls" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "public_read" {
  statement {
    sid       = "PublicReadAssets"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.public_read.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

resource "aws_s3_object" "assets" {
  for_each = local.asset_files

  bucket       = aws_s3_bucket.this.id
  key          = each.value
  source       = "${var.assets_path}/${each.value}"
  content_type = lookup(local.content_types, lower(regex("\\.[^.]+$", each.value)), "application/octet-stream")
  etag         = filemd5("${var.assets_path}/${each.value}")
}

output "bucket_name" {
  value = aws_s3_bucket.this.bucket
}

output "base_url" {
  value = "https://${aws_s3_bucket.this.bucket}.s3.${data.aws_region.current.name}.amazonaws.com"
}

data "aws_region" "current" {}
