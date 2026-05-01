project     = "capitec-booking"
environment = "dev"
aws_region  = "af-south-1"

vpc_cidr = "10.20.0.0/16"
az_count = 2

frontend_image_tag   = "latest"
backend_image_tag    = "latest"
fusionauth_image_tag = "latest"

fusionauth_application_id = "85a03867-dccf-4882-adde-1a79aeec50df"
owner_email               = "admin@capitec-booking.co.za"

fargate_cpu    = 256
fargate_memory = 512

app_min_capacity = 1
app_max_capacity = 2
app_cpu_target   = 65

rds_instance_class    = "db.t4g.micro"
rds_allocated_storage = 20
rds_multi_az          = false

domain_name     = ""
route53_zone_id = ""

ses_domain                  = ""
ses_from_address            = "no-reply@example.co.za"
ses_sandbox_verified_emails = ["dev-team@example.co.za"]
