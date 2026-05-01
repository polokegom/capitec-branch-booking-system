variable "project" { type = string }
variable "environment" { type = string }
variable "service_name" { type = string }
variable "cluster_id" { type = string }
variable "cluster_name" {
  type        = string
  description = "ECS cluster name, required when autoscaling is enabled."
  default     = ""
}
variable "execution_role_arn" { type = string }
variable "task_role_arn" {
  type    = string
  default = ""
}
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "alb_security_group_id" {
  type        = string
  description = "SG of the ALB. Required when attach_to_alb = true."
  default     = ""
}
variable "target_group_arn" {
  type        = string
  description = "Target group to register tasks against. Required when attach_to_alb = true."
  default     = ""
}
variable "attach_to_alb" {
  type        = bool
  description = "Whether the service is fronted by the ALB. Headless workers (e.g. cron) set this to false."
  default     = true
}
variable "container_image" { type = string }
variable "container_port" {
  type    = number
  default = 8080
}
variable "cpu" {
  type    = number
  default = 256
}
variable "memory" {
  type    = number
  default = 512
}
variable "desired_count" {
  type    = number
  default = 1
}
variable "autoscaling_min_capacity" {
  type        = number
  description = "Minimum ECS desired count. Set to 0 to disable autoscaling."
  default     = 0
}
variable "autoscaling_max_capacity" {
  type        = number
  description = "Maximum ECS desired count. Set to 0 to disable autoscaling."
  default     = 0
}
variable "autoscaling_cpu_target" {
  type        = number
  description = "Average CPU percentage target for ECS target tracking."
  default     = 60
}
variable "environment_vars" {
  type    = map(string)
  default = {}
}
variable "secrets" {
  description = "Map of envVarName => Secrets Manager secret ARN (or ARN with json key suffix)."
  type        = map(string)
  default     = {}
}
variable "service_discovery_namespace_id" {
  description = "Cloud Map private DNS namespace ID. Leave empty to disable service discovery."
  type        = string
  default     = ""
}
variable "enable_service_discovery" {
  description = "Create a Cloud Map service registration for this ECS service."
  type        = bool
  default     = false
}
variable "service_discovery_name" {
  description = "Cloud Map service name. Defaults to service_name when empty."
  type        = string
  default     = ""
}

locals {
  name_prefix              = "${var.project}-${var.environment}-${var.service_name}"
  use_service_discovery    = var.enable_service_discovery
  service_discovery_record = var.service_discovery_name != "" ? var.service_discovery_name : var.service_name
  use_autoscaling          = var.autoscaling_max_capacity > 0 && var.cluster_name != ""
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = var.environment == "prod" ? 30 : 7

}

resource "aws_security_group" "service" {
  name        = "${local.name_prefix}-sg"
  description = var.attach_to_alb ? "Inbound from ALB only" : "Egress-only (headless worker)"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group_rule" "alb_ingress" {
  count = var.attach_to_alb ? 1 : 0

  type                     = "ingress"
  from_port                = var.container_port
  to_port                  = var.container_port
  protocol                 = "tcp"
  security_group_id        = aws_security_group.service.id
  source_security_group_id = var.alb_security_group_id
  description              = "Service traffic from ALB"
}

resource "aws_ecs_task_definition" "this" {
  family                   = local.name_prefix
  cpu                      = var.cpu
  memory                   = var.memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn != "" ? var.task_role_arn : var.execution_role_arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name      = var.service_name
    image     = var.container_image
    essential = true
    portMappings = [{
      containerPort = var.container_port
      hostPort      = var.container_port
      protocol      = "tcp"
    }]
    environment = [for k, v in var.environment_vars : { name = k, value = v }]
    secrets     = [for k, v in var.secrets : { name = k, valueFrom = v }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = var.service_name
      }
    }
  }])
}

data "aws_region" "current" {}

resource "aws_service_discovery_service" "this" {
  count = local.use_service_discovery ? 1 : 0

  name = local.service_discovery_record

  dns_config {
    namespace_id   = var.service_discovery_namespace_id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_ecs_service" "this" {
  name            = local.name_prefix
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.attach_to_alb ? [1] : []
    content {
      target_group_arn = var.target_group_arn
      container_name   = var.service_name
      container_port   = var.container_port
    }
  }

  dynamic "service_registries" {
    for_each = local.use_service_discovery ? [1] : []
    content {
      registry_arn = aws_service_discovery_service.this[0].arn
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [desired_count]
  }
}

output "service_name" { value = aws_ecs_service.this.name }
output "security_group_id" { value = aws_security_group.service.id }
output "log_group_name" { value = aws_cloudwatch_log_group.this.name }
output "service_discovery_name" {
  value = local.use_service_discovery ? aws_service_discovery_service.this[0].name : ""
}

resource "aws_appautoscaling_target" "this" {
  count = local.use_autoscaling ? 1 : 0

  max_capacity       = var.autoscaling_max_capacity
  min_capacity       = var.autoscaling_min_capacity > 0 ? var.autoscaling_min_capacity : var.desired_count
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.this.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  count = local.use_autoscaling ? 1 : 0

  name               = "${local.name_prefix}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.this[0].resource_id
  scalable_dimension = aws_appautoscaling_target.this[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.this[0].service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_cpu_target
    scale_in_cooldown  = 180
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
