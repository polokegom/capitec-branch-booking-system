project     = "capitec-booking"
environment = "prod"
aws_region  = "af-south-1"

vpc_cidr = "10.30.0.0/16"
az_count = 2

frontend_image_tag   = "stable"
backend_image_tag    = "stable"
fusionauth_image_tag = "stable"

fusionauth_application_id = "85a03867-dccf-4882-adde-1a79aeec50df"
owner_email               = "owner@capitec-booking-system.co.za"

fargate_cpu    = 256
fargate_memory = 512

app_min_capacity = 1
app_max_capacity = 4
app_cpu_target   = 60

rds_instance_class    = "db.t4g.micro"
rds_allocated_storage = 20
rds_multi_az          = false

domain_name      = "app.polokego-booking-system.co.za"
alb_enable_https = true
route53_zone_id  = ""

ses_region = "eu-west-1"

ses_domain       = "polokego-booking-system.co.za"
ses_from_address = "no-reply@polokego-booking-system.co.za"
ses_sandbox_verified_emails = [
  "polokego.work@gmail.com",
  "pjmakgakge@hotmail.com",
  "capitecbookingsystemtestuser@zohomail.com",
  "penelopemaake4@gmail.com",
  "polokego.hackathon@gmail.com",
  "lebzen19@gmail.com"
]
