variable "project" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "instance_class" { type = string }
variable "allocated_storage" { type = number }
variable "multi_az" { type = bool }

locals {
  name_prefix = "${var.project}-${var.environment}"
  db_username = "capitec_app"
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${local.name_prefix}/rds/postgres"
  recovery_window_in_days = var.environment == "prod" ? 30 : 0

}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = local.db_username
    password = random_password.db.result
  })
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Postgres access from application tasks"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${local.name_prefix}-db-subnets"
  subnet_ids = var.subnet_ids

}

resource "aws_db_parameter_group" "this" {
  name   = "${local.name_prefix}-pg16"
  family = "postgres16"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "this" {
  identifier                          = "${local.name_prefix}-pg"
  engine                              = "postgres"
  engine_version                      = "16.13"
  instance_class                      = var.instance_class
  allocated_storage                   = var.allocated_storage
  storage_type                        = "gp3"
  storage_encrypted                   = true
  username                            = jsondecode(aws_secretsmanager_secret_version.db.secret_string).username
  password                            = jsondecode(aws_secretsmanager_secret_version.db.secret_string).password
  db_name                             = "capitec"
  port                                = 5432
  multi_az                            = var.multi_az
  iam_database_authentication_enabled = true
  publicly_accessible                 = false
  db_subnet_group_name                = aws_db_subnet_group.this.name
  vpc_security_group_ids              = [aws_security_group.rds.id]
  parameter_group_name                = aws_db_parameter_group.this.name
  backup_retention_period             = var.environment == "prod" ? 14 : 1
  deletion_protection                 = var.environment == "prod"
  skip_final_snapshot                 = var.environment != "prod"
  apply_immediately                   = var.environment != "prod"
  auto_minor_version_upgrade          = true
  copy_tags_to_snapshot               = true

}

output "endpoint" { value = aws_db_instance.this.address }
output "port" { value = aws_db_instance.this.port }
output "db_name" { value = aws_db_instance.this.db_name }
output "db_username" { value = local.db_username }
output "resource_id" { value = aws_db_instance.this.resource_id }
output "secret_arn" { value = aws_secretsmanager_secret.db.arn }
output "security_group_id" { value = aws_security_group.rds.id }
